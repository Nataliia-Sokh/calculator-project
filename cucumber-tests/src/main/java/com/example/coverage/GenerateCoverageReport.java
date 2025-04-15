package com.example.coverage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Generates a comprehensive HTML report that shows correlations between Cucumber scenarios
 * and the methods they execute.
 */
public class GenerateCoverageReport {

    private static final String OUTPUT_FILE = "build/reports/cucumber-method-coverage.html";
    private static final String METHOD_COVERAGE_CSV = "build/reports/cucumber-method-coverage.csv";

    public static void main(String[] args) {
        try {
            System.out.println("Generating comprehensive coverage report...");

            // Parse coverage data
            Map<String, Set<String>> scenarioMethods = new HashMap<>();
            Map<String, Set<String>> methodScenarios = new HashMap<>();
            Set<String> allMethods = new HashSet<>();

            // Read the CSV data
            parseMethodCoverageData(scenarioMethods, methodScenarios, allMethods);

            // Generate HTML report
            generateHtmlReport(scenarioMethods, methodScenarios, allMethods);

            System.out.println("Report generated successfully: " + OUTPUT_FILE);
        } catch (Exception e) {
            System.err.println("Error generating report: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void parseMethodCoverageData(
            Map<String, Set<String>> scenarioMethods,
            Map<String, Set<String>> methodScenarios,
            Set<String> allMethods) throws IOException {

        Path csvPath = Paths.get(METHOD_COVERAGE_CSV);
        if (!Files.exists(csvPath)) {
            System.err.println("Coverage CSV file not found: " + METHOD_COVERAGE_CSV);
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(csvPath.toFile()))) {
            // Skip header
            String line = reader.readLine();

            while ((line = reader.readLine()) != null) {
                // Simple CSV parsing - handles quoted fields with commas
                String[] parts = parseCSVLine(line);
                if (parts.length >= 3) {
                    String scenario = parts[0];
                    String className = parts[1];
                    String methodName = parts[2];

                    // Create full method signature
                    String methodSignature = className + "#" + methodName;

                    // Track all methods
                    allMethods.add(methodSignature);

                    // Add to scenario -> methods map
                    scenarioMethods.computeIfAbsent(scenario, k -> new HashSet<>()).add(methodSignature);

                    // Add to method -> scenarios map
                    methodScenarios.computeIfAbsent(methodSignature, k -> new HashSet<>()).add(scenario);
                }
            }
        }
    }

    private static String[] parseCSVLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString().replace("\"\"", "\""));
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }

        result.add(current.toString().replace("\"\"", "\""));
        return result.toArray(new String[0]);
    }

    private static void generateHtmlReport(
            Map<String, Set<String>> scenarioMethods,
            Map<String, Set<String>> methodScenarios,
            Set<String> allMethods) throws IOException {

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>Cucumber Method Coverage Report</title>\n");
        html.append("    <style>\n");
        html.append("        body { font-family: Arial, sans-serif; line-height: 1.6; margin: 0; padding: 20px; color: #333; }\n");
        html.append("        h1, h2, h3 { color: #2c3e50; }\n");
        html.append("        .container { max-width: 1200px; margin: 0 auto; }\n");
        html.append("        .summary { background-color: #f8f9fa; border-radius: 4px; padding: 15px; margin-bottom: 20px; }\n");
        html.append("        .card { border: 1px solid #ddd; border-radius: 4px; padding: 15px; margin-bottom: 15px; }\n");
        html.append("        .scenario-card { background-color: #e8f4f8; }\n");
        html.append("        .method-card { background-color: #f8f1e8; }\n");
        html.append("        .method-list, .scenario-list { list-style-type: none; padding-left: 0; }\n");
        html.append("        .method-list li, .scenario-list li { padding: 5px 0; }\n");
        html.append("        .coverage-good { color: #28a745; }\n");
        html.append("        .coverage-medium { color: #fd7e14; }\n");
        html.append("        .coverage-bad { color: #dc3545; }\n");
        html.append("        .method-name { font-family: monospace; background-color: #f5f5f5; padding: 2px 4px; border-radius: 3px; }\n");
        html.append("        .tabs { display: flex; margin-bottom: 15px; }\n");
        html.append("        .tab { padding: 10px 15px; cursor: pointer; background-color: #f8f9fa; border: 1px solid #ddd; }\n");
        html.append("        .tab.active { background-color: #007bff; color: white; border-color: #007bff; }\n");
        html.append("        .tab-content { display: none; }\n");
        html.append("        .tab-content.active { display: block; }\n");
        html.append("        .search { width: 100%; padding: 8px; margin-bottom: 15px; }\n");
        html.append("        .hidden { display: none; }\n");
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("    <div class=\"container\">\n");
        html.append("        <h1>Cucumber Method Coverage Report</h1>\n");

        // Generate summary section
        generateSummarySection(html, scenarioMethods, methodScenarios, allMethods);

        // Create tabs
        html.append("        <div class=\"tabs\">\n");
        html.append("            <div class=\"tab active\" data-tab=\"scenarios\">Scenarios</div>\n");
        html.append("            <div class=\"tab\" data-tab=\"methods\">Methods</div>\n");
        html.append("        </div>\n");

        // Search bar
        html.append("        <input type=\"text\" class=\"search\" id=\"searchInput\" placeholder=\"Search scenarios or methods...\">\n");

        // Scenarios tab
        html.append("        <div class=\"tab-content active\" id=\"scenarios-tab\">\n");
        html.append("            <h2>Scenarios and Their Methods</h2>\n");

        List<String> sortedScenarios = new ArrayList<>(scenarioMethods.keySet());
        Collections.sort(sortedScenarios);

        for (String scenario : sortedScenarios) {
            Set<String> methods = scenarioMethods.get(scenario);

            html.append("            <div class=\"card scenario-card scenario-item\">\n");
            html.append("                <h3>").append(escapeHtml(scenario)).append("</h3>\n");
            html.append("                <p>Methods called: ").append(methods.size()).append("</p>\n");
            html.append("                <ul class=\"method-list\">\n");

            List<String> sortedMethods = new ArrayList<>(methods);
            Collections.sort(sortedMethods);

            for (String method : sortedMethods) {
                html.append("                    <li><span class=\"method-name\">").append(escapeHtml(method)).append("</span></li>\n");
            }

            html.append("                </ul>\n");
            html.append("            </div>\n");
        }

        html.append("        </div>\n");

        // Methods tab
        html.append("        <div class=\"tab-content\" id=\"methods-tab\">\n");
        html.append("            <h2>Methods and Their Scenarios</h2>\n");

        List<String> sortedMethods = new ArrayList<>(methodScenarios.keySet());
        Collections.sort(sortedMethods);

        for (String method : sortedMethods) {
            Set<String> scenarios = methodScenarios.get(method);

            String coverageClass = getCoverageClass(scenarios.size());

            html.append("            <div class=\"card method-card method-item\">\n");
            html.append("                <h3 class=\"").append(coverageClass).append("\">").append(escapeHtml(method)).append("</h3>\n");
            html.append("                <p>Called in ").append(scenarios.size()).append(" scenario(s)</p>\n");
            html.append("                <ul class=\"scenario-list\">\n");

            List<String> scenariosSorted = new ArrayList<>(scenarios);
            Collections.sort(scenariosSorted);

            for (String scenario : scenariosSorted) {
                html.append("                    <li>").append(escapeHtml(scenario)).append("</li>\n");
            }

            html.append("                </ul>\n");
            html.append("            </div>\n");
        }

        html.append("        </div>\n");

        // Add JavaScript for interactivity
        html.append("        <script>\n");
        html.append("            // Tab functionality\n");
        html.append("            document.querySelectorAll('.tab').forEach(tab => {\n");
        html.append("                tab.addEventListener('click', () => {\n");
        html.append("                    document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));\n");
        html.append("                    document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));\n");
        html.append("                    \n");
        html.append("                    tab.classList.add('active');\n");
        html.append("                    document.getElementById(tab.dataset.tab + '-tab').classList.add('active');\n");
        html.append("                });\n");
        html.append("            });\n");
        html.append("            \n");
        html.append("            // Search functionality\n");
        html.append("            document.getElementById('searchInput').addEventListener('input', (e) => {\n");
        html.append("                const searchTerm = e.target.value.toLowerCase();\n");
        html.append("                \n");
        html.append("                if (document.getElementById('scenarios-tab').classList.contains('active')) {\n");
        html.append("                    document.querySelectorAll('.scenario-item').forEach(item => {\n");
        html.append("                        const scenarioName = item.querySelector('h3').textContent.toLowerCase();\n");
        html.append("                        const methodsText = Array.from(item.querySelectorAll('.method-name'))\n");
        html.append("                            .map(el => el.textContent.toLowerCase()).join(' ');\n");
        html.append("                        \n");
        html.append("                        if (scenarioName.includes(searchTerm) || methodsText.includes(searchTerm)) {\n");
        html.append("                            item.classList.remove('hidden');\n");
        html.append("                        } else {\n");
        html.append("                            item.classList.add('hidden');\n");
        html.append("                        }\n");
        html.append("                    });\n");
        html.append("                } else {\n");
        html.append("                    document.querySelectorAll('.method-item').forEach(item => {\n");
        html.append("                        const methodName = item.querySelector('h3').textContent.toLowerCase();\n");
        html.append("                        const scenariosText = Array.from(item.querySelectorAll('.scenario-list li'))\n");
        html.append("                            .map(el => el.textContent.toLowerCase()).join(' ');\n");
        html.append("                        \n");
        html.append("                        if (methodName.includes(searchTerm) || scenariosText.includes(searchTerm)) {\n");
        html.append("                            item.classList.remove('hidden');\n");
        html.append("                        } else {\n");
        html.append("                            item.classList.add('hidden');\n");
        html.append("                        }\n");
        html.append("                    });\n");
        html.append("                }\n");
        html.append("            });\n");
        html.append("        </script>\n");

        html.append("    </div>\n");
        html.append("</body>\n");
        html.append("</html>\n");

        // Write the HTML to file
        try (FileWriter writer = new FileWriter(OUTPUT_FILE)) {
            writer.write(html.toString());
        }
    }

    private static void generateSummarySection(
            StringBuilder html,
            Map<String, Set<String>> scenarioMethods,
            Map<String, Set<String>> methodScenarios,
            Set<String> allMethods) {

        int totalScenarios = scenarioMethods.size();
        int totalMethods = allMethods.size();
        int coveredMethods = methodScenarios.size();

        double coveragePercent = totalMethods > 0 ?
                (double) coveredMethods / totalMethods * 100 : 0;

        String coverageClass = getCoverageClass(coveragePercent);

        html.append("        <div class=\"summary\">\n");
        html.append("            <h2>Coverage Summary</h2>\n");
        html.append("            <p>Total Scenarios: ").append(totalScenarios).append("</p>\n");
        html.append("            <p>Total Methods: ").append(totalMethods).append("</p>\n");
        html.append("            <p>Methods Covered: ").append(coveredMethods).append("</p>\n");
        html.append("            <p>Coverage: <span class=\"").append(coverageClass).append("\">")
                .append(String.format("%.2f%%", coveragePercent)).append("</span></p>\n");

        // Find scenarios with most and least method coverage
        if (!scenarioMethods.isEmpty()) {
            String mostCoveredScenario = Collections.max(scenarioMethods.entrySet(),
                    Comparator.comparingInt(e -> e.getValue().size())).getKey();

            String leastCoveredScenario = Collections.min(scenarioMethods.entrySet(),
                    Comparator.comparingInt(e -> e.getValue().size())).getKey();

            html.append("            <p>Most Methods Covered: \"").append(escapeHtml(mostCoveredScenario))
                    .append("\" (").append(scenarioMethods.get(mostCoveredScenario).size()).append(" methods)</p>\n");

            html.append("            <p>Least Methods Covered: \"").append(escapeHtml(leastCoveredScenario))
                    .append("\" (").append(scenarioMethods.get(leastCoveredScenario).size()).append(" methods)</p>\n");
        }

        // Find methods called by most scenarios
        if (!methodScenarios.isEmpty()) {
            String mostUsedMethod = Collections.max(methodScenarios.entrySet(),
                    Comparator.comparingInt(e -> e.getValue().size())).getKey();

            html.append("            <p>Most Called Method: <span class=\"method-name\">").append(escapeHtml(mostUsedMethod))
                    .append("</span> (").append(methodScenarios.get(mostUsedMethod).size()).append(" scenarios)</p>\n");
        }

        html.append("        </div>\n");
    }

    private static String getCoverageClass(double coverage) {
        if (coverage >= 80) {
            return "coverage-good";
        } else if (coverage >= 50) {
            return "coverage-medium";
        } else {
            return "coverage-bad";
        }
    }

    private static String getCoverageClass(int scenarioCount) {
        if (scenarioCount >= 3) {
            return "coverage-good";
        } else if (scenarioCount >= 1) {
            return "coverage-medium";
        } else {
            return "coverage-bad";
        }
    }

    private static String escapeHtml(String input) {
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}