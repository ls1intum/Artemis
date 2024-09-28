package de.tum.cit.aet.artemis.core.domain;

import java.time.ZonedDateTime;

import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;

@Entity
@Table(name = "llm_token_usage")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class LLMTokenUsage extends DomainObject {

    @Column(name = "service")
    private LLMService service;

    @Column(name = "model")
    private String model;

    @Column(name = "cost_per_token")
    private double cost_per_token;

    @Column(name = "num_input_tokens")
    private int num_input_tokens;

    @Column(name = "num_output_tokens")
    private int num_output_tokens;

    @Nullable
    @Column(name = "timestamp")
    private ZonedDateTime timestamp = ZonedDateTime.now();

    @Nullable
    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "iris_message_id")
    IrisMessage message;
}
