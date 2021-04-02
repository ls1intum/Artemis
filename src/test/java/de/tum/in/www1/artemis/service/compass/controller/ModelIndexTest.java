package de.tum.in.www1.artemis.service.compass.controller;

import static de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration.EQUALITY_THRESHOLD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import com.hazelcast.config.Config;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLPackage;

class ElementAnswer implements Serializable, Answer {

    private final List<UMLElement> elements;

    private final List<Double> similarities;

    ElementAnswer(List<UMLElement> elements, List<Double> similarities) {
        this.elements = elements;
        this.similarities = similarities;
    }

    @Override
    public Object answer(InvocationOnMock invocation) {
        UMLElement umlElement = invocation.getArgument(0);
        int i = 0;
        for (UMLElement element : elements) {
            if (umlElement.getJSONElementID().equals(element.getJSONElementID())) {
                return similarities.get(i);
            }
            i += 1;
        }
        return 0.0;
    }
}

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ModelIndexTest {

    private ModelIndex modelIndex;

    @Mock(serializable = true)
    private UMLElement umlElement1;

    @Mock(serializable = true)
    private UMLElement umlElement2;

    @Mock(serializable = true)
    private UMLElement umlElement3;

    @Mock(serializable = true)
    private UMLElement umlElement4;

    @BeforeEach
    void setUp() {
        Long exerciseId = 1L;
        Config config = new Config();
        config.setProperty("hazelcast.shutdownhook.enabled", "false");
        config.setInstanceName("testHazelcastInstance");
        NetworkConfig network = config.getNetworkConfig();
        network.getJoin().getTcpIpConfig().setEnabled(false);
        network.getJoin().getMulticastConfig().setEnabled(false);
        HazelcastInstance testInstance = Hazelcast.getOrCreateHazelcastInstance(config);
        modelIndex = new ModelIndex(exerciseId, testInstance);
        testInstance.getMap("similarities - " + exerciseId).clear();
        testInstance.getQueue("elements - " + exerciseId).clear();
        testInstance.getMap("models - " + exerciseId).clear();
        when(umlElement1.getJSONElementID()).thenReturn("jsonElement1");
        when(umlElement2.getJSONElementID()).thenReturn("jsonElement2");
        when(umlElement3.getJSONElementID()).thenReturn("jsonElement3");
        when(umlElement4.getJSONElementID()).thenReturn("jsonElement4");
    }

    @AfterEach
    void tearDown() {
        Mockito.reset(umlElement1, umlElement2, umlElement3, umlElement4);
    }

    @Test
    void retrieveSimilarityId_sameElementTwice() {
        UMLPackage umlPackage = new UMLPackage("testPackage", new ArrayList<>(), "jsonElementPackage");
        int similarityId1 = modelIndex.retrieveSimilarityId(umlPackage);
        int similarityId2 = modelIndex.retrieveSimilarityId(umlPackage);

        assertThat(similarityId1).isEqualTo(0);
        assertThat(similarityId2).isEqualTo(0);
        assertThat(modelIndex.getElementSimilarityMap().keySet()).containsExactly(umlPackage);
        assertThat(modelIndex.getNumberOfUniqueElements()).isEqualTo(1);
    }

    @Test
    void retrieveSimilarityId_twoSimilarElements() {
        mockSimilarityBetweenElements(List.of(umlElement2, umlElement3, umlElement4), umlElement1, List.of(EQUALITY_THRESHOLD / 2, EQUALITY_THRESHOLD / 2, EQUALITY_THRESHOLD / 2));
        mockSimilarityBetweenElements(List.of(umlElement3, umlElement4), umlElement2, List.of(EQUALITY_THRESHOLD / 2, EQUALITY_THRESHOLD + 0.01));
        mockSimilarityBetweenElements(List.of(umlElement4), umlElement3, List.of(EQUALITY_THRESHOLD / 2));

        when(umlElement2.getSimilarityID()).thenReturn(1);
        int similarityId1 = modelIndex.retrieveSimilarityId(umlElement1);
        int similarityId2 = modelIndex.retrieveSimilarityId(umlElement2);
        int similarityId3 = modelIndex.retrieveSimilarityId(umlElement3);
        int similarityId4 = modelIndex.retrieveSimilarityId(umlElement4);

        assertThat(similarityId1).isEqualTo(0);
        assertThat(similarityId2).isEqualTo(1);
        assertThat(similarityId3).isEqualTo(2);
        assertThat(similarityId4).isEqualTo(1);
        assertThat(modelIndex.getElementSimilarityMap().keySet().stream().map(UMLElement::getJSONElementID)).containsExactlyInAnyOrder("jsonElement1", "jsonElement2",
                "jsonElement3", "jsonElement4");
        assertThat(modelIndex.getNumberOfUniqueElements()).isEqualTo(3);
    }

    @Test
    void retrieveSimilarityId_multipleSimilarElements() {

        mockSimilarityBetweenElements(List.of(umlElement2, umlElement3, umlElement4), umlElement1,
                List.of(EQUALITY_THRESHOLD / 2, EQUALITY_THRESHOLD / 2, EQUALITY_THRESHOLD + 0.01));
        mockSimilarityBetweenElements(List.of(umlElement3, umlElement4), umlElement2, List.of(EQUALITY_THRESHOLD / 2, EQUALITY_THRESHOLD + 0.03));
        mockSimilarityBetweenElements(List.of(umlElement4), umlElement3, List.of(EQUALITY_THRESHOLD + 0.02));

        // This is because hazelcast clones the object so there is no point in setting mocking this after sending it to modelIndex
        // Value is checked later as well so it is safe
        when(umlElement2.getSimilarityID()).thenReturn(1);
        int similarityId1 = modelIndex.retrieveSimilarityId(umlElement1);
        int similarityId2 = modelIndex.retrieveSimilarityId(umlElement2);
        int similarityId3 = modelIndex.retrieveSimilarityId(umlElement3);
        int similarityId4 = modelIndex.retrieveSimilarityId(umlElement4);
        assertThat(similarityId1).isEqualTo(0);
        assertThat(similarityId2).isEqualTo(1);
        assertThat(similarityId3).isEqualTo(2);
        assertThat(similarityId4).isEqualTo(1);

        assertThat(modelIndex.getElementSimilarityMap().keySet().stream().map(UMLElement::getJSONElementID)).containsExactlyInAnyOrder("jsonElement1", "jsonElement2",
                "jsonElement3", "jsonElement4");
        assertThat(modelIndex.getNumberOfUniqueElements()).isEqualTo(3);
    }

    private void mockSimilarityBetweenElements(List<UMLElement> elements, UMLElement element2, List<Double> similarities) {
        when(element2.similarity(any(UMLElement.class))).thenAnswer(new ElementAnswer(elements, similarities));
    }
}
