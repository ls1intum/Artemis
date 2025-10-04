***********
Performance
***********

These guidelines focus on optimizing the performance of Spring Boot applications using Hibernate, with an emphasis on data economy, large-scale testing, paging, and general SQL database best practices. You can find more best practices in the `Database Guidelines <database.html>`_ section.

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

2. Large Scale Testing
======================

**Test with Realistic Data Loads**

Given that courses can have up to 2,000 students, simulate this scale during testing to identify potential performance bottlenecks when handling large amounts of data.

**Benchmarking**

Perform load testing to ensure that the application can handle the expected volume of data efficiently.

Example:

Use tools like JMeter or Gatling to simulate concurrent users and large datasets.

3. Paging
=========

**Implement Paging for Large Results**

For queries that return large datasets, implement pagination to avoid loading too much data into memory at once.

Example:

.. code-block:: java

   Page<Exercise> findByCourseId(Long courseId, Pageable pageable);

**Prefer Slice over Page When Counts Are Not Needed**

When you do not need numbered pages or total element counts, prefer using ``Slice`` instead of ``Page``.
``Page`` always triggers an additional count query (even if not explicitly specified), which can negatively affect performance.
If you need Page, think about providing your own count query using the ``countQuery`` attribute of the ``@Query`` annotation.

Example:

.. code-block:: java

   @Query("""
          SELECT b.id
          FROM BuildJob b
          WHERE b.buildStatus NOT IN (de.tum.cit.aet.artemis.programming.domain.build.BuildStatus.QUEUED,
                                      de.tum.cit.aet.artemis.programming.domain.build.BuildStatus.BUILDING)
          """)
   Slice<Long> findFinishedIds(Pageable pageable);

**Caution with Collection Fetching and Pagination**

Avoid combining ``LEFT JOIN FETCH`` with pagination, as this can cause performance issues or even fail due to the Cartesian Product problem.

Example:

Instead of:

.. code-block:: java

   @Query("""
          SELECT c
          FROM Course c
              LEFT JOIN FETCH c.exercises
          WHERE c.id = :courseId
          """)
   Page<Course> findCourseWithExercises(@Param("courseId") Long courseId, Pageable pageable);

Do:

.. code-block:: java

   @Query("""
          SELECT c
          FROM Course c
          WHERE c.id = :courseId
          """)
   Course findCourseById(@Param("courseId") Long courseId);

   // Fetch exercises in a separate query if needed
   @Query("""
          SELECT e
          FROM Exercise e
          WHERE e.course.id = :courseId
          """)
   List<Exercise> findExercisesByCourseId(@Param("courseId") Long courseId);

You can find out more on https://vladmihalcea.com/hibernate-query-fail-on-pagination-over-collection-fetch

4. Avoiding the N+1 Issue
=========================

**Eager Fetching and Left Join Fetch**

The N+1 query issue occurs when lazy-loaded collections cause multiple queries to be executed — one for the parent entity and additional queries for each related entity. To avoid this issue, consider using eager fetching or ``JOIN FETCH`` for collections that are critical to performance.

Example:

.. code-block:: java

   @Query("""
          SELECT e
          FROM Exercise e
          JOIN FETCH e.submissions
          WHERE e.course.id = :courseId
          """)
   List<Exercise> findExercisesWithSubmissions(@Param("courseId") Long courseId);

In this example, the query fetches exercises along with their submissions in a single query, avoiding the N+1 problem. Be cautious, however, as fetching too many collections eagerly can lead to performance degradation due to large result sets.

5. Optimal Use of Left Join Fetch
=================================

**Balance Between Queries**

While reducing the number of queries by using ``LEFT JOIN FETCH`` is often beneficial, overusing this strategy can lead to performance issues, especially when fetching multiple ``OneToMany`` relationships. As a best practice, avoid fetching more than three ``OneToMany`` collections in a single query.

To avoid fetching too many entities at once, we have a script that checks for ``@EntityGraph`` or ``JOIN FETCH`` usages in the codebase and warns if too many relationships are fetched at once. The script can be found at ``supporting_scripts/find_slow_queries.py``

This script is also automatically invoked in the CI pipeline as GitHub Action called ``Query Quality Check``. When it fails, check the output of the GitHub Action to see which violating queries were found and reduce the number of fetched relationships in these queries.

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

This query efficiently fetches a course with its exercises and their submissions. However, if more collections are added to the fetch, consider splitting the query into multiple parts to prevent large result sets and excessive memory usage.

**Selective Fetching**

Use lazy loading by default, and override with ``JOIN FETCH`` only when necessary for performance-critical queries. This approach minimizes the risk of performance degradation due to large query results.

Example:

.. code-block:: java

   @Entity
   public class Exercise {

       @OneToMany(fetch = FetchType.LAZY, mappedBy = "exercise")
       private List<Participation> participations;

       // Other fields and methods
   }

By default, participations are lazily loaded. When you need to fetch them, use a specific ``JOIN FETCH`` query only in performance-sensitive situations. Alternatively, consider using ``@EntityGraph`` to define fetch plans for specific queries.

6. General SQL Database Best Practices
======================================

**Indexing**

Indexes are critical for query performance, especially on columns that are frequently used in ``WHERE`` clauses, ``JOIN`` conditions, or are sorted. Ensure that all key fields, such as ``releaseDate`` and ``courseId``, are properly indexed.

Indexes come with tradeoffs: while they speed up reads, they increase storage and can slow down writes (INSERT/UPDATE/DELETE). Evaluate these tradeoffs carefully.

Example:

Create an index on the ``releaseDate`` column to speed up queries filtering exercises by date:

.. code-block:: sql

   CREATE INDEX idx_exercise_release_date ON exercise(release_date);

**Normalization vs. Denormalization**

While normalization reduces data redundancy, it can lead to complex queries with multiple joins. In scenarios where read performance is critical, consider denormalizing certain tables to reduce the number of joins. However, always balance this against potential issues such as data inconsistency and increased storage requirements.

**Use of Foreign Keys**

Maintain foreign key constraints to enforce data integrity. However, be aware of the potential performance impact on insert, update, and delete operations in high-load scenarios. Proper indexing can help mitigate these effects.

Example:

.. code-block:: sql

   ALTER TABLE submission ADD CONSTRAINT fk_exercise FOREIGN KEY (exercise_id) REFERENCES exercise(id);

This foreign key ensures that submissions are always linked to a valid exercise, maintaining data integrity.

**Query Optimization**

Regularly review and optimize SQL queries to ensure they are performing efficiently. Use tools like ``EXPLAIN`` to analyze query execution plans and make adjustments where necessary.

Example:

.. code-block:: sql

   EXPLAIN SELECT * FROM exercise WHERE course_id = 1 AND release_date > '2024-01-01';

Use the ``EXPLAIN`` output to identify slow-running queries and optimize them by adding indexes, rewriting queries, or adjusting table structures.

**Sorting and Counting at the Database Level**

Perform sorting and counting operations at the database level whenever possible. This leverages the database’s optimized query engine and avoids unnecessary data transfer or in-memory processing.

**Avoid Transactions**

Transactions are generally very slow and should be avoided when possible.

7. Server Startup Performance
=============================

**Why It Matters**

Fast startup improves developer feedback cycles and enables rolling deployments without user-facing disruptions or degraded performance.

**What Has Been Done**

All Spring beans are now marked as *@Lazy*, preventing their instantiation at startup by default. This ensures that only the required beans are initialized immediately.

However, lazy initialization introduces latency on the first request. To mitigate this, a *Deferred Eager Bean Instantiation* mechanism was added, which initializes all remaining lazy beans after startup.
If a bean fails to instantiate, the application shuts down to avoid running in a partially functional state.

**Keeping the Number of Beans Minimal**

A GitHub Action — *Check Bean Instantiations on Startup and with Deferred Eager Initialization* — verifies:

* The number of beans instantiated at startup
* The length of dependency chains

It fails when thresholds are exceeded. The output provides:

* By how much the number of beans exceeds the threshold
* Which dependency chains are too long

If long dependency chains are reported, review and refactor dependencies to reduce initialization complexity and startup time.

---

By following these best practices, you can build Spring Boot applications with Hibernate that are optimized for performance — both at runtime and during startup.
