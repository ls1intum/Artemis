package de.tum.in.www1.artemis.domain.lecture_module;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue(value = "V")
public class VideoModule extends LectureModule {

    @Column(name = "source")
    private String source;

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
