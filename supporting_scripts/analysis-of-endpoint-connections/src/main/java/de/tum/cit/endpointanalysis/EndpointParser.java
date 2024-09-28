package de.tum.cit.endpointanalysis;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;

public class EndpointParser {

    private static final Logger log = LoggerFactory.getLogger(EndpointParser.class);

    private static final Config CONFIG = readConfig();

    public static void main(String[] args) {
        final Path absoluteDirectoryPath = Path.of("../../src/main/java").toAbsolutePath().normalize();

        StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);

        String[] filesToParse = {};
        try (Stream<Path> paths = Files.walk(absoluteDirectoryPath)) {
            filesToParse = paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java") && !CONFIG.excludedServerFiles().contains(path))
                    .map(Path::toString).toArray(String[]::new);
        }
        catch (IOException e) {
            log.error("Error reading files from directory: {}", absoluteDirectoryPath, e);
        }

        parseServerEndpoints(filesToParse);
    }

    static Config readConfig() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try {
            return mapper.readValue(new File("analysisOfEndpointConnections.config.yml"), Config.class);
        }
        catch (IOException e) {
            System.err.println("Failed to read config file: " + e.getMessage());
            return null;
        }
    }

    /**
     * Parses server endpoints from the given file paths.
     *
     * This method reads Java files from the specified file paths, extracts endpoint
     * information annotated with HTTP method annotations, and writes the parsed
     * endpoint information to a JSON file. It also logs any files that failed to parse.
     *
     * @param filePaths an array of file paths to parse for endpoint information
     */
    private static void parseServerEndpoints(String[] filePaths) {
        List<EndpointClassInformation> endpointClasses = new ArrayList<>();
        final Set<String> httpMethodClasses = Set.of(GetMapping.class.getSimpleName(), PostMapping.class.getSimpleName(), PutMapping.class.getSimpleName(),
                DeleteMapping.class.getSimpleName(), PatchMapping.class.getSimpleName(), RequestMapping.class.getSimpleName());
        List<String> filesFailedToParse = new ArrayList<>();

        for (String filePath : filePaths) {
            CompilationUnit compilationUnit;
            try {
                compilationUnit = StaticJavaParser.parse(new File(filePath));
            }
            catch (Exception e) {
                filesFailedToParse.add(filePath);
                continue;
            }

            List<ClassOrInterfaceDeclaration> classes = compilationUnit.findAll(ClassOrInterfaceDeclaration.class);
            for (ClassOrInterfaceDeclaration javaClass : classes) {
                List<EndpointInformation> endpoints = new ArrayList<>();
                final String classRequestMappingString = extractClassRequestMapping(javaClass, httpMethodClasses);

                endpoints.addAll(extractAnnotationPathValues(javaClass, httpMethodClasses, classRequestMappingString));

                if (!endpoints.isEmpty()) {
                    endpointClasses.add(new EndpointClassInformation(javaClass.getNameAsString(), classRequestMappingString, endpoints));
                }
            }
        }

        printFilesFailedToParse(filesFailedToParse);

        writeEndpointsToFile(endpointClasses);
    }

    /**
     * Extracts endpoint information from the methods of a given class declaration.
     *
     * This method iterates over the methods of the provided class and their annotations.
     * If an annotation matches one of the specified HTTP method annotations, it extracts
     * the path values from the annotation and creates EndpointInformation objects for each path.
     *
     * @param javaClass                 the class declaration to extract endpoint information from
     * @param httpMethodClasses         a set of HTTP method annotation class names
     * @param classRequestMappingString the class-level request mapping string
     * @return a list of EndpointInformation objects representing the extracted endpoint information
     */
    private static List<EndpointInformation> extractAnnotationPathValues(ClassOrInterfaceDeclaration javaClass, Set<String> httpMethodClasses, String classRequestMappingString) {
        return javaClass.getMethods().stream()
                .flatMap(method -> method.getAnnotations().stream().filter(annotation -> httpMethodClasses.contains(annotation.getNameAsString()))
                        .flatMap(annotation -> extractPathsFromAnnotation(annotation).stream()
                                .map(path -> new EndpointInformation(classRequestMappingString, method.getNameAsString(), annotation.getNameAsString(), path,
                                        javaClass.getNameAsString(), method.getBegin().get().line, method.getAnnotations().stream().map(AnnotationExpr::toString).toList()))))
                .toList();
    }

    /**
     * Extracts the paths from the given annotation.
     *
     * This method processes the provided annotation to extract path values.
     * It handles both single-member and normal annotations, extracting the
     * path values from the annotation's member values or pairs.
     *
     * @param annotation the annotation to extract paths from
     * @return a list of extracted path values
     */
    private static List<String> extractPathsFromAnnotation(AnnotationExpr annotation) {
        List<String> paths = new ArrayList<>();
        if (annotation instanceof SingleMemberAnnotationExpr singleMemberAnnotationExpr) {
            Expression memberValue = singleMemberAnnotationExpr.getMemberValue();
            if (memberValue instanceof ArrayInitializerExpr arrayInitializerExpr) {
                paths.addAll(arrayInitializerExpr.getValues().stream().map(Expression::toString).collect(Collectors.toList()));
            }
            else {
                paths.add(memberValue.toString());
            }
        }
        else if (annotation instanceof NormalAnnotationExpr normalAnnotationExpr) {
            normalAnnotationExpr.getPairs().stream().filter(pair -> "value".equals(pair.getNameAsString())).forEach(pair -> paths.add(pair.getValue().toString()));
        }
        return paths;
    }

    /**
     * Extracts the class-level request mapping from a given class declaration.
     *
     * This method scans the annotations of the provided class to find a `RequestMapping` annotation.
     * It then checks if the class contains any methods annotated with HTTP method annotations.
     * If such methods are found, it extracts the value of the `RequestMapping` annotation.
     *
     * @param javaClass         the class declaration to extract the request mapping from
     * @param httpMethodClasses a set of HTTP method annotation class names
     * @return the extracted request mapping value, or an empty string if no request mapping is found or the class has no HTTP method annotations
     */
    private static String extractClassRequestMapping(ClassOrInterfaceDeclaration javaClass, Set<String> httpMethodClasses) {
        boolean hasEndpoint = javaClass.getMethods().stream().flatMap(method -> method.getAnnotations().stream())
                .anyMatch(annotation -> httpMethodClasses.contains(annotation.getNameAsString()));

        if (!hasEndpoint) {
            return "";
        }

        String classRequestMapping = javaClass.getAnnotations().stream().filter(annotation -> annotation.getNameAsString().equals(RequestMapping.class.getSimpleName())).findFirst()
                .map(annotation -> {
                    if (annotation instanceof SingleMemberAnnotationExpr singleMemberAnnotationExpr) {
                        return singleMemberAnnotationExpr.getMemberValue().toString();
                    }
                    else if (annotation instanceof NormalAnnotationExpr normalAnnotationExpr) {
                        return normalAnnotationExpr.getPairs().stream().filter(pair -> "path".equals(pair.getNameAsString())).map(pair -> pair.getValue().toString()).findFirst()
                                .orElse("");
                    }
                    return "";
                }).orElse("");

        return classRequestMapping;
    }

    /**
     * Prints the list of files that failed to parse.
     *
     * This method checks if the provided list of file paths is not empty.
     * If it is not empty, it prints a message indicating that some files failed to parse,
     * followed by the paths of the files that failed.
     *
     * @param filesFailedToParse the list of file paths that failed to parse
     */
    private static void printFilesFailedToParse(List<String> filesFailedToParse) {
        if (!filesFailedToParse.isEmpty()) {
            log.warn("Files failed to parse:", filesFailedToParse);
            for (String file : filesFailedToParse) {
                log.warn(file);
            }
        }
    }

    /**
     * Writes the list of endpoint class information to a JSON file.
     *
     * This method uses the Jackson ObjectMapper to serialize the list of
     * EndpointClassInformation objects and write them to a file specified
     * by the EndpointParsingResultPath constant.
     *
     * @param endpointClasses the list of EndpointClassInformation objects to write to the file
     */
    private static void writeEndpointsToFile(List<EndpointClassInformation> endpointClasses) {
        try {
            new ObjectMapper().writeValue(new File(CONFIG.endpointParsingResultPath()), endpointClasses);
        }
        catch (IOException e) {
            log.error("Failed to write endpoint information to file", e);
        }
    }
}
