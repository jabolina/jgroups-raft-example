package org.jgroups.raft.rsm;

import jakarta.enterprise.context.ApplicationScoped;
import org.jgroups.raft.StateMachine;

import java.io.DataInput;
import java.io.DataOutput;

@ApplicationScoped
public class LocalStateMachine implements StateMachine {
  @Override
  public byte[] apply(byte[] bytes, int i, int i1, boolean b) throws Exception {
    return new byte[0];
  }

  @Override
  public void readContentFrom(DataInput dataInput) throws Exception {

  }

  @Override
  public void writeContentTo(DataOutput dataOutput) throws Exception {

  }
}
