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
package org.openhab.binding.insteon.internal.discovery;

import java.util.Arrays;
import java.util.HashSet;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.insteon.internal.InsteonBindingConstants;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link InsteonNetworkDiscoveryService} is responsible for device discovery.
 *
 * @author Rob Nielsen - Initial contribution
 */
@NonNullByDefault
@Component(service = DiscoveryService.class, immediate = true, configurationPid = "discovery.insteon")
public class InsteonNetworkDiscoveryService extends AbstractDiscoveryService {
    private final Logger logger = LoggerFactory.getLogger(InsteonNetworkDiscoveryService.class);

    private static final ThingUID LOCAL_THING = new ThingUID(InsteonBindingConstants.NETWORK_THING_TYPE, "local");

    private static final int DISCOVER_TIMEOUT_SECONDS = 2;

    public InsteonNetworkDiscoveryService() {
        super(new HashSet<>(Arrays.asList(InsteonBindingConstants.NETWORK_THING_TYPE)), DISCOVER_TIMEOUT_SECONDS, true);

        logger.debug("Initializing InsteonNetworkDiscoveryService");
    }

    @Override
    protected void startBackgroundDiscovery() {
        startScan();
    }

    @Override
    protected void startScan() {
        logger.debug("Starting scan for Insteon network");

        thingDiscovered(DiscoveryResultBuilder.create(LOCAL_THING).withLabel("Insteon PLM or Hub").build());
    }
}
