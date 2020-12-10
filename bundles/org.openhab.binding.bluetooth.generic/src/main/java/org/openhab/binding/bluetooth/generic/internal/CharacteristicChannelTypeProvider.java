/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.bluetooth.generic.internal;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.bluetooth.BluetoothBindingConstants;
import org.openhab.binding.bluetooth.BluetoothCharacteristic;
import org.openhab.core.thing.type.ChannelType;
import org.openhab.core.thing.type.ChannelTypeBuilder;
import org.openhab.core.thing.type.ChannelTypeProvider;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.types.StateDescriptionFragmentBuilder;
import org.openhab.core.types.StateOption;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.gattparser.BluetoothGattParser;
import org.sputnikdev.bluetooth.gattparser.BluetoothGattParserFactory;
import org.sputnikdev.bluetooth.gattparser.spec.Enumerations;
import org.sputnikdev.bluetooth.gattparser.spec.Field;

/**
 * {@link CharacteristicChannelTypeProvider} that provides channel types for dynamically discovered characteristics.
 *
 * @author Vlad Kolotov - Original author
 * @author Connor Petty - Modified for openHAB use.
 */
@NonNullByDefault
@Component(service = { CharacteristicChannelTypeProvider.class, ChannelTypeProvider.class })
public class CharacteristicChannelTypeProvider implements ChannelTypeProvider {

    private static final String CHANNEL_TYPE_NAME_PATTERN = "characteristic-%s-%s-%s-%s";

    private final Logger logger = LoggerFactory.getLogger(CharacteristicChannelTypeProvider.class);

    private final @NonNullByDefault({}) Map<ChannelTypeUID, ChannelType> cache = new ConcurrentHashMap<>();

    private final BluetoothGattParser gattParser = BluetoothGattParserFactory.getDefault();

    @Override
    public Collection<ChannelType> getChannelTypes(@Nullable Locale locale) {
        return cache.values();
    }

    @Override
    public @Nullable ChannelType getChannelType(ChannelTypeUID channelTypeUID, @Nullable Locale locale) {
        if (isValidUID(channelTypeUID)) {
            return cache.computeIfAbsent(channelTypeUID, uid -> {
                // characteristic-advncd-10-00002a04-0000-1000-8000-00805f9b34fb-Battery_Level
                String channelID = uid.getId();
                boolean advanced = "advncd".equals(channelID.substring(15, 21));
                int properties;
                try {
                    properties = Integer.parseUnsignedInt(channelID.substring(22, 24), 16);
                } catch (NumberFormatException ex) {
                    return null;
                }
                String characteristicUUID = channelID.substring(25, 61);
                String fieldName = channelID.substring(62, channelID.length());

                if (gattParser.isKnownCharacteristic(characteristicUUID)) {
                    List<Field> fields = gattParser.getFields(characteristicUUID).stream()
                            .filter(field -> BluetoothChannelUtils.encodeFieldID(field).equals(fieldName))
                            .collect(Collectors.toList());

                    if (fields.size() > 1) {
                        logger.warn("Multiple fields with the same name found: {} / {}. Skipping them.",
                                characteristicUUID, fieldName);
                        return null;
                    }
                    Field field = fields.get(0);
                    return buildChannelType(uid, advanced, properties, field);
                }
                return null;
            });
        }
        return null;
    }

    private static boolean isValidUID(ChannelTypeUID channelTypeUID) {
        if (!channelTypeUID.getBindingId().equals(BluetoothBindingConstants.BINDING_ID)) {
            return false;
        }
        String channelID = channelTypeUID.getId();
        if (!channelID.startsWith("characteristic")) {
            return false;
        }
        if (channelID.length() < 68) {
            return false;
        }
        if (channelID.charAt(21) != '-') {
            return false;
        }
        if (channelID.charAt(30) != '-') {
            return false;
        }
        if (channelID.charAt(67) != '-') {
            return false;
        }
        return true;
    }

    public ChannelTypeUID registerChannelType(String characteristicUUID, boolean advanced, int properties,
            Field field) {
        // characteristic-advncd-10-00002a04-0000-1000-8000-00805f9b34fb-Battery_Level

        String propStr = String.format("%02x", properties);

        String channelType = String.format(CHANNEL_TYPE_NAME_PATTERN, advanced ? "advncd" : "simple", propStr,
                characteristicUUID, BluetoothChannelUtils.encodeFieldID(field));

        ChannelTypeUID channelTypeUID = new ChannelTypeUID(BluetoothBindingConstants.BINDING_ID, channelType);
        cache.computeIfAbsent(channelTypeUID, uid -> buildChannelType(uid, advanced, properties, field));
        logger.debug("registered channel type: {}", channelTypeUID);
        return channelTypeUID;
    }

    private ChannelType buildChannelType(ChannelTypeUID channelTypeUID, boolean advanced, int properties, Field field) {
        String itemType = BluetoothChannelUtils.getItemType(field);

        if (itemType == null) {
            throw new IllegalStateException("Unknown field format type: " + field.getUnit());
        }

        List<StateOption> options = itemType.equals("Switch") ? List.of() : getStateOptions(field);

        // we consider it readOnly if it cannot be written to
        boolean readOnly = (properties
                & (BluetoothCharacteristic.PROPERTY_WRITE | BluetoothCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) == 0;

        StateDescriptionFragmentBuilder stateDescBuilder = StateDescriptionFragmentBuilder.create()//
                .withPattern(getPattern(field))//
                .withReadOnly(readOnly)//
                .withOptions(options);

        BigDecimal min = toBigDecimal(field.getMinimum());
        BigDecimal max = toBigDecimal(field.getMaximum());
        if (min != null) {
            stateDescBuilder = stateDescBuilder.withMinimum(min);
        }
        if (max != null) {
            stateDescBuilder = stateDescBuilder.withMaximum(max);
        }
        ChannelTypeBuilder<?> builder = ChannelTypeBuilder.state(channelTypeUID, field.getName(), itemType)//
                .isAdvanced(advanced)//
                .withDescription(field.getInformativeText())//
                .withStateDescriptionFragment(stateDescBuilder.build());

        if ((properties & BluetoothCharacteristic.PROPERTY_NOTIFY) > 0) {
            builder.withConfigDescriptionURI(URI.create("channel-type:bluetooth:notifyChannel"));
        }

        return builder.build();
    }

    private static List<StateOption> getStateOptions(Field field) {
        return Optional.ofNullable(field.getEnumerations())//
                .map(Enumerations::getEnumerations)//
                .stream()//
                .flatMap(List::stream)
                .map(enumeration -> new StateOption(String.valueOf(enumeration.getKey()), enumeration.getValue()))
                .collect(Collectors.toList());
    }

    private static @Nullable BigDecimal toBigDecimal(@Nullable Double value) {
        return value != null ? BigDecimal.valueOf(value) : null;
    }

    private static String getPattern(Field field) {
        String format = getFormat(field);
        String unit = getUnit(field);
        StringBuilder pattern = new StringBuilder();
        pattern.append(format);
        if (unit != null) {
            pattern.append(" ").append(unit);
        }
        return pattern.toString();
    }

    private static String getFormat(Field field) {
        Integer decimalExponent = field.getDecimalExponent();
        if (field.getFormat().isReal() && decimalExponent != null && decimalExponent < 0) {
            return "%." + Math.abs(decimalExponent) + "f";
        }
        return "%s";
    }

    private static @Nullable String getUnit(Field field) {
        String gattUnit = field.getUnit();
        if (gattUnit != null) {
            BluetoothUnit unit = BluetoothUnit.findByType(gattUnit);
            if (unit != null) {
                if (unit.getQuantityClass() != null) {
                    return "%unit%";
                }
                return unit.getUnit().getSymbol();
            }
        }
        return null;
    }
}
