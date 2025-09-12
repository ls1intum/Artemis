package de.tum.cit.aet.artemis.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "calendar_subscription_token_store")
public class CalendarSubscriptionTokenStore extends DomainObject {

    @Column(name = "token", length = 32, nullable = false, unique = true)
    private String token;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
