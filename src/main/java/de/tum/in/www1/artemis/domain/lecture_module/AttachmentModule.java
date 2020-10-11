package de.tum.in.www1.artemis.domain.lecture_module;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.*;

import de.tum.in.www1.artemis.domain.Attachment;

@Entity
@DiscriminatorValue(value = "A")
public class AttachmentModule extends LectureModule {

    @ManyToMany(fetch = FetchType.EAGER)
    @OrderColumn(name = "attachment_order")
    @JoinTable(name = "attachment_module_attachment", joinColumns = @JoinColumn(name = "attachment_module_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "attachment_id", referencedColumnName = "id"))
    private List<Attachment> attachments = new ArrayList<>();

    public AttachmentModule addAttachement(Attachment attachment) {
        this.attachments.add(attachment);
        return this;
    }

    public List<Attachment> getAttachements() {
        return attachments;
    }

    public void setAttachements(List<Attachment> attachments) {
        this.attachments = attachments;
    }
}
