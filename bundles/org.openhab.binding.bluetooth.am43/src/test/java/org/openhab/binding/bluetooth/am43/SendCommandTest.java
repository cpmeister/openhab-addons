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
package org.openhab.binding.bluetooth.am43;

import org.eclipse.smarthome.core.util.HexUtils;
import org.junit.Assert;
import org.junit.Test;
import org.openhab.binding.bluetooth.am43.internal.AM43Constants;
import org.openhab.binding.bluetooth.am43.internal.AM43Handler;

/**
 *
 * @author Connor Petty - Initial contribution
 */
public class SendCommandTest {

    @Test
    public void findAllCommandTest() {
        byte[] expected = HexUtils.hexToBytes("00ff00009aa701013d");
        byte[] actual = AM43Handler.createBleCommand(AM43Constants.Command_Head_Type_Setting_findAll, null,
                AM43Constants.Command_Send_Content_Type_Setting_findAll);

        Assert.assertArrayEquals(expected, actual);
    }

    @Test
    public void controlStopCommandTest() {
        byte[] expected = HexUtils.hexToBytes("00ff00009a0a01cc5d");
        byte[] actual = AM43Handler.createBleCommand(AM43Constants.Command_Head_Type_Control_Direct, null,
                AM43Constants.Command_Send_Content_Control_Stop);

        Assert.assertArrayEquals(expected, actual);
    }

    @Test
    public void controlOpenCommandTest() {
        byte[] expected = HexUtils.hexToBytes("00ff00009a0a01dd4c");
        byte[] actual = AM43Handler.createBleCommand(AM43Constants.Command_Head_Type_Control_Direct, null,
                AM43Constants.Command_Send_Content_Control_Open);

        Assert.assertArrayEquals(expected, actual);
    }

}
