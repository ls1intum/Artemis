package de.tum.in.www1.artemis.domain;

import java.time.Instant;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

@Entity
@Table(name = "submission_version")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class SubmissionVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Submission submission;

    @ManyToOne
    private User author;

    @Lob
    @Column(name = "content")
    private String content;

    @CreatedDate
    @Column(name = "created_date", updatable = false)
    private Instant createdDate = Instant.now();

    @LastModifiedDate
    @Column(name = "last_modified_date")
    private Instant lastModifiedDate = Instant.now();

    public Long getId() {
        return id;
    }

    public SubmissionVersion id(Long id) {
        this.id = id;
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Submission getSubmission() {
        return submission;
    }

    public SubmissionVersion submission(Submission submission) {
        this.submission = submission;
        return this;
    }

    public void setSubmission(Submission submission) {
        this.submission = submission;
    }

    public User getAuthor() {
        return author;
    }

    public SubmissionVersion author(User author) {
        this.author = author;
        return this;
    }

    public void setAuthor(User author) {
        this.author = author;
    }

    public String getContent() {
        return content;
    }

    public SubmissionVersion content(String content) {
        this.content = content;
        return this;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public SubmissionVersion createdDate(Instant createdDate) {
        this.createdDate = createdDate;
        return this;
    }

    public void setCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
    }

    public Instant getLastModifiedDate() {
        return lastModifiedDate;
    }

    public SubmissionVersion lastModifiedDate(Instant lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate;
        return this;
    }

    public void setLastModifiedDate(Instant lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate;
    }
}
