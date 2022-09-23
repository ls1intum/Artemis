package de.tum.in.www1.artemis.modeling.compass.umlmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;

import org.assertj.core.data.Offset;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.umlmodel.component.*;
import de.tum.in.www1.artemis.service.plagiarism.ModelingPlagiarismDetectionService;
import de.tum.in.www1.artemis.service.plagiarism.PlagiarismWebsocketService;
import de.tum.in.www1.artemis.service.plagiarism.cache.PlagiarismCacheService;

public abstract class AbstractUMLDiagramTest {

    protected ModelingPlagiarismDetectionService modelingPlagiarismDetectionService = new ModelingPlagiarismDetectionService(mock(PlagiarismWebsocketService.class),
            mock(PlagiarismCacheService.class));

    protected void compareSubmissions(ModelingSubmission modelingSubmission1, ModelingSubmission modelingSubmission2, double minimumSimilarity, double expectedSimilarity) {
        // not really necessary, but avoids issues.
        modelingSubmission1.setId(1L);
        modelingSubmission2.setId(2L);

        List<ModelingSubmission> submissions = new ArrayList<>();
        submissions.add(modelingSubmission1);
        submissions.add(modelingSubmission2);

        var comparisonResult = modelingPlagiarismDetectionService.checkPlagiarism(submissions, minimumSimilarity, 1, 0, 1L);
        assertThat(comparisonResult).isNotNull();
        assertThat(comparisonResult.getComparisons()).hasSize(1);
        assertThat(comparisonResult.getComparisons().stream().findFirst().get().getSimilarity()).isEqualTo(expectedSimilarity, Offset.offset(0.01));
    }

    protected UMLComponent getComponent(UMLComponentDiagram componentDiagram, String name) {
        return componentDiagram.getComponentList().stream().filter(component -> component.getName().equals(name)).findFirst().get();
    }

    protected UMLComponentInterface getInterface(UMLComponentDiagram componentDiagram, String name) {
        return componentDiagram.getComponentInterfaceList().stream().filter(componentInterface -> componentInterface.getName().equals(name)).findFirst().get();
    }

    protected UMLComponentRelationship getRelationship(UMLComponentDiagram componentDiagram, UMLElement source, UMLElement target) {
        // Source and target do not really matter in this test so we can also check the other way round
        return componentDiagram.getComponentRelationshipList().stream().filter(relationship -> (relationship.getSource().equals(source) && relationship.getTarget().equals(target))
                || (relationship.getSource().equals(target) && relationship.getTarget().equals(source))).findFirst().get();
    }

    protected ModelingSubmission modelingSubmission(String model) {
        var submission = new ModelingSubmission();
        submission.setModel(model);
        var participation = new StudentParticipation();
        var user = new User();
        user.setLogin("student");
        participation.setParticipant(user);
        submission.setParticipation(participation);
        return submission;
    }
}
