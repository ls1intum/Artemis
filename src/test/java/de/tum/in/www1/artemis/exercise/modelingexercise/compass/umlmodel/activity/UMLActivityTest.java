package de.tum.in.www1.artemis.exercise.modelingexercise.compass.umlmodel.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.in.www1.artemis.service.compass.umlmodel.activity.UMLActivity;
import de.tum.in.www1.artemis.service.compass.umlmodel.activity.UMLControlFlow;
import me.xdrop.fuzzywuzzy.FuzzySearch;

@ExtendWith(MockitoExtension.class)
class UMLActivityTest {

    private UMLActivity activity;

    @Spy
    private UMLActivity referenceActivity;

    @BeforeEach
    void setUp() {
        activity = new UMLActivity("myActivity", Collections.emptyList(), "activityId");
    }

    @Test
    void similarity_null() {
        double similarity = activity.similarity(null);
        assertThat(similarity).isZero();
    }

    @Test
    void similarity_differentElementType() {
        double similarity = activity.similarity(mock(UMLControlFlow.class));
        assertThat(similarity).isZero();
    }

    @Test
    void similarity_sameActivity() {
        doReturn("myActivity").when(referenceActivity).getName();
        double similarity = activity.similarity(referenceActivity);
        assertThat(similarity).isEqualTo(1);
    }

    @Test
    void similarity_differentName() {
        doReturn("otherElementName").when(referenceActivity).getName();
        double expectedSimilarity = FuzzySearch.ratio("myActivity", "otherElementName") / 100.0;
        double similarity = activity.similarity(referenceActivity);
        assertThat(similarity).isEqualTo(expectedSimilarity);
    }

    @Test
    void similarity_nullReferenceName() {
        doReturn(null).when(referenceActivity).getName();
        double similarity = activity.similarity(referenceActivity);
        assertThat(similarity).isZero();
    }

    @Test
    void similarity_bothNamesNull() {
        activity = spy(UMLActivity.class); // is automatically null
        doReturn(null).when(referenceActivity).getName();
        double similarity = activity.similarity(referenceActivity);
        assertThat(similarity).isEqualTo(1);
    }

    @Test
    void similarity_bothNamesEmpty() {
        activity = spy(UMLActivity.class);
        doReturn("").when(activity).getName();
        doReturn("").when(referenceActivity).getName();
        double similarity = activity.similarity(referenceActivity);
        assertThat(similarity).isEqualTo(1);
    }
}
