package de.tum.in.www1.artemis.modeling.compass.umlmodel.petrinet;

import static com.google.gson.JsonParser.parseString;
import static de.tum.in.www1.artemis.modeling.compass.umlmodel.petrinet.PetriNets.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.modeling.compass.umlmodel.AbstractUMLDiagramTest;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.parsers.UMLModelParser;
import de.tum.in.www1.artemis.service.compass.umlmodel.petrinet.PetriNet;
import de.tum.in.www1.artemis.service.compass.umlmodel.petrinet.PetriNetArc;
import de.tum.in.www1.artemis.service.compass.umlmodel.petrinet.PetriNetPlace;
import de.tum.in.www1.artemis.service.compass.umlmodel.petrinet.PetriNetTransition;

class PetriNetTest extends AbstractUMLDiagramTest {

    @Test
    void similarityPetriNet_equalModels() {
        compareSubmissions(modelingSubmission(PETRI_NET_MODEL_1A), modelingSubmission(PETRI_NET_MODEL_1A), 0.8, 100.0);
        compareSubmissions(modelingSubmission(PETRI_NET_MODEL_2), modelingSubmission(PETRI_NET_MODEL_2), 0.8, 100.0);

        compareSubmissions(modelingSubmission(PETRI_NET_MODEL_1A), modelingSubmission(PETRI_NET_MODEL_1B), 0.8, 93.75);
    }

    @Test
    void similarityPetriNet_differentModels() {
        compareSubmissions(modelingSubmission(PETRI_NET_MODEL_1A), modelingSubmission(PETRI_NET_MODEL_2), 0.0, 43.5625);
    }

    @Test
    void parsePetriNetModelCorrectly() throws IOException {
        UMLDiagram diagram = UMLModelParser.buildModelFromJSON(parseString(PETRI_NET_MODEL_1B).getAsJsonObject(), 1L);
        assertThat(diagram).isInstanceOf(PetriNet.class);
        PetriNet petriNet = (PetriNet) diagram;

        assertThat(petriNet.getPlaces()).hasSize(2);
        assertThat(petriNet.getTransitions()).hasSize(2);
        assertThat(petriNet.getArcs()).hasSize(4);

        assertThat(petriNet.getElementByJSONID("f406d5df-9404-4914-8631-8588cd289566")).asInstanceOf(type(PetriNetTransition.class)).satisfies(transition -> {
            assertThat(transition.getName()).isEqualTo("t1");
        });
        assertThat(petriNet.getElementByJSONID("c67e50de-22a0-42df-8056-1cc65fc0f950")).asInstanceOf(type(PetriNetPlace.class)).satisfies(place -> {
            assertThat(place.getName()).isEqualTo("b");
            assertThat(place.getAmountOfTokens()).isEqualTo("0");
            assertThat(place.getCapacity()).isEqualTo("Infinity");
        });
        assertThat(petriNet.getElementByJSONID("818fd351-bf3e-48b8-b41b-d775b23c13e5")).asInstanceOf(type(PetriNetArc.class)).satisfies(arc -> {
            assertThat(arc.getMultiplicity()).isEqualTo("1");
            assertThat(arc.getSource()).isNotNull().isSameAs(petriNet.getElementByJSONID("a12c5ed0-56aa-4174-b64b-7aead67429fd"));
            assertThat(arc.getTarget()).isNotNull().isSameAs(petriNet.getElementByJSONID("9336eb67-ddfb-4622-8593-d640402a04fc"));
        });
    }
}
