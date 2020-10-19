package de.tum.in.www1.artemis.domain.lecture_module;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.artemis.domain.Attachment;

@Entity
@DiscriminatorValue("A")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class AttachmentModule extends LectureModule {

    @Column(name = "description")
    @Lob
    private String description;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "attachment_module_attachment", joinColumns = @JoinColumn(name = "attachment_module_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "attachment_id", referencedColumnName = "id"))
    private Set<Attachment> attachments = new HashSet<>();

    public AttachmentModule addAttachement(Attachment attachment) {
        this.attachments.add(attachment);
        return this;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Set<Attachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(Set<Attachment> attachments) {
        this.attachments = attachments;
    }
}
