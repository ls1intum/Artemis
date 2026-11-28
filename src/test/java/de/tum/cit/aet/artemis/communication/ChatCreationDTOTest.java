package de.tum.cit.aet.artemis.communication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.communication.dto.GroupChatCreationDTO;
import de.tum.cit.aet.artemis.communication.dto.OneToOneChatCreationDTO;

class ChatCreationDTOTest {

    private final JsonMapper mapper = new JsonMapper();

    @ParameterizedTest
    @ValueSource(strings = { "{\"memberLogins\":[\"alice\",\"bob\"]}", "[\"alice\",\"bob\"]" })
    void readsBothGroupChatRequestFormats(String json) {
        assertThat(mapper.readValue(json, GroupChatCreationDTO.class).memberLogins()).containsExactly("alice", "bob");
    }

    @ParameterizedTest
    @ValueSource(strings = { "{\"login\":\"alice\"}", "[\"alice\"]" })
    void readsBothOneToOneChatLoginFormats(String json) {
        var dto = mapper.readValue(json, OneToOneChatCreationDTO.class);
        assertThat(dto.login()).isEqualTo("alice");
        assertThat(dto.userId()).isNull();
    }

    @Test
    void readsOneToOneChatByNumericId() {
        var dto = mapper.readValue("{\"userId\":42}", OneToOneChatCreationDTO.class);
        assertThat(dto.userId()).isEqualTo(42L);
        assertThat(dto.login()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = { "{}", "{\"memberLogins\":null}", "[]" })
    void preservesEmptyGroupMembersForEndpointValidation(String json) {
        assertThat(mapper.readValue(json, GroupChatCreationDTO.class).memberLogins()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "true", "1", "\"alice\"", "[[\"alice\"]]", "[12]", "[true]", "[null]", "[{\"login\":\"alice\"}]", "{\"memberLogins\":\"alice\"}",
            "{\"memberLogins\":[false]}" })
    void rejectsMalformedGroupChatRequests(String json) {
        assertThatThrownBy(() -> mapper.readValue(json, GroupChatCreationDTO.class)).isInstanceOf(JacksonException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "true", "1", "\"alice\"", "[]", "[\"alice\",\"bob\"]", "[[\"alice\"]]", "[12]", "[true]", "[null]", "[{\"login\":\"alice\"}]", "{\"userId\":1.9}",
            "{\"userId\":\"42\"}", "{\"userId\":9223372036854775808}" })
    void rejectsMalformedOneToOneChatRequests(String json) {
        assertThatThrownBy(() -> mapper.readValue(json, OneToOneChatCreationDTO.class)).isInstanceOf(JacksonException.class);
    }
}
