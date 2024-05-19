package org.jgroups.raft.graal;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.TargetClass;
import org.jgroups.util.Util;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@TargetClass(Util.class)
final class JGroupsSubstitutions {

  private JGroupsSubstitutions() { }

  @Alias
  @InjectAccessors(NetworkInterfacesAccessor.class)
  private static volatile List<NetworkInterface> CACHED_INTERFACES;

  @Alias
  @InjectAccessors(AddressesAccessor.class)
  private static volatile Collection<InetAddress> CACHED_ADDRESSES;

  private static final class NetworkInterfacesAccessor {
    static List<NetworkInterface> get() {
      return NetworkInterfacesLazyHolder.INTERFACES;
    }

    static void set(List<NetworkInterface> ignore) { }
  }

  private static final class AddressesAccessor {
    static Collection<InetAddress> get() {
      return AddressesLazyHolder.ADDRESSES;
    }

    static void set(Collection<InetAddress> addresses) { }
  }

  private static final class NetworkInterfacesLazyHolder {
    private static final List<NetworkInterface> INTERFACES;

    static {
      try {
        INTERFACES = getAllAvailableInterfaces();
      } catch (SocketException e) {
        throw new RuntimeException(e);
      }
    }

    public static List<NetworkInterface> getAllAvailableInterfaces() throws SocketException {
      List<NetworkInterface> retval=new ArrayList<>(10);
      for(Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
        NetworkInterface intf=en.nextElement();
        retval.add(intf);
        for(Enumeration<NetworkInterface> subs=intf.getSubInterfaces(); subs.hasMoreElements();) {
          NetworkInterface sub=subs.nextElement();
          if(sub != null)
            retval.add(sub);
        }
      }
      return CACHED_INTERFACES=retval;
    }
  }

  private static final class AddressesLazyHolder {
    private static final Collection<InetAddress> ADDRESSES = getAllAvailableAddresses();

    private static synchronized Collection<InetAddress> getAllAvailableAddresses() {
      Set<InetAddress> addresses=new HashSet<>();
      try {
        List<NetworkInterface> interfaces = NetworkInterfacesLazyHolder.getAllAvailableInterfaces();
        for(NetworkInterface intf: interfaces) {
          if(!Util.isUp(intf)  /*!intf.isUp()*/)
            continue;
          intf.getInetAddresses().asIterator().forEachRemaining(addresses::add);
        }
      } catch(SocketException e) {}
      // immutable list
      return List.copyOf(addresses);
    }
  }
}
