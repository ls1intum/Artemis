package de.tum.cit.endpointanalysis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RestCallAnalyzer {

    private static String RestCallAnalysisResultPath = "restCallAnalysisResult.json";

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
            List<EndpointClassInformation> endpointClasses = mapper.readValue(new File(EndpointParser.EndpointParsingResultPath),
                new TypeReference<List<EndpointClassInformation>>() {
                });
            List<RestCallFileInformation> restCalls = mapper.readValue(new File(EndpointParser.RestCallParsingResultPath),
                new TypeReference<List<RestCallFileInformation>>() {
                });

            List<RestCallWithMatchingEndpoint> restCallsWithMatchingEndpoint = new ArrayList<>();
            List<RestCallInformation> restCallsWithoutMatchingEndpoint = new ArrayList<>();

            for (RestCallFileInformation restCallFile : restCalls) {
                for (RestCallInformation restCall : restCallFile.getRestCalls()) {
                    Optional<EndpointInformation> matchingEndpoint = Optional.empty();

                    for (EndpointClassInformation endpointClass : endpointClasses) {
                        for (EndpointInformation endpoint : endpointClass.getEndpoints()) {
                            String endpointURI = endpoint.buildComparableEndpointUri();
                            String restCallURI = restCall.buildComparableRestCallUri();
                            if (endpointURI.equals(restCallURI) && endpoint.getHttpMethod().toLowerCase().equals(restCall.getMethod().toLowerCase())) {
                                matchingEndpoint = Optional.of(endpoint);
                            } else if (endpointURI.endsWith("*") && restCallURI.startsWith(endpointURI.substring(0, endpointURI.length() - 1)) && endpoint.getHttpMethod().toLowerCase().equals(restCall.getMethod().toLowerCase())) {
                                matchingEndpoint = Optional.of(endpoint);
                            }
                        }
                    }

                    if (matchingEndpoint.isPresent()) {
                        restCallsWithMatchingEndpoint.add(new RestCallWithMatchingEndpoint(matchingEndpoint.get(), restCall, restCall.getFileName()));
                    }
                    else {
                        restCallsWithoutMatchingEndpoint.add(restCall);
                    }
                }
            }
            RestCallAnalysis restCallAnalysis = new RestCallAnalysis(restCallsWithMatchingEndpoint, restCallsWithoutMatchingEndpoint);
            mapper.writeValue(new File(RestCallAnalysisResultPath), restCallAnalysis);
        }
        catch (IOException e) {
            e.printStackTrace();
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
            restCallsAndMatchingEndpoints = mapper.readValue(new File(RestCallAnalysisResultPath),
                new TypeReference<RestCallAnalysis>() {
                });
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }

        restCallsAndMatchingEndpoints.getRestCallsWithoutMatchingEndpoints().stream().forEach(endpoint -> {
            System.out.println("=============================================");
            System.out.println("REST call URI: " + endpoint.buildCompleteRestCallURI());
            System.out.println("HTTP method: " + endpoint.getMethod());
            System.out.println("File path: " + endpoint.getFileName());
            System.out.println("Line: " + endpoint.getLine());
            System.out.println("=============================================");
            System.out.println("No matching endpoint found for REST call: " + endpoint.buildCompleteRestCallURI());
            System.out.println("---------------------------------------------");
            System.out.println();
        });

        System.out.println("Number of REST calls without matching endpoints: " + restCallsAndMatchingEndpoints.getRestCallsWithoutMatchingEndpoints().size());
    }
}
