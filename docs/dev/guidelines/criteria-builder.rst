****************
Criteria Builder
****************

1. Requirements
===============

In order to use Criteria Builder and benefit from Specifications, we need to adjust the Repository.

1. **Metamodel:** The metamodel is used to refer to the columns of a table, in an object-oriented way. For this, each entity needs to have a corresponding metamodel class. (Artemis already fulfills this requirement)

    .. code-block:: java

        @Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
        @StaticMetamodel(User.class)
        public abstract class User_ extends de.tum.in.www1.artemis.domain.AbstractAuditingEntity_ {

            public static volatile SingularAttribute<User, String> lastName;
            public static volatile SingularAttribute<User, Instant> resetDate;
            public static volatile SingularAttribute<User, ZonedDateTime> hideNotificationsUntil;
            public static volatile SetAttribute<User, String> groups;
            public static volatile SetAttribute<User, GuidedTourSetting> guidedTourSettings;
            public static volatile SingularAttribute<User, String> login;
            public static volatile SingularAttribute<User, String> activationKey;
            public static volatile SingularAttribute<User, String> resetKey;
            public static volatile SetAttribute<User, Authority> authorities;
            ...
        }

2. **JpaSpecificationExecutor:** To execute Specifications and generate SQL statements, we need to extend the JpaSpecificationExecutor interface in our Spring Data JPA Repository.

    .. code-block:: java

        @Repository
        public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
        }

3. **(Optional) Show queries:** To ease debugging, generated queries can be displayed by enabling the output of executed SQL probes.

    .. code-block:: yaml

        jpa:
            database-platform: org.hibernate.dialect.MySQL8Dialect
            database: MYSQL
            show-sql: true


2. Generating the query
=======================

1. **Query Generation:** In most occasions, it is sufficient to execute any one of the following methods:

    .. code-block:: java

        List<T> findAll(Specification<T> spec);

        Page<T> findAll(Specification<T> spec, Pageable pageable);

        List<T> findAll(Specification<T> spec, Sort sort);

2. **Defining the initial Specification:** To generate a query with multiple Specifications, we can use the ``and()`` method for concatenation. However, the first Specification must always be called via the ``where()`` method as a rule.

    .. code-block:: java

        Specification<T> specification = Specification.where(getFirstSpecification()).and(getSecondSpecification()).and(getThirdSpecification())...and(getNthSpecification());
        return findAll(specification, sort/pageable);

3. **Defining Specifications:** A specification is a functional interface with a single method. This method has three parameters - a root, a query and a criteria builder. You don't need to specify these arguments manually because they are provided during chaining.

    .. code-block:: java

        public interface Specification<T> {
            Predicate toPredicate(Root<T> root, CriteriaQuery query, CriteriaBuilder cb);
        }

    Now we can create Specifications. We can achieve this in two ways:

    - Anonymous ``new Specification<User>()``:

        .. code-block:: java

            private Specification<User> getAllUsersMatchingEmptyCourses() {
                return new Specification<User>() {
                    @Override
                    public Predicate toPredicate(Root<User> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
                        return criteriaBuilder.isEmpty(root.get(User_.GROUPS));
                    }
                };
            }

    - Lambda expression (preferred version):

        .. code-block:: java

            private Specification<User> getAllUsersMatchingEmptyCourses() {
                return (root, query, criteriaBuilder) -> criteriaBuilder.isEmpty(root.get(User_.GROUPS));
            }


3. Operations
=============

- **AND:** We can perform the ``and`` operation on an arbitrary number of predicates via the ``criteriaBuilder`` object, which results in a new ``Predicate``.

    .. code-block:: java

        return (root, query, criteriaBuilder) -> {
            Predicate one = criteriaBuilder.equal(x, z);
            Predicate two = criteriaBuilder.notEqual(a, b);

            return criteriaBuilder.and(one, two, ...);
        };

- **OR:** We can perform the ``or`` operation on an arbitrary number of predicates via the ``criteriaBuilder`` object, which results in a new ``Predicate``.

    .. code-block:: java

        return (root, query, criteriaBuilder) -> {
            Predicate one = criteriaBuilder.equal(x, z);
            Predicate two = criteriaBuilder.notEqual(a, b);

            return criteriaBuilder.or(one, two, ...);
        };

- **EQUAL / NOT EQUAL:**

    .. code-block:: java

        return (root, query, criteriaBuilder) -> {
            Predicate one = criteriaBuilder.equal(root.get(User_.IS_INTERNAL), true);
            Predicate two = criteriaBuilder.notEqual(root.get(User_.ACTIVATED), true);

            return criteriaBuilder.and(one, two, ...);
        };

- **NOT:**

    .. code-block:: java

        return (root, query, criteriaBuilder) -> {
            ...
            Predicate predicate = criteriaBuilder.exists(subQuery).not();

            return criteriaBuilder.equals(predicate);
        };

- **IN:** To check if the collection contains a value.

    .. code-block:: java

        return (root, query, criteriaBuilder) -> {
            Predicate in = criteriaBuilder.in(root.get(User_.ID)).value(ids);
            return in;
        };


4. Joins
========

Different joins are available (e.g. Join, ListJoin, SetJoin, CollectionJoin, ...) - please choose the right one.

- If we want to join from X to Y, we need to define the column and the join type. Please mind that when the join type is not specified an Inner Join is made by default.

    .. code-block:: java

        Join<X, Y> join = root.join(X_.COLUMN, JoinType.LEFT);

- We can define custom on clauses to specify the join condition.

    .. code-block:: java

        Join<X, Y> join = root.join(X_.COLUMN, JoinType.LEFT);
        join.on(criteriaBuilder.in(join.get(Y_.NAME)).value(names));

- We can concatenate joins.

    .. code-block:: java

        Join<X, Z> join = root.join(X_.COLUMN, JoinType.LEFT).join(Y_.COLUMN, JoinType.LEFT);


4. Sub-Queries
==============

Sub-queries are usually fine unless they are dependent sub-queries (also known as `correlated <https://en.wikipedia.org/wiki/Correlated_subquery>`_ sub queries).

1. **Dependent Sub-Query:**
    In an SQL database query, a correlated sub-query is a sub-query (a query nested inside another query) that uses values from the outer query. But with a dependent sub-query you might run into performance problems because a dependent sub-query typically needs to be run once for each row in the outer query, e.g. if your outer query has 1000 rows, the sub-query will be run 1000 times.

2. **Independent Sub-Query:**
    An independent sub-query is a sub-query that can be run on its own, without the main (sub-)query. Therefore, an independent sub-query typically only needs to be evaluated once.

You can find additional information on dependent sub-queries and how to identify them `here <https://stackoverflow.com/questions/4799820/when-to-use-sql-sub-queries-versus-a-standard-join/4799847#4799847>`_.

5. Examples
===========

- Specification that matches the specified string:

    .. code-block:: java

        public static Specification<User> getSearchTermSpecification(String searchTerm) {
            String extendedSearchTerm = "%" + searchTerm + "%";
            return (root, query, criteriaBuilder) -> {
                String[] columns = new String[] { User_.LOGIN, User_.EMAIL, User_.FIRST_NAME, User_.LAST_NAME };
                Predicate[] predicates = Arrays.stream(columns).map((column) -> criteriaBuilder.like(root.get(column), extendedSearchTerm)).toArray(Predicate[]::new);

                return criteriaBuilder.or(predicates);
            };
        }

    .. code-block:: sql

        SELECT DISTINCT user FROM jhi_user user
        WHERE user.login LIKE ?
            OR user.email LIKE ?
            OR user.first_name LIKE ?
            OR user.last_name LIKE ?
        ORDER BY user.id ASC limit ?

- Specification that matches all selected courses:

    .. code-block:: java

        public static Specification<User> getAllUsersMatchingCourses(Set<Long> courseIds) {
            return (root, query, criteriaBuilder) -> {
                Root<Course> courseRoot = query.from(Course.class);

                Join<User, String> group = root.join(User_.GROUPS, JoinType.LEFT);

                // Select all possible group types
                String[] columns = new String[] { Course_.STUDENT_GROUP_NAME, Course_.TEACHING_ASSISTANT_GROUP_NAME, Course_.EDITOR_GROUP_NAME, Course_.INSTRUCTOR_GROUP_NAME };
                Predicate[] predicates = Arrays.stream(columns).map((column) -> criteriaBuilder.in(courseRoot.get(column)).value(group)).toArray(Predicate[]::new);

                // The course needs to be one of the selected
                Predicate inCourse = criteriaBuilder.in(courseRoot.get(Course_.ID)).value(courseIds);

                group.on(criteriaBuilder.or(predicates));

                query.groupBy(root.get(User_.ID)).having(criteriaBuilder.equal(criteriaBuilder.count(group), courseIds.size()));          

        	    return criteriaBuilder.in(courseRoot.get(Course_.ID)).value(courseIds);
            }
        }

    .. code-block:: sql

        SELECT DISTINCT user FROM jhi_user user
        CROSS JOIN course course
        LEFT OUTER JOIN user_groups groups ON user.id = groups.user_id
        AND (course.student_group_name IN ( groups.`groups` )
            OR course.teaching_assistant_group_name IN ( groups.`groups` )
            OR course.editor_group_name IN ( groups.`groups` )
            OR course.instructor_group_name IN ( groups.`groups` )
        WHERE (user.login LIKE ?
            OR user.email LIKE ?
            OR user.first_name LIKE ?
            OR user.last_name LIKE ?)
        AND ( course.id IN ( ? ) )
        GROUP BY user.id
        HAVING Count(groups.`groups`) = ?
        ORDER BY user.id ASC
        LIMIT ?

- Specification to get distinct results:

    .. code-block:: java

        public static Specification<User> distinct() {
            return (root, query, criteriaBuilder) -> {
                query.distinct(true);
                return null;
            };
        }

    .. code-block:: sql

       SELECT DISTINCT ...

    We can simply return null, since specifications/predicates that are null are ignored.

    .. code-block:: sql

        SpecificationComposition:

        static <T> Specification<T> composed(@Nullable Specification<T> lhs, @Nullable Specification<T> rhs, Combiner combiner) {
            return (root, query, builder) -> {
                Predicate thisPredicate = toPredicate(lhs, root, query, builder);
                Predicate otherPredicate = toPredicate(rhs, root, query, builder);

                if (thisPredicate == null) {
                    return otherPredicate;
                }
                return otherPredicate == null ? thisPredicate : combiner.combine(builder, thisPredicate, otherPredicate);
            };
        }

        @Nullable
        private static <T> Predicate toPredicate(@Nullable Specification<T> specification, Root<T> root, CriteriaQuery<?> query, CriteriaBuilder builder) {
            return specification == null ? null : specification.toPredicate(root, query, builder);
        }


6. Limitations
==============

- Executing simple queries becomes more complex — but reusable.
- Multiple "group by" are not combined but overwritten → you need a specification that combines them.
- Pagination feature of Spring Data JPA does not support the use of specifications with "group by". See `issue <https://github.com/spring-projects/spring-data-jpa/issues/2361>`_.


7. Additional links
===================

- https://spring.io/blog/2011/04/26/advanced-spring-data-jpa-specifications-and-querydsl
- https://www.baeldung.com/hibernate-criteria-queries
- https://docs.oracle.com/javaee/7/api/javax/persistence/criteria/CriteriaBuilder.html
