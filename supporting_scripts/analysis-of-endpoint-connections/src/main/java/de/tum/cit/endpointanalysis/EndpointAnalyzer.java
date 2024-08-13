package de.tum.cit .endpointanalysis;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class EndpointAnalyzer {

    public static void main(String[] args) {
        System.out.println("working directory: " + System.getProperty("user.dir"));
        analyzeEndpoints();
        printEndpointAnalysisResult();
    }

    private static void analyzeEndpoints() {
        final String endpointsJsonPath = "endpoints.json";
        ObjectMapper mapper = new ObjectMapper();

        try {
            List<EndpointClassInformation> endpointClasses = mapper.readValue(new File(endpointsJsonPath),
                new TypeReference<List<EndpointClassInformation>>() {
                });
            List<RestCallFileInformation> restCallFiles = mapper.readValue(new File("restCalls.json"),
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
            System.out.println("working directory: " + System.getProperty("user.dir"));
            mapper.writeValue(new File("endpointAnalysisResult.json"), endpointAnalysis);
            System.out.println("Endpoint analysis result written to file: endpointAnalysisResult.json");
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void printEndpointAnalysisResult() {
        ObjectMapper mapper = new ObjectMapper();
        EndpointAnalysis endpointsAndMatchingRestCalls = null;
        try {
            System.out.println("trying to read file: endpointAnalysisResult.json");
            endpointsAndMatchingRestCalls = mapper.readValue(new File("endpointAnalysisResult.json"),
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
