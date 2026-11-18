package de.tum.cit.aet.artemis.account.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record OIDCCodeExchangeDTO(@JsonProperty("code") String code, @JsonProperty("codeVerifier") @JsonAlias("code_verifier") String codeVerifier) {
}
