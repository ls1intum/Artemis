package de.tum.in.www1.artemis.service.listeners;

import org.hibernate.EmptyInterceptor;
import org.hibernate.type.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.Serializable;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.service.StudentScoreService;

@Component
public class ResultInterceptor extends EmptyInterceptor {

    private final Logger log = LoggerFactory.getLogger(ResultInterceptor.class);

    private static StudentScoreService studentScoreService;

    @Autowired
    // we use lazy injection here, because a EntityListener needs an empty constructor
    public void setStudentScoreService(StudentScoreService studentScoreService) {
        ResultInterceptor.studentScoreService = studentScoreService;
    }

    @Override
    public boolean onFlushDirty(Object entity, Serializable id, Object[] currentState, Object[] previousState, String[] propertyNames, Type[] types) {
        if (entity instanceof Result) {
            log.info("onFlushDirty for Result: " + entity);
            studentScoreService.updateResult((Result) entity);
        }

        return super.onFlushDirty(entity, id, currentState, previousState, propertyNames, types);
    }
}
