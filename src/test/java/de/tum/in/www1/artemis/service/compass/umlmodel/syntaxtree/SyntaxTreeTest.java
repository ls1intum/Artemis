package de.tum.in.www1.artemis.service.compass.umlmodel.syntaxtree;

import static com.google.gson.JsonParser.parseString;
import static de.tum.in.www1.artemis.service.compass.umlmodel.syntaxtree.SyntaxTrees.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.service.compass.umlmodel.AbstractUMLDiagramTest;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.parsers.UMLModelParser;

class SyntaxTreeTest extends AbstractUMLDiagramTest {

    @Test
    void similaritySyntaxTree_equalModels() {
        compareSubmissions(modelingSubmission(SYNTAX_TREE_MODEL_1A), modelingSubmission(SYNTAX_TREE_MODEL_1A), 0.8, 100.0);
        compareSubmissions(modelingSubmission(SYNTAX_TREE_MODEL_2), modelingSubmission(SYNTAX_TREE_MODEL_2), 0.8, 100.0);

        compareSubmissions(modelingSubmission(SYNTAX_TREE_MODEL_1A), modelingSubmission(SYNTAX_TREE_MODEL_1B), 0.8, 100.0);
    }

    @Test
    void similaritySyntaxTree_differentModels() {
        compareSubmissions(modelingSubmission(SYNTAX_TREE_MODEL_1A), modelingSubmission(SYNTAX_TREE_MODEL_2), 0.0, 39.166);
    }

    @Test
    void parseSyntaxTreeModelCorrectly() throws IOException {
        UMLDiagram diagram = UMLModelParser.buildModelFromJSON(parseString(SYNTAX_TREE_MODEL_1A).getAsJsonObject(), 1L);
        assertThat(diagram).isInstanceOf(SyntaxTree.class);
        SyntaxTree syntaxTree = (SyntaxTree) diagram;

        assertThat(syntaxTree.getNonTerminals()).hasSize(1);
        assertThat(syntaxTree.getTerminals()).hasSize(4);
        assertThat(syntaxTree.getLinks()).hasSize(4);

        assertThat(syntaxTree.getElementByJSONID("8e21a66e-9fc4-4f82-b2d1-3d26a51b309a")).asInstanceOf(type(SyntaxTreeNonterminal.class)).satisfies(nonterminal -> {
            assertThat(nonterminal.getName()).isEqualTo("stmt");
        });
        assertThat(syntaxTree.getElementByJSONID("3a54da7e-ad8c-410d-83d3-541ec58ea038")).asInstanceOf(type(SyntaxTreeTerminal.class)).satisfies(terminal -> {
            assertThat(terminal.getName()).isEqualTo("=");
        });
        assertThat(syntaxTree.getElementByJSONID("200cbd5f-4ce1-4d96-8bbd-34ef89aae533")).asInstanceOf(type(SyntaxTreeLink.class)).satisfies(link -> {
            assertThat(link.getSource()).isNotNull().isSameAs(syntaxTree.getElementByJSONID("2ea3a753-b2d9-4a57-881e-1f10837ef5a2"));
            assertThat(link.getTarget()).isNotNull().isSameAs(syntaxTree.getElementByJSONID("8e21a66e-9fc4-4f82-b2d1-3d26a51b309a"));
        });
    }
}
