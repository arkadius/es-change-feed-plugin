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

import com.google.common.collect.ImmutableList;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.index.IndexModule;
import org.elasticsearch.plugins.Plugin;

import java.util.Collection;

public class ChangesFeedPlugin extends Plugin {
    private final Logger log = Loggers.getLogger(ChangesFeedPlugin.class);

    private final ChangeForwarder forwarder = new ChangeForwarder();

    public ChangesFeedPlugin() {
        log.info("Starting Changes Plugin");
    }

    @Override
    public Collection<Module> createGuiceModules() {
        Module module = new ChangesModule(forwarder);
        return ImmutableList.of(module);
    }

    @Override
    public void onIndexModule(IndexModule indexModule) {
        forwarder.onIndexModule(indexModule);
    }

    public String description() {
        return "Changes Plugin";
    }

    public String name() {
        return "changes";
    }
}
