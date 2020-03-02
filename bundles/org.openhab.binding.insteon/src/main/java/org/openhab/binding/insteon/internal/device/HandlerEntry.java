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
package org.openhab.binding.insteon.internal.device;

import java.util.HashMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Ugly little helper class to facilitate late instantiation of handlers
 *
 * @author Bernd Pfrommer - Initial contribution
 * @author Rob Nielsen - Port to OpenHAB 2 insteon binding
 */
@NonNullByDefault
public class HandlerEntry {
    HandlerEntry(String name, HashMap<String, @Nullable String> params) {
        m_hname = name;
        m_params = params;
    }

    HashMap<String, @Nullable String> m_params;
    String m_hname;

    HashMap<String, @Nullable String> getParams() {
        return m_params;
    }

    String getName() {
        return m_hname;
    }
}
