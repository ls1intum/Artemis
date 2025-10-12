***********
Performance
***********

These guidelines focus on optimizing the performance of Spring Boot applications using Hibernate, with an emphasis on data economy, large-scale testing, paging, JSON data usage, and general SQL database best practices.
You can find more best practices in the `Database Guidelines <database.html>`_ section.

1. Data Economy
===============

**Database-Level Filtering**

Ensure that all filtering is done at the database level rather than in memory. This approach minimizes data transfer to the application and reduces memory usage.

Example:

.. code-block:: java

   @Query("""
          SELECT e
          FROM Exercise e
          WHERE e.course.id = :courseId
              AND e.releaseDate >= :releaseDate
          """)
   List<Exercise> findExercisesByCourseAndReleaseDate(@Param("courseId") Long courseId, @Param("releaseDate") ZonedDateTime releaseDate);

**Projections and DTOs**

When only a subset of fields is needed, use projections or Data Transfer Objects (DTOs) instead of fetching entire entities. This reduces the amount of data loaded and improves query performance.

Example:

.. code-block:: java

   @Query("""
          SELECT new com.example.dto.ExerciseDTO(e.id, e.title)
          FROM Exercise e
          WHERE e.course.id = :courseId
          AND e.releaseDate >= :releaseDate
          """)
   List<ExerciseDTO> findExerciseDTOsByCourseAndReleaseDate(@Param("courseId") Long courseId, @Param("releaseDate") ZonedDateTime releaseDate);

**Avoid Adding Rarely Used Columns to Frequently Queried Tables**

For frequently queried tables (e.g., ``User``), carefully evaluate whether you need to extend the table with additional columns that are rarely used. Since such tables are often fetched, adding more columns increases memory and network load unnecessarily.
Instead, consider introducing a new table and query the additional data only when needed.

Example:

Instead of storing the calendar subscription (ICS) token directly in the ``user`` table, an extra table was introduced:

.. code-block:: java

   @Entity
   @Table(name = "calendar_subscription_token_store")
   public class CalendarSubscriptionTokenStore extends DomainObject {

       @Column(name = "token", length = 32, nullable = false, unique = true)
       private String token;

       @OneToOne
       @JsonIgnore
       @JoinColumn(name = "jhi_user_id", nullable = false, unique = true)
       private User user;
   }

**Using JSON Data for Contained Information**

When data does not need to be queried by individual fields and is always contained within another entity,
storing it as JSON can greatly simplify the schema and improve performance. This is particularly suitable for nested structures that are always fetched together and never queried individually.

Example:

.. code-block:: java

   @Entity
   public class QuizQuestionProgress extends DomainObject {

       @Column(name = "user_id")
       private long userId;

       @Column(name = "course_id")
       private long courseId;

       @Column(name = "quiz_question_id")
       private long quizQuestionId;

       @JdbcTypeCode(SqlTypes.JSON)
       @Column(name = "progress_json", columnDefinition = "json")
       private QuizQuestionProgressData progress;


**Advantages:**

* Reduces unnecessary joins and table complexity.
* Improves query and insert/update performance dramatically.
* Simplifies maintenance and evolution of data structures.

**Use JSON columns when:**

* The data is self-contained (not referenced elsewhere).
* You don’t need to filter or sort by inner JSON fields.
* The structure changes over time and flexibility is required.

2. Large Scale Testing
======================

**Test with Realistic Data Loads**

Given that courses can have up to 2,000 students, simulate this scale during testing to identify potential performance bottlenecks when handling large amounts of data.

**Benchmarking**

Perform load testing to ensure that the application can handle the expected volume of data efficiently.

Example:

Use tools like `JMeter` or `Gatling` to simulate concurrent users and large datasets.
Test both query performance and memory usage under heavy load.

3. Paging
=========

**Implement Paging for Large Results**

For queries that return large datasets, implement pagination to avoid loading too much data into memory at once.

Example:

.. code-block:: java

   Page<Exercise> findByCourseId(Long courseId, Pageable pageable);

**Prefer Slice over Page When Counts Are Not Needed**

When you do not need numbered pages or total element counts, prefer using ``Slice`` instead of ``Page``.
``Page`` always triggers an additional count query, which can degrade performance on large datasets.

Example:

.. code-block:: java

   @Query("""
          SELECT b.id
          FROM BuildJob b
          WHERE b.buildStatus NOT IN (
              de.tum.cit.aet.artemis.programming.domain.build.BuildStatus.QUEUED,
              de.tum.cit.aet.artemis.programming.domain.build.BuildStatus.BUILDING
          )
          """)
   Slice<Long> findFinishedIds(Pageable pageable);

**Caution with Collection Fetching and Pagination**

Avoid combining ``LEFT JOIN FETCH`` with pagination, as this can cause performance issues or even fail due to the Cartesian Product problem.
Fetch related collections separately if needed.

You can find out more at https://vladmihalcea.com/hibernate-query-fail-on-pagination-over-collection-fetch

4. Avoiding the N+1 Issue
=========================

**Eager Fetching and Join Fetch**

The N+1 query issue occurs when lazy-loaded collections cause multiple queries to be executed — one for the parent entity and additional queries for each related entity.
To avoid this issue, use ``JOIN FETCH`` or ``@EntityGraph`` for performance-critical collections.

Example:

.. code-block:: java

   @Query("""
          SELECT e
          FROM Exercise e
          JOIN FETCH e.submissions
          WHERE e.course.id = :courseId
          """)
   List<Exercise> findExercisesWithSubmissions(@Param("courseId") Long courseId);

Be cautious: fetching too many relationships at once can lead to large result sets and degraded performance.

5. Optimal Use of Left Join Fetch
=================================

**Balance Between Queries**

While reducing the number of queries by using ``LEFT JOIN FETCH`` is often beneficial, overusing this strategy can lead to performance issues — especially when fetching multiple ``OneToMany`` relationships.
As a rule of thumb, avoid fetching more than **three** collections in a single query.

A script (`supporting_scripts/find_slow_queries.py`) automatically checks for excessive use of `JOIN FETCH` or `@EntityGraph` and runs as part of the GitHub Action **Query Quality Check**.

Example:

.. code-block:: java

   @Query("""
          SELECT c
          FROM Course c
              LEFT JOIN FETCH c.exercises e
              LEFT JOIN FETCH e.participations
          WHERE c.id = :courseId
          """)
   Course findCourseWithExercisesAndParticipations(@Param("courseId") Long courseId);

**Selective Fetching and FetchType Rules**

Use **lazy loading by default** and override it selectively in queries when necessary.

.. warning::

   Never use ``fetch = FetchType.EAGER`` on ``@OneToMany`` or ``@ManyToMany`` relationships!
   Doing so forces Hibernate to load all related entities every time the parent is fetched, which can easily lead to major performance issues and out-of-memory errors.

Example:

.. code-block:: java

   @Entity
   public class Exercise {

       @OneToMany(fetch = FetchType.LAZY, mappedBy = "exercise")
       private List<Participation> participations;
   }

.. note::

   The **default fetch type** for ``@OneToMany`` ``@ManyToMany`` is **LAZY** (good and should remain so).
   The **default fetch type** for ``@OneToOne`` and ``@ManyToOne`` is **EAGER** — this should be explicitly set to **LAZY** unless the related entity is always needed.

6. General SQL Database Best Practices
======================================

**Indexing**

Indexes are critical for query performance, especially on columns frequently used in ``WHERE`` clauses, ``JOIN`` conditions, or sorting.
While indexes speed up reads, they also increase storage and can slow down writes. Evaluate these tradeoffs carefully.

Example:

.. code-block:: sql

   CREATE INDEX idx_exercise_release_date ON exercise(release_date);

**Normalization vs. Denormalization**

Normalization reduces redundancy, but too much can lead to expensive joins.
In performance-critical read scenarios, consider moderate denormalization — or JSON columns — to minimize joins.

**Use of Foreign Keys**

Maintain foreign key constraints to ensure data integrity. Proper indexing can help mitigate performance costs on high-load operations.

Example:

.. code-block:: sql

   ALTER TABLE submission ADD CONSTRAINT fk_exercise FOREIGN KEY (exercise_id) REFERENCES exercise(id);

**Query Optimization**

Use tools like ``EXPLAIN`` to review execution plans and optimize slow queries.

Example:

.. code-block:: sql

   EXPLAIN SELECT * FROM exercise WHERE course_id = 1 AND release_date > '2024-01-01';

**Sorting and Counting at the Database Level**

Always perform sorting, filtering, and counting at the database level whenever possible.

**Avoid Transactions**

Transactions are generally slow and should be avoided unless necessary for consistency.

7. Server Startup Performance
=============================

**Why It Matters**

Fast startup improves developer feedback cycles and enables rolling deployments without user-facing disruptions or degraded performance.

**What Has Been Done**

All Spring beans are now marked as ``@Lazy`` by default to prevent instantiation at startup.
A *Deferred Eager Bean Instantiation* mechanism initializes remaining beans asynchronously after startup.
If initialization fails, the application stops to avoid partial functionality.

**Keeping the Number of Beans Minimal**

A GitHub Action — *Check Bean Instantiations on Startup and with Deferred Eager Initialization* — validates:

* The number of beans instantiated at startup
* The length of dependency chains

It fails when thresholds are exceeded and provides detailed diagnostics for performance tuning.

---

By following these best practices — especially regarding JSON usage, strict control of fetch types, and efficient query design —
you can build Spring Boot applications with Hibernate that are optimized for performance both at runtime and during startup.
