package de.tum.in.www1.artemis.domain.settings.ide;

import java.util.Collection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import de.tum.in.www1.artemis.domain.DomainObject;

@Entity
@Table(name = "ides")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class Ide extends DomainObject {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "deep_link", nullable = false, unique = true)
    private String deepLink;

    @OneToMany(mappedBy = "ide", orphanRemoval = true)
    private Collection<UserIdeMapping> userIdeMappings;

    public Ide() {
        // empty constructor for Jackson
    }

    public Ide(String name, String deepLink) {
        this.name = name;
        this.deepLink = deepLink;
    }

    // region getter
    public String getName() {
        return name;
    }

    public String getDeepLink() {
        return deepLink;
    }
    // endregion
}
