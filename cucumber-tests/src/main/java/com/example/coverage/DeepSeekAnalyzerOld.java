package com.example.coverage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Integration with DeepSeek 7B for intelligent code analysis and test coverage enhancement
 */
public class DeepSeekAnalyzerOld {

    private final Path sourceDir;
    private final Path outputDir;
    private final String deepseekPath;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Constructor for DeepSeek analyzer
     *
     * @param sourceDirectory Directory containing Java source files
     * @param outputDirectory Directory for analysis output
     * @param deepseekModelPath Path to the DeepSeek model directory or script
     */
    public DeepSeekAnalyzerOld(String sourceDirectory, String outputDirectory, String deepseekModelPath) {
        this.sourceDir = Paths.get(sourceDirectory);
        this.outputDir = Paths.get(outputDirectory);
        this.deepseekPath = deepseekModelPath;

        if (!Files.exists(outputDir)) {
            try {
                Files.createDirectories(outputDir);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create output directory", e);
            }
        }
    }

    /**
     * Analyze source code and generate test coverage recommendations
     */
    public void analyzeCode() throws IOException {
        System.out.println("Starting DeepSeek code analysis...");

        // Step 1: Collect Java files
        List<Path> javaFiles = collectJavaFiles();
        System.out.println("Found " + javaFiles.size() + " Java files");

        // Step 2: Extract class information
        List<ClassInfo> classes = extractClassInfo(javaFiles);
        System.out.println("Extracted information from " + classes.size() + " classes");

        // Step 3: Prepare input for DeepSeek
        Path deepseekInput = prepareDeepSeekInput(classes);
        System.out.println("Prepared DeepSeek input at: " + deepseekInput);

        // Step 4: Run DeepSeek analysis
        Path deepseekOutput = runDeepSeekAnalysis(deepseekInput);
        System.out.println("DeepSeek analysis complete. Output at: " + deepseekOutput);

        // Step 5: Process DeepSeek output
        Map<String, TestRecommendation> recommendations = processDeepSeekOutput(deepseekOutput);
        System.out.println("Processed " + recommendations.size() + " test recommendations");

        // Step 6: Generate recommendation report
        generateRecommendationReport(recommendations);
        System.out.println("Generated recommendation report");
    }

    /**
     * Collect all Java files from the source directory
     */
    private List<Path> collectJavaFiles() throws IOException {
        List<Path> javaFiles = new ArrayList<>();

        Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.toString().endsWith(".java")) {
                    javaFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return javaFiles;
    }

    /**
     * Extract class information from Java files
     */
    private List<ClassInfo> extractClassInfo(List<Path> javaFiles) throws IOException {
        List<ClassInfo> classes = new ArrayList<>();

        for (Path file : javaFiles) {
            // Simple parsing to extract basic class info
            // In a real implementation, you might want to use a more robust Java parser
            String content = Files.readString(file);
            String className = extractClassName(file);
            List<String> methods = extractMethods(content);

            ClassInfo classInfo = new ClassInfo(className, file, methods);
            classes.add(classInfo);
        }

        return classes;
    }

    /**
     * Extract class name from file path
     */
    private String extractClassName(Path file) {
        String fileName = file.getFileName().toString();
        return fileName.substring(0, fileName.lastIndexOf("."));
    }

    /**
     * Simple method to extract method names from Java source
     * NOTE: This is a simplified implementation - real world usage should use a proper Java parser
     */
    private List<String> extractMethods(String content) {
        List<String> methods = new ArrayList<>();

        // Very simplified method extraction - just for demonstration
        // Would need JavaParser or similar for real implementation
        for (String line : content.split("\n")) {
            line = line.trim();
            if ((line.contains("public ") || line.contains("private ") ||
                    line.contains("protected ")) && line.contains("(") &&
                    !line.contains("class ") && !line.contains("interface ")) {

                // Extract method name
                String methodPart = line.substring(0, line.indexOf('('));
                String[] parts = methodPart.split("\\s+");
                if (parts.length > 0) {
                    methods.add(parts[parts.length - 1]);
                }
            }
        }

        return methods;
    }

    /**
     * Prepare input file for DeepSeek analysis
     */
    private Path prepareDeepSeekInput(List<ClassInfo> classes) throws IOException {
        Path inputFile = outputDir.resolve("deepseek-input.json");

        ObjectNode root = mapper.createObjectNode();
        ArrayNode classesNode = root.putArray("classes");

        for (ClassInfo classInfo : classes) {
            ObjectNode classNode = classesNode.addObject();
            classNode.put("className", classInfo.getClassName());
            classNode.put("filePath", classInfo.getFilePath().toString());

            ArrayNode methodsNode = classNode.putArray("methods");
            for (String method : classInfo.getMethods()) {
                methodsNode.add(method);
            }
        }

        mapper.writerWithDefaultPrettyPrinter().writeValue(inputFile.toFile(), root);
        return inputFile;
    }

    /**
     * Run DeepSeek analysis on the prepared input
     */
    private Path runDeepSeekAnalysis(Path inputFile) throws IOException {
        Path outputFile = outputDir.resolve("deepseek-output.json");

        String scriptPath = "/Users/nhammerschmidt/work/calculator-project/cucumber-tests/src/main/java/com/example/coverage/run_deepseek.sh";

        String[] command = {
                "bash",
                scriptPath,
                "--model", "deepseek-local",
                "--input", inputFile.toString(),
                "--output", outputFile.toString()
        };

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // Read output
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("DeepSeek: " + line);
                }
            }

            boolean completed = process.waitFor(30, TimeUnit.MINUTES);
            if (!completed) {
                process.destroyForcibly();
                throw new IOException("DeepSeek analysis timed out after 30 minutes");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new IOException("DeepSeek analysis failed with exit code " + exitCode);
            }

            return outputFile;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("DeepSeek analysis was interrupted", e);
        }
    }


    /**
     * Process the output from DeepSeek
     */
    private Map<String, TestRecommendation> processDeepSeekOutput(Path outputFile) throws IOException {
        Map<String, TestRecommendation> recommendations = new HashMap<>();

        try {
            Map<String, Object> output = mapper.readValue(outputFile.toFile(), Map.class);
            List<Map<String, Object>> recList = (List<Map<String, Object>>) output.get("recommendations");

            for (Map<String, Object> rec : recList) {
                String className = (String) rec.get("className");
                double complexity = ((Number) rec.get("complexity")).doubleValue();
                double testPriority = ((Number) rec.get("testPriority")).doubleValue();
                List<String> suggestedTestScenarios = (List<String>) rec.get("suggestedTestScenarios");

                TestRecommendation recommendation = new TestRecommendation(
                        className, complexity, testPriority, suggestedTestScenarios);
                recommendations.put(className, recommendation);
            }

        } catch (Exception e) {
            // If DeepSeek fails or returns invalid format, create dummy recommendations
            System.err.println("Warning: Could not process DeepSeek output. Using fallback analysis.");
            recommendations = generateFallbackRecommendations();
        }

        return recommendations;
    }

    /**
     * Generate fallback recommendations if DeepSeek analysis fails
     */
    private Map<String, TestRecommendation> generateFallbackRecommendations() throws IOException {
        Map<String, TestRecommendation> recommendations = new HashMap<>();

        List<Path> javaFiles = collectJavaFiles();
        for (Path file : javaFiles) {
            String className = extractClassName(file);

            // Simple heuristic - count lines of code as rough complexity measure
            int lineCount = Files.readAllLines(file).size();
            double complexity = Math.min(10.0, lineCount / 50.0); // Scale 0-10
            double priority = Math.min(10.0, lineCount / 100.0);  // Scale 0-10

            List<String> scenarios = Arrays.asList(
                    "Test basic functionality",
                    "Test edge cases",
                    "Test error handling"
            );

            TestRecommendation recommendation = new TestRecommendation(
                    className, complexity, priority, scenarios);
            recommendations.put(className, recommendation);
        }

        return recommendations;
    }

    /**
     * Generate recommendation report
     */
    private void generateRecommendationReport(Map<String, TestRecommendation> recommendations) throws IOException {
        Path reportFile = outputDir.resolve("test-recommendations.json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(reportFile.toFile(), recommendations);

        // Also generate a HTML report
        generateHtmlReport(recommendations);
    }

    /**
     * Generate HTML report from recommendations
     */
    private void generateHtmlReport(Map<String, TestRecommendation> recommendations) throws IOException {
        Path htmlFile = outputDir.resolve("test-recommendations.html");

        // Sort recommendations by priority (highest first)
        List<TestRecommendation> sortedRecs = recommendations.values().stream()
                .sorted(Comparator.comparingDouble(TestRecommendation::getTestPriority).reversed())
                .collect(Collectors.toList());

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n")
                .append("<html lang=\"en\">\n")
                .append("<head>\n")
                .append("  <meta charset=\"UTF-8\">\n")
                .append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
                .append("  <title>DeepSeek Test Recommendations</title>\n")
                .append("  <link href=\"https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css\" rel=\"stylesheet\">\n")
                .append("</head>\n")
                .append("<body>\n")
                .append("  <div class=\"container mt-4\">\n")
                .append("    <h1>DeepSeek Test Coverage Recommendations</h1>\n")
                .append("    <p class=\"lead\">AI-powered analysis of test coverage needs and priorities</p>\n")
                .append("    <div class=\"row mt-4\">\n")
                .append("      <div class=\"col-12\">\n")
                .append("        <div class=\"card\">\n")
                .append("          <div class=\"card-header bg-primary text-white\">\n")
                .append("            Test Priority by Class\n")
                .append("          </div>\n")
                .append("          <div class=\"card-body\">\n")
                .append("            <table class=\"table table-striped\">\n")
                .append("              <thead>\n")
                .append("                <tr>\n")
                .append("                  <th>Class</th>\n")
                .append("                  <th>Complexity</th>\n")
                .append("                  <th>Test Priority</th>\n")
                .append("                  <th>Suggested Test Scenarios</th>\n")
                .append("                </tr>\n")
                .append("              </thead>\n")
                .append("              <tbody>\n");

        for (TestRecommendation rec : sortedRecs) {
            html.append("                <tr>\n")
                    .append("                  <td>").append(rec.getClassName()).append("</td>\n")
                    .append("                  <td>").append(String.format("%.1f", rec.getComplexity())).append("</td>\n")
                    .append("                  <td>").append(String.format("%.1f", rec.getTestPriority())).append("</td>\n")
                    .append("                  <td>\n")
                    .append("                    <ul>\n");

            for (String scenario : rec.getSuggestedTestScenarios()) {
                html.append("                      <li>").append(scenario).append("</li>\n");
            }

            html.append("                    </ul>\n")
                    .append("                  </td>\n")
                    .append("                </tr>\n");
        }

        html.append("              </tbody>\n")
                .append("            </table>\n")
                .append("          </div>\n")
                .append("        </div>\n")
                .append("      </div>\n")
                .append("    </div>\n")
                .append("  </div>\n")
                .append("</body>\n")
                .append("</html>");

        Files.writeString(htmlFile, html.toString());
    }

    // Helper classes

    static class ClassInfo {
        private final String className;
        private final Path filePath;
        private final List<String> methods;

        public ClassInfo(String className, Path filePath, List<String> methods) {
            this.className = className;
            this.filePath = filePath;
            this.methods = methods;
        }

        public String getClassName() {
            return className;
        }

        public Path getFilePath() {
            return filePath;
        }

        public List<String> getMethods() {
            return methods;
        }
    }

    static class TestRecommendation {
        private final String className;
        private final double complexity;
        private final double testPriority;
        private final List<String> suggestedTestScenarios;

        public TestRecommendation(String className, double complexity, double testPriority,
                                  List<String> suggestedTestScenarios) {
            this.className = className;
            this.complexity = complexity;
            this.testPriority = testPriority;
            this.suggestedTestScenarios = suggestedTestScenarios;
        }

        public String getClassName() {
            return className;
        }

        public double getComplexity() {
            return complexity;
        }

        public double getTestPriority() {
            return testPriority;
        }

        public List<String> getSuggestedTestScenarios() {
            return suggestedTestScenarios;
        }
    }

    // Main method for standalone testing
    public static void main(String[] args) {
        try {
            if (args.length < 3) {
                System.out.println("Usage: com.comforte.coverage.DeepSeekAnalyzer <sourceDir> <outputDir> <deepseekPath>");
                System.exit(1);
            }

            DeepSeekAnalyzerOld analyzer = new DeepSeekAnalyzerOld(args[0], args[1], args[2]);
            analyzer.analyzeCode();

        } catch (Exception e) {
            System.err.println("Error running DeepSeek analysis: " + e.getMessage());
            e.printStackTrace();
        }
    }
}