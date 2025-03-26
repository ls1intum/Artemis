package de.tum.cit.aet.artemis;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

public class TLTest {

    ApplicationModules modules = ApplicationModules.of(ArtemisApp.class);

    @Test
    void writeDocumentationSnippets() {

        new Documenter(modules).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
    }
}
