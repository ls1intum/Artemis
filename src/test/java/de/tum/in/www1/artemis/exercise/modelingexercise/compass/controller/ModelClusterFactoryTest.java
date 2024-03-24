package de.tum.in.www1.artemis.exercise.modelingexercise.compass.controller;

import static de.tum.in.www1.artemis.util.TestResourceUtils.loadFileFromResources;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.domain.modeling.ModelCluster;
import de.tum.in.www1.artemis.domain.modeling.ModelElement;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.service.compass.controller.ModelClusterFactory;

class ModelClusterFactoryTest {

    private ModelClusterFactory modelClusterFactory;

    @BeforeEach
    void initTestCase() {
        modelClusterFactory = new ModelClusterFactory();
    }

    @Test
    void testBuildingClustersWithSimilarElements() throws Exception {
        ModelingSubmission submission1 = ParticipationFactory.generateModelingSubmission(loadFileFromResources("test-data/model-submission/model.54727.json"), true);
        submission1.setId(1L);
        ModelingSubmission submission2 = ParticipationFactory.generateModelingSubmission(loadFileFromResources("test-data/model-submission/model.54727.cpy.json"), true);
        submission2.setId(2L);
        ModelingSubmission submission3 = ParticipationFactory.generateModelingSubmission(loadFileFromResources("test-data/model-submission/model.54727.cpy.json"), true);
        submission3.setId(3L);
        ModelingSubmission submission4 = ParticipationFactory.generateModelingSubmission(loadFileFromResources("test-data/model-submission/model.54727.cpy.json"), true);
        submission4.setId(4L);
        ModelingSubmission submission5 = ParticipationFactory.generateModelingSubmission(loadFileFromResources("test-data/model-submission/model.one-element.json"), true);
        submission5.setId(5L);

        ModelingExercise exercise = new ModelingExercise();
        List<ModelCluster> modelClusters = modelClusterFactory.buildClusters(List.of(submission1, submission2, submission3, submission4, submission5), exercise);

        assertThat(modelClusters).as("model clusters created").hasSize(10);
        ModelCluster modelCluster = modelClusters.get(0);
        assertThat(modelCluster.getModelElements()).as("all elements are created").hasSize(4);
        for (ModelElement element : modelCluster.getModelElements()) {
            assertThat(element.getCluster()).as("created elements keeps the cluster").isEqualTo(modelCluster);
        }
    }

    @Test
    void testBuildingClustersWithSimilarV3Elements() throws Exception {
        ModelingSubmission submission1 = ParticipationFactory.generateModelingSubmission(loadFileFromResources("test-data/model-submission/v3/model.54727.json"), true);
        submission1.setId(1L);
        ModelingSubmission submission2 = ParticipationFactory.generateModelingSubmission(loadFileFromResources("test-data/model-submission/v3/model.54727.cpy.json"), true);
        submission2.setId(2L);
        ModelingSubmission submission3 = ParticipationFactory.generateModelingSubmission(loadFileFromResources("test-data/model-submission/v3/model.54727.cpy.json"), true);
        submission3.setId(3L);
        ModelingSubmission submission4 = ParticipationFactory.generateModelingSubmission(loadFileFromResources("test-data/model-submission/v3/model.54727.cpy.json"), true);
        submission4.setId(4L);
        ModelingSubmission submission5 = ParticipationFactory.generateModelingSubmission(loadFileFromResources("test-data/model-submission/v3/model.one-element.json"), true);
        submission5.setId(5L);

        ModelingExercise exercise = new ModelingExercise();
        List<ModelCluster> modelClusters = modelClusterFactory.buildClusters(List.of(submission1, submission2, submission3, submission4, submission5), exercise);

        assertThat(modelClusters).as("model clusters created").hasSize(10);
        ModelCluster modelCluster = modelClusters.get(0);
        assertThat(modelCluster.getModelElements()).as("all elements are created").hasSize(4);
        for (ModelElement element : modelCluster.getModelElements()) {
            assertThat(element.getCluster()).as("created elements keeps the cluster").isEqualTo(modelCluster);
        }
    }
}
