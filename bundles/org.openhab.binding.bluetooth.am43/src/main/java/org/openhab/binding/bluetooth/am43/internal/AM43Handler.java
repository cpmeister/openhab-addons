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

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.measure.quantity.Length;

import org.apache.commons.lang.ArrayUtils;
import org.eclipse.jdt.annotation.DefaultLocation;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.library.types.QuantityType;
import org.eclipse.smarthome.core.library.types.StopMoveType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.library.types.UpDownType;
import org.eclipse.smarthome.core.library.unit.SIUnits;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.eclipse.smarthome.core.types.State;
import org.eclipse.smarthome.core.types.UnDefType;
import org.eclipse.smarthome.core.util.HexUtils;
import org.openhab.binding.bluetooth.BluetoothCharacteristic;
import org.openhab.binding.bluetooth.BluetoothCompletionStatus;
import org.openhab.binding.bluetooth.BluetoothDevice.ConnectionState;
import org.openhab.binding.bluetooth.ConnectedBluetoothHandler;
import org.openhab.binding.bluetooth.notification.BluetoothConnectionStatusNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tec.uom.se.unit.MetricPrefix;

/**
 * The {@link AM43Handler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Connor Petty - Initial contribution
 */
@NonNullByDefault({ DefaultLocation.PARAMETER, DefaultLocation.RETURN_TYPE, DefaultLocation.ARRAY_CONTENTS,
        DefaultLocation.TYPE_ARGUMENT, DefaultLocation.TYPE_BOUND, DefaultLocation.TYPE_PARAMETER })
public class AM43Handler extends ConnectedBluetoothHandler {

    private final Logger logger = LoggerFactory.getLogger(AM43Handler.class);

    protected volatile Boolean enabledNotifications = false;

    private ScheduledFuture<?> motorSettingsJob;
    private ScheduledFuture<?> refreshBatteryJob;
    private ScheduledFuture<?> refreshLightLevelJob;

    public AM43Handler(Thing thing) {
        super(thing);
    }

    private MotorSettings motorSettings = null;

    @Override
    public void initialize() {
        super.initialize();
        motorSettingsJob = scheduler.scheduleWithFixedDelay(() -> {
            enableNotifications();
            if (isReadyForCommand()) {
                sendFindSetCommand();
            }
        }, 0, 60, TimeUnit.SECONDS);
        refreshBatteryJob = scheduler.scheduleWithFixedDelay(() -> {
            enableNotifications();
            if (isReadyForCommand()) {
                sendFindElectricCommand();
            }
        }, 5, 60, TimeUnit.SECONDS);

        refreshLightLevelJob = scheduler.scheduleWithFixedDelay(() -> {
            enableNotifications();
            if (isReadyForCommand()) {
                sendFindLightLevelCommand();
            }
        }, 10, 60, TimeUnit.SECONDS);
    }

    private void cancelMotorSettingsJob() {
        if (motorSettingsJob != null) {
            motorSettingsJob.cancel(true);
            motorSettingsJob = null;
        }
    }

    @Override
    public void dispose() {
        cancelMotorSettingsJob();
        if (refreshBatteryJob != null) {
            refreshBatteryJob.cancel(true);
            refreshBatteryJob = null;
        }
        if (refreshLightLevelJob != null) {
            refreshLightLevelJob.cancel(true);
            refreshLightLevelJob = null;
        }
        super.dispose();
    }

    @Override
    public void onServicesDiscovered() {
        super.onServicesDiscovered();
        enableNotifications();
    }

    private void enableNotifications() {
        if (!resolved || !isConnected() || enabledNotifications) {
            return;
        }

        BluetoothCharacteristic characteristic = device.getCharacteristic(AM43Constants.RX_CHAR_UUID);
        if (characteristic != null) {
            if (!device.enableNotifications(characteristic)) {
                logger.debug("failed to enable notifications for characteristic: {}", AM43Constants.RX_CHAR_UUID);
            } else {
                enabledNotifications = true;
                if (isAnyChannelLinked()) {
                    sendFindSetCommand();
                }
            }
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "Failed to find service with characteristic: " + AM43Constants.RX_CHAR_UUID);
            device.disconnect();
        }
    }

    @Override
    public void onConnectionStateChange(BluetoothConnectionStatusNotification connectionNotification) {
        super.onConnectionStateChange(connectionNotification);
        if (enabledNotifications && connectionNotification.getConnectionState() != ConnectionState.CONNECTED) {
            enabledNotifications = false;
        }
    }

    private boolean isAnyChannelLinked() {
        for (String channelId : AM43BindingConstants.getAllChannels()) {
            if (isLinked(channelId)) {
                return true;
            }
        }
        return false;
    }

    private boolean isConnected() {
        return device != null && device.getConnectionState() == ConnectionState.CONNECTED;
    }

    private boolean isReadyForCommand() {
        return isConnected() && enabledNotifications;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (!isReadyForCommand()) {
            return;
        }
        if (command instanceof RefreshType) {
            switch (channelUID.getId()) {
                case AM43BindingConstants.CHANNEL_ID_ELECTRIC:
                    sendFindElectricCommand();
                    return;
                case AM43BindingConstants.CHANNEL_ID_LIGHT_LEVEL:
                    sendFindLightLevelCommand();
                    return;
            }
            sendFindSetCommand();
            return;
        }
        switch (channelUID.getId()) {
            case AM43BindingConstants.CHANNEL_ID_POSITION:
                if (command instanceof PercentType) {
                    PercentType percent = (PercentType) command;
                    sendControlPercentCommand(percent.intValue());
                    return;
                }
                if (command instanceof StopMoveType) {
                    switch ((StopMoveType) command) {
                        case STOP:
                            sendControlCommand(AM43Constants.Command_Send_Content_Control_Stop);
                            return;
                        case MOVE:
                            // do nothing
                            return;
                    }
                }
                if (command instanceof UpDownType) {
                    switch ((UpDownType) command) {
                        case UP:
                            sendControlCommand(AM43Constants.Command_Send_Content_Control_Open);
                            return;
                        case DOWN:
                            sendControlCommand(AM43Constants.Command_Send_Content_Control_Close);
                            return;
                    }
                }
                break;
            case AM43BindingConstants.CHANNEL_ID_TOP_LIMIT_SET:
                if (command instanceof OnOffType) {
                    switch ((OnOffType) command) {
                        case ON:
                            sendChangeLimitStateCommand(AM43Constants.Command_Send_Content_saveLimit, 0);
                            return;
                        case OFF:
                            sendResetLimitStateCommand();
                            updateBottomLimitSet(false);
                            return;
                    }
                }
                break;
            case AM43BindingConstants.CHANNEL_ID_BOTTOM_LIMIT_SET:
                if (command instanceof OnOffType) {
                    switch ((OnOffType) command) {
                        case ON:
                            sendChangeLimitStateCommand(AM43Constants.Command_Send_Content_saveLimit, 1);
                            return;
                        case OFF:
                            sendResetLimitStateCommand();
                            updateTopLimitSet(false);
                            return;
                    }
                }
                break;
            case AM43BindingConstants.CHANNEL_ID_DIRECTION:
                if (command instanceof StringType) {
                    motorSettings.setDirection(Direction.valueOf(command.toString()));
                    sendMotorSettingsCommand();
                    return;
                }
        }

        super.handleCommand(channelUID, command);
    }

    @Override
    public void onCharacteristicWriteComplete(BluetoothCharacteristic characteristic,
            BluetoothCompletionStatus status) {
        super.onCharacteristicWriteComplete(characteristic, status);

        switch (status) {
            case ERROR:
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "Failed to set characteristic "
                        + characteristic.getUuid() + " to value " + HexUtils.bytesToHex(characteristic.getByteValue()));
                device.disconnect();
                return;
            case SUCCESS:
                return;
        }
    }

    @Override
    public void onCharacteristicUpdate(BluetoothCharacteristic characteristic) {
        super.onCharacteristicUpdate(characteristic);
        if (!characteristic.getUuid().equals(AM43Constants.RX_CHAR_UUID)) {
            return;
        }
        byte[] data = characteristic.getByteValue();
        byte headType = data[1];

        switch (headType) {
            case AM43Constants.Command_Head_Type_Control_direct: {
                logger.debug("received control ack");
                if (data[3] == AM43Constants.Command_Notify_Content_Succese) {
                    // direct command was successful
                }
                return;
            }
            case AM43Constants.Command_Notify_Head_Type_Move: {
                logger.debug("received movement notify");
                if (data.length < 5) {
                    return;
                }
                updatePosition(data[4]);
                break;
            }
            case AM43Constants.Command_Notify_Head_Type_Battery_Level: {
                if (data.length < 9) {
                    return;
                }
                updateBatteryLevel(data[7]);
                break;
            }
            case AM43Constants.Command_Notify_Head_Type_Light_Level: {
                if (data.length < 6) {
                    return;
                }
                // data[3] == 0 && data[4] == 0 when charging

                // boolean hasLightSensor = data[3] == 1;
                // if (hasLightSensor) {
                updateLightLevel(data[4]);
                // }
                break;
            }
            case AM43Constants.Command_Notify_Head_Type_Speed: {
                if (data.length < 5) {
                    return;
                }

                // updateDirection((data[3] & 2) > 0);
                // updateOperationMode((data[3] & 4) > 0);
                // updateHasLightSensor(((data[3] & 8)) > 0);
                updateSpeed(data[4]);
                break;
            }
            case AM43Constants.Command_Notify_Head_Type_Find_Normal: {
                if (data.length < 10) {
                    return;
                }

                motorSettings = new MotorSettings();

                updateDirection((data[3] & 1) > 0);
                updateOperationMode((data[3] & 2) > 0);
                updateTopLimitSet((data[3] & 4) > 0);
                updateBottomLimitSet((data[3] & 8) > 0);
                updateHasLightSensor(((data[3] & 16)) > 0);
                updateSpeed(data[4]);
                updatePosition(data[5]);
                updateLength(data[6], data[7]);
                updateDiameter(data[8]);
                updateDeviceType(Math.abs(data[9] >> 4));

                cancelMotorSettingsJob();
                break;
            }
            case AM43Constants.Command_Notify_Head_Type_Find_Timing: {
                // if (data[2] == 0) {
                // bleSetNormalBean.getTimingList().clear();
                // return;
                // }
                //
                // int i3 = data[2] / 5;
                // bleSetNormalBean.getTimingList().clear();
                //
                // for (int i = 0; i < i3; i++) {
                // int i6 = i * 5;
                // int i7 = i6 + 5;
                // boolean[] bools = new boolean[7];
                // bools[0] = (data[i7] & 1) != 0;
                // bools[1] = (data[i7] & 2) != 0;
                // bools[2] = (data[i7] & 4) != 0;
                // bools[3] = (data[i7] & 8) != 0;
                // bools[4] = (data[i7] & 16) != 0;
                // bools[5] = (data[i7] & 32) != 0;
                // bools[6] = (data[i7] & 64) != 0;
                //
                // BleTimingBean bleTimingBean = new BleTimingBean(data[i6 + 3] == 1, Integer.valueOf(data[i6 + 4]),
                // arr,
                // data[i6 + 6], data[i6 + 7]);
                // ArrayList timingList = bleSetNormalBean.getTimingList();
                // timingList.add(bleTimingBean);
                // }
                return;
            }
            case AM43Constants.Command_Notify_Head_Type_Find_Season: {
                // if (data.length < 20) {
                // StringBuilder sb = new StringBuilder();
                // sb.append("错误的数据为：");
                // sb.append(CollectionsKt.joinToString$default(ByteUtils.Companion.formatHexStringList(bArr2), null,
                // null,
                // null, 0, null, null, 63, null));
                // Log.i("Error", sb.toString());
                // return;
                // }
                // bleSetNormalBean2.setSummerSeasonState(data[4] == 1);
                // bleSetNormalBean2.setSummerLightSeasonState(data[5]);
                // bleSetNormalBean2.setSummerLightLevel(data[6]);
                // bleSetNormalBean2.setSummerLightStartHour(data[7]);
                // bleSetNormalBean2.setSummerLightStartMinute(data[8]);
                // bleSetNormalBean2.setSummerLightEndHour(data[9]);
                // bleSetNormalBean2.setSummerLightEndMinute(data[10]);
                // if (data[12] != 1) {
                // z = false;
                // }
                // bleSetNormalBean2.setWinterSeasonState(z);
                // bleSetNormalBean2.setWinterLightSeasonState(data[13]);
                // bleSetNormalBean2.setWinterLightLevel(data[14]);
                // bleSetNormalBean2.setWinterLightStartHour(data[15]);
                // bleSetNormalBean2.setWinterLightStartMinute(data[16]);
                // bleSetNormalBean2.setWinterLightEndHour(data[17]);
                // bleSetNormalBean2.setWinterLightEndMinute(data[18]);
                return;
            }
            case AM43Constants.Command_Notify_Head_Type_Fault: {
                // sendBleNotifyAck(header);
                break;
            }

        }
        sendBleNotifyAck(headType);
    }

    private void updateDirection(boolean bitValue) {
        Direction direction = Direction.valueOf(bitValue);
        motorSettings.setDirection(direction);
        StringType directionType = new StringType(direction.toString());
        logger.debug("updating direction to: {}", directionType);
        updateStateIfLinked(AM43BindingConstants.CHANNEL_ID_DIRECTION, directionType);
    }

    private void updateOperationMode(boolean bitValue) {
        OperationMode opMode = OperationMode.valueOf(bitValue);
        motorSettings.setOperationMode(opMode);
        StringType mode = new StringType(opMode.toString());
        logger.debug("updating operationMode to: {}", mode);
        updateStateIfLinked(AM43BindingConstants.CHANNEL_ID_OPERATION_MODE, mode);
    }

    private void updateTopLimitSet(boolean bitValue) {
        OnOffType limitSet = bitValue ? OnOffType.ON : OnOffType.OFF;
        logger.debug("updating topLimitSet to: {}", bitValue);
        updateStateIfLinked(AM43BindingConstants.CHANNEL_ID_TOP_LIMIT_SET, limitSet);
    }

    private void updateBottomLimitSet(boolean bitValue) {
        OnOffType limitSet = bitValue ? OnOffType.ON : OnOffType.OFF;
        logger.debug("updating bottomLimitSet to: {}", bitValue);
        updateStateIfLinked(AM43BindingConstants.CHANNEL_ID_BOTTOM_LIMIT_SET, limitSet);
    }

    private void updateHasLightSensor(boolean bitValue) {
        OnOffType hasSensor = bitValue ? OnOffType.ON : OnOffType.OFF;
        logger.debug("updating hasLightSensor to: {}", bitValue);
        updateStateIfLinked(AM43BindingConstants.CHANNEL_ID_HAS_LIGHT_SENSOR, hasSensor);
    }

    private void updateSpeed(byte value) {
        motorSettings.setSpeed(value);
        DecimalType speed = new DecimalType(value);
        logger.debug("updating speed to: {}", speed);
        updateStateIfLinked(AM43BindingConstants.CHANNEL_ID_SPEED, speed);
    }

    private void updatePosition(byte value) {
        if (value >= 0 && value <= 100) {
            PercentType position = new PercentType(value);
            logger.debug("updating position to: {}", position);
            updateStateIfLinked(AM43BindingConstants.CHANNEL_ID_POSITION, position);
        } else {
            logger.debug("updating position to: undef");
            updateStateIfLinked(AM43BindingConstants.CHANNEL_ID_POSITION, UnDefType.UNDEF);
        }
    }

    private void updateLength(byte upper, byte lower) {
        motorSettings.setLength(upper << 8 | lower);
        QuantityType<Length> length = QuantityType.valueOf(motorSettings.getLength(),
                MetricPrefix.MILLI(SIUnits.METRE));
        logger.debug("updating length to: {}", length);
        updateStateIfLinked(AM43BindingConstants.CHANNEL_ID_LENGTH, length);
    }

    private void updateDiameter(byte value) {
        motorSettings.setDiameter(value);
        QuantityType<Length> diameter = QuantityType.valueOf(value, MetricPrefix.MILLI(SIUnits.METRE));
        logger.debug("updating diameter to: {}", diameter);
        updateStateIfLinked(AM43BindingConstants.CHANNEL_ID_DIAMETER, diameter);
    }

    private void updateDeviceType(int value) {
        motorSettings.setType(value);
        DecimalType type = new DecimalType(value);
        logger.debug("updating deviceType to: {}", type);
        updateStateIfLinked(AM43BindingConstants.CHANNEL_ID_TYPE, type);
    }

    private void updateLightLevel(byte value) {
        DecimalType lightLevel = new DecimalType(value);
        logger.debug("updating lightLevel to: {}", lightLevel);
        updateStateIfLinked(AM43BindingConstants.CHANNEL_ID_LIGHT_LEVEL, lightLevel);
    }

    private void updateBatteryLevel(int value) {
        if (value >= 0 && value <= 100) {
            PercentType deviceElectric = new PercentType(value & 0xFF);
            logger.debug("updating battery lebel to: {}", deviceElectric);
            updateStateIfLinked(AM43BindingConstants.CHANNEL_ID_ELECTRIC, deviceElectric);
        } else {
            logger.debug("Received battery value {}. Updating battery lebel: undef", value);
            updateStateIfLinked(AM43BindingConstants.CHANNEL_ID_ELECTRIC, UnDefType.UNDEF);
        }
    }

    /**
     * Update DecimalType channel state
     *
     * Update is not done when value is null.
     *
     * @param channelUID channel UID
     * @param value value to update
     * @return whether the value was present
     */
    private void updateStateIfLinked(String channelUID, State state) {
        if (isLinked(channelUID)) {
            updateState(channelUID, state);
        }
    }

    private void sendBleNotifyAck(byte commandType) {
        byte[] data = { AM43Constants.Command_Notify_Content_Succese };
        // sendBleCommand(commandType, data);
        sendBleCommandWithoutCrc(commandType, data, true);
    }

    private void sendBleCommandWithoutCrc(byte commandType, byte[] contentByteArray, boolean z) {
        byte[] value = AM43Constants.Command_Head_Tag;
        value = ArrayUtils.add(value, AM43Constants.Command_Head_Value);
        value = ArrayUtils.add(value, commandType);
        value = ArrayUtils.add(value, (byte) contentByteArray.length);
        value = ArrayUtils.addAll(value, contentByteArray);
        value = ArrayUtils.add(value, (byte) (z ? 49 : 206));

        BluetoothCharacteristic characteristic = device.getCharacteristic(AM43Constants.TX_CHAR_UUID);
        characteristic.setValue(value);
        device.writeCharacteristic(characteristic);
    }

    private void sendBleCommand(byte commandType, byte[] contentByteArray) {
        byte[] header = AM43Constants.Command_Head_Tag;
        byte[] value = ArrayUtils.EMPTY_BYTE_ARRAY;
        value = ArrayUtils.add(value, AM43Constants.Command_Head_Value);
        value = ArrayUtils.add(value, commandType);
        value = ArrayUtils.add(value, (byte) contentByteArray.length);
        value = ArrayUtils.addAll(value, contentByteArray);
        value = ArrayUtils.add(value, computeCrc(value));
        value = ArrayUtils.addAll(header, value);

        BluetoothCharacteristic characteristic = device.getCharacteristic(AM43Constants.TX_CHAR_UUID);
        characteristic.setValue(value);
        device.writeCharacteristic(characteristic);
    }

    private byte computeCrc(byte[] data) {
        byte crc = data[0];
        for (int i = 1; i < data.length; i++) {
            crc ^= data[i];
        }
        return crc;
    }

    private void sendMotorSettingsCommand() {
        if (motorSettings == null) {
            throw new IllegalStateException("settings have not yet been retrieved from the motor");
        }

        int direction = motorSettings.getDirection().toByte();
        int operationMode = motorSettings.getOperationMode().toByte();
        int deviceType = motorSettings.getType();
        int deviceLength = motorSettings.getLength();
        int deviceSpeed = motorSettings.getSpeed();
        int deviceDiameter = motorSettings.getDiameter();

        int dataHead = ((direction & 1) << 1) | ((operationMode & 1) << 2) | (deviceType << 4);

        byte[] data = { (byte) dataHead, (byte) deviceSpeed, 0, (byte) ((deviceLength & 0xFF00) >> 8),
                (byte) (deviceLength & 0xFF), (byte) deviceDiameter };
        sendBleCommand(AM43Constants.Command_Head_Type_Setting_Frequently, data);
    }

    private void sendPasswordCommand(int i) {
        int i2 = (i & 0xFF00) >> 8;
        int i3 = i & 0xFF;
        byte[] data = { (byte) i2, (byte) i3 };
        sendBleCommand(AM43Constants.Command_Head_Type_PassWord, data);
    }

    private void sendChangedPasswordCommand(int i) {
        int i2 = (i & 0xFF00) >> 8;
        int i3 = i & 0xFF;
        byte[] data = { (byte) i2, (byte) i3 };
        sendBleCommand(AM43Constants.Command_Head_Type_PassWord_Change, data);
    }

    private void sendControlCommand(byte command) {
        byte[] data = { command };
        sendBleCommand(AM43Constants.Command_Head_Type_Control_direct, data);
    }

    private void sendControlPercentCommand(int percent) {
        byte[] data = { (byte) percent };
        sendBleCommand(AM43Constants.Command_Head_Type_Control_percent, data);
    }

    private void sendChangeLimitStateCommand(byte limitType, int limitMode) {
        byte[] data = { limitType, (byte) (1 << limitMode), 0 };
        sendChangeLimitStateCommand(data);
    }

    private void sendResetLimitStateCommand() {
        byte[] data = { 0, 0, 1 };
        sendChangeLimitStateCommand(data);
    }

    private void sendChangeLimitStateCommand(byte[] data) {
        sendBleCommand(AM43Constants.Command_Head_Type_LimitOrReset, data);
    }

    private void sendNewNameCommand(byte[] data) {
        sendBleCommand(AM43Constants.Command_Notify_Head_Type_NewName, data);
    }

    private void sendChangeSeasonCommand(byte[] data) {
        sendBleCommand(AM43Constants.Command_Head_Type_Season, data);
    }

    private void sendTimingCommand(byte[] data) {
        sendBleCommand(AM43Constants.Command_Head_Type_Timing, data);
    }

    private void sendTimingSwitchCommand(int i, boolean z) {
        byte[] data = { (byte) i, 0, z ? (byte) 1 : 0, 0, 0, 0, 0 };
        sendBleCommand(AM43Constants.Command_Head_Type_Timing, data);
    }

    private void sendCurrentTimeCommand() {

    }

    private void sendFindElectricCommand() {
        byte[] data = { (byte) 1 };
        sendBleCommand(AM43Constants.Command_Head_Type_Battery_Level, data);
    }

    private void sendFindSetCommand() {
        byte[] data = { AM43Constants.Command_Send_content_Type_Setting_findAll };
        sendBleCommand(AM43Constants.Command_Head_Type_Setting_findAll, data);
    }

    private void sendFindLightLevelCommand() {
        byte[] data = { AM43Constants.Command_Send_Content_findLightLevel };
        sendBleCommand(AM43Constants.Command_Head_Type_Light_Level, data);
    }

}
