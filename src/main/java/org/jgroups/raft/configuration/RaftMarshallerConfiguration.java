package org.jgroups.raft.configuration;

import jakarta.enterprise.context.ApplicationScoped;
import org.infinispan.protostream.ProtobufUtil;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.config.Configuration;
import org.jgroups.raft.serialization.GlobalProtoInitializer;
import org.jgroups.raft.serialization.GlobalProtoInitializerImpl;
import org.jgroups.raft.serialization.RaftMarshaller;
import org.jgroups.raft.serialization.impl.DefaultRaftMarshaller;

public class RaftMarshallerConfiguration {

  private final GlobalProtoInitializer gpi = new GlobalProtoInitializerImpl();

  @ApplicationScoped
  public RaftMarshaller producesRaftMarshaller() {
    Configuration cfg = Configuration.builder()
        .build();
    SerializationContext sc = ProtobufUtil.newSerializationContext(cfg);
    gpi.registerSchema(sc);
    gpi.registerMarshallers(sc);

    return new DefaultRaftMarshaller(sc);
  }
}
