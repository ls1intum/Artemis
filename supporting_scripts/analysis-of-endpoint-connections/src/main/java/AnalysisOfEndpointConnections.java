import java.io.File;
import java.util.Arrays;
import java.util.Collection;
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
        JavaProjectBuilder builder = new JavaProjectBuilder();
        for (String filePath : filePaths) {
            builder.addSourceTree(new File(filePath));
        }

        Collection<JavaClass> classes = builder.getClasses();
        for (JavaClass javaClass : classes) {
            Optional<JavaAnnotation> requestMappingOptional = javaClass.getAnnotations().stream()
                .filter(annotation ->
                    annotation.getType().getFullyQualifiedName().startsWith("org.springframework.web.bind.annotation")
                    && annotation.getType().getName().equals("RequestMapping"))
                .findFirst();
            for (JavaMethod method : javaClass.getMethods()) {
                for (JavaAnnotation annotation : method.getAnnotations()) {
                    if (annotation.getType().getFullyQualifiedName().startsWith("org.springframework.web.bind.annotation")) {
                        if (requestMappingOptional.isPresent()) {
                            System.out.println("Request Mapping: " + requestMappingOptional.get().getProperty("value"));
                        };
                        System.out.println("Endpoint: " + method.getName());
                        System.out.println("HTTP Method: " + annotation.getType().getName());
                        System.out.println("Path: " + annotation.getProperty("value"));
                        System.out.println("Class: " + javaClass.getFullyQualifiedName());
                        System.out.println("Line: " + method.getLineNumber());
                        var annotations = method.getAnnotations().stream()
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
