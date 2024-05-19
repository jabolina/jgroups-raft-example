package org.jgroups.raft.api.v1;

import jakarta.inject.Inject;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jgroups.raft.RaftHandle;

@Path("/v1/system")
@Produces(MediaType.APPLICATION_JSON)
public class SystemRegistryResource {

  @Inject
  RaftHandle handle;
}
