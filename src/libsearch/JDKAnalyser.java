/**
 * JDK library analyser project
 *
 * Copyright © 2014, Oracle and/or its affiliates. All rights reserved.
 */
package libsearch;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

/**
 * Compare the JDK7 and JDK8 libraries to find out which are new in JDK8 that
 * use Lambda expressions as parameters
 *
 * @author Simon Ritter (@speakjava)
 */
public class JDKAnalyser {
  private static final String STREAM_PACKAGE = "java.util.stream";
  private static final String STREAM_TYPE = "java.util.stream.Stream";
  private static final String UNDERLINE
      = "---------------------------------------------------------------------";

  private final PrintWriter output;
  private final boolean markNew;

  /* List of all classes in JDK 8 */
  private final List<String> jdk8ClassList;

  /* List of functional interfaces derived from the JDK8 classes */
  private final List<String> functionalInterfaces;

  /* Map of all classes and methods in JDK7 */
  private final Map<String, List<MethodSignature>> jdk7Methods
      = new HashMap<>();

  /**
   * Map of which methods can have lambda expressions as parameters. The key is
   * the name of the type and the value is a list of Methods in the type that
   * take a functional interface as a parameter.
   */
  private final Map<String, List<MethodSignature>> functionalParameterMethodMap
      = new HashMap<>();

  /**
   * Map of which methods in which classes return a Stream (and are therefore a
   * source).
   */
  private final Map<String, List<MethodSignature>> streamReturningMethodMap
      = new HashMap<>();

  /**
   * Constructor
   *
   * @param jdk7MethodFile Name of the JDK7 CSV method list file
   * @param jdk8RtDotJarFile Name of the JDK8 rt.jar file
   * @param output PrintWriter to send output to
   * @param markNew Whether to mark types and methods as new if they are
   * @throws IOException If there is an IO error reading the files
   */
  public JDKAnalyser(String jdk7MethodFile, String jdk8RtDotJarFile,
      PrintWriter output, boolean markNew) throws IOException {
    this.output = output;
    this.markNew = markNew;

    /**
     * Read in the list of all methods extracted from the JDK7 rt.jar file
     */
    System.out.println("Reading in JDK7 method list...");
    new BufferedReader(new FileReader(jdk7MethodFile))
        .lines()
        .forEach(l -> splitLine(l));

    System.out.println("JDK7 has " + jdk7Methods.keySet().size() + " classes");

    /**
     * Extract a list of all the public classes from the JDK8 rt.jar file.
     */
    System.out.println("Extracting JDK8 class list...");
    jdk8ClassList = new JarFile(jdk8RtDotJarFile)
        .stream()
        .parallel()
        .map(JarEntry::getName)
        .filter(n -> n.endsWith(".class") && // Only public classes
            (n.startsWith("java") || n.startsWith("org")))
        .filter(n -> !n.contains("$")) // Ignore inner classes
        .map(n -> n.replace("/", ".")) // Switch from file to class name
        .map(n -> n.replace(".class", ""))
        .collect(Collectors.toList());

    System.out.println("JDK8 has " + jdk8ClassList.size() + " classes");

    /**
     * Search each of these classes to find which are functional interfaces.
     */
    functionalInterfaces = jdk8ClassList.stream()
        .filter(t -> isFunctionalInterface(t))
        .sorted()
        .collect(Collectors.toList());

    System.out.println("Found "
        + functionalInterfaces.size()
        + " functional interfaces");
  }

  /**
   * Is this class new or old?
   *
   * @param type The name of the class
   * @return If it is new
   */
  public boolean isNewClass(String type) {
    return false;
  }

  /**
   * Checke whether this method already exists in JDK7
   *
   * @param c The name of the class
   * @param m The Method to search for
   * @return True if the method exists in JDK7
   */
  public boolean isNewMethod(String c, MethodSignature m) {
    /* Check whether the class exists in JDK7 */
    if (jdk7Methods.containsKey(c)) {
      /* Then check whether a method of the same signature exists */
      return jdk7Methods.get(c).stream()
          .filter(ms -> ms.equals(m))
          .count() == 0;
    }

    return true;
  }

  /**
   * Print a list of all the functional interfaces found highlighting those that
   * are new
   */
  public void printFunctionalInterfaces() {
    output.println("Functional Interfaces");
    output.println("=====================\n");
    functionalInterfaces.stream()
        .forEach(n -> {
          output.print(n);

          if (markNew && !jdk7Methods.containsKey(n))
            output.print(" NEW");

          output.println();
        });
  }

  /**
   * Analyse the JDK8 libraries searching for all methods that use the specified
   * functional interface as a parameter.
   *
   * @param type The functional interface type to find methods for
   * @param ignoreStreamPackage Ignore the stream package when searching
   * @throws IllegalArgumentException If the type is not a functional interface
   * @throws IOException If there is an IO error
   */
  public void findUseOfFunctionalInterface(String type, 
      boolean ignoreStreamPackage) 
      throws IllegalArgumentException, IOException {
    if (!functionalInterfaces.contains(type))
      throw new IllegalArgumentException("Type is not a functional interface");

    System.out.println("Searching for methods that can use " + type);
    jdk8ClassList.stream()
        .forEach(t -> processType(t, type));
    outputResults(ignoreStreamPackage);
  }

  /**
   * Analyse a single type looking for methods that can use Lambdas
   *
   * @param typeName The name of the type to analyse
   * @throws IOException If there is an IO error
   */
  public void analyseType(String typeName) throws IOException {
    processType(typeName, null);
    outputResults(false);
  }

  /**
   * Analyse all the types in JDK8 looking for methods that can use Lambdas
   *
   * @throws IOException If there is an IO error
   */
  public void analyseAllTypes() throws IOException {
    /**
     * Process each of the Java SE 8 classes looking for methods that can use
     * Lmabda expressions and those that are Stream sources.
     */
    jdk8ClassList.stream()
        .forEach(t -> processType(t, null));
    outputResults(false);
  }

  /**
   * Print the list of all methods that return a Stream (and therefore are
   * either a Stream source or intermediate operation)
   * 
   * @param ignoreStreamPackage
   */
  public void printMethodsReturningStream(boolean ignoreStreamPackage) {
    output.println("\nStream sources");
    output.println("===============");
    Set<String> sourceKeySet = streamReturningMethodMap.keySet();

    sourceKeySet.stream()
        .filter(c -> !(ignoreStreamPackage && c.startsWith(STREAM_PACKAGE)))
        .peek(c -> output.println("\n" + c
                + "\n" + UNDERLINE.substring(0, c.length())))
        .forEach(c -> streamReturningMethodMap.get(c).stream()
            .forEach(m -> output.println(m)));

    /* Determine the Stream source stats and print them out */
    long sourceCount = sourceKeySet.stream()
        .mapToInt(c -> streamReturningMethodMap.get(c).size())
        .sum();
    
    System.out.println("Stream sources/intermediate operations: "
        + sourceCount + " methods in "
        + streamReturningMethodMap.size() + " classes");
  }

  /**
   * Output the results of the analysis
   * 
   * @param ignoreStreamPackage Whether to ignore the stream package
   */
  private void outputResults(boolean ignoreStreamPackage) {
    output.println("Methods that can use Lambda expressions for parameters");
    output.println("======================================================\n");
    Set<String> functionalKeySet = functionalParameterMethodMap.keySet();

    /**
     * Print out each class that has valid methods and keep track of the total
     * number of methods
     */
    long newMethodCount = functionalKeySet.stream()
        .filter(c -> !(ignoreStreamPackage && c.startsWith(STREAM_PACKAGE)))
        .mapToInt(c -> printClass(output, c))
        .sum();

    /* Determine the stats for Lambda usage and print them out */
    long methodCount = functionalKeySet.stream()
        .mapToInt(c -> functionalParameterMethodMap.get(c).size())
        .sum();
    
    System.out.println("Lambda usage: " + methodCount
        + " methods (of which " + newMethodCount
        + " are new) in " + functionalParameterMethodMap.size() + " classes");
  }

  /**
   * Print the details of a given class
   *
   * @param output Where to send the output
   * @param c The name of the class to print details of
   */
  private int printClass(PrintWriter output, String c) {
    output.println(c);
    output.println(UNDERLINE.substring(0, c.length()));

    /* Get a Stream of Methods from this class */
    int newMethodCount = functionalParameterMethodMap.get(c).stream()
        .mapToInt(m -> {
          int newMethod = 0;
          output.print(m);

          if (isNewMethod(c, m)) {
            newMethod = 1;

            if (markNew)
              output.print(" NEW");
          }

          output.println();
          return newMethod;
        })
        .sum();

    output.println();
    return newMethodCount;
  }

  /**
   * Split a CSV line from the JDK7 data file into class, method and parameter
   * details. Record them in the JDK7 method map.
   *
   * @param line The line of text to split up
   */
  private void splitLine(String line) {
    String[] fields = line.split(",");
    String className = fields[0];

    /* If the map does not contain this class add it */
    if (!jdk7Methods.containsKey(className))
      jdk7Methods.put(className, new ArrayList<>());

    /* Nothing further to do for types with no methods */
    if (fields.length == 1)
      return;

    /* Extract the parameter types */
    String methodName = fields[1];
    List<String> parameters = Arrays.stream(fields)
        .skip(2)
        .collect(Collectors.toList());

    /* Add the signature to the method list */
    List<MethodSignature> jdk7MethodList = jdk7Methods.get(className);
    jdk7MethodList.add(new MethodSignature(methodName, parameters));
  }

  /**
   * Search a type for methods that can use Lambda expressions as parameters and
   * for methods that return a Stream (so are therefore a Stream source)
   *
   * @param typeName The name of the type to test
   * @param functionalInterface Functional interface to find methods for (null
   * means search for all)
   */
  public void processType(String typeName, String functionalInterface) {
    /* Get a class reference for this type so we can use reflection */
    Class type;

    try {
      type = Class.forName(typeName);
    } catch (ClassNotFoundException cnfe) {
      System.out.println("Class not found: " + typeName);
      return;
    }

    /* Get the array of methods for this type */
    Method[] methods = type.getDeclaredMethods();

    /**
     * Create a list of methods that can use Lambda expressions for parameters.
     * We use distinct to eliminate methods where the name and parameter types
     * are the same, but the return type is different.
     */
    List<MethodSignature> lambdaMethodList = Arrays.stream(methods)
        .filter(m -> !m.getName().contains("$")) // Ignore inner class methods
        .map(m -> new MethodSignature(m))
        .filter(ms -> isMethodWithFunctionalParameter(ms, functionalInterface))
        .distinct()
        .collect(Collectors.toList());

    if (!lambdaMethodList.isEmpty())
      functionalParameterMethodMap.put(typeName, lambdaMethodList);

    /**
     * Analyse the type to find any methods that return a Stream, so we can
     * identify Stream sources and intermediate methods
     */
    List<MethodSignature> streamSourceList = Arrays.stream(methods)
        .filter(m -> m.getReturnType().getName().compareTo(STREAM_TYPE) == 0)
        .map(m -> new MethodSignature(m))
        .collect(Collectors.toList());

    if (!streamSourceList.isEmpty())
      streamReturningMethodMap.put(typeName, streamSourceList);
  }

  /**
   * Determine whether a given type is a functional interface or not. A
   * functional interface is one that has only one abstract method, so we need
   * to ignore default and static methods.
   *
   * @param typeName The name of the type to test
   */
  private boolean isFunctionalInterface(String typeName) {
    /* Get a class reference for this type so we can use reflection */
    Class type;

    try {
      type = Class.forName(typeName);
    } catch (ClassNotFoundException cnfe) {
      System.out.println("Class not found: " + typeName);
      return false;
    }

    /* A functional interface must be an interface, so ignore everything else */
    if (!type.isInterface())
      return false;

    /* Get the array of methods that this interface has defined in it */
    Method[] methods = type.getDeclaredMethods();

    if (Arrays.stream(methods)
        .parallel()
        .filter(m -> !m.getName().contains("$")) // Ignore synthetic refs
        .filter(m -> !m.isDefault()) // Ignore default methods
        .filter(m -> !Modifier.isStatic(m.getModifiers())) // And static methods
        .count() == 1)
      return true;
    return false;
  }

  /**
   * Determine whether a parameter is functional using the list of functional
   * interfaces extracted from the core libraries.
   *
   * @param parameter The parameter to test
   * @param functionalInterface Which functional interface we want to find (null
   * means search for all)
   * @return Whether the type is a functional interface or not
   */
  private boolean isMethodWithFunctionalParameter(MethodSignature method,
      String functionalInterface) {
    Predicate<String> matchFunctionalInterfaces;

    if (functionalInterface != null)
      matchFunctionalInterfaces = n -> functionalInterface.compareTo(n) == 0;
    else
      matchFunctionalInterfaces = n -> functionalInterfaces.contains(n);

    List<String> functionalMethodList = method.getParameters()
        .stream()
        .filter(p -> !p.startsWith("[")) // Ignore arrays
        .filter(matchFunctionalInterfaces)
        .collect(Collectors.toList());

    return !functionalMethodList.isEmpty();
  }
}
