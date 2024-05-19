package org.jgroups.raft.graal;

import io.quarkus.logging.Log;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.jgroups.annotations.Component;
import org.jgroups.annotations.Property;
import org.jgroups.conf.ConfiguratorFactory;
import org.jgroups.conf.PropertyConverter;
import org.jgroups.conf.PropertyHelper;
import org.jgroups.conf.ProtocolConfiguration;
import org.jgroups.conf.ProtocolStackConfigurator;
import org.jgroups.protocols.raft.InMemoryLog;
import org.jgroups.raft.configuration.Constants;
import org.jgroups.util.Util;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

public class JGroupsProtocolStackFeature implements Feature {

  @Override
  public void beforeAnalysis(BeforeAnalysisAccess access) {
    String file = System.getProperty(Constants.JGROUPS_CONFIGURATION_FILE, "raft.xml");
    Log.infof("Feature parsing protocol stack: %s", file);
    try {
      ProtocolStackConfigurator psc = ConfiguratorFactory.getStackConfigurator(file);
      for (ProtocolConfiguration protocolConfiguration : psc.getProtocolStack()) {
        registerLayer(protocolConfiguration);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    RuntimeReflection.register(InMemoryLog.class);
    for (Constructor<?> constructor : InMemoryLog.class.getConstructors()) {
      RuntimeReflection.register(constructor);
    }
  }

  private void registerLayer(ProtocolConfiguration configuration) throws Exception {
    configuration.substituteVariables();

    Log.infof("Parsing protocol %s", configuration.getProtocolName());

    Class<?> protocol = configuration.loadProtocolClass(this.getClass());
    RuntimeReflection.register(protocol);

    for (Constructor<?> constructor : protocol.getConstructors()) {
      RuntimeReflection.register(constructor);
    }

    AccessibleObject[] properties = computePropertyDependencies(protocol, configuration.getProperties());
    for (AccessibleObject ao : properties) {
      Log.debugf("%s: defines %s", configuration.getProtocolName(), ao);
      switch (ao) {
        case Field f -> RuntimeReflection.register(f);
        case Method m -> RuntimeReflection.register(m);
        default -> throw new RuntimeException("Unknown type: " + ao);
      }

      Property property = ao.getAnnotation(Property.class);
      if (property != null) {
        Class<? extends PropertyConverter> pc = property.converter();
        RuntimeReflection.register(pc);
        for (Constructor<?> constructor : pc.getConstructors()) {
          RuntimeReflection.register(constructor);
        }
      }
    }
  }

  static AccessibleObject[] computePropertyDependencies(Class<?> baseClass, Map<String, String> properties) {

    // List of Fields and Methods of the protocol annotated with @Property
    List<AccessibleObject> unorderedFieldsAndMethods = new LinkedList<>();
    List<AccessibleObject> orderedFieldsAndMethods = new LinkedList<>();
    // Maps property name to property object
    Map<String, AccessibleObject> propertiesInventory = new HashMap<>();

    // get the methods for this class and add them to the list if annotated with @Property
    Method[] methods = baseClass.getMethods();
    for (Method method : methods) {
      if (method.isAnnotationPresent(Property.class) && isSetPropertyMethod(method, baseClass)) {
        String propertyName = PropertyHelper.getPropertyName(method);
        unorderedFieldsAndMethods.add(method);
        propertiesInventory.put(propertyName, method);
      }
    }

    //traverse class hierarchy and find all annotated fields and add them to the list if annotated
    AccessibleObject[] nested = new AccessibleObject[0];
    for (Class<?> clazz = baseClass; clazz != null; clazz = clazz.getSuperclass()) {
      for (Field field : clazz.getDeclaredFields()) {
        if (field.isAnnotationPresent(Property.class)) {
          String propertyName = PropertyHelper.getPropertyName(field, properties);
          unorderedFieldsAndMethods.add(field);
          // may need to change this based on name parameter of Property
          propertiesInventory.put(propertyName, field);
        }

        if (field.isAnnotationPresent(Component.class)) {
          unorderedFieldsAndMethods.add(field);

          // Need to find all properties with prefix to the field name.
          String prefix = field.getName();
          Map<String, String> sub = new HashMap<>();
          for (Map.Entry<String, String> entry : properties.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
              String s = entry.getKey().substring(prefix.length() + 1);
              sub.put(s, entry.getValue());
            }
          }

          AccessibleObject[] arr = computePropertyDependencies(field.getType(), sub);
          nested = Util.combine(nested, arr);
        }
      }
    }

    // at this stage, we have all Fields and Methods annotated with @Property
    checkDependencyReferencesPresent(unorderedFieldsAndMethods, propertiesInventory);

    // order the fields and methods by dependency
    orderedFieldsAndMethods = orderFieldsAndMethodsByDependency(unorderedFieldsAndMethods, propertiesInventory);

    // convert to array of Objects
    AccessibleObject[] result = new AccessibleObject[orderedFieldsAndMethods.size()];
    for (int i = 0; i < orderedFieldsAndMethods.size(); i++)
      result[i] = orderedFieldsAndMethods.get(i);
    return Util.combine(nested, result);
  }

  static void checkDependencyReferencesPresent(List<AccessibleObject> objects, Map<String, AccessibleObject> props) {
    for (AccessibleObject ao : objects) { // iterate overall properties marked by @Property
      // get the Property annotation
      Property annotation = ao.getAnnotation(Property.class);
      if (annotation == null)
        continue;
      String dependsClause = annotation.dependsUpon();
      if (dependsClause.trim().isEmpty())
        continue;

      // split dependsUpon specifier into tokens; trim each token; search for token in list
      StringTokenizer st = new StringTokenizer(dependsClause, ",");
      while (st.hasMoreTokens()) {
        String token = st.nextToken().trim();

        // check that the string representing a property name is in the list
        boolean found = false;
        Set<String> keyset = props.keySet();
        for (String s : keyset) {
          if (s.equals(token)) {
            found = true;
            break;
          }
        }
        if (!found)
          throw new IllegalArgumentException("@Property annotation " + annotation.name() +
              " has an unresolved dependsUpon property: " + token);
      }
    }
  }

  static List<AccessibleObject> orderFieldsAndMethodsByDependency(List<AccessibleObject> unorderedList,
                                                                  Map<String, AccessibleObject> propertiesMap) {
    // Stack to detect cycle in depends relation
    Deque<AccessibleObject> stack = new ArrayDeque<>();
    // the result list
    List<AccessibleObject> orderedList = new LinkedList<>();

    // add the elements from the unordered list to the ordered list
    // any dependencies will be checked and added first, in recursive manner
    for (AccessibleObject obj : unorderedList) {
      addPropertyToDependencyList(orderedList, propertiesMap, stack, obj);
    }

    return orderedList;
  }

  static void addPropertyToDependencyList(List<AccessibleObject> orderedList, Map<String, AccessibleObject> props, Deque<AccessibleObject> stack, AccessibleObject obj) {
    if (orderedList.contains(obj))
      return;
    if (stack.contains(obj))
      throw new RuntimeException("Deadlock in @Property dependency processing");
    // record the fact that we are processing obj
    stack.push(obj);
    // process dependencies for this object before adding it to the list
    Property annotation = obj.getAnnotation(Property.class);
    String dependsClause = annotation != null ? annotation.dependsUpon() : "";
    StringTokenizer st = new StringTokenizer(dependsClause, ",");
    while (st.hasMoreTokens()) {
      String token = st.nextToken().trim();
      AccessibleObject dep = props.get(token);
      // if null, throw exception
      addPropertyToDependencyList(orderedList, props, stack, dep);
    }
    // indicate we're done with processing dependencies
    stack.pop();
    // we can now add in dependency order
    orderedList.add(obj);
  }

  public static boolean isSetPropertyMethod(Method method, Class<?> enclosing_clazz) {
    return (method.getReturnType() == java.lang.Void.TYPE || method.getReturnType().isAssignableFrom(enclosing_clazz))
        && method.getParameterTypes().length == 1;
  }
}
