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
package org.openhab.binding.bluetooth.internal;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.BaseBridgeHandler;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.bluetooth.BluetoothAdapter;
import org.openhab.binding.bluetooth.BluetoothAddress;
import org.openhab.binding.bluetooth.BluetoothBindingConstants;
import org.openhab.binding.bluetooth.BluetoothDevice;
import org.openhab.binding.bluetooth.BluetoothDiscoveryListener;

/**
 * The {@link RoamingBluetoothBridgeHandler} handles roaming device instances
 *
 * @author Connor Petty - Initial contribution
 */
@NonNullByDefault
public class RoamingBluetoothBridgeHandler extends BaseBridgeHandler
        implements BluetoothAdapter, BluetoothDiscoveryListener, RoamingBluetoothAdapter {

    private static final BluetoothAddress ROAMING_ADAPTER_ADDRESS = new BluetoothAddress("FF:FF:FF:FF:FF:FF");

    private final Set<BluetoothAdapter> adapters = new CopyOnWriteArraySet<>();

    /*
     * Note: this will only populate from handlers calling getDevice(BluetoothAddress), so we don't need
     * to do periodic cleanup.
     */
    private Map<BluetoothAddress, RoamingBluetoothDevice> devices = new HashMap<>();

    public RoamingBluetoothBridgeHandler(Bridge bridge) {
        super(bridge);
    }

    @Override
    public void initialize() {
        updateStatus(ThingStatus.ONLINE);
    }

    @Override
    public void dispose() {
        updateStatus(ThingStatus.OFFLINE);
    }

    @Override
    public ThingUID getUID() {
        return getThing().getUID();
    }

    @Override
    public @Nullable String getLocation() {
        return getThing().getLocation();
    }

    @Override
    public boolean isDiscoveryEnabled() {
        Object discovery = getConfig().get(BluetoothBindingConstants.CONFIGURATION_DISCOVERY);
        if (discovery != null && discovery.toString().equalsIgnoreCase(Boolean.FALSE.toString())) {
            return false;
        }
        return true;
    }

    @Override
    public void addBluetoothAdapter(BluetoothAdapter adapter) {
        if (adapter == this) {
            return;
        }
        this.adapters.add(adapter);
        adapter.addDiscoveryListener(this);

        synchronized (devices) {
            for (RoamingBluetoothDevice roamingDevice : devices.values()) {
                roamingDevice.addBluetoothDevice(adapter.getDevice(roamingDevice.getAddress()));
            }
        }
    }

    @Override
    public void removeBluetoothAdapter(BluetoothAdapter adapter) {
        if (adapter == this) {
            return;
        }
        this.adapters.remove(adapter);
        adapter.removeDiscoveryListener(this);

        synchronized (devices) {
            for (RoamingBluetoothDevice roamingDevice : devices.values()) {
                roamingDevice.removeBluetoothDevice(adapter.getDevice(roamingDevice.getAddress()));
            }
        }

    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
    }

    @Override
    public void addDiscoveryListener(BluetoothDiscoveryListener listener) {
        // we don't use this
    }

    @Override
    public void removeDiscoveryListener(@Nullable BluetoothDiscoveryListener listener) {
        // we don't use this
    }

    @Override
    public void scanStart() {
        // does nothing
    }

    @Override
    public void scanStop() {
        // does nothing
    }

    @Override
    public BluetoothAddress getAddress() {
        return ROAMING_ADAPTER_ADDRESS;
    }

    @Override
    public RoamingBluetoothDevice getDevice(BluetoothAddress address) {
        synchronized (devices) {
            return devices.computeIfAbsent(address, addr -> new RoamingBluetoothDevice(this, addr));
        }
    }

    @Override
    public void deviceDiscovered(BluetoothDevice device) {
        synchronized (devices) {
            BluetoothAddress address = device.getAddress();
            if (devices.containsKey(address)) {
                devices.get(address).addBluetoothDevice(device);
            }
        }
    }

    @Override
    public void deviceRemoved(BluetoothDevice device) {
        synchronized (devices) {
            BluetoothAddress address = device.getAddress();
            if (devices.containsKey(address)) {
                devices.remove(address).removeBluetoothDevice(device);
            }
        }
    }

    @Override
    public boolean hasHandlerForDevice(BluetoothAddress address) {
        String addrStr = address.toString();
        /*
         * This type of search is inefficient and won't scale as the number of bluetooth Thing children increases on
         * this bridge. But implementing a more efficient search would require a bit more overhead.
         * Luckily though, it is reasonable to assume that the number of Thing children will remain small.
         */
        for (Thing childThing : getThing().getThings()) {
            Object childAddr = childThing.getConfiguration().get(BluetoothBindingConstants.CONFIGURATION_ADDRESS);
            if (addrStr.equals(childAddr)) {
                return childThing.getHandler() != null;
            }
        }
        return false;
    }

}
