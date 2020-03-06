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
package org.openhab.binding.insteon.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.State;
import org.eclipse.smarthome.io.transport.serial.SerialPortManager;
import org.openhab.binding.insteon.internal.config.InsteonChannelConfiguration;
import org.openhab.binding.insteon.internal.config.InsteonNetworkConfiguration;
import org.openhab.binding.insteon.internal.device.DeviceFeature;
import org.openhab.binding.insteon.internal.device.DeviceFeatureListener;
import org.openhab.binding.insteon.internal.device.DeviceType;
import org.openhab.binding.insteon.internal.device.DeviceTypeLoader;
import org.openhab.binding.insteon.internal.device.InsteonAddress;
import org.openhab.binding.insteon.internal.device.InsteonDevice;
import org.openhab.binding.insteon.internal.device.InsteonDevice.DeviceStatus;
import org.openhab.binding.insteon.internal.device.RequestQueueManager;
import org.openhab.binding.insteon.internal.driver.Driver;
import org.openhab.binding.insteon.internal.driver.DriverListener;
import org.openhab.binding.insteon.internal.driver.ModemDBEntry;
import org.openhab.binding.insteon.internal.driver.Poller;
import org.openhab.binding.insteon.internal.handler.InsteonNetworkHandler;
import org.openhab.binding.insteon.internal.message.FieldException;
import org.openhab.binding.insteon.internal.message.Msg;
import org.openhab.binding.insteon.internal.message.MsgListener;
import org.openhab.binding.insteon.internal.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

/**
 * A majority of the code in this file is from the OpenHAB 1 binding
 * org.openhab.binding.insteonplm.InsteonPLMActiveBinding. Including the comments below.
 *
 * -----------------------------------------------------------------------------------------------
 *
 * This class represents the actual implementation of the binding, and controls the high level flow
 * of messages to and from the InsteonModem.
 *
 * Writing this binding has been an odyssey through the quirks of the Insteon protocol
 * and Insteon devices. A substantial redesign was necessary at some point along the way.
 * Here are some of the hard learned lessons that should be considered by anyone who wants
 * to re-architect the binding:
 *
 * 1) The entries of the link database of the modem are not reliable. The category/subcategory entries in
 * particular have junk data. Forget about using the modem database to generate a list of devices.
 * The database should only be used to verify that a device has been linked.
 *
 * 2) Querying devices for their product information does not work either. First of all, battery operated devices
 * (and there are a lot of those) have their radio switched off, and may generally not respond to product
 * queries. Even main stream hardwired devices sold presently (like the 2477s switch and the 2477d dimmer)
 * don't even have a product ID. Although supposedly part of the Insteon protocol, we have yet to
 * encounter a device that would cough up a product id when queried, even among very recent devices. They
 * simply return zeros as product id. Lesson: forget about querying devices to generate a device list.
 *
 * 3) Polling is a thorny issue: too much traffic on the network, and messages will be dropped left and right,
 * and not just the poll related ones, but others as well. In particular sending back-to-back messages
 * seemed to result in the second message simply never getting sent, without flow control back pressure
 * (NACK) from the modem. For now the work-around is to space out the messages upon sending, and
 * in general poll as infrequently as acceptable.
 *
 * 4) Instantiating and tracking devices when reported by the modem (either from the database, or when
 * messages are received) leads to complicated state management because there is no guarantee at what
 * point (if at all) the binding configuration will be available. It gets even more difficult when
 * items are created, destroyed, and modified while the binding runs.
 *
 * For the above reasons, devices are only instantiated when they are referenced by binding information.
 * As nice as it would be to discover devices and their properties dynamically, we have abandoned that
 * path because it had led to a complicated and fragile system which due to the technical limitations
 * above was inherently squirrely.
 *
 *
 * @author Bernd Pfrommer - Initial contribution
 * @author Daniel Pfrommer - OpenHAB 1 insteonplm binding
 * @author Rob Nielsen - Port to OpenHAB 2 insteon binding
 */
@NonNullByDefault
@SuppressWarnings({ "null", "unused" })
public class InsteonBinding {
    private static final int DEAD_DEVICE_COUNT = 10;

    private final Logger logger = LoggerFactory.getLogger(InsteonBinding.class);

    private Driver m_driver = new Driver();
    private ConcurrentHashMap<InsteonAddress, InsteonDevice> m_devices = new ConcurrentHashMap<InsteonAddress, InsteonDevice>();
    private ConcurrentHashMap<String, InsteonChannelConfiguration> m_bindingConfigs = new ConcurrentHashMap<>();
    private PortListener m_portListener = new PortListener();
    private int m_devicePollIntervalMilliseconds = 300000;
    private int m_deadDeviceTimeout = -1;
    private int m_messagesReceived = 0;
    private boolean m_isActive = false; // state of binding
    private int m_x10HouseUnit = -1;
    private InsteonNetworkHandler m_handler;

    public InsteonBinding(InsteonNetworkHandler handler, @Nullable InsteonNetworkConfiguration config,
            @Nullable SerialPortManager serialPortManager) {
        this.m_handler = handler;

        Integer devicePollIntervalSeconds = config.getDevicePollIntervalSeconds();
        if (devicePollIntervalSeconds != null) {
            m_devicePollIntervalMilliseconds = devicePollIntervalSeconds * 1000;
        }
        logger.debug("device poll interval set to {} seconds", m_devicePollIntervalMilliseconds / 1000);

        Integer modemDbRetryTimeoutSeconds = config.getModemDbRetryTimeoutSeconds();
        if (modemDbRetryTimeoutSeconds != null) {
            logger.debug("setting modem db retry timeout to {} seconds", modemDbRetryTimeoutSeconds);
            m_driver.setModemDBRetryTimeout(modemDbRetryTimeoutSeconds * 1000);
        }

        String additionalDevices = config.getAdditionalDevices();
        if (additionalDevices != null) {
            try {
                DeviceTypeLoader.s_instance().loadDeviceTypesXML(additionalDevices);
                logger.debug("read additional device definitions from {}", additionalDevices);
            } catch (ParserConfigurationException | SAXException | IOException e) {
                logger.warn("error reading additional devices from {}", additionalDevices, e);
            }
        }

        String additionalFeatures = config.getAdditionalFeatures();
        if (additionalFeatures != null) {
            logger.debug("reading additional feature templates from {}", additionalFeatures);
            DeviceFeature.s_readFeatureTemplates(additionalFeatures);
        }

        m_deadDeviceTimeout = m_devicePollIntervalMilliseconds * DEAD_DEVICE_COUNT;
        logger.debug("dead device timeout set to {} seconds", m_deadDeviceTimeout / 1000);

        String port = config.getPort();
        logger.info("port = '{}'", port);
        m_driver.addPort("port", port, serialPortManager);
        m_driver.addMsgListener(m_portListener, port);

        logger.debug("setting driver listener");
        m_driver.setDriverListener(m_portListener);
    }

    public boolean startPolling() {
        logger.debug("starting {} ports", m_driver.getNumberOfPorts());

        m_driver.startAllPorts();
        logger.debug("ports started");
        switch (m_driver.getNumberOfPorts()) {
            case 0:
                logger.warn("initialization complete, but found no ports!");
                return false;
            case 1:
                logger.debug("initialization complete, found 1 port!");
                break;
            default:
                logger.warn("initialization complete, found {} ports.", m_driver.getNumberOfPorts());
                break;
        }

        return true;
    }

    public void setIsActive(boolean isActive) {
        m_isActive = isActive;
    }

    public void sendCommand(String channelName, Command command) {
        if (!m_isActive) {
            logger.debug("not ready to handle commands yet, returning.");
            return;
        }

        InsteonChannelConfiguration bindingConfig = m_bindingConfigs.get(channelName);
        if (bindingConfig == null) {
            logger.warn("unable to find binding config for channel {}", channelName);
            return;
        }

        InsteonDevice dev = getDevice(bindingConfig.getAddress());
        if (dev == null) {
            logger.warn("no device found with insteon address {}", bindingConfig.getAddress());
            return;
        }

        dev.processCommand(m_driver, bindingConfig, command);

        logger.debug("found binding config for channel {}", channelName);
    }

    public void addFeatureListener(InsteonChannelConfiguration bindingConfig) {
        logger.debug("adding listener for channel {}", bindingConfig.getChannelName());

        InsteonAddress address = bindingConfig.getAddress();
        InsteonDevice dev = getDevice(address);
        @Nullable
        DeviceFeature f = dev.getFeature(bindingConfig.getFeature());
        if (f == null || f.isFeatureGroup()) {
            StringBuilder buf = new StringBuilder();
            ArrayList<String> names = new ArrayList<String>(dev.getFeatures().keySet());
            Collections.sort(names);
            for (String name : names) {
                DeviceFeature feature = dev.getFeature(name);
                if (!feature.isFeatureGroup()) {
                    if (buf.length() > 0) {
                        buf.append(", ");
                    }
                    buf.append(name);
                }
            }

            logger.warn("channel {} references unknown feature: {}, it will be ignored. Known features for {} are: {}.",
                    bindingConfig.getChannelName(), bindingConfig.getFeature(), bindingConfig.getProductKey(),
                    buf.toString());
            return;
        }

        DeviceFeatureListener fl = new DeviceFeatureListener(this, bindingConfig.getChannelUID(),
                bindingConfig.getChannelName());
        fl.setParameters(bindingConfig.getParameters());
        f.addListener(fl);

        m_bindingConfigs.put(bindingConfig.getChannelName(), bindingConfig);
    }

    public void removeFeatureListener(ChannelUID channelUID) {
        String channelName = channelUID.getAsString();

        logger.debug("removing listener for channel {}", channelName);

        for (Iterator<Entry<InsteonAddress, InsteonDevice>> it = m_devices.entrySet().iterator(); it.hasNext();) {
            InsteonDevice dev = it.next().getValue();
            boolean removedListener = dev.removeFeatureListener(channelName);
            if (removedListener) {
                logger.trace("removed feature listener {} from dev {}", channelName, dev);
            }
        }
    }

    public void updateFeatureState(ChannelUID channelUID, State state) {
        m_handler.updateState(channelUID, state);
    }

    public InsteonDevice makeNewDevice(InsteonAddress addr, String productKey) {
        DeviceType dt = DeviceTypeLoader.s_instance().getDeviceType(productKey);
        InsteonDevice dev = InsteonDevice.s_makeDevice(dt);
        dev.setAddress(addr);
        dev.setDriver(m_driver);
        dev.addPort(m_driver.getDefaultPort());
        if (!dev.hasValidPollingInterval()) {
            dev.setPollInterval(m_devicePollIntervalMilliseconds);
        }
        if (m_driver.isModemDBComplete() && dev.getStatus() != DeviceStatus.POLLING) {
            int ndev = checkIfInModemDatabase(dev);
            if (dev.hasModemDBEntry()) {
                dev.setStatus(DeviceStatus.POLLING);
                Poller.s_instance().startPolling(dev, ndev);
            }
        }
        m_devices.put(addr, dev);

        m_handler.insteonDeviceWasCreated();

        return (dev);
    }

    public void removeDevice(InsteonAddress addr) {
        InsteonDevice dev = m_devices.remove(addr);
        if (dev == null) {
            return;
        }

        if (dev.getStatus() == DeviceStatus.POLLING) {
            Poller.s_instance().stopPolling(dev);
        }
    }

    /**
     * Checks if a device is in the modem link database, and, if the database
     * is complete, logs a warning if the device is not present
     *
     * @param dev The device to search for in the modem database
     * @return number of devices in modem database
     */
    private int checkIfInModemDatabase(InsteonDevice dev) {
        try {
            InsteonAddress addr = dev.getAddress();
            HashMap<InsteonAddress, @Nullable ModemDBEntry> dbes = m_driver.lockModemDBEntries();
            if (dbes.containsKey(addr)) {
                if (!dev.hasModemDBEntry()) {
                    logger.info("device {} found in the modem database and {}.", addr, getLinkInfo(dbes, addr));
                    dev.setHasModemDBEntry(true);
                }
            } else {
                if (m_driver.isModemDBComplete() && !addr.isX10()) {
                    logger.warn("device {} not found in the modem database. Did you forget to link?", addr);
                }
            }
            return dbes.size();
        } finally {
            m_driver.unlockModemDBEntries();
        }
    }

    /**
     * Everything below was copied from Insteon PLM v1
     */

    /**
     * Clean up all state.
     */
    public void shutdown() {
        logger.debug("shutting down Insteon bridge");
        m_driver.stopAllPorts();
        m_devices.clear();
        RequestQueueManager.s_destroyInstance();
        Poller.s_instance().stop();
        m_isActive = false;
    }

    /**
     * Method to find a device by address
     *
     * @param aAddr the insteon address to search for
     * @return reference to the device, or null if not found
     */
    public @Nullable InsteonDevice getDevice(@Nullable InsteonAddress aAddr) {
        InsteonDevice dev = (aAddr == null) ? null : m_devices.get(aAddr);
        return (dev);
    }

    private String getLinkInfo(HashMap<InsteonAddress, @Nullable ModemDBEntry> dbes, InsteonAddress a) {
        ModemDBEntry dbe = dbes.get(a);
        ArrayList<Byte> controls = dbe.getControls();
        ArrayList<Byte> responds = dbe.getRespondsTo();

        StringBuilder buf = new StringBuilder("the modem");
        if (!controls.isEmpty()) {
            buf.append(" controls groups [");
            buf.append(toGroupString(controls));
            buf.append("]");
        }

        if (!responds.isEmpty()) {
            if (!controls.isEmpty()) {
                buf.append(" and");
            }

            buf.append(" responds to groups [");
            buf.append(toGroupString(responds));
            buf.append("]");
        }

        return buf.toString();
    }

    private String toGroupString(ArrayList<Byte> group) {
        ArrayList<Byte> sorted = new ArrayList<Byte>(group);
        Collections.sort(sorted);

        StringBuilder buf = new StringBuilder();
        for (Byte b : sorted) {
            if (buf.length() > 0) {
                buf.append(",");
            }
            buf.append("0x");
            buf.append(Utils.getHexString(b));
        }

        return buf.toString();
    }

    public void logDeviceStatistics() {
        String msg = String.format("devices: %3d configured, %3d polling, msgs received: %5d", m_devices.size(),
                Poller.s_instance().getSizeOfQueue(), m_messagesReceived);
        logger.info("{}", msg);
        m_messagesReceived = 0;
        for (InsteonDevice dev : m_devices.values()) {
            if (dev.isModem()) {
                continue;
            }
            if (m_deadDeviceTimeout > 0 && dev.getPollOverDueTime() > m_deadDeviceTimeout) {
                logger.info("device {} has not responded to polls for {} sec", dev.toString(),
                        dev.getPollOverDueTime() / 3600);
            }
        }
    }

    /**
     * Handles messages that come in from the ports.
     * Will only process one message at a time.
     */
    @NonNullByDefault
    private class PortListener implements MsgListener, DriverListener {
        @Override
        public void msg(Msg msg, String fromPort) {
            if (msg.isEcho() || msg.isPureNack()) {
                return;
            }
            m_messagesReceived++;
            logger.debug("got msg: {}", msg);
            if (msg.isX10()) {
                handleX10Message(msg, fromPort);
            } else {
                handleInsteonMessage(msg, fromPort);
            }

        }

        @Override
        public void driverCompletelyInitialized() {
            List<String> missing = new ArrayList<String>();
            try {
                HashMap<InsteonAddress, @Nullable ModemDBEntry> dbes = m_driver.lockModemDBEntries();
                logger.info("modem database has {} entries!", dbes.size());
                if (dbes.isEmpty()) {
                    logger.warn("the modem link database is empty!");
                }
                for (InsteonAddress k : dbes.keySet()) {
                    logger.debug("modem db entry: {}", k);
                }
                HashSet<InsteonAddress> addrs = new HashSet<InsteonAddress>();
                for (InsteonDevice dev : m_devices.values()) {
                    InsteonAddress a = dev.getAddress();
                    if (!dbes.containsKey(a)) {
                        if (!a.isX10()) {
                            logger.warn("device {} not found in the modem database. Did you forget to link?", a);
                        }
                    } else {
                        if (!dev.hasModemDBEntry()) {
                            addrs.add(a);
                            logger.info("device {} found in the modem database and {}.", a, getLinkInfo(dbes, a));
                            dev.setHasModemDBEntry(true);
                        }
                        if (dev.getStatus() != DeviceStatus.POLLING) {
                            Poller.s_instance().startPolling(dev, dbes.size());
                        }
                    }
                }

                for (InsteonAddress k : dbes.keySet()) {
                    if (!addrs.contains(k) && !k.equals(dbes.get(k).getPort().getAddress())) {
                        logger.info("device {} found in the modem database, but is not configured as a thing and {}.",
                                k, getLinkInfo(dbes, k));

                        missing.add(k.toString());
                    }
                }
            } finally {
                m_driver.unlockModemDBEntries();
            }

            if (!missing.isEmpty()) {
                m_handler.addMissingDevices(missing);
            }
        }

        private void handleInsteonMessage(Msg msg, String fromPort) {
            InsteonAddress toAddr = msg.getAddr("toAddress");
            if (!msg.isBroadcast() && !m_driver.isMsgForUs(toAddr)) {
                // not for one of our modems, do not process
                return;
            }
            InsteonAddress fromAddr = msg.getAddr("fromAddress");
            if (fromAddr == null) {
                logger.debug("invalid fromAddress, ignoring msg {}", msg);
                return;
            }
            handleMessage(fromPort, fromAddr, msg);
        }

        private void handleX10Message(Msg msg, String fromPort) {
            try {
                int x10Flag = msg.getByte("X10Flag") & 0xff;
                int rawX10 = msg.getByte("rawX10") & 0xff;
                if (x10Flag == 0x80) { // actual command
                    if (m_x10HouseUnit != -1) {
                        InsteonAddress fromAddr = new InsteonAddress((byte) m_x10HouseUnit);
                        handleMessage(fromPort, fromAddr, msg);
                    }
                } else if (x10Flag == 0) {
                    // what unit the next cmd will apply to
                    m_x10HouseUnit = rawX10 & 0xFF;
                }
            } catch (FieldException e) {
                logger.warn("got bad X10 message: {}", msg, e);
                return;
            }
        }

        private void handleMessage(String fromPort, InsteonAddress fromAddr, Msg msg) {
            InsteonDevice dev = getDevice(fromAddr);
            if (dev == null) {
                logger.debug("dropping message from unknown device with address {}", fromAddr);
            } else {
                dev.handleMessage(fromPort, msg);
            }
        }
    }
}
