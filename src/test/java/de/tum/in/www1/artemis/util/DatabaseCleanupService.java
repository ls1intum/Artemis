package de.tum.in.www1.artemis.util;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.JoinTable;
import javax.persistence.Table;
import javax.persistence.metamodel.ManagedType;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Test utility service that allows to truncate all tables in the test database.
 * Inspired by: https://medium.com/@dSebastien/cleaning-up-database-tables-after-each-integration-test-method-with-spring-boot-2-and-kotlin-7279abcdd5cc
 */
@Service
public class DatabaseCleanupService implements InitializingBean {

    private final EntityManager entityManager;

    private List<String> tableNames;

    private List<String> joinTableNames;

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
            var tableAnnotation = AnnotationUtils.findAnnotation(managedType.getJavaType(), Table.class);
            return tableAnnotation != null && !tableAnnotation.name().startsWith("view_");
        }).map(managedType -> {
            var annotation = AnnotationUtils.findAnnotation(managedType.getJavaType(), Table.class);
            return annotation.name();
        }).toList();

        joinTableNames = metaModel.getEntities().stream().map(ManagedType::getAttributes).flatMap(Collection::stream).filter(attribute -> {
            var joinTableAnnotation = AnnotationUtils.findAnnotation((Field) attribute.getJavaMember(), JoinTable.class);
            return joinTableAnnotation != null && !joinTableAnnotation.name().startsWith("view_");
        }).map(attribute -> {
            var joinTableAnnotation = AnnotationUtils.findAnnotation((Field) attribute.getJavaMember(), JoinTable.class);
            return joinTableAnnotation.name();
        }).toList();
    }

    /**
     * Utility method that truncates all identified tables
     */
    @Transactional // ok
    public void clearDatabase() {
        entityManager.flush();
        entityManager.createNativeQuery("SET REFERENTIAL_INTEGRITY FALSE").executeUpdate();
        tableNames.forEach(tableName -> entityManager.createNativeQuery("TRUNCATE TABLE " + tableName).executeUpdate());
        joinTableNames.forEach(joinTableName -> entityManager.createNativeQuery("TRUNCATE TABLE " + joinTableName).executeUpdate());
        entityManager.createNativeQuery("SET REFERENTIAL_INTEGRITY TRUE").executeUpdate();

    }
}
