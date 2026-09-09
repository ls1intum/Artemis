# Parent NOT NULL inventory

Working note, not committed. Generated 2026-08-28 against `origin/develop` plus PR
[#13581](https://github.com/ls1intum/Artemis/pull/13581) and PR
[#13589](https://github.com/ls1intum/Artemis/pull/13589).

**How this list was produced.** Every `addForeignKeyConstraint` in `src/main/resources/config/liquibase` was replayed in
`master.xml` order together with every `createTable`, `addColumn`, `addNotNullConstraint`, `dropNotNullConstraint`,
`dropColumn` and `dropTable`, which yields the current nullability of every foreign key column. Those columns were then
joined to the owning `@ManyToOne` / `@OneToOne` field of the corresponding entity and checked against every
`@OneToMany(mappedBy = ...)` / `@OneToOne(mappedBy = ...)` collection to see whether the parent holds the child in a
cascading collection. Result: **135 foreign key columns are still nullable**, 98 of which map to an owning entity field,
and **31 of those sit behind a cascading inverse collection**.

The word "parent" here means a composition: the child row carries no meaning without it. A reference to a user, an
optional configuration object or one of several mutually exclusive owners is not a parent and is listed separately at
the end.

---

## 1. Enabled by PR #13581

| Column | Entity field | Was it in #11902? |
|---|---|---|
| `result.submission_id` | `Result.submission` | Yes |
| `complaint.result_id` | `Complaint.result` | Yes |
| `complaint_response.complaint_id` | `ComplaintResponse.complaint` | Yes |
| `build_log_entry.programming_submission_id` | `BuildLogEntry.programmingSubmission` | No |
| `assessment_note.result_id` | `Result.assessmentNote` (owning `@JoinColumn`) | No |
| `grade_step.grading_scale_id` | `GradeStep.gradingScale` | No |
| `exam.course_id` | `Exam.course` | No |
| `conversation_participant.conversation_id` | `ConversationParticipant.conversation` | No |
| `answer_post.post_id` | `AnswerPost.post` | No |

`assessment_note.result_id` already had `nullable = false` on the mapping (`Result.assessmentNote` is a unidirectional
`@OneToMany` with `@JoinColumn(name = "result_id", nullable = false)`); only the column lacked the constraint.

## 1b. Enabled by PR #13589

Twenty-two further columns, all verified against a green server suite, applied by hand to MySQL both on an empty
database and on one seeded with a parent-less row for every column, and checked against every production site that
constructs the entity.

| Area | Columns | Row deleted? |
|---|---|---|
| Exam | `student_exam.exam_id`, `exercise_group.exam_id` | guarded only |
| Exam | `exam_session.student_exam_id` | deleted |
| Team | `team.exercise_id` | guarded only |
| Communication | `conversation.course_id`, `post.author_id`, `answer_post.author_id` | guarded only |
| Communication | `conversation_participant.user_id`, `reaction.user_id` | deleted |
| Atlas | `programming_exercise_details.programming_exercise_build_config_id`, `quiz_statistic_counter.quiz_point_statistic_id`, `submitted_answer.quiz_question_id` | deleted |
| Plagiarism | `plagiarism_result.exercise_id` | guarded only |
| Plagiarism | `plagiarism_comparison_matches.plagiarism_comparison_id`, `plagiarism_result_similarity_distribution.plagiarism_result_id` | deleted |

None of these were part of #11902.

`post.author_id` needed a change in the product, not only in the fixtures: the continuous plagiarism control used to
write its plagiarism case post with no author at all. It now runs only for a course that has an instructor and writes
the post in that instructor's name, and the posts it already left behind are backfilled by the same rule. A course with
nobody to act on the findings is skipped before the check starts.

Eight test fixtures were storing a row without its parent and were corrected: an answer post with no author, a reaction
saved before its user, two channels with an exercise but no course, a team created only to hang a team score on, a
course competency with no course, a student exam saved before being added to its exam, and
`CourseUtilService.createEnrolledCourseWithExamExercisesAndSubmissions`, which built its exercise groups before the exam
existed.

**Everything below this line is not resolved by #13581 or #13589.**

---

## 2. Parent relations still nullable

### 2a. The parent holds the child in a cascading collection (27 columns)

Hibernate inserts a cascaded child of a `mappedBy` collection **before** it writes the foreign key and fills the column
in with a follow-up `UPDATE`. That is invisible while the column allows `NULL` and fails on the insert as soon as it
does not. The fix is the one `Submission.addResult` uses: set the back reference in the bidirectional add helper. It
costs one line per relation, but every call site that builds the object graph in the other order then has to be
checked, because a parent that is already persisted is merged rather than persisted, and the merge writes a **second**
row instead of reusing the transient child. That is the regression #13581 hit in
`QuizSubmissionService.submitForPractice`.

**The cascade on its own is not the blocker.** #13589 constrained `exam_session.student_exam_id`,
`exercise_group.exam_id`, `student_exam.exam_id` and `team.exercise_id`, all of which sit behind a cascading collection,
because `Exam.addExerciseGroup`, `Exam.addStudentExam` and `StudentExam.addExamSession` already keep both ends in sync.
The question for each row below is therefore whether its add helper sets the back reference, not whether the parent
cascades.

The list is approximate in the other direction too: the scan that produced it reads the collection's generic type, so a
declaration such as `Set<@Valid SubmittedAnswer>` was missed even though `QuizSubmission.submittedAnswers` cascades.
That column is constrained now as well.

| Column | Owning field | Cascading inverse side |
|---|---|---|
| `attachment.attachment_unit_id` | `Attachment.attachmentVideoUnit` | `AttachmentVideoUnit.attachment` |
| `attachment.exercise_id` | `Attachment.exercise` | `Exercise.attachments` |
| `attachment.lecture_id` | `Attachment.lecture` | `Lecture.attachments` |
| `example_submission.exercise_id` | `ExampleSubmission.exercise` | `Exercise.exampleSubmissions` |
| `feedback.result_id` | `Feedback.result` | `Result.feedbacks` |
| `grading_criterion.exercise_id` | `GradingCriterion.exercise` | `Exercise.gradingCriteria` |
| `grading_instruction.grading_criterion_id` | `GradingInstruction.gradingCriterion` | `GradingCriterion.structuredGradingInstructions` |
| `iris_message.session_id` | `IrisMessage.session` | `IrisSession.messages` |
| `knowledge_area.parent_id` | `KnowledgeArea.parent` | `KnowledgeArea.children` |
| `llm_token_usage_request.trace_id` | `LLMTokenUsageRequest.trace` | `LLMTokenUsageTrace.llmRequests` |
| `plagiarism_comparison.plagiarism_result_id` | `PlagiarismComparison.plagiarismResult` | `PlagiarismResult.comparisons` |
| `plagiarism_submission_element.plagiarism_submission_id` | `PlagiarismSubmissionElement.plagiarismSubmission` | `PlagiarismSubmission.elements` |
| `post.conversation_id` | `Post.conversation` | `Conversation.posts` |
| `post.plagiarism_case_id` | `Post.plagiarismCase` | `PlagiarismCase.post` |
| `programming_exercise_auxiliary_repositories.exercise_id` | `AuxiliaryRepository.exercise` | `ProgrammingExercise.auxiliaryRepositories` |
| `programming_exercise_task.exercise_id` | `ProgrammingExerciseTask.exercise` | `ProgrammingExercise.tasks` |
| `programming_exercise_test_case.exercise_id` | `ProgrammingExerciseTestCase.exercise` | `ProgrammingExercise.testCases` |
| `quiz_question.exercise_id` | `QuizQuestion.exercise` | `QuizExercise.quizQuestions` |
| `reaction.answer_post_id` | `Reaction.answerPost` | `AnswerPost.reactions` |
| `reaction.post_id` | `Reaction.post` | `Post.reactions` |
| `review_comment_thread.group_id` | `CommentThread.group` | `CommentThreadGroup.threads` |
| `standardized_competency.first_version_id` | `StandardizedCompetency.firstVersion` | `StandardizedCompetency.childVersions` |
| `static_code_analysis_category.exercise_id` | `StaticCodeAnalysisCategory.exercise` | `ProgrammingExercise.staticCodeAnalysisCategories` |
| `text_block.submission_id` | `TextBlock.submission` | `TextSubmission.blocks` |
| `tutor_participation.assessed_exercise_id` | `TutorParticipation.assessedExercise` | `Exercise.tutorParticipations` |
| `tutorial_group.course_id` | `TutorialGroup.course` | `Course.tutorialGroups` |
| `tutorial_group_session.tutorial_group_schedule_id` | `TutorialGroupSession.tutorialGroupSchedule` | `TutorialGroupSchedule.tutorialGroupSessions` |

Two of the inverse sides are a cascaded `@OneToOne` rather than a collection (`AttachmentVideoUnit.attachment` and
`PlagiarismCase.post`); the insert order is the same either way.

Three of these columns (`feedback.result_id`, `exercise_group.exam_id`, `grading_criterion.exercise_id`) were tried
during #13581 and reverted after the server test suite failed on exactly this insert order.

Some entries in the table are mutually exclusive with a sibling and can never be required on their own even once the
cascade is handled: `attachment` belongs to a lecture, an exercise or an attachment unit; `post` belongs to a
conversation or a plagiarism case; `reaction` reacts to a post or to an answer post; a `tutorial_group_session` created
by hand has no schedule; a root `knowledge_area` has no parent; the first `standardized_competency` version has no
predecessor.

**Resolved in #11902?** `feedback.result_id` yes, and with the same technique: #11902 added
`Result.addFeedbacks(...)` doing `feedback.setResult(this)`. None of the other 30 were part of it.

### 2b. The link is stored on the other side

| Column | Why |
|---|---|
| `participation.exercise_id` | A programming exercise points at its template and solution participation through `exercise.template_participation_id` and `exercise.solution_participation_id`, so for those two the foreign key runs the other way and they hold no exercise of their own. The column is only set for student participations. Requiring it, as #11902 did, deletes the template and solution participation of every programming exercise. |

**Resolved in #11902?** No. #11902 added this constraint together with a delete of the offending rows, which is what
made it dangerous.

### 2c. The child legitimately has no parent today

| Column | Why |
|---|---|
| `submission.participation_id` | `ExampleSubmissionService.save` marks the submission as an example submission and stores it without ever giving it a participation, so every example submission is a submission with no participation. |

**Resolved in #11902?** Yes, and it is the right model: #11902 introduced `ExampleParticipation extends Participation`
("to ensure all submissions have a valid participation reference") and then required the column. Reviving that class is
the prerequisite for this constraint, and it is the single largest remaining item.

### 2d. Only fixtures and negative tests stand in the way

| Column | Why |
|---|---|
| `lecture.course_id` | Nothing in the mapping blocks it: `Course.lectures` is `@OneToMany(mappedBy = "course", fetch = LAZY)` with **no** cascade. Enabling it fails in three places instead. `CourseUtilService.createEnrolledCoursesWithExercisesAndLectures` calls `lecture1.setCourse(null)` before saving "to receive lecture ID" and reattaches the course afterwards. `AttachmentResourceIntegrationTest.deleteAttachment_noCourse` and `AttachmentVideoUnitsIntegrationTest.testAll_LectureWithoutCourse_shouldReturnBadRequest` persist a lecture without a course on purpose, to assert that the attachment endpoints answer `400`. The constraint makes that state unreachable, so those two tests and the defensive branches they cover would have to go. |

This is the most promising follow-up: one fixture reordering plus a decision about two negative tests.

**Resolved in #11902?** No, it was not part of it.

### 2e. The child carries no field for its parent

| Column | Why |
|---|---|
| `exercise_variant_group.course_id` | `ExerciseVariantGroup` has no `course` field at all. The association is a unidirectional `@OneToMany` with `@JoinColumn(name = "course_id")` on `Course`, and `ExerciseVariantGroupResource.createExerciseVariantGroup` stores the group through its own repository, so nothing carries the course at insert time and the column is only filled by a later update from the collection. Tried in #13589 and reverted after 48 failures. Requiring it means giving the entity a real `@ManyToOne`, which is a model change. |

### 2f. The two ends point at each other

| Columns | Why |
|---|---|
| `plagiarism_comparison.submission_a_id`, `plagiarism_comparison.submission_b_id`, `plagiarism_submission.plagiarism_comparison_id` | A comparison points at its two submissions and each submission points back at the comparison. Requiring all three makes the graph impossible to write: whichever row goes first references one that does not exist yet. Tried in #13589 and reverted after 56 `TransientPropertyValueException` failures. At least one side has to stay nullable. |

### 2g. Genuine parents with no blocker found, not yet evaluated

Eighteen of the twenty candidates listed here originally were worked through in #13589: sixteen are now constrained and
four turned out to be blocked, which is what sections 2e and 2f record. Two were never attempted:

`competency.course_id`, `competency_relation.head_competency_id`, `competency_relation.tail_competency_id`,
`conversation.course_id`, `example_submission.submission_id`, `exercise_variant_group.course_id`,
`iris_message_content.message_id`, `lecture_transcription.lecture_unit_id`,
`lecture_unit_processing_state.lecture_unit_id`, `participant_score.exercise_id`,
`plagiarism_comparison.submission_a_id`, `plagiarism_comparison.submission_b_id`,
`plagiarism_comparison_matches.plagiarism_comparison_id`, `plagiarism_result.exercise_id`,
`plagiarism_result_similarity_distribution.plagiarism_result_id`, `plagiarism_submission.plagiarism_comparison_id`,
`programming_exercise_details.programming_exercise_build_config_id`, `quiz_statistic_counter.quiz_point_statistic_id`,
`submitted_answer.quiz_question_id`, `submitted_answer.submission_id`

`programming_exercise_details` is the secondary table of `ProgrammingExercise`, which Hibernate writes with a `MERGE`,
so a not-null constraint there needs its own look. `quiz_statistic_counter` holds several counter subtypes in one table,
so `quiz_point_statistic_id` is only set for point counters and can never be required.

**Resolved in #11902?** Neither of them.

Note that `plagiarism_*` rows are wiped by the plagiarism cleanup job, and `participant_score` rows are rebuilt by the
participant score scheduler, so a constraint there mainly protects against half-written state rather than against
long-lived orphans.

---

## 3. Foreign keys that are not parent relations

No constraint is wanted for these, so they are not gaps.

- **Actor references (22 columns to `jhi_user`)**: `answer_post.author_id`, `assessment_note.creator_id`,
  `complaint_response.reviewer_id`, `result.assessor_id`, `data_export.user_id`, `team.owner_id`,
  `plagiarism_case.verdict_by_id` and so on. An unassessed result has no assessor, an anonymised post has no author.
- **One of several mutually exclusive owners**: `exercise.course_id` versus `exercise.exercise_group_id` (course
  exercise versus exam exercise), `grading_scale.course_id` versus `grading_scale.exam_id`,
  `participation.student_id` versus `participation.team_id`, `complaint.student_id` versus `complaint.team_id`,
  `participant_score.user_id` versus `participant_score.team_id`, `plagiarism_case.student_id` versus
  `plagiarism_case.team_id`, `forwarded_message.destination_post_id` versus `destination_answer_id`,
  `repository_vcs_access_token.participation_id` versus `auxiliary_repository_id`, `slide.attachment_unit_id` versus
  `slide.exercise_id`, and the four `iris_session` context columns, which depend on the session subtype.
- **Optional configuration and feature objects**: `course.course_configuration_id`,
  `course.online_course_configuration_id`, `course.tutorial_groups_configuration_id`, `exercise.build_plan_id`,
  `exercise.submission_policy_id`, `exercise.team_assignment_config_id`, `exercise.plagiarism_detection_config_id`,
  `exercise.quiz_point_statistic_id`, `jhi_user.learner_profile_id`, `online_course_configuration.lti_platform_id`,
  `submission.quiz_batch`, `tutorial_group.tutorial_group_channel_id`.
- **Filled in later in the lifecycle**: `build_job.result_id` (a queued or failed build has no result),
  `participant_score.last_result_id` and `last_rated_result_id`, `course_request.created_course_id` (set on approval),
  `text_block.feedback_id` (a block exists before anyone comments on it), `lecture_unit.exercise_id` (only an
  `ExerciseUnit` has one), the three `llm_token_usage_trace` context columns, `competency.linked_*`.

---

## 4. Summary against the previous PR

#11902 ("Redesign core relationships for improved stability and higher code quality", closed 2026-02-05 without being
merged) added six not-null constraints: `result.submission_id`, `complaint.result_id`, `complaint_response.complaint_id`,
`feedback.result_id`, `participation.exercise_id` and `submission.participation_id`.

- **Three of the six are delivered by #13581**: `result.submission_id`, `complaint.result_id`,
  `complaint_response.complaint_id`.
- **One is deliberately not delivered and should not be revived as written**: `participation.exercise_id` would delete
  the template and solution participation of every programming exercise.
- **Two remain open with a known path**: `feedback.result_id` needs the back reference in `Result.addFeedbacks`, which
  #11902 had already written; `submission.participation_id` needs `ExampleParticipation`, which #11902 had also already
  written.
- **#13581 adds six constraints that #11902 never touched**: `build_log_entry.programming_submission_id`,
  `assessment_note.result_id`, `grade_step.grading_scale_id`, `exam.course_id`,
  `conversation_participant.conversation_id`, `answer_post.post_id`.

Nothing still listed in section 2 or 3 is resolved by #13581 or #13589.
