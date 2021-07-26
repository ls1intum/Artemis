package de.tum.in.www1.artemis.domain.notification;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;

/**
 * Individual user's Notification Settings about one notifiction type
 */
@Entity
@Table(name = "notification_settings")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class NotificationSettings extends DomainObject {

    @Column(name = "id")
    private long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private NotificationType type;

    @Column(name = "app", columnDefinition = "boolean default true")
    private boolean app = true;

    @Column(name = "email", columnDefinition = "boolean default false")
    private boolean email = false;

    @Column(name = "user_id")
    private long user_id;

    // getter & setter

    @Override
    public Long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public boolean isApp() {
        return app;
    }

    public void setApp(boolean app) {
        this.app = app;
    }

    public boolean isEmail() {
        return email;
    }

    public void setEmail(boolean email) {
        this.email = email;
    }

    public long getUser_id() {
        return user_id;
    }

    public void setUser_id(long user_id) {
        this.user_id = user_id;
    }

    @Override
    public String toString() {
        return "NotificationSettings{" + "id=" + id + ", type='" + type + '\'' + ", app=" + app + ", email=" + email + ", user_id=" + user_id + '}';
    }

}

/*
 * Ich würde eine notification Settings Tabelle machen, die folgende columns hat: id: long type: string app: boolean email: boolean user_id: long Hier werden dann pro Nutzer und
 * pro notification type die Einstellungen gespeichert ob email und/oder App aktiv sind
 */
