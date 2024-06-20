package de.tum.cit.endpointanalysis;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.ArrayList;
import java.util.stream.Stream;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;

import com.github.javaparser.ParserConfiguration;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

public class AnalysisOfEndpointConnections {

    /**
     * This is the entry point of the analysis of server sided endpoints.
     *
     * @param args List of files that should be analyzed regarding endpoints.
     */
    public static void main(String[] args) {
        final String directoryPath = "src/main/java/de/tum/in/www1/artemis";

        StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);

        String[] filesToParse = {};
        try (Stream<Path> paths = Files.walk(Paths.get(directoryPath))) {
            filesToParse = paths.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .map(Path::toString)
                .toArray(String[]::new);
        } catch (IOException e) {

        }

        parseServerEndpoints(filesToParse);
        analyzeEndpoints();
        printEndpointAnalysisResult();
//        analyzeRestCalls();
    }

    private static void parseServerEndpoints(String[] filePaths) {
        List<EndpointClassInformation> endpointClasses = new ArrayList<>();

        final Set<String> httpMethodClasses = Set.of(GetMapping.class.getSimpleName(), PostMapping.class.getSimpleName(), PutMapping.class.getSimpleName(), DeleteMapping.class.getSimpleName(),
            PatchMapping.class.getSimpleName(), RequestMapping.class.getSimpleName());

        List<String> filesFailedToParse = new ArrayList<>();

        for (String filePath : filePaths) {
            try {
                CompilationUnit compilationUnit = StaticJavaParser.parse(new File(filePath));
                List<ClassOrInterfaceDeclaration> classes = compilationUnit.findAll(ClassOrInterfaceDeclaration.class);
                for (ClassOrInterfaceDeclaration javaClass : classes) {
                    List<EndpointInformation> endpoints = new ArrayList<>();
                    final String[] classRequestMapping = {""};
                    Optional<AnnotationExpr> requestMappingOptional = javaClass.getAnnotations().stream()
                        .filter(annotation -> annotation.getNameAsString().equals(RequestMapping.class.getSimpleName())).findFirst();

                    boolean hasEndpoint = javaClass.getMethods().stream().flatMap(method -> method.getAnnotations().stream())
                        .anyMatch(annotation -> httpMethodClasses.contains(annotation.getNameAsString()));

                    if (hasEndpoint) {
                        requestMappingOptional.ifPresent(annotation -> {
                            if (annotation instanceof SingleMemberAnnotationExpr) {
                                SingleMemberAnnotationExpr single = (SingleMemberAnnotationExpr) annotation;
                                classRequestMapping[0] = single.getMemberValue().toString();
                            } else if (annotation instanceof NormalAnnotationExpr) {
                                NormalAnnotationExpr normal = (NormalAnnotationExpr) annotation;
                                Optional<MemberValuePair> pathOptional = normal.getPairs().stream().filter(pair -> "path".equals(pair.getNameAsString())).findFirst();
                                pathOptional.ifPresent(pair -> classRequestMapping[0] = pair.getValue().toString());
                            }
                        });
                    }

                    for (MethodDeclaration method : javaClass.getMethods()) {
                        for (AnnotationExpr annotation : method.getAnnotations()) {
                            if (httpMethodClasses.contains(annotation.getNameAsString())) {
                                final String[] annotationPathValue = {""};

                                if (annotation instanceof SingleMemberAnnotationExpr) {
                                    SingleMemberAnnotationExpr single = (SingleMemberAnnotationExpr) annotation;
                                    annotationPathValue[0] = single.getMemberValue().toString();
                                } else if (annotation instanceof NormalAnnotationExpr) {
                                    NormalAnnotationExpr normal = (NormalAnnotationExpr) annotation;
                                    Optional<MemberValuePair> annotationPathOptional = normal.getPairs().stream().filter(pair -> "path".equals(pair.getNameAsString())).findFirst();
                                    annotationPathOptional.ifPresent(pair -> {
                                        annotationPathValue[0] = pair.getValue().toString();
                                    });
                                }

                                List<String> annotations = method.getAnnotations().stream().filter(a -> !a.equals(annotation)).map(a -> a.toString()).toList();
                                EndpointInformation endpointInformation = new EndpointInformation(classRequestMapping[0], method.getNameAsString(),
                                    annotation.getNameAsString(), annotationPathValue[0], javaClass.getNameAsString(), method.getBegin().get().line,
                                    annotations);
                                endpoints.add(endpointInformation);
                            }
                        }
                    }
                    if (!endpoints.isEmpty()) {
                        endpointClasses.add(new EndpointClassInformation(javaClass.getNameAsString(),
                            requestMappingOptional.isPresent() ? requestMappingOptional.get().toString() : "", endpoints));
                    }
                }
            } catch (Exception e) {
                filesFailedToParse.add(filePath);
            }
        }

        System.out.println("Files failed to Parse:");
        for (String file : filesFailedToParse) {
            System.out.println(file);
        }

        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.writeValue(new File("supporting_scripts/analysis-of-endpoint-connections/endpoints.json"), endpointClasses);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void analyzeEndpoints() {
        ObjectMapper mapper = new ObjectMapper();

        try {
            List<EndpointClassInformation> endpointClasses = mapper.readValue(new File("supporting_scripts/analysis-of-endpoint-connections/endpoints.json"),
                new TypeReference<List<EndpointClassInformation>>() {
                });
            List<RestCallFileInformation> restCallFiles = mapper.readValue(new File("supporting_scripts/analysis-of-endpoint-connections/restCalls.json"),
                new TypeReference<List<RestCallFileInformation>>() {
                });

            List<UsedEndpoints> endpointsAndMatchingRestCalls = new ArrayList<>();
            List<EndpointInformation> unusedEndpoints = new ArrayList<>();

            for (EndpointClassInformation endpointClass : endpointClasses) {
                for (EndpointInformation endpoint : endpointClass.getEndpoints()) {
                    List<RestCallInformation> matchingRestCalls = new ArrayList<>();

                    for (RestCallFileInformation restCallFile : restCallFiles) {
                        for (RestCallInformation restCall : restCallFile.getRestCalls()) {
                            String endpointURI = endpoint.buildComparableEndpointUri();
                            String restCallURI = restCall.buildComparableRestCallUri();
                            if (endpointURI.equals(restCallURI) && endpoint.getHttpMethod().equals(restCall.getMethod())) {
//                                System.out.println("Matching REST call found.\nURI: " + endpoint.getURI() + "\nHTTP method: " + restCall.getMethod());
//                                System.out.println("---------------------------------------------");
                                matchingRestCalls.add(restCall);
                            }
                        }
                    }

                    if (matchingRestCalls.isEmpty()) {
                        unusedEndpoints.add(endpoint);
                    } else {
                        endpointsAndMatchingRestCalls.add(new UsedEndpoints(endpoint, matchingRestCalls, endpointClass.getFilePath()));
                    }
                }
            }

            EndpointAnalysis endpointAnalysis = new EndpointAnalysis(endpointsAndMatchingRestCalls, unusedEndpoints);
            mapper.writeValue(new File("supporting_scripts/analysis-of-endpoint-connections/endpointsAndMatchingRestCalls.json"), endpointAnalysis);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void printEndpointAnalysisResult() {
        ObjectMapper mapper = new ObjectMapper();
        EndpointAnalysis endpointsAndMatchingRestCalls = null;
        try {
            endpointsAndMatchingRestCalls = mapper.readValue(new File("supporting_scripts/analysis-of-endpoint-connections/endpointsAndMatchingRestCalls.json"),
                new TypeReference<EndpointAnalysis>() {
                });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        endpointsAndMatchingRestCalls.getUnusedEndpoints().stream().forEach(endpoint -> {
            System.out.println("=============================================");
            System.out.println("Endpoint URI: " + endpoint.buildCompleteEndpointURI());
            System.out.println("HTTP method: " + endpoint.getHttpMethodAnnotation());
            System.out.println("File path: " + endpoint.getClassName());
            System.out.println("Line: " + endpoint.getLine());
            System.out.println("=============================================");
            System.out.println("No matching REST call found for endpoint: " + endpoint.buildCompleteEndpointURI());
            System.out.println("---------------------------------------------");
            System.out.println();
        });
    }

    private static void analyzeRestCalls() {
        ObjectMapper mapper = new ObjectMapper();

        try {
            List<EndpointClassInformation> endpointClasses = mapper.readValue(new File("supporting_scripts/analysis-of-endpoint-connections/endpoints.json"),
                new TypeReference<List<EndpointClassInformation>>() {
                });
            List<RestCallInformation> restCalls = mapper.readValue(new File("supporting_scripts/analysis-of-endpoint-connections/restCalls.json"),
                new TypeReference<List<RestCallInformation>>() {
                });

            for (RestCallInformation restCall : restCalls) {
                boolean matchingEndpointFound = false;
                System.out.println("=============================================");
                System.out.println("REST call URI: " + restCall.buildCompleteRestCallURI());
                System.out.println("HTTP method: " + restCall.getMethod());
                System.out.println("File path: " + restCall.getFilePath());
                System.out.println("Line: " + restCall.getLine());
                System.out.println("=============================================");
                for (EndpointClassInformation endpointClass : endpointClasses) {
                    for (EndpointInformation endpoint : endpointClass.getEndpoints()) {
                        String endpointURI = endpoint.buildComparableEndpointUri();
                        String restCallURI = restCall.buildComparableRestCallUri();
                        if (endpointURI.equals(restCallURI) && endpoint.getHttpMethod().equals(restCall.getMethod())) {
                            matchingEndpointFound = true;
                            System.out.println("Matching endpoint found.\nURI: " + endpoint.buildCompleteEndpointURI() + "\nHTTP method: " + endpoint.getHttpMethodAnnotation());
                            System.out.println("---------------------------------------------");
                        }
                    }
                }
                if (!matchingEndpointFound) {
                    System.out.println("No matching endpoint found for REST call: " + restCall.buildCompleteRestCallURI());
                    System.out.println("---------------------------------------------");
                }
                System.out.println();
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
}
