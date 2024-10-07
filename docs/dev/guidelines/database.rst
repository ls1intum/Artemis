********
Database
********

1. Retrieving and Building Objects
==================================

The cost of retrieving and building an object's relationships far exceeds the cost of selecting the object. This is especially true for relationships where it would trigger the loading of every child through the relationship hierarchy. The solution to this issue is **lazy fetching** (lazy loading). Lazy fetching allows the fetching of a relationship to be deferred until it is accessed. This is important not only to avoid the database access, but also to avoid the cost of building the objects if they are not needed. |br|

In JPA lazy fetching can be set on any relationship using the fetch attribute. The fetch can be set to either ``LAZY`` or ``EAGER`` as defined in the ``FetchType`` enum. The default fetch type is ``LAZY`` for all relationships except for **OneToOne** and **ManyToOne**, but in general it is a good idea to make every relationship ``LAZY``. The ``EAGER`` default for **OneToOne** and **ManyToOne** is for implementation reasons (more easier to implement), not because it is a good idea. |br|

We **always** use ``FetchType.LAZY``, unless there is a very strong case to be made for ``FetchType.EAGER``.

       .. note::
        Additional effort to use ``FetchType.LAZY`` does not count as a strong argument.

2. Relationships
================

A relationship is a reference from one object to another. In a relational database relationships are defined through foreign keys. The source row contains the primary key of the target row to define the relationship (and sometimes the inverse). A query must be performed to read the target objects of the relationship using the foreign key and primary key information. If there is a relationship to a collection of other objects, a ``Collection`` or ``array`` type is used to hold the contents of the relationship. In a relational database, collection relations are either defined by the target objects having a foreign key back to the source object's primary key, or by having an intermediate join table to store the relationship (containing both objects' primary keys). |br|

In this section, we depict common entity relationships we use in Artemis and show some code snippets.

* **OneToOne** A unique reference from one object to another. It is also inverse of itself. Example: one ``Complaint`` has a reference to one ``Result``.

 .. code:: java

    // Complaint.java
    @OneToOne
    @JoinColumn(unique = true)
    private Result result;

* **OneToMany** A ``Collection`` or ``Map`` of objects. It is the inverse of a **ManyToOne** relationship. Example: one ``Result`` has a list of ``Feedback`` elements. For ordered OneToMany relations see :ref:`ordered collections <ordered>`.

 .. code:: java

    // Result.java
    @OneToMany(mappedBy = "result", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn
    @JsonIgnoreProperties(value = "result", allowSetters = true)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonView(QuizView.Before.class)
    private List<Feedback> feedbacks = new ArrayList<>();


* **ManyToOne** A reference from one object to another. It is the inverse of an **OneToMany** relationship. Example: one ``Feedback`` has a reference to one ``Result``.

 .. code:: java

    // Feedback.java
    @ManyToOne
    @JsonIgnoreProperties("feedbacks")
    private Result result;


* **ManyToMany** ``A Collection`` or ``Map`` of objects. It is the inverse of itself. Example: one ``Exercise`` has a list of ``LearningGoal`` elements, one ``LearningGoal`` has list of ``Exercise`` elements. In other words: many exercises are connected to many learning goals and vice-versa.

 .. code:: java

    // Exercise.java
    @ManyToMany(mappedBy = "exercises")
    public Set<LearningGoal> learningGoals = new HashSet<>();

    // LearningGoal.java
    @ManyToMany
    @JoinTable(name = "learning_goal_exercise", joinColumns = @JoinColumn(name = "learning_goal_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "exercise_id", referencedColumnName = "id"))
    @JsonIgnoreProperties("learningGoals")
    private Set<Exercise> exercises = new HashSet<>();


.. warning::
    For **OneToMany**, **ManyToOne**, and **ManyToMany** relationships you must not forget to mark the associated elements with ``@JsonIgnoreProperties()``. Without this, the object serialization process will be stuck in an endless loop and throw an error. For more information check out the examples listed above and see: `Jackson and JsonIgnoreType <https://www.concretepage.com/jackson-api/jackson-jsonignore-jsonignoreproperties-and-jsonignoretype>`_.

.. admonition:: Lazy relationships

    Lazy relationships in Artemis may require some additional special handling to work correctly:

    * Lazy **OneToOne** relationships require the additional presence of the ``@JoinColumn`` annotation and only work in one direction.
      They can only lazily load the child of the relationship, not the parent. The parent is the entity whose database table owns the foreign key.

      E.g., You can lazily load ``ProgrammingExercise::solutionParticipation`` but not ``SolutionProgrammingExerciseParticipation::programmingExercise``, as the foreign key is part of the ``exercise`` table.

    * Lazy **ManyToOne** relationships require the additional presence of the ``@JoinColumn`` annotation.

    * Lazy **OneToMany** and **ManyToMany** relationships work without further changes.




3. Cascade Types
================
Entity relationships often depend on the existence of another entity — for example, the Result-Feedback relationship. Without the Result, the Feedback entity doesn't have any meaning of its own. When we delete the Result entity, our Feedback entity should also get deleted. For more information see: `jpa cascade types <https://www.baeldung.com/jpa-cascade-types>`_.

* ``CascadeType.ALL`` Propagates all operations mentioned below from the parent object to the to child object.

 .. code-block:: java

    // Result.java
    @OneToMany(mappedBy = "result", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn
    @JsonIgnoreProperties(value = "result", allowSetters = true)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonView(QuizView.Before.class)
    private List<Feedback> feedbacks = new ArrayList<>();


* ``CascadeType.PERSIST`` When persisting a parent entity, it also persists the child entities held in its fields. This cascade rule is helpful for relationships where the parent acts as a *container* to the child entity. If you do not use this, you have to ensure that you persist the child entity first, otherwise an error will be thrown. Example: The code below propagates the **persist** operation from parent ``AnswerCounter`` to child ``AnswerOption``. When an ``AnswerCounter`` is persisted, its ``AnswerOption`` is persisted as well. 

 .. code-block:: java

    // AnswerCounter.java
    @OneToOne(cascade = { CascadeType.PERSIST })
    @JoinColumn(unique = true)
    private AnswerOption answer;


* ``CascadeType.MERGE`` If you merge the source entity (saved/updated/synchronized) to the database, the merge is cascaded to the target of the association. This rule applies to existing objects only. Use this type to always merge/synchronize the existing data in the table with the data in the object. Example below: whenever we merge a ``Result`` to the database, i.e. save the changes on the object, the ``Assessor`` object is also merged/saved. 

 .. code-block:: java

    // Result.java
    @OneToOne(cascade = CascadeType.MERGE, fetch = FetchType.LAZY)
    @JoinColumn(unique = false)
    private User assessor;


* ``CascadeType.REMOVE`` If the source entity is removed, the target of the association is also removed. Example below: propagates **remove** operation from parent ``Submission`` to child ``Result``. When a ``Submission`` is deleted, the corresponding ``Result`` is also deleted.

 .. code-block:: java

    // Submission.java
    @OneToOne(mappedBy = "submission", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
    @JsonIgnoreProperties({ "submission", "participation" })
    @JoinColumn(unique = true)
    private Result result;


* ``CascadeType.REFRESH`` If the source entity is refreshed, it cascades the refresh to the target of the association. This is used to refresh the data in the object and its associations. This is useful for cases where there is a change which needs to be synchronized FROM the database.

Not used in Artemis yet.


4. Dynamic Fetching
===================
In Artemis, we use dynamic fetching to load the relationships of an entity on demand. As we do not want to load all relationships of an entity every time we fetch it from the database, we use as described above ``FetchType.LAZY``.
In order to load the relationships of an entity on demand, we then use one of 3 methods:

1. **EntityGraph**: The ``@EntityGraph`` annotation is the simplest way to specify a graph of relationships to fetch. It should be used when a query is auto-constructed by Spring Data JPA and does not have a custom ``@Query`` annotation. Example:

 .. code-block:: java

    // CourseRepository.java
    @EntityGraph(type = LOAD, attributePaths = { "exercises", "exercises.categories", "exercises.teamAssignmentConfig" })
    Course findWithEagerExercisesById(long courseId);

2. **JOIN FETCH**: The ``JOIN FETCH`` keyword is used in a custom query to specify a graph of relationships to fetch. It should be used when a query is custom and has a custom ``@Query`` annotation. You can see the example below. Also, explicitly or implicitly limiting queries in Hibernate can lead to in-memory paging. For more details, see the 'In-memory paging' section.

 .. code-block:: java

    // ProgrammingExerciseRepository.java
    @Query("""
            SELECT pe
            FROM ProgrammingExercise pe
                LEFT JOIN FETCH pe.exerciseGroup eg
                LEFT JOIN FETCH eg.exam e
            WHERE e.endDate > :dateTime
            """)
    List<ProgrammingExercise> findAllWithEagerExamByExamEndDateAfterDate(@Param("dateTime") ZonedDateTime dateTime);

3. **DynamicSpecificationRepository**: For repositories that use a lot of different queries with different relationships to fetch, we use the ``DynamicSpecificationRepository``. You can let a repository additionally implement this interface and then use the ``findAllWithEagerRelationships`` and then use the ``getDynamicSpecification(Collection<? extends FetchOptions> fetchOptions)`` method in combination with a custom enum implementing the ``FetchOptions`` interface to dynamically specify which relationships to fetch inside of service methods. Example:

 .. code-block:: java

    // ProgrammingExerciseFetchOptions.java
    public enum ProgrammingExerciseFetchOptions implements FetchOptions {

        GradingCriteria(Exercise_.GRADING_CRITERIA),
        AuxiliaryRepositories(Exercise_.AUXILIARY_REPOSITORIES),
        // ...

        private final String fetchPath;

        ProgrammingExerciseFetchOptions(String fetchPath) {
            this.fetchPath = fetchPath;
        }

        public String getFetchPath() {
            return fetchPath;
        }
    }


    // ProgrammingExerciseRepository.java
    @NotNull
    default ProgrammingExercise findByIdWithDynamicFetchElseThrow(long exerciseId, Collection<ProgrammingExerciseFetchOptions> fetchOptions) throws EntityNotFoundException {
        var specification = getDynamicSpecification(fetchOptions);
        return findOneByIdElseThrow(specification, exerciseId, "Programming Exercise");
    }


    // ProgrammingExerciseService.java
    final Set<ProgrammingExerciseFetchOptions> fetchOptions = withGradingCriteria ? Set.of(GradingCriteria, AuxiliaryRepositories) : Set.of(AuxiliaryRepositories);
    var programmingExercise = programmingExerciseRepository.findByIdWithDynamicFetchElseThrow(exerciseId, fetchOptions);

4. **In memory paging**: Since the flag ``hibernate.query.fail_on_pagination_over_collection_fetch: true`` is now active, it is crucial to carefully craft database queries that involve FETCH statements with collections and thoroughly test the changes. In-memory paging would cause performance decrements and is, therefore, disabled. Any use of it will lead to runtime errors.
Queries that may result in this error can return Page<> and contain JOIN FETCHES or involve internal limiting in Hibernate, such as findFirst, findLast, or findOne. One solution is to split the original query into multiple queries and a default method. The first query fetches only the IDs of entities whose full dependencies need to be fetched. The second query eagerly fetches all necessary dependencies, and the third query uses counting to build Pages, if they are utilized.
When possible, use the default Spring Data/JPA methods for second(fetching) and third(counting) queries.
An example implementation could look like this:

 .. code-block:: java

    // Repository interface
    default Page<User> searchAllByLoginOrNameInCourseAndReturnPage(Pageable pageable, String loginOrName, long courseId) {
        List<Long> userIds = findUserIdsByLoginOrNameInCourse(loginOrName, courseId, pageable).stream().map(DomainObject::getId).toList();;

        if (userIds.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }

        List<User> users = findUsersWithGroupsByIds(userIds);
        long total = countUsersByLoginOrNameInCourse(loginOrName, courseId);

        return new PageImpl<>(users, pageable, total);
    }

    @Query("""
            SELECT DISTINCT user
            FROM User user
                JOIN user.groups userGroup
                JOIN Course course ON course.id = :courseId
            WHERE user.isDeleted = FALSE
                AND (
                    user.login LIKE :#{#loginOrName}%
                    OR CONCAT(user.firstName, ' ', user.lastName) LIKE %:#{#loginOrName}%
                )
                AND (course.studentGroupName = userGroup
                    OR course.teachingAssistantGroupName = userGroup
                    OR course.editorGroupName = userGroup
                    OR course.instructorGroupName = userGroup
                )
            """)
    List<User> findUsersByLoginOrNameInCourse(@Param("loginOrName") String loginOrName, @Param("courseId") long courseId, Pageable pageable);

    @Query("""
            SELECT DISTINCT user
            FROM User user
                LEFT JOIN FETCH user.groups userGroup
            WHERE user.id IN :ids
            """)
    List<User> findUsersWithGroupsByIds(@Param("ids") List<Long> ids);

    @Query("""
            SELECT COUNT(DISTINCT user)
            FROM User user
                JOIN user.groups userGroup
                JOIN Course course ON course.id = :courseId
            WHERE user.isDeleted = FALSE
                AND (
                    user.login LIKE :#{#loginOrName}%
                    OR CONCAT(user.firstName, ' ', user.lastName) LIKE %:#{#loginOrName}%
                )
                AND (
                    course.studentGroupName = userGroup
                    OR course.teachingAssistantGroupName = userGroup
                    OR course.editorGroupName = userGroup
                    OR course.instructorGroupName = userGroup
                )
            """)
    long countUsersByLoginOrNameInCourse(@Param("loginOrName") String loginOrName, @Param("courseId") long courseId);




Best Practices
==============
* If you want to create a ``@OneToMany`` relationship or ``@ManyToMany`` relationship, first think about if it is important for the association to be ordered. If you do not need the association to be ordered, then always go for a ``Set`` instead of ``List``. If you are unsure, start with a ``Set``. 

  * **Unordered Collection**: A ``Set`` comes with certain advantages such as ensuring that there are no duplicates and null values in your collection. There are also performance arguments to use a ``Set``, especially for ``@ManyToMany`` relationships. For more information see this `stackoverflow thread <https://stackoverflow.com/questions/4655392/which-java-type-do-you-use-for-jpa-collections-and-why>`_. E.g.:

       .. code-block:: java

        // Course.java
        @OneToMany(mappedBy = "course", fetch = FetchType.LAZY)
        @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
        @JsonIgnoreProperties("course")
        private Set<Exercise> exercises = new HashSet<>();


.. _ordered:

  * **Ordered Collection without duplicates**: When you want to order the collection of objects of the relationship, while having no duplicates use a ``TreeSet``. A ``TreeSet`` is a sorted set, which means that the elements are ordered using their natural ordering or by a comparator provided at set creation time. E.g.:

       .. code-block:: java

        // IrisSubSettings.java
        @Column(name = "allowed_models")
        @Convert(converter = IrisModelListConverter.class)
        private TreeSet<String> allowedVariants = new TreeSet<>();


  * **Ordered Collection with duplicates**: When you want to order the collection of (potentially duplicated) objects of the relationship, then always use a ``List``. It is important to note here that there is no inherent order in a database table. One could argue that you can use the ``id`` field for the ordering, but there are edge cases where this can lead to problems. Therefore, for an ordered collection with duplicates, **always** annotate it with ``@OrderColumn``. An order column indicates to Hibernate that we want to order our collection based on a specific column of our data table. By default, the column name it expects is *tablenameS\_order*. For ordered collections, we also recommend that you annotate them with ``cascade = CascadeType.ALL`` and ``orphanRemoval = true``. E.g.:
       .. code-block:: java

        //Result.java
        @OneToMany(mappedBy = "result", cascade = CascadeType.ALL, orphanRemoval = true)
        @OrderColumn
        @JsonIgnoreProperties(value = "result", allowSetters = true)
        @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
        @JsonView(QuizView.Before.class)
        private List<Feedback> feedbacks = new ArrayList<>();


       .. note::
        Hibernate will take care of the ordering for you but you must create the order column in the database. This is not created automatically!


    With ordered collections, you have to be very careful with the way you persist the objects in the database. You must first persist the child object without a relation to the parent object. Then, you recreate the association and persist the parent object. Example of how to correctly persist objects in an ordered collection:

       .. code-block:: java

        // ProgrammingAssessmentService
        List<Feedback> savedFeedbacks = new ArrayList<>();
        result.getFeedbacks().forEach(feedback -> {
           // cut association to parent object
           feedback.setResult(null);
           // persist the child object without an association to the parent object. IMPORTANT: Use the object returned from the database!
           feedback = feedbackRepository.save(feedback);
           // restore the association to the parent object
           feedback.setResult(result);
           savedFeedbacks.add(feedback);
        });

        // set the association of the parent to its child objects which are now persisted in the database
        result.setFeedbacks(savedFeedbacks);
        // persist the parent object
        return resultRepository.save(result);


Solutions for known issues
==========================

* ``org.hibernate.LazyInitializationException : could not initialize proxy – no Session`` caused by ``fetchType.LAZY``. You must explicitly load the associated object from the database before trying to access those. Example of how to eagerly fetch the feedbacks with the result:

 .. code-block:: java

    // ResultRepository.java
    @Query("select r from Result r left join fetch r.feedbacks where r.id = :resultId")
    Optional<Result> findByIdWithEagerFeedbacks(@Param("resultId") Long id);


* ``JpaSystemException: null index column for collection`` caused by ``@OrderColumn`` annotation:

 There is a problem with the way you save the associated objects. You must follow this procedure:

 #. Save the child entity (e.g., `Feedback <https://github.com/ls1intum/Artemis/blob/develop/src/main/java/de/tum/cit/aet/artemis/domain/Feedback.java>`_) without connection to the parent entity (e.g., `Result <https://github.com/ls1intum/Artemis/blob/develop/src/main/java/de/tum/cit/aet/artemis/domain/Result.java>`_)
 #. Add back the connection of the child entity to the parent entity.
 #. Save the parent entity.
 #. Always use the returned value after saving the entity, see: ``feedback = feedbackRepository.save(feedback);``

 .. note::
        For more information see :ref:`ordered collections <ordered>`.


* There are ``null`` values in your ordered collection: You must annotate the ordered collection with ``CascadeType.ALL`` and ``orphanRemoval = true``. E.g:

   .. code-block:: java

    //Result.java
    @OneToMany(mappedBy = "result", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn
    @JsonIgnoreProperties(value = "result", allowSetters = true)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonView(QuizView.Before.class)
    private List<Feedback> feedbacks = new ArrayList<>();


.. |br| raw:: html

    <br />
