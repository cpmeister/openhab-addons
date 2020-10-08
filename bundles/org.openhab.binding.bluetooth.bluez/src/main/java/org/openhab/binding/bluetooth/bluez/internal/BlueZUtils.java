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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.bluetooth.BluetoothAddress;

/**
 *
 * @author Benjamin Lafois
 *
 */
@NonNullByDefault
public class BlueZUtils {

    private static final Pattern PATTERN_MAC = Pattern.compile("/org/bluez/([^/]+)/dev_([^/]+).*");
    private static final Pattern PATTERN_ADAPTER_NAME = Pattern.compile("/org/bluez/([^/]+)");

    private BlueZUtils() {

    }

    /**
     * Returns BluetoothAddress object from a DBus path
     *
     * @param dbusPath
     * @return
     */
    public static @Nullable BluetoothAddress dbusPathToMac(String dbusPath) {
        Matcher m = PATTERN_MAC.matcher(dbusPath);
        if (!m.matches()) {
            return null;
        } else {
            String s = m.group(2).replace("_", ":");
            return new BluetoothAddress(s);
        }
    }

    public static @Nullable String dbusPathToAdapterName(String dbusPath) {
        Matcher m = PATTERN_ADAPTER_NAME.matcher(dbusPath);
        if (!m.matches()) {
            return null;
        } else {
            String s = m.group(1);
            return s;
        }
    }

}
