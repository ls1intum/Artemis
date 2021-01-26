package de.tum.in.www1.artemis.service.compass.umlmodel.flowchart;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;

public class Flowcharts {

    static final String FLOWCHART_MODEL_1A;

    static final String FLOWCHART_MODEL_1B;

    static final String FLOWCHART_MODEL_2;

    static {
        try {
            FLOWCHART_MODEL_1A = IOUtils.toString(Flowcharts.class.getResource("flowchartModel1a.json"), StandardCharsets.UTF_8);
            FLOWCHART_MODEL_1B = IOUtils.toString(Flowcharts.class.getResource("flowchartModel1b.json"), StandardCharsets.UTF_8);
            FLOWCHART_MODEL_2 = IOUtils.toString(Flowcharts.class.getResource("flowchartModel2.json"), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Flowcharts() {
        // do not instantiate
    }
}
