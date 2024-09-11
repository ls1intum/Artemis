package de.tum.cit.aet.artemis.domain.settings.ide;

import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import de.tum.cit.aet.artemis.domain.DomainObject;

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

    public Ide() {
        // empty constructor for Jackson
        // public for server tests
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

    public static Ide[] PREDEFINED_IDES = { new Ide("VS Code", "vscode://vscode.git/clone?url={cloneUrl}"),
            new Ide("IntelliJ", "jetbrains://idea/checkout/git?idea.required.plugins.id=Git4Idea&checkout.repo={cloneUrl}"),
            new Ide("PyCharm", "jetbrains://pycharm/checkout/git?idea.required.plugins.id=Git4Idea&checkout.repo={cloneUrl}"),
            new Ide("CLion", "jetbrains://clion/checkout/git?idea.required.plugins.id=Git4Idea&checkout.repo={cloneUrl}"), new Ide("XCode", "xcode://clone?repo={cloneUrl}"), };
}
