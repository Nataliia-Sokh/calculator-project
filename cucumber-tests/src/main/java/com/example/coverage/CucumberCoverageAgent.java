package com.example.coverage;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;

/**
 * AspectJ aspect to track method execution coverage during Cucumber tests
 */
@Aspect
public class CucumberCoverageAgent {

    private static final String COVERAGE_FILE = "build/reports/cucumber-method-coverage.csv";
    private static final String SUMMARY_FILE = "build/reports/cucumber-coverage-summary.txt";
    
    // Main data structure to track which methods are called in which scenarios
    private static final Map<String, Set<String>> scenarioMethodMap = new ConcurrentHashMap<>();
    
    // Keep track of all discovered methods for summary reporting
    private static final Set<String> allDiscoveredMethods = Collections.newSetFromMap(new ConcurrentHashMap<>());
    
    // Current scenario being executed
    private static String currentScenario = "unknown";
    
    // Debug counter
    private static final AtomicInteger debugCounter = new AtomicInteger(0);
    
    // Track start time for reporting
    private static final LocalDateTime startTime = LocalDateTime.now();
    
    static {
        System.out.println("=== CucumberCoverageAgent loaded ===");
        
        // Print some basic info about the environment
        System.out.println("Java version: " + System.getProperty("java.version"));
        System.out.println("AspectJ available: " + (JoinPoint.class != null));
    }

    /**
     * Set the current Cucumber scenario name
     */
    public static void setCurrentScenario(String scenarioName) {
        if (StringUtils.isNotBlank(scenarioName)) {
            System.out.println("=== Setting current scenario: " + scenarioName + " ===");
            currentScenario = scenarioName;
            // Create a thread-safe set for this scenario if it doesn't exist
            scenarioMethodMap.putIfAbsent(currentScenario, Collections.newSetFromMap(new ConcurrentHashMap<>()));
        }
    }

    /**
     * Track all method executions in the calculator project
     * Exclude standard Java packages and test classes to reduce noise
     */
    @Before("execution(* com.example.calculator..*(..)) && !within(com.example.coverage..*)")
    public void trackMethodExecution(JoinPoint joinPoint) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            String className = joinPoint.getTarget().getClass().getName();
            
            // Skip if it's a proxy class
            if (className.contains("$$") || className.contains("$Proxy")) {
                return;
            }
            
            String methodName = method.getName();
            String methodSignature = formatMethodSignature(className, methodName, method);
            
            // Print the first few method calls for debugging
            int count = debugCounter.incrementAndGet();
            if (count <= 10) {
                System.out.println("DEBUG: Method tracked (" + count + "): " + methodSignature);
            }
            
            // Add to global set of all methods
            allDiscoveredMethods.add(methodSignature);
            
            // Record this method as being called during the current scenario
            Set<String> methods = scenarioMethodMap.get(currentScenario);
            if (methods != null) {
                methods.add(methodSignature);
            }
        } catch (Exception e) {
            System.err.println("Error tracking method execution: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Format method signature in a readable way
     */
    private static String formatMethodSignature(String className, String methodName, Method method) {
        StringBuilder sb = new StringBuilder();
        sb.append(className).append("#").append(methodName).append("(");
        
        Class<?>[] paramTypes = method.getParameterTypes();
        for (int i = 0; i < paramTypes.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(paramTypes[i].getSimpleName());
        }
        
        sb.append(")");
        return sb.toString();
    }

    /**
     * Save the coverage data to CSV and summary files
     */
    public static void saveCoverageData() {
        try {
            System.out.println("=== Saving coverage data ===");
            
            // Create directories if they don't exist
            Files.createDirectories(Paths.get("build/reports"));
            
            // Save detailed CSV data
            saveDetailedCoverageData();
            
            // Save summary report
            saveSummaryReport();
            
            System.out.println("Coverage data saved to " + COVERAGE_FILE + " and " + SUMMARY_FILE);
        } catch (IOException e) {
            System.err.println("Failed to write cucumber coverage data: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Save detailed coverage data to CSV
     */
    private static void saveDetailedCoverageData() throws IOException {
        try (FileWriter writer = new FileWriter(COVERAGE_FILE)) {
            // Write CSV header
            writer.write("Scenario,Class,Method,ParameterCount\n");
            
            // Sort for consistent output
            Map<String, Set<String>> sortedMap = new TreeMap<>(scenarioMethodMap);
            
            for (Map.Entry<String, Set<String>> entry : sortedMap.entrySet()) {
                String scenario = entry.getKey();
                Set<String> methods = new TreeSet<>(entry.getValue());

                for (String methodSignature : methods) {
                    String[] parts = methodSignature.split("#");
                    String className = parts[0];
                    String methodWithParams = parts[1];
                    
                    // Count parameters
                    int paramCount = 0;
                    if (methodWithParams.contains("(") && methodWithParams.contains(")")) {
                        String paramsStr = methodWithParams.substring(
                            methodWithParams.indexOf("(") + 1, 
                            methodWithParams.lastIndexOf(")")
                        );
                        if (!paramsStr.isEmpty()) {
                            paramCount = paramsStr.split(",").length;
                        }
                    }
                    
                    writer.write(String.format("\"%s\",\"%s\",\"%s\",%d\n", 
                        scenario.replace("\"", "\"\""), 
                        className.replace("\"", "\"\""),
                        methodWithParams.replace("\"", "\"\""),
                        paramCount));
                }
            }
        }
    }
    
    /**
     * Save summary report with coverage statistics
     */
    private static void saveSummaryReport() throws IOException {
        try (FileWriter writer = new FileWriter(SUMMARY_FILE)) {
            // Calculate coverage metrics
            int totalMethods = allDiscoveredMethods.size();
            int totalScenarios = scenarioMethodMap.size();
            
            // Count covered methods (methods executed at least once)
            Set<String> coveredMethods = new TreeSet<>();
            for (Set<String> methods : scenarioMethodMap.values()) {
                coveredMethods.addAll(methods);
            }
            int coveredMethodCount = coveredMethods.size();
            
            // Find unique classes
            Set<String> allClasses = new TreeSet<>();
            for (String method : allDiscoveredMethods) {
                allClasses.add(method.split("#")[0]);
            }
            
            // Calculate coverage percentage
            double coveragePercent = totalMethods > 0 
                ? (double)coveredMethodCount / totalMethods * 100 
                : 0;
            
            // Write summary header
            writer.write("Cucumber Method Coverage Summary\n");
            writer.write("==============================\n\n");
            writer.write(String.format("Generated: %s\n", 
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
            writer.write(String.format("Test Run Started: %s\n", 
                startTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
            writer.write("\n");
            
            // Write coverage statistics
            writer.write("Coverage Statistics\n");
            writer.write("-------------------\n");
            writer.write(String.format("Total Scenarios: %d\n", totalScenarios));
            writer.write(String.format("Total Classes Discovered: %d\n", allClasses.size()));
            writer.write(String.format("Total Methods Discovered: %d\n", totalMethods));
            writer.write(String.format("Methods Covered: %d\n", coveredMethodCount));
            writer.write(String.format("Coverage Percentage: %.2f%%\n", coveragePercent));
            writer.write("\n");
            
            // Print all found methods for debugging
            writer.write("All Discovered Methods:\n");
            for (String method : allDiscoveredMethods) {
                writer.write(" - " + method + "\n");
            }
            writer.write("\n");
            
            // Top scenarios by method coverage
            writer.write("Scenarios by Method Coverage\n");
            writer.write("---------------------------\n");
            
            scenarioMethodMap.entrySet().stream()
                .sorted((e1, e2) -> Integer.compare(e2.getValue().size(), e1.getValue().size()))
                .forEach(entry -> {
                    try {
                        writer.write(String.format("%s: %d methods\n", 
                            entry.getKey(), entry.getValue().size()));
                    } catch (IOException e) {
                        // Ignore
                    }
                });
        }
    }

    /**
     * Initialize coverage tracking
     */
    public static void initializeCoverage() {
        scenarioMethodMap.clear();
        allDiscoveredMethods.clear();
        currentScenario = "unknown";
        debugCounter.set(0);
        
        System.out.println("=== CucumberCoverageAgent initialized ===");
        System.out.println("Current classpath: " + System.getProperty("java.class.path"));
        
        // Try to load a class from the main project to verify classpath
        try {
            Class<?> calculatorClass = Class.forName("com.example.calculator.Calculator");
            System.out.println("Successfully loaded Calculator class: " + calculatorClass.getName());
            
            // Print all methods from the Calculator class for debugging
            System.out.println("Calculator methods:");
            for (Method method : calculatorClass.getDeclaredMethods()) {
                System.out.println(" - " + method.getName());
            }
        } catch (ClassNotFoundException e) {
            System.err.println("FAILED TO LOAD CALCULATOR CLASS: " + e.getMessage());
            System.err.println("This indicates a classpath issue!");
        }
    }
}
