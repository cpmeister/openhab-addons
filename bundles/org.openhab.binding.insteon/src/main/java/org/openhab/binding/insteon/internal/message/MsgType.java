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
package org.openhab.binding.insteon.internal.message;

import java.util.HashMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Represents insteon message type flags
 *
 * @author Daniel Pfrommer - Initial contribution
 * @author Rob Nielsen - Port to openHAB 2 insteon binding
 */
@NonNullByDefault
public enum MsgType {
    /*
     * From the official Insteon docs: the message flags are as follows:
     *
     * Bit 0 max hops low bit
     * Bit 1 max hops high bit
     * Bit 2 hops left low bit
     * Bit 3 hops left high bit
     * Bit 4 0: is standard message, 1: is extended message
     * Bit 5 ACK
     * Bit 6 0: not link related, 1: is ALL-Link message
     * Bit 7 Broadcast/NAK
     */
    BROADCAST(0x80),
    DIRECT(0x00),
    ACK_OF_DIRECT(0x20),
    NACK_OF_DIRECT(0xa0),
    ALL_LINK_BROADCAST(0xc0),
    ALL_LINK_CLEANUP(0x40),
    ALL_LINK_CLEANUP_ACK(0x60),
    ALL_LINK_CLEANUP_NACK(0xe0),
    INVALID(0xff); // should never happen

    private static HashMap<Integer, @Nullable MsgType> s_hash = new HashMap<Integer, @Nullable MsgType>();

    private byte m_byteValue = 0;

    /**
     * Constructor
     *
     * @param b byte with insteon message type flags set
     */
    MsgType(int b) {
        m_byteValue = (byte) b;
    }

    static {
        for (MsgType t : MsgType.values()) {
            Integer i = new Integer(t.getByteValue() & 0xff);
            s_hash.put(i, t);
        }
    }

    private int getByteValue() {
        return m_byteValue;
    }

    public static MsgType s_fromValue(byte b) throws IllegalArgumentException {
        Integer i = new Integer((b & 0xe0));
        @Nullable
        MsgType mt = s_hash.get(i);
        if (mt == null) {
            throw new IllegalArgumentException("msg type of byte value " + i + " not found");
        }
        return mt;
    }

}
