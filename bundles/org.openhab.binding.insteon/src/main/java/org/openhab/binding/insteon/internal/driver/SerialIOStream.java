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
package org.openhab.binding.insteon.internal.driver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.UnsupportedCommOperationException;

/**
 * Implements IOStream for serial devices.
 *
 * @author Bernd Pfrommer - Initial contribution
 * @author Daniel Pfrommer - OpenHAB 1 insteonplm binding
 * @author Rob Nielsen - Port to OpenHAB 2 insteon binding
 */
@NonNullByDefault
@SuppressWarnings("null")
public class SerialIOStream extends IOStream {
    private final Logger logger = LoggerFactory.getLogger(SerialIOStream.class);
    private @Nullable SerialPort m_port = null;
    private final String m_appName = "PLM";
    private int m_baudRate = 19200;
    private String m_devName;
    private boolean m_validConfig = true;

    public SerialIOStream(String config) {
        String[] parts = config.split(",");
        m_devName = parts[0];
        for (int i = 1; i < parts.length; i++) {
            String parameter = parts[i];
            String[] paramParts = parameter.split("=");
            if (paramParts.length != 2) {
                logger.warn("{} invalid parameter format '{}', must be 'key=value'.", config, parameter);

                m_validConfig = false;
            } else {
                String key = paramParts[0];
                String value = paramParts[1];
                if (key.equals("baudRate")) {
                    try {
                        Integer baudRate = Integer.parseInt(value);
                        logger.info("setting {} baud rate to {}.", m_devName, baudRate);
                        m_baudRate = baudRate;
                    } catch (NumberFormatException e) {
                        logger.warn("{} baudRate {} must be an integer.", config, value);

                        m_validConfig = false;
                    }
                } else {
                    logger.warn("{} invalid parameter '{}'.", config, parameter);

                    m_validConfig = false;
                }
            }
        }
    }

    @Override
    public boolean open() {
        if (!m_validConfig) {
            logger.warn("{} has an invalid configuration.", m_devName);
            return false;
        }

        try {
            updateSerialProperties(m_devName);
            CommPortIdentifier ci = CommPortIdentifier.getPortIdentifier(m_devName);
            CommPort cp = ci.open(m_appName, 1000);
            if (cp instanceof SerialPort) {
                m_port = (SerialPort) cp;
            } else {
                throw new IllegalStateException("unknown port type");
            }
            m_port.setSerialPortParams(m_baudRate, SerialPort.DATABITS_8, SerialPort.STOPBITS_1,
                    SerialPort.PARITY_NONE);
            m_port.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);
            logger.debug("setting {} baud rate to {}", m_devName, m_baudRate);
            m_port.disableReceiveFraming();
            m_port.enableReceiveThreshold(1);
            // m_port.disableReceiveTimeout();
            m_port.enableReceiveTimeout(1000);
            m_in = m_port.getInputStream();
            m_out = m_port.getOutputStream();
            logger.info("successfully opened port {}", m_devName);
            return true;
        } catch (IOException e) {
            logger.warn("cannot open port: {}, got IOException ", m_devName, e);
        } catch (PortInUseException e) {
            logger.warn("cannot open port: {}, it is in use!", m_devName);
        } catch (UnsupportedCommOperationException e) {
            logger.warn("got unsupported operation {} on port {}", e.getMessage(), m_devName);
        } catch (NoSuchPortException e) {
            logger.warn("got no such port for {}", m_devName);
        } catch (IllegalStateException e) {
            logger.warn("got unknown port type for {}", m_devName);
        }
        return false;
    }

    private void updateSerialProperties(String devName) {

        /*
         * By default, RXTX searches only devices /dev/ttyS* and
         * /dev/ttyUSB*, and will therefore not find devices that
         * have been symlinked. Adding them however is tricky, see below.
         */

        //
        // first go through the port identifiers to find any that are not in
        // "gnu.io.rxtx.SerialPorts"
        //
        ArrayList<String> allPorts = new ArrayList<String>();
        @SuppressWarnings("rawtypes")
        Enumeration portList = CommPortIdentifier.getPortIdentifiers();
        while (portList.hasMoreElements()) {
            CommPortIdentifier id = (CommPortIdentifier) portList.nextElement();
            if (id.getPortType() == CommPortIdentifier.PORT_SERIAL) {
                allPorts.add(id.getName());
            }
        }
        logger.trace("ports found from identifiers: {}", StringUtils.join(allPorts, ":"));
        //
        // now add our port so it's in the list
        //
        if (!allPorts.contains(devName)) {
            allPorts.add(devName);
        }
        //
        // add any that are already in "gnu.io.rxtx.SerialPorts"
        // so we don't accidentally overwrite some of those ports

        String ports = System.getProperty("gnu.io.rxtx.SerialPorts");
        if (ports != null) {
            ArrayList<String> propPorts = new ArrayList<String>(Arrays.asList(ports.split(":")));
            for (String p : propPorts) {
                if (!allPorts.contains(p)) {
                    allPorts.add(p);
                }
            }
        }
        String finalPorts = StringUtils.join(allPorts, ":");
        logger.trace("final port list: {}", finalPorts);

        //
        // Finally overwrite the "gnu.io.rxtx.SerialPorts" System property.
        //
        // Note: calling setProperty() is not threadsafe. All bindings run in
        // the same address space, System.setProperty() is globally visible
        // to all bindings.
        // This means if multiple bindings use the serial port there is a
        // race condition where two bindings could be changing the properties
        // at the same time
        //
        System.setProperty("gnu.io.rxtx.SerialPorts", finalPorts);
    }

    @Override
    public void close() {
        if (m_port != null) {
            m_port.close();
        }
        m_port = null;
    }

}
