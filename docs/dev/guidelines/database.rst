**********************
Database Relationship
**********************

WORK IN PROGRESS

1. Relationships
=================
Indicates how the entities are related to each other. Entities can be loaded **eagerly** ``FetchType.EAGER`` or **lazy** ``FetchType.LAZY``.
Furthermore, entities can have a special behavior on certain events of the connected entity (``CascadeTypes``)

* ``OneToOne`` e.g. One Result is connected to One Submission

 .. code:: java

    // Result.java
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(unique = true)
    @JsonView(QuizView.Before.class)
    @JsonIgnoreProperties({ "result", "participation" })
    private Submission submission;

* ``OneToMany`` One Result is connected to Many Feedback elements -> One Result has a list of Feedbacks

 .. code:: java

    // Result.java
    @OneToMany(mappedBy = "result", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn
    @JsonIgnoreProperties(value = "result", allowSetters = true)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonView(QuizView.Before.class)
    private List<Feedback> feedbacks = new ArrayList<>();

* ``ManyToMany`` Many Exercises are connected to Many LearningGoal elements -> One Exercise has a list of LearningGoals, One LearningGoal has list of Exercises

 .. code:: java

    // Exercise.java
    @ManyToMany(mappedBy = "exercises")
    public Set<LearningGoal> learningGoals = new HashSet<>();

    // LearningGoal.java
    @ManyToMany
    @JoinTable(name = "learning_goal_exercise", joinColumns = @JoinColumn(name = "learning_goal_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "exercise_id", referencedColumnName = "id"))
    @JsonIgnoreProperties("learningGoals")
    private Set<Exercise> exercises = new HashSet<>();


2. Cascade Types
=================
* ``CascadeType.ALL`` Propagates all operations (mentioned below) from Parent ``Result`` to Child ``Feedback``

 .. code-block:: java

   // Result.java
   @OneToMany(mappedBy = "result", cascade = CascadeType.ALL, orphanRemoval = true)
   @OrderColumn
   @JsonIgnoreProperties(value = "result", allowSetters = true)
   @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
   @JsonView(QuizView.Before.class)
   private List<Feedback> feedbacks = new ArrayList<>();


* ``CascadeType.PERSIST`` Propagates the **persist** operation from Parent ``AnswerCounter`` to Child ``AnswerOption``. When a ``AnswerCounter`` is saved, a ``AnswerOption`` is also saved as well

 .. code-block:: java

   // AnswerCounter.java
   @OneToOne(cascade = { CascadeType.PERSIST })
   @JoinColumn(unique = true)
   private AnswerOption answer;


* ``CascadeType.MERGE`` Propagates

 .. code-block:: java

   // Result.java
   @OneToOne(cascade = CascadeType.MERGE, fetch = FetchType.LAZY)
   @JoinColumn(unique = false)
   private User assessor;


* ``CascadeType.REMOVE`` Propagates **remove** operation from Parent ``Submission`` to Child ``Result``. When a ``Submission`` is deleted, also the ``Result`` is deleted

 .. code-block:: java

   // Submission.java
   @OneToOne(mappedBy = "submission", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
   @JsonIgnoreProperties({ "submission", "participation" })
   @JoinColumn(unique = true)
   private Result result;

* ``CascadeType.REFRESH``

Not used in Artemis yet

* ``CascadeType.DETACH``

Not used in Artemis yet


Best Practices
===============
* When using a ``List`` always annotate it with ``@OrderColumn``

 .. code-block:: java

   @OneToMany(mappedBy = "result", cascade = CascadeType.ALL, orphanRemoval = true)
   @OrderColumn
   @JsonIgnoreProperties(value = "result", allowSetters = true)
   @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
   @JsonView(QuizView.Before.class)
   private List<Feedback> feedbacks = new ArrayList<>();


Solutions for known issues
==============================
* ``JpaSystemException: null index column for collection`` caused by ``@OrderColumn`` annotation
    #. Save the child entity (e.g. `Feedback <https://github.com/ls1intum/Artemis/blob/develop/src/main/java/de/tum/in/www1/artemis/domain/Feedback.java>`_) without connection to the parent entity (e.g. `Result <https://github.com/ls1intum/Artemis/blob/develop/src/main/java/de/tum/in/www1/artemis/domain/Result.java>`_)
    #. Add back the connection of the child entity to the parent entity
    #. Save the parent entity
    #. Always use the returned value after saving the entity, see ``feedback = feedbackRepository.save(feedback);``

  .. code-block:: java

        // Avoid hibernate exception
        List<Feedback> savedFeedbacks = new ArrayList<>();
        result.getFeedbacks().forEach(feedback -> {
               feedback.setResult(null);
               feedback = feedbackRepository.save(feedback);
               feedback.setResult(result);
               savedFeedbacks.add(feedback);
        });
        result.setFeedbacks(savedFeedbacks);
        return resultRepository.save(result);


