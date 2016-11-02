package com.forgerock.elasticsearch.changes;

import org.elasticsearch.index.IndexModule;
import org.elasticsearch.index.engine.Engine;
import org.elasticsearch.index.shard.IndexingOperationListener;
import org.joda.time.DateTime;

public class ChangeForwarder {

    private ChangeRegister register;

    public void setRegister(ChangeRegister register) {
        this.register = register;
    }

    public void onIndexModule(IndexModule indexModule) {
        final String indexName = indexModule.getIndex().getName();

        indexModule.addIndexOperationListener(new IndexingOperationListener() {
            @Override
            public void postDelete(Engine.Delete delete) {
                ChangeEvent change=new ChangeEvent(
                        delete.id(),
                        indexName,
                        delete.type(),
                        new DateTime(),
                        ChangeEvent.Operation.DELETE,
                        delete.version(),
                        null
                );

                if (register != null) register.addChange(change);
            }

            @Override
            public void postIndex(Engine.Index index, boolean created) {

                ChangeEvent change=new ChangeEvent(
                        index.id(),
                        indexName,
                        index.type(),
                        new DateTime(),
                        created ? ChangeEvent.Operation.CREATE : ChangeEvent.Operation.INDEX,
                        index.version(),
                        index.source()
                );

                if (register != null) register.addChange(change);
            }
        });
    }


}
