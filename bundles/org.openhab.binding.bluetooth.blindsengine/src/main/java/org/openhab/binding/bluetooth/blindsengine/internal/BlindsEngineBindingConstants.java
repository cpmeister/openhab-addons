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
package org.openhab.binding.bluetooth.blindsengine.internal;

import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.openhab.binding.bluetooth.BluetoothBindingConstants;

/**
 * The {@link BlindsEngineBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Connor Petty - Initial contribution
 */
@NonNullByDefault
public class BlindsEngineBindingConstants {

    private static final String BINDING_ID = "bluetooth.blindsengine";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_BLINDS = new ThingTypeUID(BluetoothBindingConstants.BINDING_ID,
            "blinds_engine_device");

    // List of all Channel ids
    // public static final String CHANNEL_ID_NAME = "name";
    public static final String CHANNEL_ID_DIRECTION = "direction";
    public static final String CHANNEL_ID_TOP_LIMIT_SET = "topLimitSet";
    public static final String CHANNEL_ID_BOTTOM_LIMIT_SET = "bottomLimitSet";
    public static final String CHANNEL_ID_HAS_LIGHT_SENSOR = "hasLightSensor";
    public static final String CHANNEL_ID_OPERATION_MODE = "operationSwitch";
    public static final String CHANNEL_ID_DEVICE_SPEED = "deviceSpeed";
    public static final String CHANNEL_ID_DEVICE_ELECTRIC = "deviceElectric";
    public static final String CHANNEL_ID_DEVICE_PERCENT_POSITION = "devicePercentPosition";
    public static final String CHANNEL_ID_DEVICE_LENGTH = "deviceLength";
    public static final String CHANNEL_ID_DEVICE_DIAMETER = "deviceDiameter";
    public static final String CHANNEL_ID_DEVICE_TYPE = "deviceType";
    public static final String CHANNEL_ID_LIGHT_LEVEL = "lightLevel";

    public static List<String> getAllChannels() {
        return Arrays.asList(CHANNEL_ID_DIRECTION, CHANNEL_ID_TOP_LIMIT_SET, CHANNEL_ID_BOTTOM_LIMIT_SET,
                CHANNEL_ID_HAS_LIGHT_SENSOR, CHANNEL_ID_OPERATION_MODE, CHANNEL_ID_DEVICE_SPEED,
                CHANNEL_ID_DEVICE_ELECTRIC, CHANNEL_ID_DEVICE_PERCENT_POSITION, CHANNEL_ID_DEVICE_LENGTH,
                CHANNEL_ID_DEVICE_DIAMETER, CHANNEL_ID_DEVICE_TYPE, CHANNEL_ID_LIGHT_LEVEL);
    }

}
