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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.bluetooth.BluetoothBindingConstants;
import org.openhab.binding.bluetooth.BluetoothDevice;
import org.openhab.binding.bluetooth.discovery.BluetoothDiscoveryParticipant;
import org.osgi.service.component.annotations.Component;

/**
 * This discovery participant is able to recognize BlindsEngine devices and create discovery results for them.
 *
 * @author Connor Petty - Initial contribution
 *
 */
@NonNullByDefault
@Component(immediate = true)
public class BlindsEngineDiscoveryParticipant implements BluetoothDiscoveryParticipant {

    /*
     * Yep, they are actually using 0 for their ID.
     * They were too lazy to even make up a manufacturer id.
     * I feel sorry for Ericsson Technology Licensing.
     */
    private static final int BLINDSENGINE_ID = 0;

    @Override
    public Set<ThingTypeUID> getSupportedThingTypeUIDs() {
        return Collections.singleton(BlindsEngineBindingConstants.THING_TYPE_BLINDS);
    }

    @Override
    public @Nullable DiscoveryResult createResult(BluetoothDevice device) {
        ThingUID thingUID = getThingUID(device);
        if (thingUID == null) {
            return null;
        }
        String label = "AM43 Blind Drive Motor";
        Map<String, Object> properties = new HashMap<>();
        properties.put(BluetoothBindingConstants.CONFIGURATION_ADDRESS, device.getAddress().toString());
        properties.put(Thing.PROPERTY_VENDOR, "A-OK Precision motor Ltd.");
        Integer txPower = device.getTxPower();
        if (txPower != null) {
            properties.put(BluetoothBindingConstants.PROPERTY_TXPOWER, Integer.toString(txPower));
        }

        // Create the discovery result and add to the inbox
        return DiscoveryResultBuilder.create(thingUID).withProperties(properties)
                .withRepresentationProperty(BluetoothBindingConstants.CONFIGURATION_ADDRESS)
                .withBridge(device.getAdapter().getUID()).withLabel(label).build();
    }

    @Override
    public @Nullable ThingUID getThingUID(BluetoothDevice device) {
        if ((device.getManufacturerId() == null || device.getManufacturerId() == BLINDSENGINE_ID)
        // && device.supportsService(BlindsEngineConstants.RX_SERVICE_UUID)
        ) {
            return new ThingUID(BlindsEngineBindingConstants.THING_TYPE_BLINDS, device.getAdapter().getUID(),
                    device.getAddress().toString().toLowerCase().replace(":", ""));
        }
        return null;
    }

}
