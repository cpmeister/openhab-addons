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
package org.openhab.binding.bluetooth.am43.internal;

/**
 * This is an enum representing possible motor direction settings
 *
 * @author Connor Petty - Initial contribution
 */
public enum Direction {
    Forward,
    Reverse;

    public byte toByte() {
        switch (this) {
            case Forward:
                return 1;
            case Reverse:
                return 0;
        }
        return -1;
    }

    public static Direction valueOf(boolean bitValue) {
        return bitValue ? Forward : Reverse;
    }

}