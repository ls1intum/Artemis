package de.tum.in.www1.artemis.service;

import javax.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SessionFactoryService {

    private SessionFactory sessionFactory;

    @Autowired
    public SessionFactoryService(EntityManagerFactory factory) {
        if (factory.unwrap(SessionFactory.class) == null) {
            throw new NullPointerException("factory is not a hibernate factory");
        }
        this.sessionFactory = factory.unwrap(SessionFactory.class);
    }

    public SessionFactory getSessionFactory() {
        return sessionFactory;
    }
}
