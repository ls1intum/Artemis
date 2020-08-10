package de.tum.in.www1.artemis.service.compass.umlmodel.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;

import me.xdrop.fuzzywuzzy.FuzzySearch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.internal.util.reflection.FieldSetter;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UMLActivityTest {

    private UMLActivity activity;

    @Mock
    UMLActivity referenceActivity;

    @BeforeEach
    void setUp() {
        activity = new UMLActivity("myActivity", Collections.emptyList(), "activityId");
    }

    @Test
    void similarity_null() {
        double similarity = activity.similarity(null);

        assertThat(similarity).isEqualTo(0);
    }

    @Test
    void similarity_differentElementType() {
        double similarity = activity.similarity(mock(UMLControlFlow.class));

        assertThat(similarity).isEqualTo(0);
    }

    @Test
    void similarity_sameActivity() {
        when(referenceActivity.getName()).thenReturn("myActivity");

        double similarity = activity.similarity(referenceActivity);

        assertThat(similarity).isEqualTo(1);
    }

    @Test
    void similarity_differentName() {
        when(referenceActivity.getName()).thenReturn("otherElementName");
        double expectedSimilarity = FuzzySearch.ratio("myActivity", "otherElementName") / 100.0;

        double similarity = activity.similarity(referenceActivity);

        assertThat(similarity).isEqualTo(expectedSimilarity);
    }

    @Test
    void similarity_nullReferenceName() {
        when(referenceActivity.getName()).thenReturn(null);

        double similarity = activity.similarity(referenceActivity);

        assertThat(similarity).isEqualTo(0);
    }

    @Test
    void similarity_bothNamesNull() throws NoSuchFieldException {
        FieldSetter.setField(activity, UMLActivityElement.class.getDeclaredField("name"), null);
        when(referenceActivity.getName()).thenReturn(null);

        double similarity = activity.similarity(referenceActivity);

        assertThat(similarity).isEqualTo(1);
    }

    @Test
    void similarity_bothNamesEmpty() throws NoSuchFieldException {
        FieldSetter.setField(activity, UMLActivityElement.class.getDeclaredField("name"), "");
        when(referenceActivity.getName()).thenReturn("");

        double similarity = activity.similarity(referenceActivity);

        assertThat(similarity).isEqualTo(1);
    }
}
