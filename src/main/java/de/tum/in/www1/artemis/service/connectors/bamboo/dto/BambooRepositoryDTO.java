package de.tum.in.www1.artemis.service.connectors.bamboo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class BambooRepositoryDTO {

    private Long id;

    private String name;

    private String slug;

    private String project;

    private String scm;

    private String state;

    private String statusMessage;

    private boolean isPublic;

    private boolean isForkable;

    private String link;

    private String url;

    private String cloneUrl;

    private String cloneSshUrl;

    protected String scmType;

    /**
     * needed for Jackson
     */
    public BambooRepositoryDTO() {
    }

    public BambooRepositoryDTO(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getIdString() {
        return this.id == null ? null : Long.toString(this.id);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getScm() {
        return scm;
    }

    public void setScm(String scm) {
        this.scm = scm;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean aPublic) {
        isPublic = aPublic;
    }

    public boolean isForkable() {
        return isForkable;
    }

    public void setForkable(boolean forkable) {
        isForkable = forkable;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getCloneUrl() {
        return cloneUrl;
    }

    public void setCloneUrl(String cloneUrl) {
        this.cloneUrl = cloneUrl;
    }

    public String getCloneSshUrl() {
        return cloneSshUrl;
    }

    public void setCloneSshUrl(String cloneSshUrl) {
        this.cloneSshUrl = cloneSshUrl;
    }

    public String getScmType() {
        return scmType;
    }

    public void setScmType(String scmType) {
        this.scmType = scmType;
    }
}
