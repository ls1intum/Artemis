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
        // 4 legacy non-DTO-named classes (ConversationSummary, GeneralConversationInfo, MetisCrudAction, UserConversationInfo)
        // + 2 nested Jackson deserializers (OneToOneChatCreationDeserializer, GroupChatCreationDeserializer) that back the
        // tolerant chat-creation request bodies and cannot be named *DTO (cf. iris RawJsonDeserializer).
        return 6;
    }
}
