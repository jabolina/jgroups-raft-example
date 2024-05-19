package org.jgroups.raft.serialization;

import java.io.IOException;

public interface RaftMarshaller {

  byte[] marshall(Object object) throws IOException;

  <T> T unmarshall(byte[] data) throws IOException;
}
