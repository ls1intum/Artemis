package de.tum.in.www1.artemis.modeling.compass.umlmodel.classdiagram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLClass;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLPackage;
import me.xdrop.fuzzywuzzy.FuzzySearch;

class UMLPackageTest {

    private UMLPackage umlPackage;

    @Mock
    private UMLPackage referencePackage;

    @Mock
    private UMLClass class1;

    @Mock
    private UMLClass class2;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        umlPackage = new UMLPackage("myPackage", List.of(class1, class2), "packageId");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
    }

    @Test
    void similarity_null() {
        double similarity = umlPackage.similarity(null);

        assertThat(similarity).isZero();
    }

    @Test
    void similarity_differentElementType() {
        double similarity = umlPackage.similarity(mock(UMLClass.class));

        assertThat(similarity).isZero();
    }

    @Test
    void similarity_samePackageName() {
        when(referencePackage.getName()).thenReturn("myPackage");

        double similarity = umlPackage.similarity(referencePackage);

        assertThat(similarity).isEqualTo(1);
    }

    @Test
    void similarity_differentPackageName() {
        when(referencePackage.getName()).thenReturn("differentPackageName");
        double expectedNameSimilarity = FuzzySearch.ratio("myPackage", "differentPackageName") / 100.0;

        double similarity = umlPackage.similarity(referencePackage);

        assertThat(similarity).isEqualTo(expectedNameSimilarity);
    }
}
