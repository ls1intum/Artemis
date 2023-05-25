package de.tum.in.www1.artemis.modeling.compass.umlmodel.flowchart;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;

public class FlowchartUtil {

    public static final String FLOWCHART_MODEL_1A;

    public static final String FLOWCHART_MODEL_1B;

    public static final String FLOWCHART_MODEL_2;

    static {
        try {
            FLOWCHART_MODEL_1A = IOUtils.toString(FlowchartUtil.class.getResource("flowchartModel1a.json"), StandardCharsets.UTF_8);
            FLOWCHART_MODEL_1B = IOUtils.toString(FlowchartUtil.class.getResource("flowchartModel1b.json"), StandardCharsets.UTF_8);
            FLOWCHART_MODEL_2 = IOUtils.toString(FlowchartUtil.class.getResource("flowchartModel2.json"), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private FlowchartUtil() {
        // do not instantiate
    }
}
