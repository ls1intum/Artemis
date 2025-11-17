package de.tum.cit.aet.artemis.iris.domain.settings;

import java.util.SortedSet;
import java.util.TreeSet;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * IrisSubSettings is an abstract super class for the specific sub settings types.
 * Sub Settings are settings for a specific feature of Iris.
 * {@link IrisProgrammingExerciseChatSubSettings} are used to specify settings for the chat feature.
 * {@link IrisCompetencyGenerationSubSettings} are used to specify settings for the competency generation feature.
 * <p>
 * Also see {@link de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService} for more information.
 */
@Entity
@Table(name = "iris_sub_settings")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
// @formatter:off
@JsonSubTypes({
    @JsonSubTypes.Type(value = IrisProgrammingExerciseChatSubSettings.class, name = "programming-exercise-chat"),
    @JsonSubTypes.Type(value = IrisTextExerciseChatSubSettings.class, name = "text-exercise-chat"),
    @JsonSubTypes.Type(value = IrisCourseChatSubSettings.class, name = "course-chat"),
    @JsonSubTypes.Type(value = IrisLectureIngestionSubSettings.class, name = "lecture-ingestion"),
    @JsonSubTypes.Type(value = IrisCompetencyGenerationSubSettings.class, name = "competency-generation"),
    @JsonSubTypes.Type(value = IrisLectureChatSubSettings.class, name = "lecture-chat"),
    @JsonSubTypes.Type(value = IrisFaqIngestionSubSettings.class, name = "faq-ingestion"),
    @JsonSubTypes.Type(value = IrisTutorSuggestionSubSettings.class, name = "tutor-suggestion"),
})
// @formatter:on
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class IrisSubSettings extends DomainObject {

    @Column(name = "enabled")
    private boolean enabled = true;

    @Column(name = "allowed_variants", nullable = false)
    @Convert(converter = IrisListConverter.class)
    private SortedSet<String> allowedVariants = new TreeSet<>();

    @Column(name = "selected_variant")
    private String selectedVariant;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public SortedSet<String> getAllowedVariants() {
        return allowedVariants;
    }

    public void setAllowedVariants(SortedSet<String> allowedVariants) {
        this.allowedVariants = allowedVariants;
    }

    @Nullable
    public String getSelectedVariant() {
        return selectedVariant;
    }

    public void setSelectedVariant(@Nullable String selectedVariant) {
        this.selectedVariant = selectedVariant;
    }
}
