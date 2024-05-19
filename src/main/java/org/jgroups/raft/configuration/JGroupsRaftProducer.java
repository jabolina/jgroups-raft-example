package org.jgroups.raft.configuration;

import io.quarkus.logging.Log;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jgroups.JChannel;
import org.jgroups.raft.RaftHandle;
import org.jgroups.raft.StateMachine;

import static org.jgroups.raft.configuration.Constants.JGROUPS_CLUSTER_NAME;
import static org.jgroups.raft.configuration.Constants.JGROUPS_CONFIGURATION_FILE;

class JGroupsRaftProducer {

  @ConfigProperty(name = JGROUPS_CONFIGURATION_FILE, defaultValue = "raft.xml")
  String configurationFile;

  @ConfigProperty(name = JGROUPS_CLUSTER_NAME, defaultValue = "raft-server")
  String clusterName;

  @Singleton
  JChannel producesJChannel() throws Exception {
    Log.infof("Creating channel with configuration: %s", configurationFile);
    return new JChannel(configurationFile);
  }

  @Singleton
  RaftHandle producesRaftHandle(JChannel channel, StateMachine sm) {
    return new RaftHandle(channel, sm);
  }

  void onStartup(@Observes StartupEvent ignore, RaftHandle raft) throws Exception {
    String member = ConfigProvider.getConfig().getValue("raft_id", String.class);
    Log.infof("Connecting '%s' to cluster '%s' with members %s", member, clusterName, raft.raft().members());
    raft.channel().connect(clusterName);
  }

  void onShutdown(@Observes ShutdownEvent ignore, RaftHandle raft) {
    Log.info("Closing Raft handle");
    raft.channel().close();
  }
}
