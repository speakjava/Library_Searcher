/**
 * JDK library analyser project
 *
 * Copyright © 2014, Oracle and/or its affiliates. All rights reserved.
 */
package libsearch;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Method signature, i.e. name ond parameter types in order of definition
 *
 * @author Simon Ritter (@speakjava)
 */
public class MethodSignature {
  private final String name;
  private final List<String> parameters;
  private final int parameterCount;

  /**
   * Constructor
   *
   * @param methodName The name of this method
   * @param parameters A list of types of parameters
   */
  public MethodSignature(String methodName, List<String> parameters) {
    this.name = methodName;
    this.parameters = parameters;
    parameterCount = parameters.size();
  }

  /**
   * Constructor
   *
   * @param method Method object to turn into a MethodSignature
   */
  public MethodSignature(Method method) {
    name = method.getName();
    parameters = Arrays.stream(method.getParameterTypes())
        .map(t -> t.getName())
        .collect(Collectors.toList());
    parameterCount = parameters.size();
  }

  /**
   * Get the name of the method
   *
   * @return The name of the method
   */
  public String getName() {
    return name;
  }

  /**
   * Get the list of parameter types for this method
   *
   * @return The list of parameter types
   */
  public List<String> getParameters() {
    return parameters;
  }

  /**
   * How many parameters does this method have
   *
   * @return The number of parameters this method has
   */
  public int getParameterCount() {
    return parameterCount;
  }

  /**
   * Override the equals method from Object, so we can use distinct in the
   * Stream to elimiate duplicate entries
   *
   * @param object The object to compare against
   * @return Whether the object passed in is equal to this one
   */
  @Override
  public boolean equals(Object object) {
    if (object == null)  // Just to be sure
      return false;
    
    if (object instanceof MethodSignature) {
      MethodSignature method = (MethodSignature)object;
      
      if (name.compareTo(method.getName()) != 0 ||
          method.getParameterCount() != parameterCount)
        return false;
      
      return IntStream.range(0, parameterCount)
          .map(i -> parameters.get(i).compareTo(method.getParameters().get(i)))
          .filter(n -> n != 0)
          .count() == 0;
    }

    return false;
  }

  /**
   * Generate a hashcode which will be the same for methods of the same name
   * and parameter types.
   * 
   * @return A hashcode
   */
  @Override
  public int hashCode() {
    LongAdder hash = new LongAdder();
    hash.add(47 + Objects.hashCode(this.name));
    
    parameters.stream()
        .forEach(p -> hash.add(Objects.hashCode(p)));
    
    return hash.intValue();
  }
  
  /**
   * Return a nicely formated string for the method description
   * 
   * @return The method description string
   */
  @Override
  public String toString() {
    StringBuilder methodString = new StringBuilder();
    methodString.append(name);
    methodString.append("(");
    methodString.append(parameters.stream()
        .map(p -> printType(p))
        .collect(Collectors.joining(", ")));
    methodString.append(")");
    return methodString.toString();
  }
  
  /**
   * Convert the way parameters are represented via reflection into human
   * readable form. Primarily this involves how to represent arrays correctly
   *
   * @param type The reflection type description
   * @return A modified form of the type description
   */
  private String printType(String type) {
    /* Handle arrays */
    if (type.startsWith("[")) {
      switch (type.substring(1, 2)) {
        case "Z":
          return "boolean[]";
        case "B":
          return "byte[]";
        case "C":
          return "char[]";
        case "D":
          return "double[]";
        case "F":
          return "float[]";
        case "I":
          return "int[]";
        case "J":
          return "long[]";
        case "S":
          return "short[]";
        case "L":
          /**
           * L indicates an array of a type. We need to convert this from the
           * fully qualified type to the base name. Sometimes it's an inner
           * class, which uses a $ instead of a ., since that's already been
           * used. We convert that as well and add [].
           */
          return type
              .substring(type.lastIndexOf(".") + 1, type.length() - 1)
              .replace("$", ".") + "[]";
        default:
          return "[]";
      }
    }

    /* Convert from fully qualified type to just the base name */
    if (type.contains("."))
      return type.substring(type.lastIndexOf(".") + 1).replace("$", ".");

    /* Must be a primitive */
    return type;
  }
}
