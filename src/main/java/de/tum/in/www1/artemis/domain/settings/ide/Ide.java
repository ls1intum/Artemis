package de.tum.in.www1.artemis.domain.settings.ide;

import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import de.tum.in.www1.artemis.domain.DomainObject;

@Entity
@Table(name = "ide")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class Ide extends DomainObject {

    @Column(name = "name", nullable = false)
    private String name;

    // cloning deeplink with placeholder for the git url
    // e.g vscode://vscode.git/clone?url={cloneUrl}
    @Column(name = "deep_link", nullable = false, unique = true)
    private String deepLink;

    @OneToMany(mappedBy = "ide")
    private Set<UserIdeMapping> userIdeMappings;

    protected Ide() {
        // empty constructor for Jackson
    }

    public Ide(String name, String deepLink) {
        this.name = name;
        this.deepLink = deepLink;
    }

    public String getName() {
        return name;
    }

    public String getDeepLink() {
        return deepLink;
    }
}
