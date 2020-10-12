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
package org.openhab.binding.bluetooth.bluez.handler.events;

import org.openhab.binding.bluetooth.bluez.handler.BlueZEvent;

/**
 *
 * @author Benjamin Lafois - Initial Contribution
 *
 */
public class AdapterDiscoveringChangedEvent extends BlueZEvent {

    private boolean discovering;

    public AdapterDiscoveringChangedEvent(String dbusPath, boolean discovering) {
        super(dbusPath, EventType.ADAPTER_DISCOVERING_CHANGED);
        this.discovering = discovering;
    }

    public boolean isDiscovering() {
        return discovering;
    }
}
