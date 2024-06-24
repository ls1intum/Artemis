package de.tum.in.www1.artemis;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;

public class EndpointParser {

    public static void main(String[] args) {
        final String directoryPath = "../../../src/main/java/de/tum/in/www1/artemis";

        StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);

        String[] filesToParse = {};
        try (Stream<Path> paths = Files.walk(Paths.get(directoryPath))) {
            filesToParse = paths.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".java")).map(Path::toString).toArray(String[]::new);
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        parseServerEndpoints(filesToParse);
    }

    private static void parseServerEndpoints(String[] filePaths) {
        List<EndpointClassInformation> endpointClasses = new ArrayList<>();

        final Set<String> httpMethodClasses = Set.of(GetMapping.class.getSimpleName(), PostMapping.class.getSimpleName(), PutMapping.class.getSimpleName(),
                DeleteMapping.class.getSimpleName(), PatchMapping.class.getSimpleName(), RequestMapping.class.getSimpleName());

        List<String> filesFailedToParse = new ArrayList<>();

        for (String filePath : filePaths) {
            try {
                CompilationUnit compilationUnit = StaticJavaParser.parse(new File(filePath));
                List<ClassOrInterfaceDeclaration> classes = compilationUnit.findAll(ClassOrInterfaceDeclaration.class);
                for (ClassOrInterfaceDeclaration javaClass : classes) {
                    List<EndpointInformation> endpoints = new ArrayList<>();
                    final String[] classRequestMapping = { "" };
                    Optional<AnnotationExpr> requestMappingOptional = javaClass.getAnnotations().stream()
                            .filter(annotation -> annotation.getNameAsString().equals(RequestMapping.class.getSimpleName())).findFirst();

                    boolean hasEndpoint = javaClass.getMethods().stream().flatMap(method -> method.getAnnotations().stream())
                            .anyMatch(annotation -> httpMethodClasses.contains(annotation.getNameAsString()));

                    if (hasEndpoint) {
                        requestMappingOptional.ifPresent(annotation -> {
                            if (annotation instanceof SingleMemberAnnotationExpr) {
                                SingleMemberAnnotationExpr single = (SingleMemberAnnotationExpr) annotation;
                                classRequestMapping[0] = single.getMemberValue().toString();
                            }
                            else if (annotation instanceof NormalAnnotationExpr) {
                                NormalAnnotationExpr normal = (NormalAnnotationExpr) annotation;
                                Optional<MemberValuePair> pathOptional = normal.getPairs().stream().filter(pair -> "path".equals(pair.getNameAsString())).findFirst();
                                pathOptional.ifPresent(pair -> classRequestMapping[0] = pair.getValue().toString());
                            }
                        });
                    }

                    for (MethodDeclaration method : javaClass.getMethods()) {
                        for (AnnotationExpr annotation : method.getAnnotations()) {
                            if (httpMethodClasses.contains(annotation.getNameAsString())) {
                                final String[] annotationPathValue = { "" };

                                if (annotation instanceof SingleMemberAnnotationExpr) {
                                    SingleMemberAnnotationExpr single = (SingleMemberAnnotationExpr) annotation;
                                    annotationPathValue[0] = single.getMemberValue().toString();
                                }
                                else if (annotation instanceof NormalAnnotationExpr) {
                                    NormalAnnotationExpr normal = (NormalAnnotationExpr) annotation;
                                    Optional<MemberValuePair> annotationPathOptional = normal.getPairs().stream().filter(pair -> "path".equals(pair.getNameAsString())).findFirst();
                                    annotationPathOptional.ifPresent(pair -> {
                                        annotationPathValue[0] = pair.getValue().toString();
                                    });
                                }

                                List<String> annotations = method.getAnnotations().stream().filter(a -> !a.equals(annotation)).map(a -> a.toString()).toList();
                                EndpointInformation endpointInformation = new EndpointInformation(classRequestMapping[0], method.getNameAsString(), annotation.getNameAsString(),
                                        annotationPathValue[0], javaClass.getNameAsString(), method.getBegin().get().line, annotations);
                                endpoints.add(endpointInformation);
                            }
                        }
                    }
                    if (!endpoints.isEmpty()) {
                        endpointClasses.add(new EndpointClassInformation(javaClass.getNameAsString(),
                                requestMappingOptional.isPresent() ? requestMappingOptional.get().toString() : "", endpoints));
                    }
                }
            }
            catch (Exception e) {
                filesFailedToParse.add(filePath);
            }
        }

        if (!filesFailedToParse.isEmpty()) {
            System.out.println("Files failed to Parse:");
            for (String file : filesFailedToParse) {
                System.out.println(file);
            }
        }

        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.writeValue(new File("../endpoints.json"), endpointClasses);
        }
        catch (IOException e) {
            e.printStackTrace();

        }
    }
}
