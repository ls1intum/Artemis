package de.tum.in.www1.artemis.service.compass.umlmodel.flowchart;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;

public class FlowchartsHolder {

    static final String FLOWCHART_MODEL_1A;

    static final String FLOWCHART_MODEL_1B;

    static final String FLOWCHART_MODEL_2;

    static {
        try {
            FLOWCHART_MODEL_1A = IOUtils.toString(FlowchartsHolder.class.getResource("flowchartModel1a.json"), StandardCharsets.UTF_8);
            FLOWCHART_MODEL_1B = IOUtils.toString(FlowchartsHolder.class.getResource("flowchartModel1b.json"), StandardCharsets.UTF_8);
            FLOWCHART_MODEL_2 = IOUtils.toString(FlowchartsHolder.class.getResource("flowchartModel2.json"), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private FlowchartsHolder() {
        // do not instantiate
    }
}
