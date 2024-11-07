package de.tum.cit.aet.artemis.atlas.domain.profile;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.User;

@Entity
@Table(name = "learner_profile")
public class LearnerProfile extends DomainObject {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @OneToMany(mappedBy = "learnerProfile", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<CourseLearnerProfile> courseLearnerProfiles = new HashSet<>();

    public void setUser(User user) {
        this.user = user;
    }

    public User getUser() {
        return this.user;
    }

    public void setCourseLearnerProfiles(Set<CourseLearnerProfile> courseLearnerProfiles) {
        this.courseLearnerProfiles = courseLearnerProfiles;
    }

    public Set<CourseLearnerProfile> getCourseLearnerProfiles() {
        return this.courseLearnerProfiles;
    }

    public boolean addCourseLearnerProfile(CourseLearnerProfile courseLearnerProfile) {
        return this.courseLearnerProfiles.add(courseLearnerProfile);
    }

    public boolean addAllCourseLearnerProfiles(Collection<? extends CourseLearnerProfile> courseLearnerProfiles) {
        return this.courseLearnerProfiles.addAll(courseLearnerProfiles);
    }

    public boolean removeCourseLearnerProfile(CourseLearnerProfile courseLearnerProfile) {
        return this.courseLearnerProfiles.remove(courseLearnerProfile);
    }
}
