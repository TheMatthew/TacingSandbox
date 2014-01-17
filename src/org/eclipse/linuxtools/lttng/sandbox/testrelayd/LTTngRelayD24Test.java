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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import org.eclipse.linuxtools.lttng.sandbox.testrelayd.LTTngRelayDCommands2_4.lttng_viewer_attach_session_request;
import org.eclipse.linuxtools.lttng.sandbox.testrelayd.LTTngRelayDCommands2_4.lttng_viewer_attach_session_response;
import org.eclipse.linuxtools.lttng.sandbox.testrelayd.LTTngRelayDCommands2_4.lttng_viewer_cmd;
import org.eclipse.linuxtools.lttng.sandbox.testrelayd.LTTngRelayDCommands2_4.lttng_viewer_command;
import org.eclipse.linuxtools.lttng.sandbox.testrelayd.LTTngRelayDCommands2_4.lttng_viewer_connect;
import org.eclipse.linuxtools.lttng.sandbox.testrelayd.LTTngRelayDCommands2_4.lttng_viewer_connection_type;
import org.eclipse.linuxtools.lttng.sandbox.testrelayd.LTTngRelayDCommands2_4.lttng_viewer_list_sessions;
import org.eclipse.linuxtools.lttng.sandbox.testrelayd.LTTngRelayDCommands2_4.lttng_viewer_session;
import org.eclipse.linuxtools.lttng.sandbox.testrelayd.LTTngRelayDCommands2_4.lttng_viewer_stream;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for lttng-relayd. It actually allows us to test the API.
 *
 * @author Matthew Khouzam
 *
 */
public class LTTngRelayD24Test {

    static final String address = "142.133.111.152"; // change me //$NON-NLS-1$

    private Socket myConnection;
    private DataInputStream inNet;
    private DataOutputStream outNet;

    /**
     * Performed before each test
     * @throws UnknownHostException for localhost? we'd have a bad time
     * @throws IOException out of memory and such
     */
    @Before
    public void init() throws UnknownHostException, IOException {
        final InetAddress target = InetAddress.getByName(address);
        myConnection = new Socket(target, 5344);
        inNet = new DataInputStream(myConnection.getInputStream());
        outNet = new DataOutputStream(myConnection.getOutputStream());
    }

    /**
     * Test a connection
     * @throws IOException network timeout?
     */
    @Test
    public void testViewerConnection() throws IOException {
        establishConnection();
        lttng_viewer_list_sessions sessions = getSessions();
        lttng_viewer_session lttng_viewer_session = sessions.session_list[0];
        attachToSession(lttng_viewer_session);
    }

    private void attachToSession(lttng_viewer_session lttng_viewer_session) throws IOException {
        lttng_viewer_cmd listSessionsCmd = new lttng_viewer_cmd();
        listSessionsCmd.cmd = lttng_viewer_command.VIEWER_ATTACH_SESSION;
        outNet.write(listSessionsCmd.getBytes());

        lttng_viewer_attach_session_request attachRequest = new lttng_viewer_attach_session_request();
        attachRequest.session_id = lttng_viewer_session.id;
        attachRequest.seek = LTTngRelayDCommands2_4.lttng_viewer_seek.VIEWER_SEEK_BEGINNING;
        outNet.write(attachRequest.getBytes());
        outNet.flush();

        final int BUFFER_SIZE = 10 * 4096;
        byte[] data = new byte[BUFFER_SIZE];
        int count = inNet.read(data);
        if (count == 8) {
            count += inNet.read(data, 8, BUFFER_SIZE - 8);
        }

        lttng_viewer_attach_session_response attachResponse = new lttng_viewer_attach_session_response();
        attachResponse.populate(data);
        assertEquals(LTTngRelayDCommands2_4.lttng_viewer_attach_return_code.VIEWER_ATTACH_OK, attachResponse.status);
        assertTrue(attachResponse.stream_list.length > 0);

        for (lttng_viewer_stream stream : attachResponse.stream_list) {
            System.out.println("Stream id: " + stream.id  + " name: " + new String(stream.channel_name) + " path: " + new String(stream.path_name));
        }
    }

    private lttng_viewer_list_sessions getSessions() throws IOException {
        lttng_viewer_cmd listSessionsCmd = new lttng_viewer_cmd();
        listSessionsCmd.cmd = lttng_viewer_command.VIEWER_LIST_SESSIONS;
        listSessionsCmd.data_size = 0;

        outNet.write(listSessionsCmd.getBytes());
        byte[] data = new byte[4096];
        int count = inNet.read(data);
        if (count == 4) {
            count += inNet.read(data, 4, 4096 - 4);
        }

        lttng_viewer_list_sessions listingResponse = new lttng_viewer_list_sessions();
        listingResponse.populate(data);

        assertEquals(listingResponse.getSize(), count);

        assertEquals(1, listingResponse.sessions_count);
        assertEquals(1, listingResponse.session_list.length);
        String hostName = new String(listingResponse.session_list[0].hostname);
        String pathName = new String(listingResponse.session_list[0].session_name);
        System.out.println(hostName + " " + pathName); //$NON-NLS-1$
        if (listingResponse.session_list[0].live_timer == 0) {
            System.out.println("Not a Live session"); //$NON-NLS-1$
        }
        return listingResponse;
    }

    private void establishConnection() throws IOException {
        lttng_viewer_cmd connectCommand = new lttng_viewer_cmd();
        lttng_viewer_connect connectPayload = new lttng_viewer_connect();
        connectCommand.cmd = lttng_viewer_command.VIEWER_CONNECT;
        connectCommand.data_size = connectPayload.size();
        outNet.write(connectCommand.getBytes());
        outNet.flush();

        connectPayload.major = 2;
        connectPayload.minor = 4;
        connectPayload.type = lttng_viewer_connection_type.VIEWER_CLIENT_COMMAND;
        connectPayload.viewer_session_id = 0;
        outNet.write(connectPayload.getBytes());
        outNet.flush();

        byte data[] = new byte[connectPayload.size()];
        int count = inNet.read(data);
        assertEquals(connectPayload.size(), count);
        connectPayload.populate(data);
        System.out.println("Viewer session id: " + connectPayload.viewer_session_id); //$NON-NLS-1$
    }
}
