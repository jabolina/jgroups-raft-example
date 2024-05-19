package org.jgroups.raft.serialization.impl;

import org.infinispan.protostream.ImmutableSerializationContext;
import org.infinispan.protostream.ProtobufUtil;
import org.jgroups.raft.serialization.RaftMarshaller;

import java.io.IOException;

public class DefaultRaftMarshaller implements RaftMarshaller {

  private final ImmutableSerializationContext context;

  public DefaultRaftMarshaller(ImmutableSerializationContext context) {
    this.context = context;
  }

  @Override
  public byte[] marshall(Object object) throws IOException {
    return ProtobufUtil.toWrappedByteArray(context, object);
  }

  @Override
  public <T> T unmarshall(byte[] data) throws IOException {
    return ProtobufUtil.fromWrappedByteArray(context, data);
  }
}
