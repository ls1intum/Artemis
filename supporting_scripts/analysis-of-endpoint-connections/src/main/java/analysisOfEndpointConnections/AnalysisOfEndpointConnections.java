package analysisOfEndpointConnections;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.JavaAnnotation;
import com.thoughtworks.qdox.model.JavaClass;
import com.thoughtworks.qdox.model.JavaMethod;

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
        analyzeServerEndpoints(serverFiles);
    }

    private static void analyzeServerEndpoints(String[] filePaths) {

    }
}
