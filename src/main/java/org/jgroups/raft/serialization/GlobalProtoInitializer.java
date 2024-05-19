package org.jgroups.raft.serialization;

import org.infinispan.protostream.GeneratedSchema;
import org.infinispan.protostream.annotations.ProtoSchema;
import org.infinispan.protostream.annotations.ProtoSyntax;

@ProtoSchema(syntax = ProtoSyntax.PROTO3, includeClasses = {})
public interface GlobalProtoInitializer extends GeneratedSchema { }
