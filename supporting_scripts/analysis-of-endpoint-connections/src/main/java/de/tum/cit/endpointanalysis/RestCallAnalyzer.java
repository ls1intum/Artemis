package de.tum.cit.endpointanalysis;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

            for (RestCallFileInformation restCallFile : restCalls) {
                for (RestCallInformation restCall : restCallFile.getRestCalls()) {
                    Optional<EndpointInformation> matchingEndpoint = Optional.empty();

                    matchingEndpoint = findMatchingEndpoint(restCall, endpointClasses, matchingEndpoint);

                    if (matchingEndpoint.isPresent()) {
                        restCallsWithMatchingEndpoint.add(new RestCallWithMatchingEndpoint(matchingEndpoint.get(), restCall, restCall.getFileName()));
                    }
                    else {
                        restCallsWithoutMatchingEndpoint.add(restCall);
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
     * Finds a matching endpoint for a given REST call.
     *
     * This method iterates over a list of endpoint classes and their endpoints to find a match for the provided REST call.
     * A match is determined based on the URI and HTTP method of the endpoint and the REST call.
     *
     * @param restCall The REST call information to match.
     * @param endpointClasses The list of endpoint classes containing endpoint information.
     * @param matchingEndpoint An optional containing the matching endpoint if found.
     * @return An optional containing the matching endpoint if found, otherwise an empty optional.
     */
    private static Optional<EndpointInformation> findMatchingEndpoint(RestCallInformation restCall, List<EndpointClassInformation> endpointClasses, Optional<EndpointInformation> matchingEndpoint) {
        for (EndpointClassInformation endpointClass : endpointClasses) {
            for (EndpointInformation endpoint : endpointClass.getEndpoints()) {
                String endpointURI = endpoint.buildComparableEndpointUri();
                String restCallURI = restCall.buildComparableRestCallUri();
                if (endpointURI.equals(restCallURI) && endpoint.getHttpMethod().toLowerCase().equals(restCall.getMethod().toLowerCase())) {
                    matchingEndpoint = Optional.of(endpoint);
                }
                else if (endpointURI.endsWith("*") && restCallURI.startsWith(endpointURI.substring(0, endpointURI.length() - 1))
                        && endpoint.getHttpMethod().toLowerCase().equals(restCall.getMethod().toLowerCase())) {
                    matchingEndpoint = Optional.of(endpoint);
                }
            }
        }
        return matchingEndpoint;
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
            logger.error("failed to deserialize rest call analysis results", e);
        }

        restCallsAndMatchingEndpoints.getRestCallsWithoutMatchingEndpoints().stream().forEach(endpoint -> {
            logger.info("=============================================");
            logger.info("REST call URI: {}", endpoint.buildCompleteRestCallURI());
            logger.info("HTTP method: {}", endpoint.getMethod());
            logger.info("File path: {}", endpoint.getFileName());
            logger.info("Line: {}", endpoint.getLine());
            logger.info("=============================================");
            logger.info("No matching endpoint found for REST call: {}", endpoint.buildCompleteRestCallURI());
            logger.info("---------------------------------------------");
            logger.info("");
        });

        logger.info("Number of REST calls without matching endpoints: {}", restCallsAndMatchingEndpoints.getRestCallsWithoutMatchingEndpoints().size());
    }
}
