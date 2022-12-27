package de.tum.in.www1.artemis.modeling.compass.umlmodel.usecase;

import static com.google.gson.JsonParser.parseString;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.modeling.compass.umlmodel.AbstractUMLDiagramTest;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.parsers.UMLModelParser;
import de.tum.in.www1.artemis.service.compass.umlmodel.usecase.UMLActor;
import de.tum.in.www1.artemis.service.compass.umlmodel.usecase.UMLUseCase;
import de.tum.in.www1.artemis.service.compass.umlmodel.usecase.UMLUseCaseAssociation;
import de.tum.in.www1.artemis.service.compass.umlmodel.usecase.UMLUseCaseDiagram;

class UMLUseCaseDiagramTest extends AbstractUMLDiagramTest {

    @Test
    void similarityUseCaseDiagram_EqualModels() {
        compareSubmissions(modelingSubmission(UMLUseCaseDiagrams.USE_CASE_MODEL_1), modelingSubmission(UMLUseCaseDiagrams.USE_CASE_MODEL_1), 0.8, 100.0);
        compareSubmissions(modelingSubmission(UMLUseCaseDiagrams.USE_CASE_MODEL_2), modelingSubmission(UMLUseCaseDiagrams.USE_CASE_MODEL_2), 0.8, 100.0);
    }

    @Test
    void similarityUseCaseDiagram_DifferentModels() {
        compareSubmissions(modelingSubmission(UMLUseCaseDiagrams.USE_CASE_MODEL_1), modelingSubmission(UMLUseCaseDiagrams.USE_CASE_MODEL_2), 0.0, 18.44);
    }

    @Test
    void parseUseCaseDiagramModelCorrectly() throws IOException {
        UMLDiagram diagram = UMLModelParser.buildModelFromJSON(parseString(UMLUseCaseDiagrams.USE_CASE_MODEL_2).getAsJsonObject(), 1L);
        assertThat(diagram).isInstanceOf(UMLUseCaseDiagram.class);
        UMLUseCaseDiagram useCaseDiagram = (UMLUseCaseDiagram) diagram;
        assertThat(useCaseDiagram.getSystemBoundaryList()).hasSize(1);
        assertThat(useCaseDiagram.getActorList()).hasSize(2);
        assertThat(useCaseDiagram.getUseCaseList()).hasSize(9);
        assertThat(useCaseDiagram.getUseCaseAssociationList()).hasSize(9);

        assertThat(useCaseDiagram.getElementByJSONID("559c80d8-5778-4c65-a57e-a0a7980404ed")).isInstanceOf(UMLActor.class);
        assertThat(useCaseDiagram.getElementByJSONID("67f8af32-d803-4b36-b69c-bd0bb7b65207")).isInstanceOf(UMLUseCase.class);
        assertThat(useCaseDiagram.getElementByJSONID("f84c7d48-a98f-4667-83be-76aded95df10")).isInstanceOf(UMLUseCaseAssociation.class);
    }
}
