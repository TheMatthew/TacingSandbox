/**********************************************************************
 * Copyright (c) 2013 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Matthew Khouzam - Initial implementation
 **********************************************************************/
package org.eclipse.linuxtools.lttng.sandbox.testrelayd;

import static org.junit.Assert.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import org.eclipse.linuxtools.lttng.sandbox.testrelayd.LTTngRelayDCommands2_4.lttng_viewer_cmd;
import org.eclipse.linuxtools.lttng.sandbox.testrelayd.LTTngRelayDCommands2_4.lttng_viewer_command;
import org.eclipse.linuxtools.lttng.sandbox.testrelayd.LTTngRelayDCommands2_4.lttng_viewer_connect;
import org.eclipse.linuxtools.lttng.sandbox.testrelayd.LTTngRelayDCommands2_4.lttng_viewer_connection_type;
import org.eclipse.linuxtools.lttng.sandbox.testrelayd.LTTngRelayDCommands2_4.lttng_viewer_list_sessions;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for lttng-relayd. It actually allows us to test the API.
 *
 * @author Matthew Khouzam
 *
 */
public class LTTngRelayD24Test {

    private Socket myConnection;
    DataInputStream inNet;
    DataOutputStream outNet;
    byte address[] = { (byte) 127, (byte) 0, (byte) 0, (byte) 1 }; // change me

    /**
     * Performed before each test
     * @throws UnknownHostException for localhost? we'd have a bad time
     * @throws IOException out of memory and such
     */
    @Before
    public void init() throws UnknownHostException, IOException {
        final InetAddress target = InetAddress.getByAddress(address);
        myConnection = new Socket(target, 5344);
        inNet = new DataInputStream(myConnection.getInputStream());
        outNet = new DataOutputStream(myConnection.getOutputStream());
    }

    /**
     * Test a connection
     * @throws IOException network timeout?
     */
    @Test
    public void TestViewerConnection() throws IOException {
        lttng_viewer_cmd command = new lttng_viewer_cmd();
        lttng_viewer_connect connnection = new lttng_viewer_connect();
        lttng_viewer_connect connectionResponse = new lttng_viewer_connect();
        lttng_viewer_list_sessions listing = new lttng_viewer_list_sessions();
        lttng_viewer_list_sessions listingResponse = new lttng_viewer_list_sessions();

        command.cmd = lttng_viewer_command.VIEWER_CONNECT;
        command.cmd_version = 2;
        command.data_size = connectionResponse.size();
        connectionResponse.major = 2;
        connectionResponse.minor = 4;
        connectionResponse.type = lttng_viewer_connection_type.VIEWER_CLIENT_COMMAND;
        connectionResponse.viewer_session_id = 0;

        outNet.write(command.getBytes());
        outNet.flush();

        outNet.write(connectionResponse.getBytes());
        outNet.flush();

        byte data[] = new byte[connectionResponse.size()];
        int count = inNet.read(data);

        assertEquals(connectionResponse.size(), count);
        assertEquals(connectionResponse.size(), count);
        connnection.populate(data);
        assertEquals(2, connnection.major);
        assertEquals(4, connnection.minor);

        command.cmd = lttng_viewer_command.VIEWER_LIST_SESSIONS;
        command.data_size = 4;

        listing.sessions_count = 0;

        outNet.write(command.getBytes());
        outNet.write(listing.getBytes());
        data = new byte[4096];
        count = inNet.read(data);

        listingResponse.populate(data);
        assertEquals(1, listingResponse.sessions_count);
        assertEquals(1, listingResponse.session_list.length);
        String hostName = new String(listingResponse.session_list[0].hostname);
        String pathName = new String(listingResponse.session_list[0].session_name);
        System.out.println(hostName +" "+ pathName);


    }
}
