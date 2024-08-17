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

public class EndpointAnalyzer {

    private static String EndpointAnalysisResultPath = "endpointAnalysisResult.json";

    private static final Logger logger = LoggerFactory.getLogger(EndpointAnalyzer.class);

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
            List<EndpointClassInformation> endpointClasses = mapper.readValue(new File(EndpointParser.ENDPOINT_PARSING_RESULT_PATH),
                    new TypeReference<List<EndpointClassInformation>>() {
                    });
            List<RestCallFileInformation> restCallFiles = mapper.readValue(new File(EndpointParser.REST_CALL_PARSING_RESULT_PATH),
                    new TypeReference<List<RestCallFileInformation>>() {
                    });

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
                    List<RestCallInformation> matchingRestCalls = restCallMap.getOrDefault(endpointURI, new ArrayList<>());

                    // Check for wildcard endpoints if no exact match is found
                    if (matchingRestCalls.isEmpty() && endpointURI.endsWith("*")) {
                        for (String uri : restCallMap.keySet()) {
                            if (uri.startsWith(endpoint.buildComparableEndpointUri().substring(0, endpoint.buildComparableEndpointUri().length() - 1))
                                    && endpoint.getHttpMethod().toLowerCase().equals(restCallMap.get(uri).get(0).method().toLowerCase())) {
                                matchingRestCalls.addAll(restCallMap.get(uri));
                            }
                        }
                    }

                    if (matchingRestCalls.isEmpty()) {
                        unusedEndpoints.add(endpoint);
                    }
                    else {
                        endpointsAndMatchingRestCalls.add(new UsedEndpoints(endpoint, matchingRestCalls, endpointClass.filePath()));
                    }
                }
            }

            EndpointAnalysis endpointAnalysis = new EndpointAnalysis(endpointsAndMatchingRestCalls, unusedEndpoints);
            mapper.writeValue(new File(EndpointAnalysisResultPath), endpointAnalysis);
        }
        catch (IOException e) {
            logger.error("Failed to analyze endpoints", e);
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
            endpointsAndMatchingRestCalls = mapper.readValue(new File(EndpointAnalysisResultPath), new TypeReference<EndpointAnalysis>() {
            });
        }
        catch (IOException e) {
            logger.error("Failed to deserialize endpoint analysis result", e);
        }

        endpointsAndMatchingRestCalls.unusedEndpoints().stream().forEach(endpoint -> {
            logger.info("=============================================");
            logger.info("Endpoint URI: {}", endpoint.buildCompleteEndpointURI());
            logger.info("HTTP method: {}", endpoint.httpMethodAnnotation());
            logger.info("File path: {}", endpoint.className());
            logger.info("Line: {}", endpoint.line());
            logger.info("=============================================");
            logger.info("No matching REST call found for endpoint: {}", endpoint.buildCompleteEndpointURI());
            logger.info("---------------------------------------------");
            logger.info("");
        });

        logger.info("Number of endpoints without matching REST calls: {}", endpointsAndMatchingRestCalls.unusedEndpoints().size());
    }
}
