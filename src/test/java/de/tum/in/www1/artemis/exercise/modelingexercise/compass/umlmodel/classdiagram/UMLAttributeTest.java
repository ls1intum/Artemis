package de.tum.in.www1.artemis.exercise.modelingexercise.compass.umlmodel.classdiagram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLAttribute;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLClass;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLMethod;
import de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration;
import me.xdrop.fuzzywuzzy.FuzzySearch;

class UMLAttributeTest {

    private UMLAttribute attribute;

    @Mock
    private UMLClass parentClass;

    @Mock
    private UMLAttribute referenceAttribute;

    @Mock
    private UMLClass referenceParentClass;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);

        attribute = new UMLAttribute("myAttribute", "String", "attributeId");
        attribute.setParentElement(parentClass);

        when(referenceAttribute.getParentElement()).thenReturn(referenceParentClass);
        when(parentClass.getSimilarityID()).thenReturn(123);
        when(referenceParentClass.getSimilarityID()).thenReturn(123);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
    }

    @Test
    void similarity_nullReference() {
        double similarity = attribute.similarity(null);

        assertThat(similarity).isZero();
    }

    @Test
    void similarity_differentElementType() {
        double similarity = attribute.similarity(mock(UMLMethod.class));

        assertThat(similarity).isZero();
    }

    @Test
    void similarity_sameAttribute() {
        when(referenceAttribute.getName()).thenReturn("myAttribute");
        when(referenceAttribute.getAttributeType()).thenReturn("String");

        double similarity = attribute.similarity(referenceAttribute);

        assertThat(similarity).isEqualTo(1);
    }

    @Test
    void similarity_sameAttribute_noParentSimilarityId() {
        when(referenceAttribute.getName()).thenReturn("myAttribute");
        when(referenceAttribute.getAttributeType()).thenReturn("String");
        when(referenceParentClass.getSimilarityID()).thenReturn(-1);
        when(parentClass.similarity(referenceParentClass)).thenReturn(CompassConfiguration.EQUALITY_THRESHOLD + 0.01);

        double similarity = attribute.similarity(referenceAttribute);

        assertThat(similarity).isEqualTo(1);
    }

    @Test
    void similarity_sameAttribute_differentParent() {
        when(referenceAttribute.getName()).thenReturn("myAttribute");
        when(referenceAttribute.getAttributeType()).thenReturn("String");
        when(referenceParentClass.getSimilarityID()).thenReturn(321);

        double similarity = attribute.similarity(referenceAttribute);

        assertThat(similarity).isZero();
    }

    @Test
    void similarity_sameAttribute_differentParent_noParentSimilarityId() {
        when(referenceAttribute.getName()).thenReturn("myAttribute");
        when(referenceAttribute.getAttributeType()).thenReturn("String");
        when(referenceParentClass.getSimilarityID()).thenReturn(-1);
        when(parentClass.similarity(referenceParentClass)).thenReturn(CompassConfiguration.EQUALITY_THRESHOLD - 0.01);

        double similarity = attribute.similarity(referenceAttribute);

        assertThat(similarity).isZero();
    }

    @Test
    void similarity_differentAttributeName() {
        when(referenceAttribute.getName()).thenReturn("differentAttr");
        when(referenceAttribute.getAttributeType()).thenReturn("String");
        double expectedNameSimilarity = FuzzySearch.ratio("myAttribute", "differentAttr") / 100.0 * CompassConfiguration.ATTRIBUTE_NAME_WEIGHT;
        double expectedTypeSimilarity = CompassConfiguration.ATTRIBUTE_TYPE_WEIGHT;

        double similarity = attribute.similarity(referenceAttribute);

        assertThat(similarity).isEqualTo(expectedNameSimilarity + expectedTypeSimilarity);
    }

    @Test
    void similarity_differentAttributeType() {
        when(referenceAttribute.getName()).thenReturn("myAttribute");
        when(referenceAttribute.getAttributeType()).thenReturn("int");
        double expectedNameSimilarity = CompassConfiguration.ATTRIBUTE_NAME_WEIGHT;
        double expectedTypeSimilarity = 0;

        double similarity = attribute.similarity(referenceAttribute);

        assertThat(similarity).isEqualTo(expectedNameSimilarity + expectedTypeSimilarity);
    }

    @Test
    void similarity_nullReferenceValues() {
        when(referenceAttribute.getName()).thenReturn(null);
        when(referenceAttribute.getAttributeType()).thenReturn(null);

        double similarity = attribute.similarity(referenceAttribute);

        assertThat(similarity).isZero();
    }
}
