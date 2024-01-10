package de.tum.in.www1.artemis.domain;

import java.time.ZonedDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@Table(name = "build_job")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class BuildJob extends DomainObject {

    public enum BuildJobResult {
        SUCCESSFUL, FAILED, ERROR, CANCELED
    }

    @Column(name = "name")
    private String name;

    @Column(name = "exercise_id")
    private Long exerciseId;

    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "participation_id")
    private Long participationId;

    @Column(name = "build_agent_address")
    private String buildAgentAddress;

    @Column(name = "build_start_date")
    private ZonedDateTime buildStartDate;

    @Column(name = "build_completion_date")
    private ZonedDateTime buildCompletionDate;

    @Column(name = "repository_type_or_user_name")
    private String repositoryTypeOrUserName;

    @Column(name = "commit_hash")
    private String commitHash;

    @Column(name = "retry_count")
    private int retryCount;

    @Column(name = "priority")
    private int priority;

    @Column(name = "is_push_to_test_or_aux_repository")
    private boolean isPushToTestOrAuxRepository;

    @Column(name = "build_job_result")
    private BuildJobResult buildJobResult;

    @Column(name = "docker_image")
    private String dockerImage;

}
