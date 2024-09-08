package de.tum.cit.endpointanalysis;

import static de.tum.cit.endpointanalysis.EndpointParser.readConfig;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class EndpointAnalyzer {

    private static final Config CONFIG = readConfig();

    private static final Logger log = LoggerFactory.getLogger(EndpointAnalyzer.class);

    public static void main(String[] args) {
        analyzeEndpoints();
        printEndpointAnalysisResult();
    }

    /**
     * Analyzes server side endpoints and matches them with client side REST calls.
     *
     * This method reads endpoint and REST call information from JSON files,
     * compares them to find matching REST calls for each endpoint, and writes
     * the analysis result to a JSON file. Endpoints without matching REST calls
     * are also recorded.
     */
    private static void analyzeEndpoints() {
        ObjectMapper mapper = new ObjectMapper();

        try {
            List<EndpointClassInformation> endpointClasses = mapper.readValue(new File(CONFIG.endpointParsingResultPath()), new TypeReference<List<EndpointClassInformation>>() {
            });
            List<RestCallFileInformation> restCallFiles = mapper.readValue(new File(CONFIG.restCallParsingResultPath()), new TypeReference<List<RestCallFileInformation>>() {
            });

            excludeExcludedEndpoints(endpointClasses);

            List<UsedEndpoints> endpointsAndMatchingRestCalls = new ArrayList<>();
            List<EndpointInformation> unusedEndpoints = new ArrayList<>();

            Map<String, List<RestCallInformation>> restCallMap = new HashMap<>();

            // Populate the map with rest calls
            for (RestCallFileInformation restCallFile : restCallFiles) {
                for (RestCallInformation restCall : restCallFile.restCalls()) {
                    String restCallURI = restCall.buildComparableRestCallUri();
                    restCallMap.computeIfAbsent(restCallURI, uri -> new ArrayList<>()).add(restCall);
                }
            }

            for (EndpointClassInformation endpointClass : endpointClasses) {
                for (EndpointInformation endpoint : endpointClass.endpoints()) {

                    String endpointURI = endpoint.buildComparableEndpointUri();
                    List<RestCallInformation> restCallsWithMatchingURI = restCallMap.getOrDefault(endpointURI, new ArrayList<>());

                    // Check for wildcard endpoints if no exact match is found
                    checkForWildcardEndpoints(endpoint, restCallsWithMatchingURI, endpointURI, restCallMap);

                    List<RestCallInformation> matchingRestCalls = restCallsWithMatchingURI.stream()
                            .filter(restCall -> restCall.method().toLowerCase().equals(endpoint.getHttpMethod().toLowerCase())).toList();

                    if (matchingRestCalls.isEmpty()) {
                        unusedEndpoints.add(endpoint);
                    }
                    else {
                        endpointsAndMatchingRestCalls.add(new UsedEndpoints(endpoint, matchingRestCalls, endpointClass.filePath()));
                    }
                }
            }

            EndpointAnalysis endpointAnalysis = new EndpointAnalysis(endpointsAndMatchingRestCalls, unusedEndpoints);
            mapper.writeValue(new File(CONFIG.endpointAnalysisResultPath()), endpointAnalysis);
        }
        catch (IOException e) {
            log.error("Failed to analyze endpoints", e);
        }
    }

    /**
     * Checks for wildcard endpoints and adds matching REST calls to the list.
     *
     * This method is used to find matching REST calls for endpoints that use wildcard URIs.
     * If no exact match is found for an endpoint, it checks if the endpoint URI ends with a wildcard ('*').
     * It then iterates through the rest call map to find URIs that start with the same prefix as the endpoint URI
     * (excluding the wildcard) and have the same HTTP method. If such URIs are found, they are added to the list of matching REST calls.
     *
     * @param endpoint          The endpoint information to check for wildcard matches.
     * @param matchingRestCalls The list of matching REST calls to be populated.
     * @param endpointURI       The URI of the endpoint being checked.
     * @param restCallMap       The map of rest call URIs to their corresponding information.
     */
    private static void checkForWildcardEndpoints(EndpointInformation endpoint, List<RestCallInformation> matchingRestCalls, String endpointURI,
            Map<String, List<RestCallInformation>> restCallMap) {
        if (matchingRestCalls.isEmpty() && endpointURI.endsWith("*")) {
            for (String uri : restCallMap.keySet()) {
                if (uri.startsWith(endpoint.buildComparableEndpointUri().substring(0, endpoint.buildComparableEndpointUri().length() - 1))
                        && endpoint.getHttpMethod().toLowerCase().equals(restCallMap.get(uri).get(0).method().toLowerCase())) {
                    matchingRestCalls.addAll(restCallMap.get(uri));
                }
            }
        }
    }

    /**
     * Excludes endpoints that are specified in the configuration from the provided EndpointClassInformation list.
     *
     * This method performs two main tasks:
     * 1. Removes entire files that are excluded from the analysis based on the file paths specified in the configuration.
     * 2. Removes individual endpoints that are excluded from the analysis based on the endpoint URIs specified in the configuration.
     *
     * @param endpoints The list of EndpointClassInformation objects to be filtered.
     */
    private static void excludeExcludedEndpoints(List<EndpointClassInformation> endpoints) {
        // remove entire files that are excluded from the analysis
        CONFIG.excludedEndpointFiles().forEach(excludedEndpointFile -> {
            endpoints.removeIf(endpointFile -> CONFIG.excludedEndpointFiles().contains(endpointFile.filePath()));
        });

        // remove entire files that are excluded from the analysis by URI
        for (EndpointClassInformation endpointFile : endpoints) {
            endpointFile.endpoints().removeIf(endpoint -> CONFIG.excludedEndpoints().contains(endpoint.buildCompleteEndpointURI()));
        }
    }

    /**
     * Prints the endpoint analysis result.
     *
     * This method reads the endpoint analysis result from a JSON file and prints
     * the details of unused endpoints to the console. The details include the
     * endpoint URI, HTTP method, file path, and line number. If no matching REST
     * call is found for an endpoint, it prints a message indicating this.
     */
    private static void printEndpointAnalysisResult() {
        ObjectMapper mapper = new ObjectMapper();
        EndpointAnalysis endpointsAndMatchingRestCalls = null;
        try {
            endpointsAndMatchingRestCalls = mapper.readValue(new File(CONFIG.endpointAnalysisResultPath()), new TypeReference<EndpointAnalysis>() {
            });
        }
        catch (IOException e) {
            log.error("Failed to deserialize endpoint analysis result", e);
            return;
        }

        endpointsAndMatchingRestCalls.unusedEndpoints().stream().forEach(endpoint -> {
            log.info("=============================================");
            log.info("Endpoint URI: {}", endpoint.buildCompleteEndpointURI());
            log.info("HTTP method: {}", endpoint.httpMethodAnnotation());
            log.info("File path: {}", endpoint.className());
            log.info("Line: {}", endpoint.line());
            log.info("=============================================");
            log.info("No matching REST call found for endpoint: {}", endpoint.buildCompleteEndpointURI());
            log.info("---------------------------------------------");
            log.info("");
        });

        log.info("Number of endpoints without matching REST calls: {}", endpointsAndMatchingRestCalls.unusedEndpoints().size());

        if (!endpointsAndMatchingRestCalls.unusedEndpoints().isEmpty()) {
            System.exit(1);
        }
    }
}
