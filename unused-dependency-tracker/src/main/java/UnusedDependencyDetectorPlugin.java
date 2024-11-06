import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleDependency;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UnusedDependencyDetectorPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getTasks().create("detectUnusedDependencies", task -> {
            task.doLast(action -> {
                Set<String> usedDependencies = new HashSet<>();
                Set<String> declaredDependencies = new HashSet<>();

                // Regex to find class usage
                Pattern pattern = Pattern.compile("import\\s+([\\w\\.]+);");

                // Specify source directories manually
                project.getConvention().getPlugin(org.gradle.api.plugins.JavaPluginConvention.class)
                        .getSourceSets().forEach(sourceSet -> {
                            sourceSet.getAllJava().getSrcDirs().forEach(srcDir -> {
                                project.fileTree(srcDir).forEach(file -> {
                                    if (file.getName().endsWith(".java")) {
                                        try {
                                            Files.lines(Paths.get(file.toURI())).forEach(line -> {
                                                Matcher matcher = pattern.matcher(line);
                                                while (matcher.find()) {
                                                    String className = matcher.group(1);
                                                    usedDependencies.add(className);
                                                }
                                            });
                                        } catch (IOException e) {
                                            System.err.println("Error reading file: " + file.getAbsolutePath());
                                        }
                                    }
                                });
                            });
                        });

                // Collect declared dependencies
                project.getConfigurations().forEach(configuration -> {
                    for (Dependency dependency : configuration.getAllDependencies()) {
                        if (dependency instanceof ModuleDependency) {
                            declaredDependencies.add(dependency.getName());
                        }
                    }
                });

                // Check if used dependencies match declared ones
                Set<String> unusedDependencies = new HashSet<>(declaredDependencies);
                usedDependencies.forEach(used -> {
                    String dependencyName = used.substring(used.lastIndexOf('.') + 1);
                    unusedDependencies.remove(dependencyName);
                });

                // Create report file
                File reportFile = new File(project.getBuildDir(), "unused-dependencies-report.txt");
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(reportFile))) {
                    if (!unusedDependencies.isEmpty()) {
                        writer.write("Unused dependencies detected:\n");
                        for (String dependency : unusedDependencies) {
                            writer.write(dependency + "\n");
                        }
                    } else {
                        writer.write("No unused dependencies found.\n");
                    }
                } catch (IOException e) {
                    System.err.println("Error writing report file: " + reportFile.getAbsolutePath());
                }

                // Output report file path
                System.out.println("Report generated: " + reportFile.getAbsolutePath());
            });
        });
    }
}
