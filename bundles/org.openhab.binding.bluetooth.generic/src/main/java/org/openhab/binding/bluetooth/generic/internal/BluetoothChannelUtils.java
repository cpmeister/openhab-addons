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
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.gattparser.BluetoothGattParser;
import org.sputnikdev.bluetooth.gattparser.FieldHolder;
import org.sputnikdev.bluetooth.gattparser.GattRequest;
import org.sputnikdev.bluetooth.gattparser.spec.Enumeration;
import org.sputnikdev.bluetooth.gattparser.spec.Field;
import org.sputnikdev.bluetooth.gattparser.spec.FieldFormat;

/**
 * The {@link BluetoothChannelUtils} contains utility functions used by the GattChannelHandler
 *
 * @author Vlad Kolotov - Original author
 * @author Connor Petty - Modified for openHAB use
 */
@NonNullByDefault
public class BluetoothChannelUtils {

    private static final Logger logger = LoggerFactory.getLogger(BluetoothChannelUtils.class);

    public static String encodeFieldID(Field field) {
        String requirements = Optional.ofNullable(field.getRequirements()).orElse(Collections.emptyList()).stream()
                .collect(Collectors.joining());
        return encodeFieldName(field.getName() + requirements);
    }

    public static String encodeFieldName(String fieldName) {
        return Base64.getEncoder().encodeToString(fieldName.getBytes(StandardCharsets.UTF_8)).replace("=", "");
    }

    public static String decodeFieldName(String encodedFieldName) {
        return new String(Base64.getDecoder().decode(encodedFieldName), StandardCharsets.UTF_8);
    }

    public static @Nullable String getItemType(Field field) {
        FieldFormat format = field.getFormat();
        if (format == null) {
            // unknown format
            return null;
        }
        if (format.isBoolean()) {
            return "Switch";
        }
        if (format.isNumber()) {
            BluetoothUnit unit = BluetoothUnit.findByType(field.getUnit());
            if (unit != null) {
                Class<?> quantityClass = unit.getQuantityClass();
                if (quantityClass != null) {
                    return "Number:" + quantityClass.getSimpleName();
                }
            }
            return "Number";
        }
        if (format.isString()) {
            return "String";
        }
        if (format.isStruct()) {
            return "String";
        }
        // unsupported format
        return null;
    }

    public static State convert(BluetoothGattParser parser, FieldHolder holder) {
        if (!holder.isValueSet()) {
            return UnDefType.UNDEF;
        }
        Field field = holder.getField();
        FieldFormat format = field.getFormat();
        if (format.isBoolean()) {
            return OnOffType.from(Boolean.TRUE.equals(holder.getBoolean()));
        }
        // check if we can use enumerations
        if (field.hasEnumerations()) {
            Enumeration enumeration = holder.getEnumeration();
            if (enumeration != null) {
                if (format.isNumber()) {
                    return new DecimalType(new BigDecimal(enumeration.getKey()));
                } else {
                    return new StringType(enumeration.getKey().toString());
                }
            }
            // fall back to simple types
        }
        if (format.isNumber()) {
            BluetoothUnit unit = BluetoothUnit.findByType(field.getUnit());
            if (unit != null) {
                Class<?> quantityClass = unit.getQuantityClass();
                if (quantityClass != null) {
                    return new QuantityType<>(holder.getBigDecimal(), unit.getUnit());
                }
            }
            return new DecimalType(holder.getBigDecimal());
        }
        if (format.isString()) {
            return new StringType(holder.getString());
        }
        if (format.isStruct()) {
            return new StringType(parser.parse(holder.getBytes(), 16));
        }
        // unsupported format
        return UnDefType.UNDEF;
    }

    public static void updateHolder(BluetoothGattParser parser, GattRequest request, String fieldName, State state) {
        Field field = request.getFieldHolder(fieldName).getField();
        FieldFormat format = field.getFormat();
        if (format.isBoolean()) {
            OnOffType onOffType = state.as(OnOffType.class);
            if (onOffType == null) {
                logger.debug("Could not convert state to OnOffType: {} : {} : {} ", request.getCharacteristicUUID(),
                        fieldName, state);
                return;
            }
            request.setField(fieldName, onOffType == OnOffType.ON);
            return;
        }
        if (field.hasEnumerations()) {
            // check if we can use enumerations
            Enumeration enumeration = getEnumeration(field, state);
            if (enumeration != null) {
                request.setField(fieldName, enumeration);
                return;
            } else {
                logger.debug("Could not convert state to enumeration: {} : {} : {} ", request.getCharacteristicUUID(),
                        fieldName, state);
            }
            // fall back to simple types
        }
        if (format.isNumber()) {
            Number number = null;
            if (state instanceof QuantityType) {
                QuantityType<?> quantity = (QuantityType<?>) state;
                BluetoothUnit unit = BluetoothUnit.findByType(field.getUnit());
                if (unit != null && unit.getQuantityClass() != null) {
                    quantity = quantity.toUnit(unit.getUnit());
                }
                number = quantity;
            } else {
                number = state.as(DecimalType.class);
            }
            if (number == null) {
                logger.debug("Could not convert state to a Number: {} : {} : {} ", request.getCharacteristicUUID(),
                        fieldName, state);
                return;
            }
            if (format.isReal()) {
                request.setField(fieldName, number.longValue());
                return;
            }
            if (format.isDecimal()) {
                request.setField(fieldName, number.doubleValue());
                return;
            }
        }
        if (format.isString() || format.isStruct()) {
            StringType textType = state.as(StringType.class);
            if (textType == null) {
                logger.debug("Could not convert state to StringType: {} : {} : {} ", request.getCharacteristicUUID(),
                        fieldName, state);
                return;
            }
            if (format.isString()) {
                request.setField(fieldName, textType.toString());
                return;
            }
            String text = textType.toString().trim();
            if (text.startsWith("[")) {
                request.setField(fieldName, parser.serialize(text, 16));
            } else {
                request.setField(fieldName, new BigInteger(text));
            }
            return;
        }
    }

    private static @Nullable Enumeration getEnumeration(Field field, State state) {
        DecimalType decimalType = state.as(DecimalType.class);
        if (decimalType != null) {
            try {
                return field.getEnumeration(new BigInteger(decimalType.toString()));
            } catch (NumberFormatException ex) {
                // do nothing
            }
        }
        return null;
    }
}
