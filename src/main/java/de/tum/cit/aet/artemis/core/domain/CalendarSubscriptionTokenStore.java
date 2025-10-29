package de.tum.cit.aet.artemis.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "calendar_subscription_token_store")
public class CalendarSubscriptionTokenStore extends DomainObject {

    @Column(name = "token", length = 32, nullable = false, unique = true)
    private String token;

    @OneToOne
    @JsonIgnore
    @JoinColumn(name = "jhi_user_id", nullable = false, unique = true)
    private User user;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}
