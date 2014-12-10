/**
 * JDK8 library analyser project
 *
 * Copyright © 2014, Oracle and/or its affiliates. All rights reserved.
 */
package libsearch;

import java.io.IOException;
import java.io.PrintWriter;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

/**
 * Application to find all the functional interfaces in JDK8, all the places
 * where they can be used (so where Lambda expressions can be used) and a list
 * of Stream sources. All new interfaces and methods since JDK7 are also
 * highlighted.
 *
 * @author Speakjava (simon.ritter@oracle.com)
 */
public class Main {
  /**
   * Main entry point
   * 
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    String outputFile = "/tmp/jdk8-info.txt";
    String type = null;
    String[] functionalParams = null;
    boolean printFunctionalInterfaces = false;
    boolean printStreamReturningMethods = false;
    boolean markNew = false;
    boolean ignoreStreamPackage = false;

    /* Process the command line options */
    Options options = new Options();
    options.addOption("o", true, "Output file");
    options.addOption("t", true, 
        "Type to analyse (rather than full JDK)");
    options.addOption("f", true, 
        "Functional parameters to search for");
    options.addOption("p", false, 
        "Record details of functional interfaces found");
    options.addOption("s", false, 
        "Record all methods that return a Stream");
    options.addOption("n", false, "Mark new types and methods");
    options.addOption("i", false, 
        "Ignore the java.util.stream package for method searches");
    PosixParser parser = new PosixParser();
    
    try {
      CommandLine commandLine = parser.parse(options, args, false);
      Option[] cliOpts = commandLine.getOptions();

      for (Option o : cliOpts) {
        switch (o.getOpt()) {
          case "o":
            outputFile = o.getValue();
            break;
          case "t":
            type = o.getValue();
            break;
          case "f":
            functionalParams = o.getValue().split(",");
            break;
          case "p":
            printFunctionalInterfaces = true;
            break;
          case "s":
            printStreamReturningMethods = true;
            break;
          case "n":
            markNew = true;
            break;
          case "i":
            ignoreStreamPackage = true;
            break;
          default:
            System.out.println("Unrecognized option: " + o.getArgName());
            usage(options);
        }
      }

      String[] otherArgs = commandLine.getArgs();

      /* Check we have an arg for the two file names we need */
      if (otherArgs.length < 2)
        usage(options);

      /* Now run the analyser */
      try (PrintWriter output = new PrintWriter(outputFile)) {
        /* Now run the analyser */
        JDKAnalyser analyser =
            new JDKAnalyser(otherArgs[0], otherArgs[1], output, markNew);
        
        if (printFunctionalInterfaces)
          analyser.printFunctionalInterfaces();
        
        if (type != null)
          analyser.analyseType(type);
        else if (functionalParams != null && functionalParams.length != 0) {
          for (String fp : functionalParams)
            analyser.findUseOfFunctionalInterface(fp, ignoreStreamPackage);
        } else
          analyser.analyseAllTypes();
        
        if (printStreamReturningMethods)
          analyser.printMethodsReturningStream(ignoreStreamPackage);
      }
    } catch (ParseException pe) {
      usage(options);
    } catch (IOException ioe) {
      System.out.println("Error with input or output");
      System.out.println(ioe.getMessage());
      System.exit(2);
    }
  }

  /**
   * Print the correct usage for the application
   * 
   * @param options The options to use for formating the help
   */
  public static void usage(Options options) {
    HelpFormatter formatter = new HelpFormatter();
    formatter.printHelp(
        "LibrarySearcher [OPTIONS] JDK7_Methods_File JDK8_rt.jar", options);
    System.exit(1);
  }
}