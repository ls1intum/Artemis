package de.tum.in.www1.artemis;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class EndpointAnalyzer {
    public static void main(String[] args) {
        analyzeEndpoints();
        printEndpointAnalysisResult();
    }

    private static void analyzeEndpoints() {
        ObjectMapper mapper = new ObjectMapper();

        try {
            List<EndpointClassInformation> endpointClasses = mapper.readValue(new File("supporting_scripts/analysis-of-endpoint-connections/endpoints.json"),
                new TypeReference<List<EndpointClassInformation>>() {
                });
            List<RestCallFileInformation> restCallFiles = mapper.readValue(new File("supporting_scripts/analysis-of-endpoint-connections/restCalls.json"),
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
                            if (endpointURI.equals(restCallURI) && endpoint.getHttpMethod().equals(restCall.getMethod())) {
                                matchingRestCalls.add(restCall);
                            }
                        }
                    }

                    if (matchingRestCalls.isEmpty()) {
                        unusedEndpoints.add(endpoint);
                    } else {
                        endpointsAndMatchingRestCalls.add(new UsedEndpoints(endpoint, matchingRestCalls, endpointClass.getFilePath()));
                    }
                }
            }

            EndpointAnalysis endpointAnalysis = new EndpointAnalysis(endpointsAndMatchingRestCalls, unusedEndpoints);
            mapper.writeValue(new File("supporting_scripts/analysis-of-endpoint-connections/endpointsAndMatchingRestCalls.json"), endpointAnalysis);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void printEndpointAnalysisResult() {
        ObjectMapper mapper = new ObjectMapper();
        EndpointAnalysis endpointsAndMatchingRestCalls = null;
        try {
            endpointsAndMatchingRestCalls = mapper.readValue(new File("supporting_scripts/analysis-of-endpoint-connections/endpointsAndMatchingRestCalls.json"),
                new TypeReference<EndpointAnalysis>() {
                });
        } catch (IOException e) {
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
