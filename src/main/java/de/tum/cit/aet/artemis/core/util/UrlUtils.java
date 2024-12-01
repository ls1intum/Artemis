package de.tum.cit.aet.artemis.core.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.web.util.UriComponentsBuilder;

public class UrlUtils {

    /**
     * Creates a {@link UriComponentsBuilder} that can be used for creating complex URLs for REST API endpoints.
     * Uses the given base URL and a list of path segments (i.e. /api/some/path would have [api, some, path] as the
     * segments) in order to create the builder. The given arguments will be used to replace variable path segments.
     * I.e. if you have a path of <code>/api/some/&lt;variable&gt;/endpoint/&lt;another variable&gt;</code> and
     * <code>args=["firstArg", 42]</code>, then the returned builder will be based on this path: <code>/api/some/frstArg/endpoint/42</code>
     *
     * @param baseUrl      The URL to take as basis when building the endpoints path
     * @param pathSegments The segments to be appended to the base URL
     * @param args         Arguments that should replace the variable path segments
     * @return A URI builder which combines all parameters into one base path for a REST API endpoint
     */
    public static UriComponentsBuilder buildEndpoint(String baseUrl, List<String> pathSegments, Object... args) {
        // Counts how many variable segments we have in the URL, e.g. like ["some static var", "<some variable>"] has one variable segment
        int segmentCtr = 0;
        final var parsedSegments = new ArrayList<String>();
        // Go through all path segments and replace variable segments with the supplied args
        for (var pathSegment : pathSegments) {
            if (pathSegment.matches("<.*>")) {
                // If we don't have enough args, throw an error
                if (segmentCtr == args.length) {
                    throw new IllegalArgumentException("Unable to build endpoint. Too few arguments!" + Arrays.toString(args));
                }
                parsedSegments.add(String.valueOf(args[segmentCtr++]));
            }
            else {
                parsedSegments.add(pathSegment);
            }
        }
        // If there are too many args, throw an error since this should not be intended
        if (segmentCtr != args.length) {
            throw new IllegalArgumentException("Unable to build endpoint. Too many arguments! " + Arrays.toString(args));
        }

        return UriComponentsBuilder.fromHttpUrl(baseUrl).pathSegment(parsedSegments.toArray(String[]::new));
    }
}
