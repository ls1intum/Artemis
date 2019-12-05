package de.tum.in.www1.artemis.service.util;

import java.util.Arrays;
import java.util.List;

import org.springframework.web.util.UriComponentsBuilder;

public class UrlUtils {

    public static UriComponentsBuilder buildEndpoint(String baseUrl, List<String> pathSegments, Object... args) {
        // Counts how many variable segments we have in the URL, e.g. like ["some static var", "<some variable>"] has one variable segment
        int segmentCtr = 0;
        // Go through all path segments and replace variable segments with the supplied args
        for (int i = 0; i < pathSegments.size(); i++) {
            if (pathSegments.get(i).matches("<.*>")) {
                // If we don't have enough args, throw an error
                if (segmentCtr == args.length) {
                    throw new IllegalArgumentException("Unable to build endpoint. Too few arguments!" + Arrays.toString(args));
                }
                pathSegments.set(i, String.valueOf(args[segmentCtr++]));
            }
        }
        // If there are too many args, throw an error since this should not be intended
        if (segmentCtr != args.length) {
            throw new IllegalArgumentException("Unable to build endpoint. Too many arguments! " + Arrays.toString(args));
        }

        return UriComponentsBuilder.fromHttpUrl(baseUrl).pathSegment(pathSegments.toArray(new String[0]));
    }
}
