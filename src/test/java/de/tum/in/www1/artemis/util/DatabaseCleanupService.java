package de.tum.in.www1.artemis.util;

import java.util.List;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.Table;
import javax.transaction.Transactional;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Service;

/**
 * Test utility service that allows to truncate all tables in the test database.
 * Inspired by: https://medium.com/@dSebastien/cleaning-up-database-tables-after-each-integration-test-method-with-spring-boot-2-and-kotlin-7279abcdd5cc
 */
@Service
public class DatabaseCleanupService implements InitializingBean {

    private final EntityManager entityManager;

    private List<String> tableNames;

    public DatabaseCleanupService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Uses the JPA metamodel to find all managed types then try to get the [Table] annotation's from each (if present) to discover the table name.
     * If the [Table] annotation is not defined then we skip that entity
     */
    @Override
    public void afterPropertiesSet() {
        var metaModel = entityManager.getMetamodel();
        tableNames = metaModel.getManagedTypes().stream().filter(managedType -> {
            var annotation = AnnotationUtils.findAnnotation(managedType.getJavaType(), Table.class);
            return annotation != null;
        }).map(managedType -> {
            var annotation = AnnotationUtils.findAnnotation(managedType.getJavaType(), Table.class);
            return annotation.name();
        }).collect(Collectors.toList());
    }

    /**
     * Utility method that truncates all identified tables
     */
    @Transactional // ok
    public void clearDatabase() {
        entityManager.flush();
        entityManager.createNativeQuery("SET REFERENTIAL_INTEGRITY FALSE").executeUpdate();
        tableNames.forEach(tableName -> entityManager.createNativeQuery("TRUNCATE TABLE " + tableName).executeUpdate());
        entityManager.createNativeQuery("SET REFERENTIAL_INTEGRITY TRUE").executeUpdate();
    }
}
