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

public class RestCallAnalyzer {

    private static final Config CONFIG = readConfig();

    private static final Logger log = LoggerFactory.getLogger(RestCallAnalyzer.class);

    public static void main(String[] args) {
        analyzeRestCalls();
        printRestCallAnalysisResult();
    }

    /**
     * The RestCallAnalyzer analyzes the client REST Calls and focuses on them having a matching Endpoint on the server
     *
     * This method reads endpoint and REST call information from JSON files.
     * It then matches the REST calls with the endpoints they are calling and
     * writes the analysis result to a JSON file.
     * REST calls without matching endpoints are also recorded.
     */
    private static void analyzeRestCalls() {
        ObjectMapper mapper = new ObjectMapper();

        try {
            List<EndpointClassInformation> endpointClasses = mapper.readValue(new File(CONFIG.endpointParsingResultPath()), new TypeReference<List<EndpointClassInformation>>() {
            });
            List<RestCallFileInformation> restCalls = mapper.readValue(new File(CONFIG.restCallParsingResultPath()), new TypeReference<List<RestCallFileInformation>>() {
            });

            excludeExcludedRestCalls(restCalls);

            List<RestCallWithMatchingEndpoint> restCallsWithMatchingEndpoint = new ArrayList<>();
            List<RestCallInformation> restCallsWithoutMatchingEndpoint = new ArrayList<>();

            Map<String, List<EndpointInformation>> endpointMap = new HashMap<>();

            // Populate the map with endpoints
            for (EndpointClassInformation endpointClass : endpointClasses) {
                for (EndpointInformation endpoint : endpointClass.endpoints()) {
                    String endpointURI = endpoint.buildComparableEndpointUri();
                    endpointMap.computeIfAbsent(endpointURI, uri -> new ArrayList<>()).add(endpoint);
                }
            }

            for (RestCallFileInformation restCallFile : restCalls) {
                for (RestCallInformation restCall : restCallFile.restCalls()) {
                    String restCallURI = restCall.buildComparableRestCallUri();
                    List<EndpointInformation> endpointsWithMatchingUri = endpointMap.getOrDefault(restCallURI, new ArrayList<>());

                    checkForWildcardMatches(restCall, endpointsWithMatchingUri, restCallURI, endpointMap);

                    List<EndpointInformation> endpointsWithMatchingHttpMethod = endpointsWithMatchingUri.stream()
                            .filter(endpoint -> endpoint.getHttpMethod().toLowerCase().equals(restCall.method().toLowerCase())).toList();

                    if (endpointsWithMatchingHttpMethod.isEmpty()) {
                        restCallsWithoutMatchingEndpoint.add(restCall);
                    }
                    else {
                        for (EndpointInformation endpoint : endpointsWithMatchingHttpMethod) {
                            restCallsWithMatchingEndpoint.add(new RestCallWithMatchingEndpoint(endpoint, restCall, restCall.filePath()));
                        }
                    }
                }
            }

            RestCallAnalysis restCallAnalysis = new RestCallAnalysis(restCallsWithMatchingEndpoint, restCallsWithoutMatchingEndpoint);
            mapper.writeValue(new File(CONFIG.restCallAnalysisResultPath()), restCallAnalysis);
        }
        catch (IOException e) {
            log.error("Failed to analyze REST calls", e);
        }
    }

    /**
     * Checks for wildcard matches and adds matching endpoints to the list.
     *
     * This method is used to find matching endpoints for REST calls that use wildcard URIs.
     * If no exact match is found for a REST call, it checks if the REST call URI ends with a wildcard ('*').
     * It then iterates through the endpoint map to find URIs that start with the same prefix as the REST call URI
     * (excluding the wildcard) and have the same HTTP method. If such URIs are found, they are added to the list of matching endpoints.
     *
     * @param restCall          The REST call information to check for wildcard matches.
     * @param matchingEndpoints The list of matching endpoints to be populated.
     * @param restCallURI       The URI of the REST call being checked.
     * @param endpointMap       The map of endpoint URIs to their corresponding information.
     */
    private static void checkForWildcardMatches(RestCallInformation restCall, List<EndpointInformation> matchingEndpoints, String restCallURI,
            Map<String, List<EndpointInformation>> endpointMap) {
        if (matchingEndpoints.isEmpty() && restCallURI.endsWith("*")) {
            for (String uri : endpointMap.keySet()) {
                if (uri.startsWith(restCallURI.substring(0, restCallURI.length() - 1))
                        && endpointMap.get(uri).get(0).getHttpMethod().toLowerCase().equals(restCall.method().toLowerCase())) {
                    matchingEndpoints.addAll(endpointMap.get(uri));
                }
            }
        }
    }

    /**
     * Excludes REST calls that are specified in the configuration from the provided RestCallFileInformation list.
     *
     * This method performs two main tasks:
     * 1. Removes entire files that are excluded from the analysis based on the file paths specified in the configuration.
     * 2. Removes individual REST calls that are excluded from the analysis based on the REST call URIs specified in the configuration.
     *
     * @param restCalls The list of RestCallFileInformation objects to be filtered.
     */
    private static void excludeExcludedRestCalls(List<RestCallFileInformation> restCalls) {
        CONFIG.excludedRestCallFiles().forEach(excludedFile -> {
            restCalls.removeIf(restCallFile -> restCallFile.filePath().equals(excludedFile));
        });

        for (RestCallFileInformation restCallFile : restCalls) {
            restCallFile.restCalls().removeIf(restCall -> CONFIG.excludedRestCalls().contains(restCall.buildCompleteRestCallURI()));
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
    private static void printRestCallAnalysisResult() {
        ObjectMapper mapper = new ObjectMapper();

        RestCallAnalysis restCallsAndMatchingEndpoints = null;

        try {
            restCallsAndMatchingEndpoints = mapper.readValue(new File(CONFIG.restCallAnalysisResultPath()), new TypeReference<RestCallAnalysis>() {
            });
        }
        catch (IOException e) {
            log.error("Failed to deserialize rest call analysis results", e);
            return;
        }

        restCallsAndMatchingEndpoints.restCallsWithoutMatchingEndpoints().stream().forEach(endpoint -> {
            log.info("=============================================");
            log.info("REST call URI: {}", endpoint.buildCompleteRestCallURI());
            log.info("HTTP method: {}", endpoint.method());
            log.info("File path: {}", endpoint.filePath());
            log.info("Line: {}", endpoint.line());
            log.info("=============================================");
            log.info("No matching endpoint found for REST call: {}", endpoint.buildCompleteRestCallURI());
            log.info("---------------------------------------------");
            log.info("");
        });

        log.info("Number of REST calls without matching endpoints: {}", restCallsAndMatchingEndpoints.restCallsWithoutMatchingEndpoints().size());

        if (!restCallsAndMatchingEndpoints.restCallsWithMatchingEndpoints().isEmpty()) {
            System.exit(1);
        }
    }
}
