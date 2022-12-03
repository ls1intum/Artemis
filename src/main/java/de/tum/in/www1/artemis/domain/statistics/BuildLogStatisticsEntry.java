package de.tum.in.www1.artemis.domain.statistics;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import javax.annotation.Nullable;
import javax.persistence.Entity;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;

@Entity
@Table(name = "build_log_statistics_entry")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class BuildLogStatisticsEntry extends DomainObject {

    @OneToOne
    @JsonIgnore
    private ProgrammingSubmission programmingSubmission;

    @Nullable
    private Integer agentSetupDuration; // in seconds

    @Nullable
    private Integer testDuration; // in seconds

    @Nullable
    private Integer scaDuration; // in seconds;

    @Nullable
    private Integer totalJobDuration; // in seconds;

    @Nullable
    private Integer dependenciesDownloadedCount;

    public BuildLogStatisticsEntry() {
        // Required for Hibernate
    }

    public BuildLogStatisticsEntry(ProgrammingSubmission programmingSubmission, @Nullable Integer agentSetupDuration, @Nullable Integer testDuration, @Nullable Integer scaDuration,
            @Nullable Integer totalJobDuration, @Nullable Integer dependenciesDownloadedCount) {
        this.programmingSubmission = programmingSubmission;
        this.agentSetupDuration = agentSetupDuration;
        this.testDuration = testDuration;
        this.scaDuration = scaDuration;
        this.totalJobDuration = totalJobDuration;
        this.dependenciesDownloadedCount = dependenciesDownloadedCount;
    }

    public ProgrammingSubmission getProgrammingSubmission() {
        return programmingSubmission;
    }

    public void setProgrammingSubmission(ProgrammingSubmission programmingSubmission) {
        this.programmingSubmission = programmingSubmission;
    }

    @Nullable
    public Integer getAgentSetupDuration() {
        return agentSetupDuration;
    }

    public void setAgentSetupDuration(@Nullable Integer agentSetupDuration) {
        this.agentSetupDuration = agentSetupDuration;
    }

    @Nullable
    public Integer getTestDuration() {
        return testDuration;
    }

    public void setTestDuration(@Nullable Integer testDuration) {
        this.testDuration = testDuration;
    }

    @Nullable
    public Integer getScaDuration() {
        return scaDuration;
    }

    public void setScaDuration(@Nullable Integer scaDuration) {
        this.scaDuration = scaDuration;
    }

    @Nullable
    public Integer getTotalJobDuration() {
        return totalJobDuration;
    }

    public void setTotalJobDuration(@Nullable Integer totalJobDuration) {
        this.totalJobDuration = totalJobDuration;
    }

    @Nullable
    public Integer getDependenciesDownloadedCount() {
        return dependenciesDownloadedCount;
    }

    public void setDependenciesDownloadedCount(@Nullable Integer dependenciesDownloadedCount) {
        this.dependenciesDownloadedCount = dependenciesDownloadedCount;
    }

    public record BuildJobPartDuration(ZonedDateTime from, ZonedDateTime to) {

        /**
         * Calculate the duration in seconds for the BuildJobPartDuration.
         *
         * @return the duration in second between the from and to arguments, or null if at least one of them is null
         */
        public Integer durationInSeconds() {
            if (from == null || to == null) {
                return null;
            }
            else {
                return Math.toIntExact(ChronoUnit.SECONDS.between(from, to));
            }
        }
    }
}
