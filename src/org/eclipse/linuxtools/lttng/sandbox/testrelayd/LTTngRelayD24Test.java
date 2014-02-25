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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.linuxtools.lttng.sandbox.testrelayd.LTTngRelayDCommands2_4.lttng_viewer_attach_session_request;
import org.eclipse.linuxtools.lttng.sandbox.testrelayd.LTTngRelayDCommands2_4.lttng_viewer_attach_session_response;
import org.eclipse.linuxtools.lttng.sandbox.testrelayd.LTTngRelayDCommands2_4.lttng_viewer_cmd;
import org.eclipse.linuxtools.lttng.sandbox.testrelayd.LTTngRelayDCommands2_4.lttng_viewer_command;
import org.eclipse.linuxtools.lttng.sandbox.testrelayd.LTTngRelayDCommands2_4.lttng_viewer_connect;
import org.eclipse.linuxtools.lttng.sandbox.testrelayd.LTTngRelayDCommands2_4.lttng_viewer_connection_type;
import org.eclipse.linuxtools.lttng.sandbox.testrelayd.LTTngRelayDCommands2_4.lttng_viewer_get_metadata;
import org.eclipse.linuxtools.lttng.sandbox.testrelayd.LTTngRelayDCommands2_4.lttng_viewer_get_metadata_return_code;
import org.eclipse.linuxtools.lttng.sandbox.testrelayd.LTTngRelayDCommands2_4.lttng_viewer_get_next_index;
import org.eclipse.linuxtools.lttng.sandbox.testrelayd.LTTngRelayDCommands2_4.lttng_viewer_get_packet;
import org.eclipse.linuxtools.lttng.sandbox.testrelayd.LTTngRelayDCommands2_4.lttng_viewer_get_packet_return_code;
import org.eclipse.linuxtools.lttng.sandbox.testrelayd.LTTngRelayDCommands2_4.lttng_viewer_index;
import org.eclipse.linuxtools.lttng.sandbox.testrelayd.LTTngRelayDCommands2_4.lttng_viewer_list_sessions;
import org.eclipse.linuxtools.lttng.sandbox.testrelayd.LTTngRelayDCommands2_4.lttng_viewer_metadata_packet;
import org.eclipse.linuxtools.lttng.sandbox.testrelayd.LTTngRelayDCommands2_4.lttng_viewer_next_index_return_code;
import org.eclipse.linuxtools.lttng.sandbox.testrelayd.LTTngRelayDCommands2_4.lttng_viewer_session;
import org.eclipse.linuxtools.lttng.sandbox.testrelayd.LTTngRelayDCommands2_4.lttng_viewer_stream;
import org.eclipse.linuxtools.lttng.sandbox.testrelayd.LTTngRelayDCommands2_4.lttng_viewer_trace_packet;
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
        lttng_viewer_attach_session_response attachedSession = attachToSession(lttng_viewer_session);
        String metaData = getMetadata(attachedSession);
        getPackets(attachedSession);
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
        inNet.readFully(data, 0, connectPayload.size());
        connectPayload.populate(data);
        System.out.println("Viewer session id: " + connectPayload.viewer_session_id); //$NON-NLS-1$
    }

    private lttng_viewer_list_sessions getSessions() throws IOException {
        lttng_viewer_cmd listSessionsCmd = new lttng_viewer_cmd();
        listSessionsCmd.cmd = lttng_viewer_command.VIEWER_LIST_SESSIONS;
        listSessionsCmd.data_size = 0;

        outNet.write(listSessionsCmd.getBytes());
        byte[] data = new byte[4096];
        inNet.readFully(data, 0, 4);

        lttng_viewer_list_sessions listingResponse = new lttng_viewer_list_sessions();
        listingResponse.populate(data);

        List<lttng_viewer_session> temp = new ArrayList<>();
        for (int i = 0; i < listingResponse.sessions_count; i++) {
            lttng_viewer_session viewer = new lttng_viewer_session();

            inNet.readFully(data, 0, viewer.size());
            viewer.populate(data);
            temp.add(viewer);
        }
        listingResponse.session_list = temp.toArray(new lttng_viewer_session[0]);

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

    private lttng_viewer_attach_session_response attachToSession(lttng_viewer_session lttng_viewer_session) throws IOException {
        lttng_viewer_cmd listSessionsCmd = new lttng_viewer_cmd();
        listSessionsCmd.cmd = lttng_viewer_command.VIEWER_ATTACH_SESSION;
        outNet.write(listSessionsCmd.getBytes());

        lttng_viewer_attach_session_request attachRequest = new lttng_viewer_attach_session_request();
        attachRequest.session_id = lttng_viewer_session.id;
        attachRequest.seek = LTTngRelayDCommands2_4.lttng_viewer_seek.VIEWER_SEEK_BEGINNING;
        outNet.write(attachRequest.getBytes());
        outNet.flush();

        byte[] data = new byte[8];
        inNet.readFully(data, 0, 8);

        lttng_viewer_attach_session_response attachResponse = new lttng_viewer_attach_session_response();
        attachResponse.populate(data);

        List<lttng_viewer_stream> temp = new ArrayList<>();
        for (int i = 0; i < attachResponse.streams_count; i++) {
            lttng_viewer_stream stream = new lttng_viewer_stream();
            byte[] streamData = new byte[stream.size()];
            inNet.readFully(streamData, 0, stream.size());
            stream.populate(streamData);
            temp.add(stream);
        }
        attachResponse.stream_list = temp.toArray(new lttng_viewer_stream[0]);

        assertEquals(LTTngRelayDCommands2_4.lttng_viewer_attach_return_code.VIEWER_ATTACH_OK, attachResponse.status);
        assertTrue("Need at least one for the metadata and one channel", attachResponse.stream_list.length > 1);

        for (lttng_viewer_stream stream : attachResponse.stream_list) {
            System.out.println("Stream id: " + stream.id  + " name: " + new String(stream.channel_name) + " path: " + new String(stream.path_name));
        }

        return attachResponse;
    }

    private String getMetadata(lttng_viewer_attach_session_response attachedSession) throws IOException {

        for (lttng_viewer_stream stream : attachedSession.stream_list) {
            if (stream.metadata_flag == 1) {
                lttng_viewer_cmd connectCommand = new lttng_viewer_cmd();
                lttng_viewer_connect connectPayload = new lttng_viewer_connect();
                connectCommand.cmd = lttng_viewer_command.VIEWER_GET_METADATA;
                connectCommand.data_size = connectPayload.size();
                outNet.write(connectCommand.getBytes());
                outNet.flush();

                lttng_viewer_get_metadata metadataRequest = new lttng_viewer_get_metadata();
                metadataRequest.stream_id = stream.id;
                outNet.write(metadataRequest.getBytes());
                outNet.flush();


                lttng_viewer_metadata_packet metaDataPacket = new lttng_viewer_metadata_packet();
                final int BUFFER_SIZE = 12;
                byte[] data = new byte[BUFFER_SIZE];
                inNet.readFully(data, 0, BUFFER_SIZE);
                metaDataPacket.populate(data);
                assertEquals(lttng_viewer_get_metadata_return_code.VIEWER_METADATA_OK, metaDataPacket.status);

                metaDataPacket.data = new byte[(int)metaDataPacket.len];
                inNet.readFully(metaDataPacket.data, 0, (int)metaDataPacket.len);
                String strMetadata = new String(metaDataPacket.data);
                assertFalse(strMetadata.isEmpty());
                return strMetadata;
            }
        }

        return null;
    }

    private void getPackets(lttng_viewer_attach_session_response attachedSession) throws IOException {
        int numPacketsReceived = 0;
        while (numPacketsReceived < 100) {
            for (lttng_viewer_stream stream : attachedSession.stream_list) {
                if (stream.metadata_flag != 1) {
                    lttng_viewer_cmd connectCommand = new lttng_viewer_cmd();
                    lttng_viewer_connect connectPayload = new lttng_viewer_connect();
                    connectCommand.cmd = lttng_viewer_command.VIEWER_GET_NEXT_INDEX;
                    connectCommand.data_size = connectPayload.size();
                    outNet.write(connectCommand.getBytes());
                    outNet.flush();

                    lttng_viewer_get_next_index indexRequest = new lttng_viewer_get_next_index();
                    indexRequest.stream_id = stream.id;
                    outNet.write(indexRequest.getBytes());
                    outNet.flush();

                    lttng_viewer_index indexReply = new lttng_viewer_index();
                    byte[] data = new byte[indexReply.size()];
                    inNet.readFully(data, 0, indexReply.size());
                    indexReply.populate(data);

                    // Nothing else supported for now
                    assertEquals(0, indexReply.flags);
                    //assertEquals(stream.id, indexReply.stream_id);

                    System.out.print("Next index for stream " + stream.id + "(" + indexReply.stream_id + ") ... "); //$NON-NLS-1$
                    if (indexReply.status == lttng_viewer_next_index_return_code.VIEWER_INDEX_OK) {
                        System.out.println("OK"); //$NON-NLS-1$
                        if(getPacketFromStream(indexReply, stream.id)) {
                            numPacketsReceived++;
                        }
                    } else if (indexReply.status == lttng_viewer_next_index_return_code.VIEWER_INDEX_RETRY) {
                        System.out.println("Retry"); //$NON-NLS-1$
                    }
                }
            }
        }
    }

    private boolean getPacketFromStream(lttng_viewer_index index, long id) throws IOException {
        lttng_viewer_cmd connectCommand = new lttng_viewer_cmd();
        lttng_viewer_connect connectPayload = new lttng_viewer_connect();
        connectCommand.cmd = lttng_viewer_command.VIEWER_GET_PACKET;
        connectCommand.data_size = connectPayload.size();
        outNet.write(connectCommand.getBytes());
        outNet.flush();

        lttng_viewer_get_packet packetRequest = new lttng_viewer_get_packet();
        //FIXME why do we need a cast here?
        packetRequest.len = (int)(index.packet_size / 8);
        packetRequest.offset = index.offset;
        packetRequest.stream_id = id;
        outNet.write(packetRequest.getBytes());
        outNet.flush();

        System.out.print("get packet...");
        lttng_viewer_trace_packet tracePacket = new lttng_viewer_trace_packet();
        final int BUFFER_SIZE = 12;
        byte[] data = new byte[BUFFER_SIZE];
        inNet.readFully(data, 0, BUFFER_SIZE);
        tracePacket.populate(data);
        assertEquals(lttng_viewer_get_packet_return_code.VIEWER_GET_PACKET_OK, tracePacket.status);
        // Nothing else supported for now
        assertEquals(0, tracePacket.flags);

        if (tracePacket.status == lttng_viewer_get_packet_return_code.VIEWER_GET_PACKET_OK) {
            System.out.println("OK");
            tracePacket.data = new byte[tracePacket.len];
            inNet.readFully(tracePacket.data, 0, tracePacket.len);
            assertTrue(tracePacket.data.length > 0);
            return true;
        } else if (tracePacket.status == lttng_viewer_get_packet_return_code.VIEWER_GET_PACKET_RETRY) {
            System.out.println("Retry");
        }

        return false;
    }
}
