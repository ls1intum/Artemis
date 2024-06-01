package de.tum.cit.endpointanalysis;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.JavaAnnotation;
import com.thoughtworks.qdox.model.JavaClass;
import com.thoughtworks.qdox.model.JavaMethod;

public class AnalysisOfEndpointConnections {

    /**
     * This is the entry point of the analysis of server sided endpoints.
     *
     * @param args List of files that should be analyzed regarding endpoints.
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("No files to analyze.");
            return;
        }
        String[] filePaths = args[0].split("\n");
        String[] serverFiles = Arrays.stream(filePaths).map(filePath -> Paths.get("..", "..", filePath).toString()).filter(filePath -> Files.exists(Paths.get(filePath)) && filePath.endsWith(".java"))
                .toArray(String[]::new);
        analyzeServerEndpoints(serverFiles);
    }

    private static void analyzeServerEndpoints(String[] filePaths) {
        final Set<String> httpMethodClasses = Set.of(GetMapping.class.getName(), PostMapping.class.getName(), PutMapping.class.getName(), DeleteMapping.class.getName(),
                PatchMapping.class.getName(), RequestMapping.class.getName());

        JavaProjectBuilder builder = new JavaProjectBuilder();
        for (String filePath : filePaths) {
            builder.addSourceTree(new File(filePath));
        }

        Collection<JavaClass> classes = builder.getClasses();
        for (JavaClass javaClass : classes) {
            Optional<JavaAnnotation> requestMappingOptional = javaClass.getAnnotations().stream()
                    .filter(annotation -> annotation.getType().getFullyQualifiedName().equals(RequestMapping.class.getName())).findFirst();

            boolean hasEndpoint = javaClass.getMethods().stream().flatMap(method -> method.getAnnotations().stream())
                    .anyMatch(annotation -> httpMethodClasses.contains(annotation.getType().getFullyQualifiedName()));

            if (hasEndpoint) {
                System.out.println("==================================================");
                System.out.println("Class: " + javaClass.getFullyQualifiedName());
                requestMappingOptional.ifPresent(annotation -> System.out.println("Class Request Mapping: " + annotation.getProperty("value")));
                System.out.println("==================================================");
            }

            for (JavaMethod method : javaClass.getMethods()) {
                for (JavaAnnotation annotation : method.getAnnotations()) {
                    if (httpMethodClasses.contains(annotation.getType().getFullyQualifiedName())) {
                        System.out.println("Endpoint: " + method.getName());
                        System.out.println(
                                annotation.getType().getFullyQualifiedName().equals(RequestMapping.class.getName()) ? "RequestMapping·method: " + annotation.getProperty("method")
                                        : "HTTP method annotation: " + annotation.getType().getName());
                        System.out.println("Path: " + annotation.getProperty("value"));
                        System.out.println("Line: " + method.getLineNumber());
                        List<String> annotations = method.getAnnotations().stream().filter(a -> !a.equals(annotation)).map(a -> a.getType().getName()).toList();
                        System.out.println("Other annotations: " + annotations);
                        System.out.println("---------------------------------------------------");
                    }
                }
            }
        }
    }
}
