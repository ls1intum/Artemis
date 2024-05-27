package analysisOfEndpointConnections;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;
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
        String[] serverFiles = Arrays.stream(args).filter(filePath -> new File(filePath).exists() && filePath.endsWith(".java")).toArray(String[]::new);
        parseServerEndpoints(serverFiles);
        analyzeEndpoints();
        analyzeRestCalls();
    }

    private static void parseServerEndpoints(String[] filePaths) {
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

            List<EndpointInformation> endpoints = new ArrayList<>();
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
                        EndpointInformation endpointInformation = new EndpointInformation(requestMappingOptional.get().getProperty("value").toString(), method.getName(), annotation.getType().getName(), annotation.getProperty("value").toString(), javaClass.getFullyQualifiedName(), method.getLineNumber(), javaAnnotations);
                        endpoints.add(endpointInformation);
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

    private static void analyzeEndpoints() {
        ObjectMapper mapper = new ObjectMapper();

        try {
            List<EndpointClassInformation> endpointClasses = mapper.readValue(new File("supporting_scripts/analysis-of-endpoint-connections/endpoints.json"), new TypeReference<List<EndpointClassInformation>>(){});
            List<RestCallInformation> restCalls = mapper.readValue(new File("supporting_scripts/analysis-of-endpoint-connections/restCalls.json"), new TypeReference<List<RestCallInformation>>(){});

            for (EndpointClassInformation endpointClass : endpointClasses) {
                for (EndpointInformation endpoint : endpointClass.getEndpoints()) {
                    boolean matchingRestCallFound = false;
                    System.out.println("=============================================");
                    System.out.println("Endpoint URI: " + endpoint.buildCompleteEndpointURI());
                    System.out.println("HTTP method: " + endpoint.getHttpMethodAnnotation());
                    System.out.println("File path: " + endpointClass.getFilePath());
                    System.out.println("Line: " + endpoint.getLine());
                    System.out.println("=============================================");
                    for (RestCallInformation restCall : restCalls) {
                        String endpointURI = endpoint.buildComparableEndpointUri();
                        String restCallURI = restCall.buildComparableRestCallUri();
                        if (endpointURI.equals(restCallURI) && endpoint.getHttpMethod().equals(restCall.getMethod())) {
                            matchingRestCallFound = true;
                            System.out.println("Matching REST call found.\nURI: " + endpoint.getURI() + "\nHTTP method: " + restCall.getMethod());
                            System.out.println("---------------------------------------------");
                        }
                    }
                    if (!matchingRestCallFound) {
                        System.out.println("No matching REST call found for endpoint: " + endpoint.buildCompleteEndpointURI());
                        System.out.println("---------------------------------------------");
                    }
                    System.out.println();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void analyzeRestCalls() {

    }

}
