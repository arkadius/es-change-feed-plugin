package com.forgerock.elasticsearch.changes;

/*
    Copyright 2015 ForgeRock AS

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.glassfish.tyrus.server.Server;

import javax.websocket.DeploymentException;
import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ChangeRegister {

    // TODO: how to check this?
    private static final String SETTING_PRIMARY_SHARD_ONLY = "changes.primaryShardOnly";
    private static final String SETTING_PORT = "changes.port";
    private static final String SETTING_LISTEN_SOURCE = "changes.listenSource";

    private final Logger log = Loggers.getLogger(ChangeRegister.class);

    private static final Map<String, WebSocket> LISTENERS = new HashMap<String, WebSocket>();

    private final Set<Source> sources;

    @Inject
    public ChangeRegister(final Settings settings, final ChangeForwarder forwarder) {
        final int port = settings.getAsInt(SETTING_PORT, 9400);
        final String[] sourcesStr = settings.getAsArray(SETTING_LISTEN_SOURCE, new String[]{"*"});
        sources = new HashSet<>();
        for(String sourceStr : sourcesStr) {
            sources.add(new Source(sourceStr));
        }

        final Server server = new Server("localhost", port, "/ws", null, WebSocket.class) ;

        try {
            log.info("Starting WebSocket server");
            AccessController.doPrivileged(new PrivilegedAction() {
                @Override
                public Object run() {
                    try {
                        // Tyrus tries to load the server code using reflection. In Elasticsearch 2.x Java
                        // security manager is used which breaks the reflection code as it can't find the class.
                        // This is a workaround for that
                        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
                        server.start();
                        return null;
                    } catch (DeploymentException e) {
                        throw new RuntimeException("Failed to start server", e);
                    }
                }
            });
            log.info("WebSocket server started");
        } catch (Exception e) {
            log.error("Failed to start WebSocket server",e);
            throw new RuntimeException(e);
        }

        forwarder.setRegister(this);
    }

    private boolean filter(String index, String type, String id, Source source) {
        if (source.getIndices() != null && !source.getIndices().contains(index)) {
            return false;
        }

        if (source.getTypes() != null && !source.getTypes().contains(type)) {
            return false;
        }

        if (source.getIds() != null && !source.getIds().contains(id)) {
            return false;
        }

        return true;
    }

    private boolean filter(ChangeEvent change) {
        for (Source source : sources) {
            if (filter(change.getIndexName(), change.getType(), change.getId(), source)) {
                return true;
            }
        }

        return false;
    }

    void addChange(ChangeEvent change) {

        if (!filter(change)) {
            return;
        }

        String message;
        try {
            XContentBuilder builder = new XContentBuilder(JsonXContent.jsonXContent, new BytesStreamOutput());
            builder.startObject()
                    .field("_index", change.getIndexName())
                    .field("_type", change.getType())
                    .field("_id", change.getId())
                    .field("_timestamp", change.getTimestamp())
                    .field("_version", change.getVersion())
                    .field("_operation", change.getOperation().toString());
            if (change.getSource() != null) {
                builder.rawField("_source", change.getSource());
            }
            builder.endObject();

            message = builder.string();
        } catch (IOException e) {
            log.error("Failed to write JSON", e);
            return;
        }

        for (WebSocket listener : LISTENERS.values()) {
            try {
                listener.sendMessage(message);
            } catch (Exception e) {
                log.error("Failed to send message", e);
            }

        }

    }

    public static void registerListener(WebSocket webSocket) {
        LISTENERS.put(webSocket.getId(), webSocket);
    }

    public static void unregisterListener(WebSocket webSocket) {
        LISTENERS.remove(webSocket.getId());
    }

}
