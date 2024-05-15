package analysisOfEndpointConnections;

import java.io.File;
import java.util.Arrays;

public class AnalysisOfEndpointConnections {

    /**
     * This is the entry point of the analysis of server sided endpoints.
     *
     * @param args List of files that should be analyzed regarding endpoints.
     */
    public static void main(String[] args) {
        String[] serverFiles = Arrays.stream(args)
            .filter(filePath -> new File(filePath).exists() && filePath.endsWith(".java"))
            .toArray(String[]::new);
    }

}
