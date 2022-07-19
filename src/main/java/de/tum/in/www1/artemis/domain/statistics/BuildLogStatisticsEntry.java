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
    private Long agentSetupDuration; // in seconds

    @Nullable
    private Long testDuration; // in seconds

    @Nullable
    private Long scaDuration; // in seconds;

    @Nullable
    private Long totalJobDuration; // in seconds;

    @Nullable
    private Long dependenciesDownloadedCount;

    public BuildLogStatisticsEntry() {
        // Required for Hibernate
    }

    public BuildLogStatisticsEntry(ProgrammingSubmission programmingSubmission, @Nullable Long agentSetupDuration, @Nullable Long testDuration, @Nullable Long scaDuration,
            @Nullable Long totalJobDuration, @Nullable Long dependenciesDownloadedCount) {
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
    public Long getAgentSetupDuration() {
        return agentSetupDuration;
    }

    public void setAgentSetupDuration(@Nullable Long agentSetupDuration) {
        this.agentSetupDuration = agentSetupDuration;
    }

    @Nullable
    public Long getTestDuration() {
        return testDuration;
    }

    public void setTestDuration(@Nullable Long testDuration) {
        this.testDuration = testDuration;
    }

    @Nullable
    public Long getScaDuration() {
        return scaDuration;
    }

    public void setScaDuration(@Nullable Long scaDuration) {
        this.scaDuration = scaDuration;
    }

    @Nullable
    public Long getTotalJobDuration() {
        return totalJobDuration;
    }

    public void setTotalJobDuration(@Nullable Long totalJobDuration) {
        this.totalJobDuration = totalJobDuration;
    }

    @Nullable
    public Long getDependenciesDownloadedCount() {
        return dependenciesDownloadedCount;
    }

    public void setDependenciesDownloadedCount(@Nullable Long dependenciesDownloadedCount) {
        this.dependenciesDownloadedCount = dependenciesDownloadedCount;
    }

    public record BuildJobPartDuration(ZonedDateTime from, ZonedDateTime to) {

        public Long durationInSeconds() {
            if (from == null || to == null) {
                return null;
            }
            else {
                return ChronoUnit.SECONDS.between(from, to);
            }
        }
    }
}
