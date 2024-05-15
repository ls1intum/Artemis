package analysisOfEndpointConnections;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

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
        String[] serverFiles = Arrays.stream(args).filter(filePath -> new File(filePath).exists() && filePath.endsWith(".java")).toArray(String[]::new);
        analyzeServerEndpoints(serverFiles);
    }

    private static void analyzeServerEndpoints(String[] filePaths) {
        final String requestMappingFullName = "org.springframework.web.bind.annotation.RequestMapping";
        final List<String> httpMethodFullNames = List.of(
            "org.springframework.web.bind.annotation.GetMapping",
            "org.springframework.web.bind.annotation.PostMapping",
            "org.springframework.web.bind.annotation.PutMapping",
            "org.springframework.web.bind.annotation.DeleteMapping",
            "org.springframework.web.bind.annotation.PatchMapping",
            requestMappingFullName
        );

        JavaProjectBuilder builder = new JavaProjectBuilder();
        for (String filePath : filePaths) {
            builder.addSourceTree(new File(filePath));
        }

        Collection<JavaClass> classes = builder.getClasses();
        for (JavaClass javaClass : classes) {
            Optional<JavaAnnotation> requestMappingOptional = javaClass.getAnnotations().stream()
                .filter(annotation -> annotation.getType().getFullyQualifiedName().equals(requestMappingFullName))
                .findFirst();

            System.out.println("==================================================");
            System.out.println("Class: " + javaClass.getFullyQualifiedName());
            if (requestMappingOptional.isPresent()) {
                System.out.println("Class Request Mapping: " + requestMappingOptional.get().getProperty("value"));
            }
            System.out.println("==================================================");

            for (JavaMethod method : javaClass.getMethods()) {
                for (JavaAnnotation annotation : method.getAnnotations()) {
                    if (httpMethodFullNames.contains(annotation.getType().getFullyQualifiedName())) {
                        System.out.println("Endpoint: " + method.getName());
                        System.out.println(requestMappingFullName.equals(annotation.getType().getFullyQualifiedName()) ?
                            "RequestMapping method: " + annotation.getProperty("method"):
                            "HTTP method annotation: " + annotation.getType().getName());
                        System.out.println("Path: " + annotation.getProperty("value"));
                        System.out.println("Line: " + method.getLineNumber());
                        List<String> annotations = method.getAnnotations().stream()
                            .filter(a -> !a.equals(annotation))
                            .map(a -> a.getType().getName()).toList();
                        System.out.println("Other annotations: " + annotations);
                        System.out.println("---------------------------------------------------");
                    }
                }
            }
        }
    }
}
