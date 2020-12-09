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
package org.openhab.binding.bluetooth.bluez.internal;

import java.util.UUID;

import org.openhab.binding.bluetooth.BluetoothCharacteristic;
import org.openhab.binding.bluetooth.BluetoothDescriptor;

import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattCharacteristic;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattDescriptor;

/**
 * @author cpetty
 *
 */
public class BlueZBluetoothCharacteristic extends BluetoothCharacteristic {

    public BlueZBluetoothCharacteristic(BluetoothGattCharacteristic dBusBlueZCharacteristic) {
        super(UUID.fromString(dBusBlueZCharacteristic.getUuid()), 0);
        this.properties = getProperties(dBusBlueZCharacteristic);

        for (BluetoothGattDescriptor dBusBlueZDescriptor : dBusBlueZCharacteristic.getGattDescriptors()) {
            BluetoothDescriptor descriptor = new BluetoothDescriptor(this,
                    UUID.fromString(dBusBlueZDescriptor.getUuid()), 0);
            addDescriptor(descriptor);
        }
    }

    private static int getProperties(BluetoothGattCharacteristic dBusBlueZCharacteristic) {
        int properties = 0;
        for (String flag : dBusBlueZCharacteristic.getFlags()) {
            switch (flag) {
                case "broadcast":
                    properties |= PROPERTY_BROADCAST;
                    break;
                case "read":
                    properties |= PROPERTY_READ;
                    break;
                case "write-without-response":
                    properties |= PROPERTY_WRITE_NO_RESPONSE;
                    break;
                case "write":
                    properties |= PROPERTY_WRITE;
                    break;
                case "notify":
                    properties |= PROPERTY_NOTIFY;
                    break;
                case "indicate":
                    properties |= PROPERTY_INDICATE;
                    break;
                case "authenticated-signed-writes":
                case "reliable-write":
                case "writable-auxiliaries":
                case "encrypt-read":
                case "encrypt-write":
                case "encrypt-authenticated-read":
                case "encrypt-authenticated-write":
                    // these aren't handled yet
                    break;
            }
        }
        return properties;
    }

}
