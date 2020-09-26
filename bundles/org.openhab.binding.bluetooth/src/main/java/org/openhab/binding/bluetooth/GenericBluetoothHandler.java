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
package org.openhab.binding.bluetooth;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.bluetooth.internal.ChannelHandlerCallback;
import org.openhab.binding.bluetooth.internal.GattChannelHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.gattparser.BluetoothGattParser;

/**
 * This is a handler for generic connected bluetooth devices that dynamically generates
 * channels based off of a bluetooth device's GATT characteristics.
 *
 * @author Connor Petty - Initial contribution
 *
 */
@NonNullByDefault
public class GenericBluetoothHandler extends ConnectedBluetoothHandler {

    private final Logger logger = LoggerFactory.getLogger(GenericBluetoothHandler.class);
    private final Map<BluetoothCharacteristic, GattChannelHandler> channelHandlers = new ConcurrentHashMap<>();

    private final ChannelCallback channelCallback = new ChannelCallback();
    private final BluetoothGattParser gattParser;

    public GenericBluetoothHandler(Thing thing, BluetoothGattParser gattParser) {
        super(thing);
        this.gattParser = gattParser;
    }

    @Override
    public void onServicesDiscovered() {
        if (!resolved) {
            resolved = true;
            logger.debug("Service discovery completed for '{}'", address);
            updateThingChannels();
        }
    }

    @Override
    public void onCharacteristicReadComplete(BluetoothCharacteristic characteristic, BluetoothCompletionStatus status) {
        super.onCharacteristicReadComplete(characteristic, status);
        if (status == BluetoothCompletionStatus.SUCCESS) {
            byte[] data = characteristic.getByteValue();
            channelHandlers.get(characteristic).handleCharacteristicUpdate(data);
        }
    }

    @Override
    public void onCharacteristicUpdate(BluetoothCharacteristic characteristic) {
        super.onCharacteristicUpdate(characteristic);
        byte[] data = characteristic.getByteValue();
        channelHandlers.get(characteristic).handleCharacteristicUpdate(data);
    }

    private void updateThingChannels() {
        List<Channel> channels = device.getServices().stream()//
                .flatMap(service -> service.getCharacteristics().stream())//
                .flatMap(characteristic -> buildChannels(characteristic).stream())//
                .collect(Collectors.toList());

        Thing newThing = editThing().withChannels(channels).build();
        updateThing(newThing);
    }

    private List<Channel> buildChannels(BluetoothCharacteristic characteristic) {
        GattChannelHandler handler = new GattChannelHandler(channelCallback, gattParser, characteristic);
        channelHandlers.put(characteristic, handler);
        return handler.buildChannels();
    }

    private class ChannelCallback implements ChannelHandlerCallback {

        @Override
        public ThingUID getThingUID() {
            return getThing().getUID();
        }

        @Override
        public void updateState(ChannelUID channelUID, State state) {
            GenericBluetoothHandler.this.updateState(channelUID, state);
        }

        @Override
        public void updateStatus(ThingStatus status, ThingStatusDetail statusDetail, @Nullable String description) {
            GenericBluetoothHandler.this.updateStatus(status, statusDetail, description);
        }

        @Override
        public boolean writeCharacteristic(BluetoothCharacteristic characteristic, byte[] data) {
            characteristic.setValue(data);
            return device.writeCharacteristic(characteristic);
        }
    }
}
