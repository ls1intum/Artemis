package de.tum.in.www1.artemis.domain.settings.ide;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;

@Entity
@Table(name = "user_ide_mapping")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@IdClass(UserIdeMappingId.class)
public class UserIdeMapping {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "programming_language")
    private ProgrammingLanguage programmingLanguage;

    @ManyToOne
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "ide_id", nullable = false)
    private Ide ide;

    protected UserIdeMapping() {
        // empty constructor for Jackson
    }

    public UserIdeMapping(User user, ProgrammingLanguage programmingLanguage, Ide ide) {
        this.user = user;
        this.programmingLanguage = programmingLanguage;
        this.ide = ide;
    }

    public void setIde(Ide ide) {
        this.ide = ide;
    }

    public User getUser() {
        return user;
    }

    public ProgrammingLanguage getProgrammingLanguage() {
        return programmingLanguage;
    }

    public Ide getIde() {
        return ide;
    }
}
