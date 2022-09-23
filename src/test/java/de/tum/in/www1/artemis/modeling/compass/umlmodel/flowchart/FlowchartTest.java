package de.tum.in.www1.artemis.modeling.compass.umlmodel.flowchart;

import static com.google.gson.JsonParser.parseString;
import static de.tum.in.www1.artemis.modeling.compass.umlmodel.flowchart.FlowchartUtil.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.modeling.compass.umlmodel.AbstractUMLDiagramTest;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.flowchart.*;
import de.tum.in.www1.artemis.service.compass.umlmodel.parsers.UMLModelParser;

class FlowchartTest extends AbstractUMLDiagramTest {

    @Test
    void similarityFlowchartEqualModels() {
        compareSubmissions(modelingSubmission(FLOWCHART_MODEL_1A), modelingSubmission(FLOWCHART_MODEL_1A), 0.8, 100.0);
        compareSubmissions(modelingSubmission(FLOWCHART_MODEL_2), modelingSubmission(FLOWCHART_MODEL_2), 0.8, 100.0);

        compareSubmissions(modelingSubmission(FLOWCHART_MODEL_1A), modelingSubmission(FLOWCHART_MODEL_1B), 0.8, 100.0);
    }

    @Test
    void similarityFlowchartDifferentModels() {
        compareSubmissions(modelingSubmission(FLOWCHART_MODEL_1A), modelingSubmission(FLOWCHART_MODEL_2), 0.0, 47.0);
    }

    @Test
    void parseFlowchartModelCorrectly() throws IOException {
        UMLDiagram diagram = UMLModelParser.buildModelFromJSON(parseString(FLOWCHART_MODEL_1B).getAsJsonObject(), 1L);
        assertThat(diagram).isInstanceOf(Flowchart.class);
        Flowchart flowchart = (Flowchart) diagram;

        assertThat(flowchart.getTerminals()).hasSize(2);
        assertThat(flowchart.getInputOutputs()).hasSize(2);
        assertThat(flowchart.getDecisions()).hasSize(2);
        assertThat(flowchart.getProcesses()).hasSize(2);
        assertThat(flowchart.getFlowLines()).hasSize(10);
        assertThat(flowchart.getFunctionCalls()).hasSize(1);

        assertThat(flowchart.getElementByJSONID("129c296b-1927-49e4-b021-5d496d33f5f6")).asInstanceOf(type(FlowchartTerminal.class)).satisfies(terminal -> {
            assertThat(terminal.getName()).isEqualTo("Start");
        });
        assertThat(flowchart.getElementByJSONID("cdc6ac64-2d3d-4468-8540-0cf9ed8ce8ec")).asInstanceOf(type(FlowchartInputOutput.class)).satisfies(inputOutput -> {
            assertThat(inputOutput.getName()).isEqualTo("x=readInt(); y=readInt();");
        });
        assertThat(flowchart.getElementByJSONID("0763f5e8-3ac0-4fa2-b52c-c8df0c5df563")).asInstanceOf(type(FlowchartDecision.class)).satisfies(decision -> {
            assertThat(decision.getName()).isEqualTo("x < y");
        });
        assertThat(flowchart.getElementByJSONID("74d972c6-efb6-4a6e-bd10-2e5b1fd8c3b8")).asInstanceOf(type(FlowchartProcess.class)).satisfies(process -> {
            assertThat(process.getName()).isEqualTo("y = y - x;");
        });
        assertThat(flowchart.getElementByJSONID("8b86b06b-baf8-48d0-a8f0-925a11fd552d")).asInstanceOf(type(FlowchartFunctionCall.class)).satisfies(functionCall -> {
            assertThat(functionCall.getName()).isEqualTo("x = max();");
        });
        assertThat(flowchart.getElementByJSONID("5c178df3-686e-435c-88bc-1fca2d20d090")).asInstanceOf(type(FlowchartFlowline.class)).satisfies(flowline -> {
            assertThat(flowline.getSource()).isNotNull().isSameAs(flowchart.getElementByJSONID("129c296b-1927-49e4-b021-5d496d33f5f6"));
            assertThat(flowline.getTarget()).isNotNull().isSameAs(flowchart.getElementByJSONID("cdc6ac64-2d3d-4468-8540-0cf9ed8ce8ec"));
        });
    }
}
