package de.tum.cit .endpointanalysis;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class EndpointAnalyzer {
    private static String EndpointAnalysisResultPath = "endpointAnalysisResult.json";

    public static void main(String[] args) {
        analyzeEndpoints();
        printEndpointAnalysisResult();
    }

    /**
     * Analyzes endpoints and matches them with REST calls.
     *
     * This method reads endpoint and REST call information from JSON files,
     * compares them to find matching REST calls for each endpoint, and writes
     * the analysis result to a JSON file. Endpoints without matching REST calls
     * are also recorded.
     */
    private static void analyzeEndpoints() {
        ObjectMapper mapper = new ObjectMapper();

        try {
            List<EndpointClassInformation> endpointClasses = mapper.readValue(new File(EndpointParser.EndpointParsingResultPath),
                new TypeReference<List<EndpointClassInformation>>() {
                });
            List<RestCallFileInformation> restCallFiles = mapper.readValue(new File(EndpointParser.RestCallParsingResultPath),
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
                            if (endpointURI.equals(restCallURI) && endpoint.getHttpMethod().toLowerCase().equals(restCall.getMethod().toLowerCase())) {
                                matchingRestCalls.add(restCall);
                            } else if (endpointURI.endsWith("*") && restCallURI.startsWith(endpointURI.substring(0, endpointURI.length() - 1)) && endpoint.getHttpMethod().toLowerCase().equals(restCall.getMethod().toLowerCase())) {
                                matchingRestCalls.add(restCall);
                            }
                        }
                    }

                    if (matchingRestCalls.isEmpty()) {
                        unusedEndpoints.add(endpoint);
                    }
                    else {
                        endpointsAndMatchingRestCalls.add(new UsedEndpoints(endpoint, matchingRestCalls, endpointClass.getFilePath()));
                    }
                }
            }

            EndpointAnalysis endpointAnalysis = new EndpointAnalysis(endpointsAndMatchingRestCalls, unusedEndpoints);
            mapper.writeValue(new File(EndpointAnalysisResultPath), endpointAnalysis);
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
    private static void printEndpointAnalysisResult() {
        ObjectMapper mapper = new ObjectMapper();
        EndpointAnalysis endpointsAndMatchingRestCalls = null;
        try {
            endpointsAndMatchingRestCalls = mapper.readValue(new File(EndpointAnalysisResultPath),
                new TypeReference<EndpointAnalysis>() {
                });
        }
        catch (IOException e) {
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
}
