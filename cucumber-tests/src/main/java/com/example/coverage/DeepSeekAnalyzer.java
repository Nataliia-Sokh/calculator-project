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

public class DeepSeekAnalyzer {

    private final Path sourceDir;
    private final Path outputDir;
    private final String deepseekPath;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Set<String> coveredMethods = new HashSet<>();
    private final Map<String, Set<String>> methodScenarioMap = new HashMap<>();

    public DeepSeekAnalyzer(String sourceDirectory, String outputDirectory, String deepseekModelPath) {
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

    public void analyzeCode() throws IOException {
        loadCoverageData();
        List<Path> javaFiles = collectJavaFiles();
        List<ClassInfo> classes = extractClassInfo(javaFiles);
        Path deepseekInput = prepareDeepSeekInput(classes);
        Path deepseekOutput = runDeepSeekAnalysis(deepseekInput);
        Map<String, TestRecommendation> recommendations = processDeepSeekOutput(deepseekOutput);
        generateRecommendationReport(recommendations);
    }

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

    private void loadCoverageData() throws IOException {
        Path coverageFile = Paths.get("build/reports/cucumber-method-coverage.csv");
        if (!Files.exists(coverageFile)) return;
        try (BufferedReader reader = Files.newBufferedReader(coverageFile)) {
            reader.readLine(); // skip header
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 3) {
                    String scenario = parts[0].replace("\"", "").trim();
                    String className = parts[1].replace("\"", "").trim();
                    String method = parts[2].replace("\"", "").trim();
                    String methodKey = className + "#" + method;
                    coveredMethods.add(methodKey);
                    methodScenarioMap.computeIfAbsent(methodKey, k -> new HashSet<>()).add(scenario);
                }
            }
        }
    }

    private String extractClassName(Path file) {
        String fileName = file.getFileName().toString();
        return fileName.substring(0, fileName.lastIndexOf("."));
    }

    private List<ClassInfo> extractClassInfo(List<Path> javaFiles) throws IOException {
        List<ClassInfo> classes = new ArrayList<>();
        for (Path file : javaFiles) {
            String content = Files.readString(file);
            String className = extractClassName(file);
            List<MethodInfo> methods = extractMethodsWithBodies(content, className);
            classes.add(new ClassInfo(className, file, methods));
        }
        return classes;
    }

    private List<MethodInfo> extractMethodsWithBodies(String content, String className) {
        List<MethodInfo> methods = new ArrayList<>();
        String[] lines = content.split("\n");
        StringBuilder methodBuilder = new StringBuilder();
        boolean inMethod = false;
        String signature = null;

        for (String line : lines) {
            line = line.trim();
            if ((line.startsWith("public") || line.startsWith("private") || line.startsWith("protected")) &&
                    line.contains("(") && line.contains(")") && line.contains("{") &&
                    !line.contains("class") && !line.contains("interface")) {
                inMethod = true;
                methodBuilder.setLength(0);
                methodBuilder.append(line);
                signature = line.substring(0, line.indexOf('(')).trim();
            } else if (inMethod) {
                methodBuilder.append("\n").append(line);
                if (line.contains("}")) {
                    inMethod = false;
                    String methodName = signature.substring(signature.lastIndexOf(" ") + 1);
                    String fullSig = className + "#" + methodName;
                    boolean isCovered = coveredMethods.contains(fullSig);
                    List<String> scenarios = new ArrayList<>(methodScenarioMap.getOrDefault(fullSig, Collections.emptySet()));
                    methods.add(new MethodInfo(methodName, signature, methodBuilder.toString(), isCovered, scenarios));
                }
            }
        }

        return methods;
    }

    private Path prepareDeepSeekInput(List<ClassInfo> classes) throws IOException {
        Path inputFile = outputDir.resolve("deepseek-input.json");
        ObjectNode root = mapper.createObjectNode();
        ArrayNode classesNode = root.putArray("classes");

        for (ClassInfo classInfo : classes) {
            ObjectNode classNode = classesNode.addObject();
            classNode.put("className", classInfo.getClassName());
            classNode.put("filePath", classInfo.getFilePath().toString());
            ArrayNode methodsNode = classNode.putArray("methods");
            for (MethodInfo method : classInfo.getMethods()) {
                ObjectNode methodNode = methodsNode.addObject();
                methodNode.put("name", method.name);
                methodNode.put("signature", method.signature);
                methodNode.put("body", method.body);
                methodNode.put("isCovered", method.isCovered);
                ArrayNode scenariosNode = methodNode.putArray("coveredInScenarios");
                for (String s : method.coveredInScenarios) scenariosNode.add(s);
            }
        }

        mapper.writerWithDefaultPrettyPrinter().writeValue(inputFile.toFile(), root);
        return inputFile;
    }

    private Path runDeepSeekAnalysis(Path inputFile) throws IOException {
        Path outputFile = outputDir.resolve("deepseek-output.json");
        String[] command = {
                "bash",
                deepseekPath,
                "--model", "deepseek-local",
                "--input", inputFile.toString(),
                "--output", outputFile.toString()
        };
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) System.out.println("DeepSeek: " + line);
            }
            boolean completed = process.waitFor(30, TimeUnit.MINUTES);
            if (!completed) {
                process.destroyForcibly();
                throw new IOException("DeepSeek analysis timed out");
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) throw new IOException("DeepSeek failed with code " + exitCode);
            return outputFile;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("DeepSeek analysis interrupted", e);
        }
    }

    private Map<String, TestRecommendation> processDeepSeekOutput(Path outputFile) throws IOException {
        Map<String, TestRecommendation> recommendations = new HashMap<>();
        Map<String, Object> output = mapper.readValue(outputFile.toFile(), Map.class);
        List<Map<String, Object>> recList = (List<Map<String, Object>>) output.get("recommendations");
        for (Map<String, Object> rec : recList) {
            String className = (String) rec.get("className");
            double complexity = ((Number) rec.get("complexity")).doubleValue();
            double testPriority = ((Number) rec.get("testPriority")).doubleValue();
            List<String> scenarios = (List<String>) rec.get("suggestedTestScenarios");
            recommendations.put(className, new TestRecommendation(className, complexity, testPriority, scenarios));
        }
        return recommendations;
    }

    private void generateRecommendationReport(Map<String, TestRecommendation> recommendations) throws IOException {
        Path reportFile = outputDir.resolve("test-recommendations.json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(reportFile.toFile(), recommendations);
    }

    public static void main(String[] args) {
        try {
            if (args.length < 3) {
                System.out.println("Usage: DeepSeekAnalyzer <sourceDir> <outputDir> <deepseekPath>");
                System.exit(1);
            }
            DeepSeekAnalyzer analyzer = new DeepSeekAnalyzer(args[0], args[1], args[2]);
            analyzer.analyzeCode();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    static class MethodInfo {
        public final String name, signature, body;
        public final boolean isCovered;
        public final List<String> coveredInScenarios;
        public MethodInfo(String name, String signature, String body, boolean isCovered, List<String> scenarios) {
            this.name = name;
            this.signature = signature;
            this.body = body;
            this.isCovered = isCovered;
            this.coveredInScenarios = scenarios;
        }
    }

    static class ClassInfo {
        private final String className;
        private final Path filePath;
        private final List<MethodInfo> methods;
        public ClassInfo(String className, Path filePath, List<MethodInfo> methods) {
            this.className = className;
            this.filePath = filePath;
            this.methods = methods;
        }
        public String getClassName() { return className; }
        public Path getFilePath() { return filePath; }
        public List<MethodInfo> getMethods() { return methods; }
    }

    static class TestRecommendation {
        private final String className;
        private final double complexity;
        private final double testPriority;
        private final List<String> suggestedTestScenarios;
        public TestRecommendation(String className, double complexity, double testPriority, List<String> scenarios) {
            this.className = className;
            this.complexity = complexity;
            this.testPriority = testPriority;
            this.suggestedTestScenarios = scenarios;
        }
        public String getClassName() { return className; }
        public double getComplexity() { return complexity; }
        public double getTestPriority() { return testPriority; }
        public List<String> getSuggestedTestScenarios() { return suggestedTestScenarios; }
    }
}
