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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.bluetooth.BluetoothCharacteristic;

@NonNullByDefault
public interface ChannelHandlerCallback {

    ThingUID getThingUID();

    void updateState(ChannelUID channelUID, State state);

    void updateStatus(ThingStatus status, ThingStatusDetail statusDetail, @Nullable String description);

    /**
     * Updates the status of the thing.
     *
     * @param status the status
     * @param statusDetail the detail of the status
     */
    default void updateStatus(ThingStatus status, ThingStatusDetail statusDetail) {
        updateStatus(status, statusDetail, null);
    }

    /**
     * Updates the status of the thing. The detail of the status will be 'NONE'.
     *
     * @param status the status
     */
    default void updateStatus(ThingStatus status) {
        updateStatus(status, ThingStatusDetail.NONE, null);
    }

    public boolean writeCharacteristic(BluetoothCharacteristic characteristic, byte[] data);
}
