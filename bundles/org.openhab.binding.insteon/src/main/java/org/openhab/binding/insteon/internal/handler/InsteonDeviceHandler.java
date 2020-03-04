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
package org.openhab.binding.insteon.internal.handler;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.thing.binding.builder.ThingBuilder;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.insteon.internal.InsteonBinding;
import org.openhab.binding.insteon.internal.InsteonBindingConstants;
import org.openhab.binding.insteon.internal.config.InsteonChannelConfiguration;
import org.openhab.binding.insteon.internal.config.InsteonDeviceConfiguration;
import org.openhab.binding.insteon.internal.device.DeviceFeature;
import org.openhab.binding.insteon.internal.device.DeviceTypeLoader;
import org.openhab.binding.insteon.internal.device.InsteonAddress;
import org.openhab.binding.insteon.internal.device.InsteonDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link InsteonDeviceHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Rob Nielsen - Initial contribution
 */
@NonNullByDefault
@SuppressWarnings("null")
public class InsteonDeviceHandler extends BaseThingHandler {

    private static final Set<String> DEFINED_CHANNELS = Collections.unmodifiableSet(Stream.of(
            InsteonBindingConstants.AC_DELAY, InsteonBindingConstants.BACKLIGHT_DURATION,
            InsteonBindingConstants.BATTERY_LEVEL, InsteonBindingConstants.BATTERY_WATERMARK_LEVEL,
            InsteonBindingConstants.BOTTOM_OUTLET, InsteonBindingConstants.BUTTON_A, InsteonBindingConstants.BUTTON_B,
            InsteonBindingConstants.BUTTON_C, InsteonBindingConstants.BUTTON_D, InsteonBindingConstants.BUTTON_E,
            InsteonBindingConstants.BUTTON_F, InsteonBindingConstants.BUTTON_G, InsteonBindingConstants.BUTTON_H,
            InsteonBindingConstants.BROADCAST_ON_OFF, InsteonBindingConstants.CONTACT,
            InsteonBindingConstants.COOL_SET_POINT, InsteonBindingConstants.DIMMER, InsteonBindingConstants.FAN,
            InsteonBindingConstants.FAN_MODE, InsteonBindingConstants.FAST_ON_OFF,
            InsteonBindingConstants.FAST_ON_OFF_BUTTON_A, InsteonBindingConstants.FAST_ON_OFF_BUTTON_B,
            InsteonBindingConstants.FAST_ON_OFF_BUTTON_C, InsteonBindingConstants.FAST_ON_OFF_BUTTON_D,
            InsteonBindingConstants.HEAT_SET_POINT, InsteonBindingConstants.HUMIDITY,
            InsteonBindingConstants.HUMIDITY_HIGH, InsteonBindingConstants.HUMIDITY_LOW,
            InsteonBindingConstants.IS_COOLING, InsteonBindingConstants.IS_HEATING,
            InsteonBindingConstants.KEYPAD_BUTTON_A, InsteonBindingConstants.KEYPAD_BUTTON_B,
            InsteonBindingConstants.KEYPAD_BUTTON_C, InsteonBindingConstants.KEYPAD_BUTTON_D,
            InsteonBindingConstants.KEYPAD_BUTTON_E, InsteonBindingConstants.KEYPAD_BUTTON_F,
            InsteonBindingConstants.KEYPAD_BUTTON_G, InsteonBindingConstants.KEYPAD_BUTTON_H,
            InsteonBindingConstants.KWH, InsteonBindingConstants.LAST_HEARD_FROM,
            InsteonBindingConstants.LED_BRIGHTNESS, InsteonBindingConstants.LIGHT_DIMMER,
            InsteonBindingConstants.LIGHT_LEVEL, InsteonBindingConstants.LOAD_DIMMER,
            InsteonBindingConstants.LOAD_SWITCH, InsteonBindingConstants.LOAD_SWITCH_FAST_ON_OFF,
            InsteonBindingConstants.LOAD_SWITCH_MANUAL_CHANGE, InsteonBindingConstants.MANUAL_CHANGE,
            InsteonBindingConstants.MANUAL_CHANGE_BUTTON_A, InsteonBindingConstants.MANUAL_CHANGE_BUTTON_B,
            InsteonBindingConstants.MANUAL_CHANGE_BUTTON_C, InsteonBindingConstants.MANUAL_CHANGE_BUTTON_D,
            InsteonBindingConstants.NOTIFICATION, InsteonBindingConstants.ON_LEVEL, InsteonBindingConstants.RAMP_DIMMER,
            InsteonBindingConstants.RAMP_RATE, InsteonBindingConstants.RESET, InsteonBindingConstants.STAGE1_DURATION,
            InsteonBindingConstants.SWITCH, InsteonBindingConstants.SYSTEM_MODE, InsteonBindingConstants.TEMP_CELSIUS,
            InsteonBindingConstants.TEMP_FAHRENHEIT, InsteonBindingConstants.TOP_OUTLET, InsteonBindingConstants.UPDATE,
            InsteonBindingConstants.WATTS).collect(Collectors.toSet()));

    private static final String BROADCAST_ON_OFF = "broadcastonoff";
    private static final String CMD = "cmd";
    private static final String CMD_RESET = "reset";
    private static final String CMD_UPDATE = "update";
    private static final String DATA = "data";
    private static final String FIELD = "field";
    private static final String FIELD_BATTERY_LEVEL = "battery_level";
    private static final String FIELD_BATTERY_WATERMARK_LEVEL = "battery_watermark_level";
    private static final String FIELD_KWH = "kwh";
    private static final String FIELD_LIGHT_LEVEL = "light_level";
    private static final String FIELD_WATTS = "watts";
    private static final String GROUP = "group";
    private static final String METER = "meter";

    private static final String HIDDEN_DOOR_SENSOR_PRODUCT_KEY = "F00.00.03";
    private static final String MOTION_SENSOR_PRODUCT_KEY = "0x00004A";
    private static final String PLM_PRODUCT_KEY = "0x000045";
    private static final String POWER_METER_PRODUCT_KEY = "F00.00.17";

    private final Logger logger = LoggerFactory.getLogger(InsteonDeviceHandler.class);

    private @Nullable InsteonDeviceConfiguration config;

    public InsteonDeviceHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        config = getConfigAs(InsteonDeviceConfiguration.class);

        scheduler.execute(() -> {
            if (getBridge() == null) {
                String msg = "An Insteon network bridge has not been selected for this device.";
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, msg);
                return;
            }

            String address = config.getAddress();
            if (!InsteonAddress.s_isValid(address)) {
                String msg = "Unable to start Insteon device, the insteon or X10 address '" + address
                        + "' is invalid. It must be in the format 'AB.CD.EF' or 'H.U' (X10).";
                logger.warn(msg);

                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, msg);
                return;
            }

            String productKey = config.getProductKey();
            if (DeviceTypeLoader.s_instance().getDeviceType(productKey) == null) {
                String msg = "Unable to start Insteon device, invalid product key '" + productKey + "'.";
                logger.warn(msg);

                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, msg);
                return;
            }

            InsteonBinding insteonBinding = getInsteonBinding();
            InsteonAddress insteonAddress = new InsteonAddress(address);
            if (insteonBinding.getDevice(insteonAddress) != null) {
                String msg = "a device already exists with the address '" + address + "'.";
                logger.warn(msg);

                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, msg);
                return;
            }

            InsteonDevice device = insteonBinding.makeNewDevice(insteonAddress, productKey);

            // By default the device is assigned all channels. Channels are associated with a feature, cull the ones
            // that are supported by this device.
            StringBuffer channelList = new StringBuffer();
            List<Channel> supportedChannels = new ArrayList<Channel>();
            List<Channel> initialChannels = getThing().getChannels();
            String thingId = getThing().getUID().getAsString();
            for (Channel channel : initialChannels) {
                String channelId = channel.getUID().getId();
                String feature = channelId.toLowerCase();

                if (productKey.equals(HIDDEN_DOOR_SENSOR_PRODUCT_KEY)) {
                    if (feature.equalsIgnoreCase(InsteonBindingConstants.BATTERY_LEVEL)
                            || feature.equalsIgnoreCase(InsteonBindingConstants.BATTERY_WATERMARK_LEVEL)) {
                        feature = DATA;
                    }
                } else if (productKey.equals(MOTION_SENSOR_PRODUCT_KEY)) {
                    if (feature.equalsIgnoreCase(InsteonBindingConstants.BATTERY_LEVEL)
                            || feature.equalsIgnoreCase(InsteonBindingConstants.LIGHT_LEVEL)) {
                        feature = DATA;
                    }
                } else if (productKey.equals(PLM_PRODUCT_KEY)) {
                    String parts[] = feature.split("#");
                    if (parts.length == 2 && parts[0].equalsIgnoreCase(InsteonBindingConstants.BROADCAST_ON_OFF)
                            && parts[1].matches("^\\d+$")) {
                        feature = BROADCAST_ON_OFF;
                    }
                } else if (productKey.equals(POWER_METER_PRODUCT_KEY)) {
                    if (feature.equalsIgnoreCase(InsteonBindingConstants.KWH)
                            || feature.equalsIgnoreCase(InsteonBindingConstants.RESET)
                            || feature.equalsIgnoreCase(InsteonBindingConstants.UPDATE)
                            || feature.equalsIgnoreCase(InsteonBindingConstants.WATTS)) {
                        feature = METER;
                    }
                }

                DeviceFeature f = device.getFeature(feature);
                if (f == null) {
                    if (!DEFINED_CHANNELS.contains(channelId)) {
                        logger.warn("{} includes channel {}, it is not a valid feature for {}. It has been removed.",
                                thingId, channelId, productKey);
                    }
                } else if (f.isFeatureGroup()) {
                    logger.warn("{} includes channel {}, it is a feature group for {}. It has been removed.", thingId,
                            channelId, productKey);
                } else {
                    supportedChannels.add(channel);

                    if (channelList.length() > 0) {
                        channelList.append(", ");
                    }
                    channelList.append(channelId);

                    if (!DEFINED_CHANNELS.contains(channelId)) {
                        for (String c : DEFINED_CHANNELS) {
                            if (channelId.equalsIgnoreCase(c)) {
                                logger.warn("{} includes channel {}, should it be {}?", thingId, channelId, c);
                                break;
                            }
                        }
                    }
                }
            }

            // only update if channels were culled
            if (initialChannels.size() != supportedChannels.size()) {
                ThingBuilder thingBuilder = editThing();
                thingBuilder.withChannels(supportedChannels);
                updateThing(thingBuilder.build());
            }

            logger.info("{} address = {} productKey = {} channels = {}", thingId, address, productKey,
                    channelList.toString());

            updateStatus(ThingStatus.ONLINE);
        });
    }

    @Override
    public void dispose() {
        String address = config.getAddress();
        if (getBridge() != null && InsteonAddress.s_isValid(address)) {
            getInsteonBinding().removeDevice(new InsteonAddress(address));

            logger.info("removed {} address = {}", getThing().getUID().getAsString(), address);
        }

        super.dispose();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.info("channel {} was triggered with the command {}", channelUID.getAsString(), command);

        getInsteonBinding().sendCommand(channelUID.getAsString(), command);
    }

    @Override
    public void channelLinked(ChannelUID channelUID) {
        HashMap<String, @Nullable String> params = new HashMap<String, @Nullable String>();
        Channel channel = getThing().getChannel(channelUID.getId());

        Map<String, Object> channelProperties = channel.getConfiguration().getProperties();
        for (String key : channelProperties.keySet()) {
            Object value = channelProperties.get(key);
            if (value instanceof String) {
                params.put(key, (String) value);
            } else if (value instanceof BigDecimal) {
                String s = ((BigDecimal) value).toPlainString();
                params.put(key, s);
            } else {
                logger.warn("not a string or big decimal value key '{}' value '{}' {}", key, value,
                        value.getClass().getName());
            }
        }

        String feature = channelUID.getId().toLowerCase();
        String productKey = config.getProductKey();
        if (productKey.equals(HIDDEN_DOOR_SENSOR_PRODUCT_KEY)) {
            if (feature.equalsIgnoreCase(InsteonBindingConstants.BATTERY_LEVEL)) {
                params.put(FIELD, FIELD_BATTERY_LEVEL);
                feature = DATA;
            } else if (feature.equalsIgnoreCase(InsteonBindingConstants.BATTERY_WATERMARK_LEVEL)) {
                params.put(FIELD, FIELD_BATTERY_WATERMARK_LEVEL);
                feature = DATA;
            }
        } else if (productKey.equals(MOTION_SENSOR_PRODUCT_KEY)) {
            if (feature.equalsIgnoreCase(InsteonBindingConstants.BATTERY_LEVEL)) {
                params.put(FIELD, FIELD_BATTERY_LEVEL);
                feature = DATA;
            } else if (feature.equalsIgnoreCase(InsteonBindingConstants.LIGHT_LEVEL)) {
                params.put(FIELD, FIELD_LIGHT_LEVEL);
                feature = DATA;
            }
        } else if (productKey.equals(PLM_PRODUCT_KEY)) {
            String parts[] = feature.split("#");
            if (parts.length == 2 && parts[0].equalsIgnoreCase(InsteonBindingConstants.BROADCAST_ON_OFF)
                    && parts[1].matches("^\\d+$")) {
                params.put(GROUP, parts[1]);
                feature = BROADCAST_ON_OFF;
            }
        } else if (productKey.equals(POWER_METER_PRODUCT_KEY)) {
            if (feature.equalsIgnoreCase(InsteonBindingConstants.KWH)) {
                params.put(FIELD, FIELD_KWH);
            } else if (feature.equalsIgnoreCase(InsteonBindingConstants.WATTS)) {
                params.put(FIELD, FIELD_WATTS);
            } else if (feature.equalsIgnoreCase(InsteonBindingConstants.RESET)) {
                params.put(CMD, CMD_RESET);
            } else if (feature.equalsIgnoreCase(InsteonBindingConstants.UPDATE)) {
                params.put(CMD, CMD_UPDATE);
            }

            feature = METER;
        }

        InsteonChannelConfiguration bindingConfig = new InsteonChannelConfiguration(channelUID, feature,
                new InsteonAddress(config.getAddress()), productKey, params);
        getInsteonBinding().addFeatureListener(bindingConfig);

        logger.info("channel {} linked with the feature: {} parameters: {}", channelUID.getAsString(), feature, params);
    }

    @Override
    public void channelUnlinked(ChannelUID channelUID) {
        getInsteonBinding().removeFeatureListener(channelUID);

        logger.info("channel {} unlinked ", channelUID.getAsString());
    }

    private @Nullable InsteonBinding getInsteonBinding() {
        return ((InsteonNetworkHandler) getBridge().getHandler()).getInsteonBinding();
    }
}
