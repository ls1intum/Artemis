package analysisOfEndpointConnections;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
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
        String[] files = {"src/main/java/de/tum/in/www1/artemis/web/rest/tutorialgroups/TutorialGroupFreePeriodResource.java",
            "src/main/java/de/tum/in/www1/artemis/web/rest/tutorialgroups/TutorialGroupResource.java"};

        String[] serverFiles = Arrays.stream(files).filter(filePath -> new File(filePath).exists() && filePath.endsWith(".java")).toArray(String[]::new);
        analyzeServerEndpoints(serverFiles);
    }

    private static void analyzeServerEndpoints(String[] filePaths) {
        List<EndpointClassInformation> endpointClasses = new ArrayList<>();
        final List<String> httpMethodFullNames = List.of("org.springframework.web.bind.annotation.GetMapping", "org.springframework.web.bind.annotation.PostMapping",
                "org.springframework.web.bind.annotation.PutMapping", "org.springframework.web.bind.annotation.DeleteMapping",
                "org.springframework.web.bind.annotation.PatchMapping");
        final String requestMappingFullName = "org.springframework.web.bind.annotation.RequestMapping";

        JavaProjectBuilder builder = new JavaProjectBuilder();
        for (String filePath : filePaths) {
            builder.addSourceTree(new File(filePath));
        }

        Collection<JavaClass> classes = builder.getClasses();
        for (JavaClass javaClass : classes) {

            List<EnpointInformation> endpoints = new ArrayList<>();
            Optional<JavaAnnotation> requestMappingOptional = javaClass.getAnnotations().stream()
                    .filter(annotation -> annotation.getType().getFullyQualifiedName().equals(requestMappingFullName)).findFirst();

            for (JavaMethod method : javaClass.getMethods()) {
                for (JavaAnnotation annotation : method.getAnnotations()) {
                    if (httpMethodFullNames.contains(annotation.getType().getFullyQualifiedName())) {
                        if (requestMappingOptional.isPresent()) {
                            System.out.println("Request Mapping: " + requestMappingOptional.get().getProperty("value"));
                        }
                        ;
                        System.out.println("Endpoint: " + method.getName());
                        System.out.println("HTTP method annotation: " + annotation.getType().getName());
                        System.out.println("Path: " + annotation.getProperty("value"));
                        System.out.println("Class: " + javaClass.getFullyQualifiedName());
                        System.out.println("Line: " + method.getLineNumber());
                        List<String> annotations = method.getAnnotations().stream().filter(a -> !a.equals(annotation)).map(a -> a.getType().getName()).toList();
                        System.out.println("Other annotations: " + annotations);
                        System.out.println("---------------------------------------------------");

                        List<String> javaAnnotations = method.getAnnotations().stream().filter(a -> !a.equals(annotation)).map(a -> a.getType().getValue()).toList();
                        EnpointInformation enpointInformation = new EnpointInformation(requestMappingOptional.get().getProperty("value").toString(), method.getName(), annotation.getType().getName(), annotation.getProperty("value").toString(), javaClass.getFullyQualifiedName(), method.getLineNumber(), javaAnnotations);
                        endpoints.add(enpointInformation);
                    }
                }
            }
            if (endpoints.isEmpty()) {
                continue;
            }
            endpointClasses.add(new EndpointClassInformation(javaClass.getFullyQualifiedName(), requestMappingOptional.isPresent()? requestMappingOptional.get().getProperty("value").toString() : "", endpoints));
        }

        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.writeValue(new File("supporting_scripts/analysis-of-endpoint-connections/endpoints.json"), endpointClasses);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
