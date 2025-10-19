package de.tum.cit.aet.artemis.communication.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleCodeStyleTest;

class CommunicationCodeStyleArchitectureTest extends AbstractModuleCodeStyleTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".communication";
    }

    @Override
    protected int dtoAsAnnotatedRecordThreshold() {
        return 6;
    }

    @Override
    protected int dtoNameEndingThreshold() {
        return 6;
    }
}
