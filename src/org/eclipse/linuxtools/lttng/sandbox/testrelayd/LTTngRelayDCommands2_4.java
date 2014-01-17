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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * LTTng Relay Daemon API. needs a tcp connection, API is copied from BSD
 * licensed implementation here :
 * https://github.com/jdesfossez/lttng-tools-dev/blob
 * /live2013/src/bin/lttng-relayd/lttng-viewer.h
 *
 * @author Matthew Khouzam
 */
public interface LTTngRelayDCommands2_4 {

    final static int LTTNG_VIEWER_PATH_MAX = 4096;
    final static int LTTNG_VIEWER_NAME_MAX = 255;
    final static int LTTNG_VIEWER_HOST_NAME_MAX = 64;

    /**
     * Command sent, needs a getBytes to stream the data
     */
    public interface RelayCommand {
        /**
         * Gets a byte array of the command so that it may be streamed
         *
         * @return the byte array of the command
         */
        public byte[] getBytes();
    }

    /**
     * Command received, needs a populate to fill the data
     */
    public interface RelayResponse {
        /**
         * Populate the class from a byte array
         *
         * @param data
         *            the byte array containing the streamed command
         */
        public void populate(byte[] data);
    }

    /**
     * Fixed size, helps if you have an array of that type
     */
    public interface FixedSize {
        /**
         * Gets the size in bytes of an message
         *
         * @return the size of the message in bytes
         */
        public int size();
    }

    /**
     * viewer commands
     */
    enum lttng_viewer_command {
        /** get version */
        VIEWER_CONNECT(1),
        /** list all lttng sessions */
        VIEWER_LIST_SESSIONS(2),
        /** attach to a session */
        VIEWER_ATTACH_SESSION(3),
        /** get the next index */
        VIEWER_GET_NEXT_INDEX(4),
        /** get packet */
        VIEWER_GET_PACKET(5),
        /** get metadata */
        VIEWER_GET_METADATA(6);
        private int code;

        private lttng_viewer_command(int c) {
            code = c;
        }

        public int getCommand() {
            return code;
        }
    }

    /**
     * return codes for "viewer attach" command
     */
    enum lttng_viewer_attach_return_code {
        /** If the attach command succeeded. */
        VIEWER_ATTACH_OK(1),
        /** If a viewer is already attached. */
        VIEWER_ATTACH_ALREADY(2),
        /** If the session ID is unknown. */
        VIEWER_ATTACH_UNK(3),
        /** If the session is not live. */
        VIEWER_ATTACH_NOT_LIVE(4),
        /** Seek error. */
        VIEWER_ATTACH_SEEK_ERR(5);
        private int code;

        private lttng_viewer_attach_return_code(int c) {
            code = c;
        }

        public int getCommand() {
            return code;
        }
    }

    /**
     * get next index return code (hope it's viewer_index_ok)
     */
    enum lttng_viewer_next_index_return_code {
        /** Index is available. */
        VIEWER_INDEX_OK(1),
        /** Index not yet available. */
        VIEWER_INDEX_RETRY(2),
        /** Index closed (trace destroyed). */
        VIEWER_INDEX_HUP(3),
        /** Unknown error. */
        VIEWER_INDEX_ERR(4),
        /** Inactive stream beacon. */
        VIEWER_INDEX_INACTIVE(5),
        /** End of index file. */
        VIEWER_INDEX_EOF(6);

        private int code;

        private lttng_viewer_next_index_return_code(int c) {
            code = c;
        }

        public int getCommand() {
            return code;
        }
    }

    /**
     * Get packet return code
     */
    enum lttng_viewer_get_packet_return_code {
        VIEWER_GET_PACKET_OK(1),
        VIEWER_GET_PACKET_RETRY(2),
        VIEWER_GET_PACKET_ERR(3),
        VIEWER_GET_PACKET_EOF(2);
        private int code;

        private lttng_viewer_get_packet_return_code(int c) {
            code = c;
        }

        public int getCommand() {
            return code;
        }

    }

    /**
     * Get metadata return code
     */
    enum lttng_viewer_get_metadata_return_code {
        VIEWER_METADATA_OK(1),
        VIEWER_NO_NEW_METADATA(2),
        VIEWER_METADATA_ERR(3);
        private int code;

        private lttng_viewer_get_metadata_return_code(int c) {
            code = c;
        }

        public int getCommand() {
            return code;
        }

    }

    /**
     * get viewer connection type
     */
    enum lttng_viewer_connection_type {
        VIEWER_CLIENT_COMMAND(1),
        VIEWER_CLIENT_NOTIFICATION(2);
        private int code;

        private lttng_viewer_connection_type(int c) {
            code = c;
        }

        public int getCommand() {
            return code;
        }

    }

    /**
     * seek command
     */
    enum lttng_viewer_seek {
        /** Receive the trace packets from the beginning. */
        VIEWER_SEEK_BEGINNING(1),
        /** Receive the trace packets from now. */
        VIEWER_SEEK_LAST(2);
        private int code;

        private lttng_viewer_seek(int c) {
            code = c;
        }

        public int getCommand() {
            return code;
        }
    }

    /**
	 * get viewer session response to command
	 */
	public class lttng_viewer_session implements RelayResponse, FixedSize, RelayCommand {
	    public long id;
	    public int live_timer;
	    public int clients;
	    public int streams;

	    public byte hostname[] = new byte[LTTNG_VIEWER_HOST_NAME_MAX];
	    public byte session_name[] = new byte[LTTNG_VIEWER_NAME_MAX];

	    @Override
	    public void populate(byte[] data) {
	        ByteBuffer bb = ByteBuffer.wrap(data);
	        bb.order(ByteOrder.BIG_ENDIAN);
	        id = bb.getLong();
	        live_timer = bb.getInt();
	        clients = bb.getInt();
	        streams = bb.getInt();

	        bb.get(hostname, 0, hostname.length);
	        bb.get(session_name, 0, session_name.length);
	    }

	    @Override
	    public int size() {
	        return LTTNG_VIEWER_HOST_NAME_MAX + LTTNG_VIEWER_NAME_MAX + (Long.SIZE + Integer.SIZE + Integer.SIZE + Integer.SIZE) / 8;
	    }

	    @Override
	    public byte[] getBytes() {
            byte data[] = new byte[size()];
            ByteBuffer bb = ByteBuffer.wrap(data);
            bb.putLong(id);
            bb.putInt(live_timer);
            bb.putInt(clients);
            bb.putInt(streams);

	        bb.put(hostname);
	        bb.put(session_name);
	        return data;
	    }

	}

	/**
	 * Get response of viewer stream
	 */
	public class lttng_viewer_stream implements FixedSize, RelayResponse {
	    /**
	     * id of the stream
	     */
	    public long id;
	    /**
	     * the id of the trace in ctf. Should be a uuid???
	     */
	    public long ctf_trace_id;
        /**
         * metadata TODO: understand me
         */
        public int metadata_flag;
	    /**
	     * the path
	     */
	    public byte path_name[] = new byte[LTTNG_VIEWER_PATH_MAX];
	    /**
	     * The channel, traditionally channel0
	     */
	    public byte channel_name[] = new byte[LTTNG_VIEWER_NAME_MAX];

	    @Override
	    public void populate(byte[] data) {
	        ByteBuffer bb = ByteBuffer.wrap(data);
	        bb.order(ByteOrder.BIG_ENDIAN);
	        id = bb.getLong();
	        ctf_trace_id = bb.getLong();
	        metadata_flag = bb.getInt();
	        bb.get(path_name, 0, LTTNG_VIEWER_PATH_MAX);
	        bb.get(channel_name, 0, LTTNG_VIEWER_NAME_MAX);
	    }

        @Override
        public int size() {
            return (Long.SIZE + Long.SIZE + Integer.SIZE) / 8 + LTTNG_VIEWER_PATH_MAX + LTTNG_VIEWER_NAME_MAX;
        }
	}

	/**
     * The LTTng command
     */
    public class lttng_viewer_cmd implements FixedSize, RelayCommand {
        /**
         * data size following this header, you normally attach a payload that
         * one, in bytes
         */
        public long data_size;
        /** enum lttcomm_relayd_command */
        public lttng_viewer_command cmd;
        /** command version */
        public int cmd_version;

        @Override
        public byte[] getBytes() {
            byte data[] = new byte[size()];
            ByteBuffer bb = ByteBuffer.wrap(data);
            bb.putLong(data_size);
            bb.putInt(cmd.getCommand());
            bb.putInt(cmd_version);
            return data;
        }

        @Override
        public int size() {
            return (Long.SIZE + Integer.SIZE + Integer.SIZE) / 8;
        }
    }

    /**
     * CONNECT payload.
     */
    public class lttng_viewer_connect implements RelayResponse, FixedSize, RelayCommand {
        /** session id, counts from 1 and increments by session */
        public long viewer_session_id;
        /**
         * Major version, hint, it's 2
         */
        public int major;
        /**
         * Minor version, hint, it's 4
         */
        public int minor;
        /**
         * type of connect to {@link lttng_viewer_connection_type}
         */
        public lttng_viewer_connection_type type;

        @Override
        public int size() {
            return (Long.SIZE + Integer.SIZE + Integer.SIZE + Integer.SIZE) / 8;
        }

        @Override
        public void populate(byte[] data) {
            ByteBuffer bb = ByteBuffer.wrap(data);
            bb.order(ByteOrder.BIG_ENDIAN);
            viewer_session_id = bb.getLong();
            major = bb.getInt();
            minor = bb.getInt();
            type = lttng_viewer_connection_type.values()[bb.getInt()];
        }

        @Override
        public byte[] getBytes() {
            byte data[] = new byte[size()];
            ByteBuffer bb = ByteBuffer.wrap(data);
            bb.order(ByteOrder.BIG_ENDIAN);
            bb.putLong(viewer_session_id);
            bb.putInt(major);
            bb.putInt(minor);
            bb.putInt(type.getCommand());
            return data;
        }
    }

    /**
     * VIEWER_LIST_SESSIONS payload.
     */
    public class lttng_viewer_list_sessions implements RelayResponse, RelayCommand {
        public int sessions_count;
        public lttng_viewer_session session_list[];

        @Override
        public void populate(byte[] data) {
            ByteBuffer bb = ByteBuffer.wrap(data);
            bb.order(ByteOrder.BIG_ENDIAN);
            List<lttng_viewer_session> temp = new ArrayList<>();
            sessions_count = bb.getInt();
            for (int i = 0; i < sessions_count && bb.hasRemaining(); i++) {
                lttng_viewer_session viewer = new lttng_viewer_session();

                byte[] subData = new byte[viewer.size()];
                bb.get(subData, 0, viewer.size());
                viewer.populate(subData);
                temp.add(viewer);
            }
            session_list = temp.toArray(new lttng_viewer_session[0]);
        }

        @Override
        public byte[] getBytes() {
            byte data[] = new byte[getSize()];
            ByteBuffer bb = ByteBuffer.wrap(data);
            bb.order(ByteOrder.BIG_ENDIAN);
            bb.putInt(sessions_count);
            if (session_list != null) {
                for (int i = 0; i < session_list.length; i++) {
                    bb.put(session_list[i].getBytes());
                }
            }
            return data;
        }

        public int getSize() {
            return 4 + ((session_list == null) ? 0 : (session_list.length * (new lttng_viewer_session()).size()));
        }
    }

    /**
     * VIEWER_ATTACH_SESSION payload.
     */
    public class lttng_viewer_attach_session_request implements FixedSize, RelayCommand {
        public long session_id;
        /** unused for now */
        public long offset;
        /** enum lttng_viewer_seek */
        public lttng_viewer_seek seek;

        @Override
        public int size() {
            return (Long.SIZE + Integer.SIZE + Integer.SIZE + Integer.SIZE) / 8;
        }

        @Override
        public byte[] getBytes() {
            byte data[] = new byte[size()];
            ByteBuffer bb = ByteBuffer.wrap(data);
            bb.putLong(session_id);
            bb.putLong(offset);
            bb.putInt(seek.getCommand());
            return data;
        }
    }

    /**
     * Attach session response
     */
    public class lttng_viewer_attach_session_response implements RelayResponse {
        /** enum lttng_viewer_attach_return_code */
        public lttng_viewer_attach_return_code status;
        /** how many streams are there */
        public int streams_count;
        /** public class lttng_viewer_stream */
        public lttng_viewer_stream stream_list[];

        @Override
        public void populate(byte[] data) {
            ByteBuffer bb = ByteBuffer.wrap(data);
            bb.order(ByteOrder.BIG_ENDIAN);
            status = lttng_viewer_attach_return_code.values()[bb.getInt() - 1];

            List<lttng_viewer_stream> temp = new ArrayList<>();
            streams_count = bb.getInt();
            for (int i = 0; i < streams_count && bb.hasRemaining(); i++) {
                lttng_viewer_stream stream = new lttng_viewer_stream();

                byte[] subData = new byte[stream.size()];
                bb.get(subData, 0, stream.size());
                stream.populate(subData);
                temp.add(stream);
            }
            stream_list = temp.toArray(new lttng_viewer_stream[0]);
        }
    }

    /**
     * VIEWER_GET_NEXT_INDEX payload.
     */
    public class lttng_viewer_get_next_index implements RelayResponse {
        /**
         * the id of thje stream
         */
        public long stream_id;

        @Override
        public void populate(byte[] data) {
            ByteBuffer bb = ByteBuffer.wrap(data);
            bb.order(ByteOrder.BIG_ENDIAN);
            stream_id = bb.getLong();
        }
    }

    /**
     * the index?
     */
    public class lttng_viewer_index implements RelayResponse {
        public long offset;
        public long packet_size;
        public long content_size;
        public long timestamp_begin;
        public long timestamp_end;
        public long events_discarded;
        public long stream_id;

        public lttng_viewer_next_index_return_code status;
        public int flags;

        @Override
        public void populate(byte[] data) {
            ByteBuffer bb = ByteBuffer.wrap(data);
            bb.order(ByteOrder.BIG_ENDIAN);
            offset = bb.getLong();
            packet_size = bb.getLong();
            content_size = bb.getLong();
            timestamp_begin = bb.getLong();
            timestamp_end = bb.getLong();
            events_discarded = bb.getLong();
            stream_id = bb.getLong();

            status = lttng_viewer_next_index_return_code.values()[bb.getInt()];
            flags = bb.getInt();
        }
    }

    /**
     * VIEWER_GET_PACKET payload.
     */
    public class lttng_viewer_get_packet implements RelayResponse {
        public long stream_id;
        public long offset;
        public int len;

        @Override
        public void populate(byte[] data) {
            ByteBuffer bb = ByteBuffer.wrap(data);
            bb.order(ByteOrder.BIG_ENDIAN);
            stream_id = bb.getLong();
            offset = bb.getLong();
            len = bb.getInt();
        }
    }

    /**
     * Response to getpacket command
     */
    public class lttng_viewer_trace_packet implements RelayResponse {
        public int status; /* enum lttng_viewer_get_packet_return_code */
        public int len;
        public int flags;
        public byte data[];

        @Override
        public void populate(byte[] input) {
            ByteBuffer bb = ByteBuffer.wrap(input);
            bb.order(ByteOrder.BIG_ENDIAN);
            status = bb.getInt();
            len = bb.getInt();
            flags = bb.getInt();
            data = new byte[len];
            bb.get(data, 0, len);
        }
    }

    /**
     * VIEWER_GET_METADATA payload.
     */
    public class lttng_viewer_get_metadata implements RelayResponse {
        /**
         * The stream id
         */
        public long stream_id;

        @Override
        public void populate(byte[] data) {
            ByteBuffer bb = ByteBuffer.wrap(data);
            bb.order(ByteOrder.BIG_ENDIAN);
            stream_id = bb.getInt();
        }
    }

    public class lttng_viewer_metadata_packet implements RelayResponse {
        public long len;
        public lttng_viewer_get_metadata_return_code status;
        public byte data[];

        @Override
        public void populate(byte[] input) {
            ByteBuffer bb = ByteBuffer.wrap(input);
            bb.order(ByteOrder.BIG_ENDIAN);
            len = bb.getLong();
            status = lttng_viewer_get_metadata_return_code.values()[bb.getInt()];
            data = new byte[(int) len];
            bb.get(data, 0, (int) len);
        }
    }

}
