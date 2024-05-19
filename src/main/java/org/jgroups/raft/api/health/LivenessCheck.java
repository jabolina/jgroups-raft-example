package org.jgroups.raft.api.health;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;
import org.jgroups.raft.RaftHandle;

@Liveness
public class LivenessCheck implements HealthCheck {

  private final RaftHandle handle;

  public LivenessCheck(RaftHandle handle) {
    this.handle = handle;
  }

  @Override
  public HealthCheckResponse call() {
    if (handle.leader() == null)
      return HealthCheckResponse.down("leader");
    return HealthCheckResponse.builder()
        .name("leader")
        .withData("address", handle.leader().toString())
        .up()
        .build();
  }
}
