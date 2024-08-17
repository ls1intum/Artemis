package de.tum.cit.endpointanalysis;

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

    private static final String REST_CALL_ANALYSIS_RESULT_PATH = "restCallAnalysisResult.json";

    private static final Logger logger = LoggerFactory.getLogger(RestCallAnalyzer.class);

    public static void main(String[] args) {
        analyzeRestCalls();
        printRestCallAnalysisResult();
    }

    /**
     * Analyzes REST calls and matches them with endpoints.
     *
     * This method reads endpoint and REST call information from JSON files,
     * compares them to find matching endpoints for each REST call, and writes
     * the analysis result to a JSON file. REST calls without matching endpoints
     * are also recorded.
     */
    private static void analyzeRestCalls() {
        ObjectMapper mapper = new ObjectMapper();

        try {
            List<EndpointClassInformation> endpointClasses = mapper.readValue(new File(EndpointParser.ENDPOINT_PARSING_RESULT_PATH),
                    new TypeReference<List<EndpointClassInformation>>() {
                    });
            List<RestCallFileInformation> restCalls = mapper.readValue(new File(EndpointParser.REST_CALL_PARSING_RESULT_PATH), new TypeReference<List<RestCallFileInformation>>() {
            });

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
                    List<EndpointInformation> matchingEndpoints = endpointMap.getOrDefault(restCallURI, new ArrayList<>());

                    checkForWildcardMatches(restCall, matchingEndpoints, restCallURI, endpointMap);

                    if (matchingEndpoints.isEmpty()) {
                        restCallsWithoutMatchingEndpoint.add(restCall);
                    }
                    else {
                        for (EndpointInformation endpoint : matchingEndpoints) {
                            restCallsWithMatchingEndpoint.add(new RestCallWithMatchingEndpoint(endpoint, restCall, restCall.fileName()));
                        }
                    }
                }
            }

            RestCallAnalysis restCallAnalysis = new RestCallAnalysis(restCallsWithMatchingEndpoint, restCallsWithoutMatchingEndpoint);
            mapper.writeValue(new File(REST_CALL_ANALYSIS_RESULT_PATH), restCallAnalysis);
        }
        catch (IOException e) {
            logger.error("Failed to analyze REST calls", e);
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
            restCallsAndMatchingEndpoints = mapper.readValue(new File(REST_CALL_ANALYSIS_RESULT_PATH), new TypeReference<RestCallAnalysis>() {
            });
        }
        catch (IOException e) {
            logger.error("Failed to deserialize rest call analysis results", e);
        }

        restCallsAndMatchingEndpoints.restCallsWithoutMatchingEndpoints().stream().forEach(endpoint -> {
            logger.info("=============================================");
            logger.info("REST call URI: {}", endpoint.buildCompleteRestCallURI());
            logger.info("HTTP method: {}", endpoint.method());
            logger.info("File path: {}", endpoint.fileName());
            logger.info("Line: {}", endpoint.line());
            logger.info("=============================================");
            logger.info("No matching endpoint found for REST call: {}", endpoint.buildCompleteRestCallURI());
            logger.info("---------------------------------------------");
            logger.info("");
        });

        logger.info("Number of REST calls without matching endpoints: {}", restCallsAndMatchingEndpoints.restCallsWithoutMatchingEndpoints().size());
    }
}
