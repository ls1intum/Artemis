package de.tum.cit.aet.artemis.text.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_DEMO_AND_SCHEDULING;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.ComplaintType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.ComplaintRequestDTO;
import de.tum.cit.aet.artemis.assessment.service.ComplaintService;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.factories.ExerciseDates;
import de.tum.cit.aet.artemis.exercise.factories.ExerciseFactory;
import de.tum.cit.aet.artemis.exercise.service.ExerciseConfigurationService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationService;
import de.tum.cit.aet.artemis.notification.service.notifications.GroupNotificationScheduleService;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfig;
import de.tum.cit.aet.artemis.text.api.dtos.DemoEssay;
import de.tum.cit.aet.artemis.text.api.dtos.DemoEssay.GeneralFeedback;
import de.tum.cit.aet.artemis.text.config.TextEnabled;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;
import de.tum.cit.aet.artemis.text.repository.TextSubmissionRepository;
import de.tum.cit.aet.artemis.text.service.TextAssessmentService;
import de.tum.cit.aet.artemis.text.service.TextSubmissionService;

/**
 * Creates the text exercises of the demo course seeded by the {@code demo} profile, together with what the demo users did in them.
 * <p>
 * Only exists on the node that seeds the demo data, so none of this is instantiated on a regular instance.
 */
@Conditional(TextEnabled.class)
@Controller
@Lazy
@Profile(PROFILE_DEMO_AND_SCHEDULING)
public class TextDemoApi extends AbstractTextApi {

    /**
     * Title of the demo essay that is currently ongoing. Used as its idempotency key together with the course, so it must stay stable.
     */
    private static final String ONGOING_ESSAY_TITLE = "Essay: Monolith or Microservices?";

    private static final String ONGOING_ESSAY_SHORT_NAME = "demotext";

    private static final String ONGOING_ESSAY_PROBLEM_STATEMENT = """
            # Essay: Monolith or Microservices?

            The fictional online bookshop **BookBarn** runs its entire platform as a single Spring Boot application. Catalogue, checkout, invoicing and the recommendation engine all
            live in one code base and are deployed together. The team has grown from 4 to 25 developers in two years, and they now release once every three weeks because every
            change requires a full regression test of the whole system.

            Management has asked whether BookBarn should migrate to microservices.

            ## Your task

            Write an essay of **600 to 800 words** that gives a clear recommendation. Do not sit on the fence: decide, and defend your decision.

            Your essay must address all of the following:

            1. **The actual problem.** Which of BookBarn's symptoms are caused by the architecture, and which are caused by their process? Be specific.
            2. **The case for splitting.** Name at least two concrete boundaries along which you would split the system, and explain why those boundaries and not others.
            3. **The cost.** Discuss at least three costs a migration would introduce, for example distributed transactions, operational complexity, or network failure modes.
            4. **An alternative.** Describe one change that would address the symptoms *without* moving to microservices, and explain when you would prefer it.
            5. **Your recommendation.** State what BookBarn should do in the next six months.

            ## What we are looking for

            - A clear position supported by reasoning, not a list of textbook definitions.
            - Concrete references to BookBarn's situation rather than generic advice.
            - Honest treatment of the trade-offs of the option you recommend.

            ## Assessment

            | Criterion                                   | Points |
            |---------------------------------------------|--------|
            | Problem analysis (process vs. architecture) | 2      |
            | Proposed service boundaries                 | 2      |
            | Discussion of migration costs               | 3      |
            | Alternative to a migration                  | 2      |
            | Clarity and structure                       | 1      |
            """;

    private static final String ONGOING_ESSAY_EXAMPLE_SOLUTION = """
            A strong answer recognises that most of BookBarn's pain is caused by their release process rather than by the monolith itself: a three week release train and a full
            regression suite are organisational choices, and both can be improved without distributing the system.

            It then identifies boundaries that follow business capabilities with genuinely different change rates and scaling needs, typically invoicing (stable, compliance driven)
            and recommendations (experimental, compute heavy), while arguing that catalogue and checkout share too much data to separate cheaply.

            On costs, it names the shift from local transactions to eventual consistency, the operational burden of running and observing many services with a team of 25, and the
            new class of partial failures that has to be handled explicitly in code.

            As an alternative it proposes modularising the monolith first, enforcing module boundaries in the build and introducing independent deployability only where measurement
            shows it is needed.

            The recommendation is to keep the monolith for now, invest in test and deployment automation, and extract the recommendation engine as the first service.
            """;

    /**
     * Title of the demo essay that is already over and assessed. Used as its idempotency key together with the course, so it must stay stable.
     */
    private static final String GRADED_ESSAY_TITLE = "Essay: The Value of Code Reviews";

    private static final String GRADED_ESSAY_SHORT_NAME = "demoreview";

    private static final String GRADED_ESSAY_PROBLEM_STATEMENT = """
            # Essay: The Value of Code Reviews

            Six months ago the fictional online bookshop **BookBarn** made code reviews mandatory: every change to its platform needs the approval of a second developer before it is
            merged. Since then, fewer bugs have reached production, but several of the 25 developers complain that their changes wait for days until someone reviews them.

            ## Your task

            Write an essay of **300 to 400 words** on whether mandatory code reviews are worth their cost for BookBarn. Take a clear position.

            Your essay must address all of the following:

            1. **The benefits.** What does BookBarn gain from reviews beyond finding bugs?
            2. **The costs.** Where does the waiting time come from, and how could BookBarn reduce it without giving up reviews?
            3. **Reviews and automation.** Which checks should no reviewer have to do by hand?
            4. **Your recommendation.** Should BookBarn keep, change or drop the rule, and how would it find out whether the rule works?

            ## Assessment

            | Criterion                         | Points |
            |-----------------------------------|--------|
            | Benefits beyond finding bugs      | 3      |
            | Costs and how to reduce them      | 3      |
            | Reviews and automation            | 2      |
            | Recommendation and its evaluation | 2      |
            """;

    private static final String GRADED_ESSAY_EXAMPLE_SOLUTION = """
            A strong answer argues that the reviews are worth their cost, but not mainly because they find bugs, which BookBarn's tests catch more reliably. Their main benefits are
            that knowledge of the code base spreads across the team, that design decisions stay consistent, and that changes to the boundaries between modules get a second look.

            The waiting time mostly comes from changes that are too large to review quickly and from reviews that nobody feels responsible for. Small changes, an agreed response
            time and a rotating reviewer duty address both.

            Formatting, naming conventions and simple bug patterns belong in the build, where tools check every change, so that reviewers can concentrate on design and readability.

            The recommendation keeps the rule, adds these measures and tracks the time from opening a change to merging it, to check after a few months whether the waiting time
            went down.
            """;

    private static final String EXAM_ESSAY_TITLE = "Essay: Layered Architecture";

    private static final String EXAM_ESSAY_PROBLEM_STATEMENT = """
            # Essay: Layered Architecture

            In the checkout of the fictional online bookshop **BookBarn**, the web controllers read and write the database directly, and the rules for discounts and shipping
            costs are spread across them.

            ## Your task

            Write **150 to 250 words** on how a layered architecture would structure the checkout:

            1. Name the layers you would introduce and what belongs in each of them.
            2. State which layer may use which, and why.
            3. Name one advantage the layers bring to BookBarn and one cost they introduce.

            ## Assessment

            | Criterion                         | Points |
            |-----------------------------------|--------|
            | Layers and their responsibilities | 4      |
            | Dependencies between the layers   | 2      |
            | An advantage for BookBarn         | 2      |
            | A cost of the layers              | 2      |
            """;

    private static final String EXAM_ESSAY_EXAMPLE_SOLUTION = """
            A strong answer introduces a presentation layer with the web controllers, a business logic layer that owns the rules for discounts and shipping costs, and a data
            access layer that alone talks to the database. Each layer only uses the one directly below it, so the rules depend neither on the web framework nor on the database.

            As an advantage, the rules can be tested without a browser or a database and change in one place. As a cost, even a small feature touches all three layers, and passing
            data through them adds code.
            """;

    /**
     * The essays the demo students submit for the graded essay, in turn, so that tutors see a variety of answers and scores. The weakest one comes fourth, so that it is the one
     * of the student at {@link #COMPLAINING_STUDENT_INDEX}.
     */
    private static final List<DemoEssay> DEMO_ESSAYS = List.of(new DemoEssay("""
            Code reviews help BookBarn to find bugs before they reach customers, and they let junior developers learn from senior ones. When several people know every part of the \
            code, BookBarn no longer depends on the few developers who wrote it. The downside is that changes have to wait for a review before they can be merged, and with 25 \
            developers this can slow everyone down. Some of the things reviewers check, like formatting and naming, could be done by tools instead. Overall the benefits are \
            bigger than the costs, so BookBarn should keep mandatory reviews.""", List.of(
            new GeneralFeedback(2.5,
                    "You name the key benefits, shared knowledge and learning from each other. Relate them to BookBarn: which parts of its platform would profit most?"),
            new GeneralFeedback(1.5,
                    "You see that the waiting time is the main cost, but you do not suggest how to reduce it, for example with smaller changes or an agreed response time."),
            new GeneralFeedback(3,
                    "Good point that tools can check formatting and naming. Your recommendation is clear, but say how BookBarn would find out whether the rule works."))),
            new DemoEssay(
                    """
                            Mandatory code reviews are worth their cost for BookBarn, but not mainly because they find bugs, which BookBarn's tests do more reliably. Their real value is \
                            shared knowledge: with 25 developers, a review is often the only moment someone else reads a change, and it keeps the boundaries between catalogue, checkout and \
                            invoicing intact.

                            The waiting time comes from large changes and from reviews nobody feels responsible for. BookBarn should keep changes below a few hundred lines, agree to answer \
                            reviews within half a working day and let the build check formatting and style, so that reviewers can focus on design. I recommend keeping the rule and \
                            measuring the time from opening a change to merging it, to see after three months whether the waiting time went down.""",
                    List.of(new GeneralFeedback(3, "Convincing: you look past finding bugs and explain how reviews spread knowledge and protect BookBarn's module boundaries."),
                            new GeneralFeedback(2,
                                    "You find the causes of the waiting time and propose concrete measures, but leave open who reviews when the usual reviewers are busy."),
                            new GeneralFeedback(4,
                                    "Automated style checks free the reviewers for design questions, and measuring the time to merge makes your recommendation testable."))),
            new DemoEssay("""
                    Code reviews are a best practice in modern software engineering. Large companies like Google review every change, so BookBarn should do the same. Reviews \
                    improve the quality of the code, reduce the number of bugs and make the code easier to read. They also help new team members to understand the system. \
                    Developers who complain that reviews slow them down should plan their work better and ask for reviews earlier. In conclusion, code reviews are always worth \
                    their cost.""", List.of(new GeneralFeedback(2, "You list several benefits, but you argue with what other companies do instead of with BookBarn's situation."),
                    new GeneralFeedback(1, "The waiting time is dismissed rather than analysed. Why do changes wait for days, and what could the team change?"),
                    new GeneralFeedback(2,
                            "Your conclusion is clear, but 'always worth their cost' skips the trade-off the task asks you to weigh, and automation is not discussed at all."))),
            new DemoEssay(
                    """
                            BookBarn has too many bugs, so a tester should check the whole platform before every release. That is what a code review is: someone who did not write the code \
                            tests it and finds the bugs the developers missed. This makes the software better, but it takes a lot of time, so BookBarn should only do reviews before \
                            important releases. A better solution would be to hire more testers, so that the developers do not have to wait.""",
                    List.of(new GeneralFeedback(1,
                            "A code review is a developer reading a colleague's change before it is merged, not a tester checking the whole platform before a release."),
                            new GeneralFeedback(1.5, "You do weigh the time reviews take against their benefit, which is the core trade-off of the task."), new GeneralFeedback(1,
                                    "Your recommendation follows from your argument, but hiring testers does not reduce the waiting time the developers complain about."))));

    /**
     * The position of the student who complains about the assessment of their essay among the students the essay is seeded for. A classmate rather than the demo student
     * visitors log in as, so that they can try complaining themselves.
     */
    private static final int COMPLAINING_STUDENT_INDEX = 3;

    private static final String COMPLAINT_TEXT = """
            I think my essay was assessed too strictly. The task asks whether mandatory code reviews are worth their cost, and my essay weighs exactly that: reviews make the \
            software better, but they take time. It also ends with a clear recommendation. Could you please have another look at the assessment?""";

    private static final Logger log = LoggerFactory.getLogger(TextDemoApi.class);

    private final TextExerciseRepository textExerciseRepository;

    private final TextSubmissionRepository textSubmissionRepository;

    private final ChannelService channelService;

    private final ExerciseConfigurationService exerciseConfigurationService;

    private final ExerciseVersionService exerciseVersionService;

    private final ParticipationService participationService;

    private final TextSubmissionService textSubmissionService;

    private final TextAssessmentService textAssessmentService;

    private final ComplaintService complaintService;

    private final GroupNotificationScheduleService groupNotificationScheduleService;

    private final ExerciseService exerciseService;

    public TextDemoApi(TextExerciseRepository textExerciseRepository, TextSubmissionRepository textSubmissionRepository, ChannelService channelService,
            ExerciseConfigurationService exerciseConfigurationService, ExerciseVersionService exerciseVersionService, ParticipationService participationService,
            TextSubmissionService textSubmissionService, TextAssessmentService textAssessmentService, ComplaintService complaintService,
            GroupNotificationScheduleService groupNotificationScheduleService, ExerciseService exerciseService) {
        this.textExerciseRepository = textExerciseRepository;
        this.textSubmissionRepository = textSubmissionRepository;
        this.channelService = channelService;
        this.exerciseConfigurationService = exerciseConfigurationService;
        this.exerciseVersionService = exerciseVersionService;
        this.participationService = participationService;
        this.textSubmissionService = textSubmissionService;
        this.textAssessmentService = textAssessmentService;
        this.complaintService = complaintService;
        this.groupNotificationScheduleService = groupNotificationScheduleService;
        this.exerciseService = exerciseService;
    }

    /**
     * Creates the demo essays in the given course that do not exist yet: one that is currently ongoing, see {@link ExerciseDates#ongoing()}, so that demo students can
     * participate right away, and one that is already over, with the assessed submissions of the demo students and a complaint about one of them.
     * <p>
     * Each essay is identified by its title. An essay that already exists is left as it is, together with everything that belongs to it.
     *
     * @param course   the demo course the essays belong to.
     * @param students the demo students, who submit the essay that is already over.
     * @param tutor    the demo tutor, who assesses the submissions.
     * @return the ongoing and the finished essay, in this order, whether they already existed or were created by this call.
     */
    public List<TextExercise> createDemo(Course course, List<User> students, User tutor) {
        List<TextExercise> existingExercises = textExerciseRepository.findByCourseIdWithCategories(course.getId());
        TextExercise ongoingEssay = findExisting(existingExercises, ONGOING_ESSAY_TITLE).orElseGet(
                () -> createEssay(course, ONGOING_ESSAY_TITLE, ONGOING_ESSAY_SHORT_NAME, ONGOING_ESSAY_PROBLEM_STATEMENT, ONGOING_ESSAY_EXAMPLE_SOLUTION, ExerciseDates.ongoing()));
        TextExercise gradedEssay = findExisting(existingExercises, GRADED_ESSAY_TITLE).orElseGet(() -> createGradedEssay(course, students, tutor));
        return List.of(ongoingEssay, gradedEssay);
    }

    private static Optional<TextExercise> findExisting(List<TextExercise> exercises, String title) {
        Optional<TextExercise> existingExercise = exercises.stream().filter(exercise -> title.equals(exercise.getTitle())).findFirst();
        existingExercise.ifPresent(exercise -> log.debug("Demo text exercise '{}' already exists, skipping creation", title));
        return existingExercise;
    }

    /**
     * Creates the essay of the demo test exam in the given exercise group. Like every exam exercise, it belongs to its exercise group instead of the course and has no dates of
     * its own: students work on it while they take the exam.
     *
     * @param exerciseGroup the exercise group of the demo test exam the essay belongs to.
     * @return the created essay.
     */
    public TextExercise createDemoExamExercise(ExerciseGroup exerciseGroup) {
        TextExercise essay = buildEssay(EXAM_ESSAY_TITLE, null, EXAM_ESSAY_PROBLEM_STATEMENT, EXAM_ESSAY_EXAMPLE_SOLUTION, new ExerciseDates(null, null, null, null),
                exerciseGroup.getExam().getCourse());
        essay.setCourse(null);
        essay.setExerciseGroup(exerciseGroup);
        return create(essay);
    }

    private TextExercise createEssay(Course course, String title, String shortName, String problemStatement, String exampleSolution, ExerciseDates dates) {
        TextExercise textExercise = buildEssay(title, shortName, problemStatement, exampleSolution, dates, course);
        textExercise.getCategories().add(ExerciseFactory.exerciseCategory("Architecture", "#691b0b"));
        return create(textExercise);
    }

    /**
     * Builds an essay worth 10 points, like the text exercise editor of the client does, which also proposes the default plagiarism detection settings.
     */
    private static TextExercise buildEssay(String title, @Nullable String shortName, String problemStatement, String exampleSolution, ExerciseDates dates, Course course) {
        TextExercise essay = ExerciseFactory.populateExercise(new TextExercise(), title, shortName, problemStatement, 10.0, 0.0, dates, course);
        essay.setExampleSolution(exampleSolution);
        essay.setPlagiarismDetectionConfig(new PlagiarismDetectionConfig());
        return essay;
    }

    /**
     * Creates an essay the way {@code TextExerciseCreationUpdateResource} creates a text exercise, rather than saving the entity directly. An essay of an exam gets no channel,
     * like in production: {@link ChannelService#createExerciseChannel} only creates channels for course exercises.
     */
    private TextExercise create(TextExercise textExercise) {
        textExercise.setAssessmentType(AssessmentType.MANUAL);
        textExercise.validateGeneralSettings();

        TextExercise createdExercise = textExerciseRepository.save(textExercise);
        // The configurations hold the key to their exercise, so their permanent rows are created right after it is stored, like the production creation path does.
        exerciseConfigurationService.initialize(createdExercise, textExercise.getTeamAssignmentConfig(), textExercise.getPlagiarismDetectionConfig());
        channelService.createExerciseChannel(createdExercise, Optional.empty());
        // Sends the release notification right away for a released exercise, and schedules the one about assessed submissions.
        groupNotificationScheduleService.checkNotificationsForNewExerciseAsync(createdExercise);
        exerciseVersionService.createExerciseVersion(createdExercise);

        log.info("Created demo text exercise '{}' with id {}", createdExercise.getTitle(), createdExercise.getId());
        return createdExercise;
    }

    /**
     * Creates the essay that is already over, and lets the demo users go through it the way they would in the client:
     * <ol>
     * <li>While the essay is open, every demo student starts it and submits an essay.</li>
     * <li>The due date is moved to right after the last submission, which closes the essay. Moving it further into the past would turn the submissions into late ones: their
     * results would be unrated, which rules out complaints, and the assessment dashboard would not count them as submissions.</li>
     * <li>The demo tutor assesses every submission.</li>
     * <li>The assessment due date is moved to right after the last assessment, which releases the results to the students. As it has to follow the due date, it cannot lie
     * further in the past either.</li>
     * <li>One demo student complains about their result, which leaves an open complaint for the tutors and instructors. The complaint period of a result starts at the latest of
     * its completion date, the due date and the assessment due date, so it ends a week (the complaint time of the demo course) after seeding. Until then, visitors can complain
     * about the result of the demo student themselves.</li>
     * </ol>
     * The dates of the essay are never revisited, so it stays over and assessed on a long-lived instance.
     */
    private TextExercise createGradedEssay(Course course, List<User> students, User tutor) {
        ZonedDateTime now = ZonedDateTime.now();
        // Released two weeks before seeding, the due date and the assessment due date only keep the essay open until the activity below has happened.
        TextExercise essay = createEssay(course, GRADED_ESSAY_TITLE, GRADED_ESSAY_SHORT_NAME, GRADED_ESSAY_PROBLEM_STATEMENT, GRADED_ESSAY_EXAMPLE_SOLUTION,
                new ExerciseDates(now.minusWeeks(2), null, now.plusDays(1), now.plusDays(2)));

        List<TextSubmission> submissions = new ArrayList<>();
        for (int index = 0; index < students.size(); index++) {
            submissions.add(submitEssay(essay, students.get(index), demoEssayOf(index).text()));
        }

        TextExercise closedEssay = updateEssay(essay.getId(), exercise -> exercise.setDueDate(ZonedDateTime.now()));
        List<Result> results = new ArrayList<>();
        for (int index = 0; index < students.size(); index++) {
            results.add(assessEssay(closedEssay, submissions.get(index), tutor, demoEssayOf(index).feedback()));
        }

        TextExercise gradedEssay = updateEssay(essay.getId(), exercise -> exercise.setAssessmentDueDate(ZonedDateTime.now()));
        complain(results.get(COMPLAINING_STUDENT_INDEX), students.get(COMPLAINING_STUDENT_INDEX));
        return gradedEssay;
    }

    private static DemoEssay demoEssayOf(int studentIndex) {
        return DEMO_ESSAYS.get(studentIndex % DEMO_ESSAYS.size());
    }

    /**
     * Starts the essay as the given student and submits the given text, like the text editor does: starting the exercise creates an empty submission, which the editor then
     * submits through {@code TextSubmissionResource#updateTextSubmission}.
     */
    private TextSubmission submitEssay(TextExercise essay, User student, String text) {
        return SecurityUtils.runAs(student, () -> {
            StudentParticipation participation = participationService.startExercise(essay, student, true);
            TextSubmission submission = new TextSubmission();
            submission.setId(participation.findLatestSubmission().map(Submission::getId).orElse(null));
            submission.setText(text);
            submission.setLanguage(Language.ENGLISH);
            submission.setSubmitted(true);
            return textSubmissionService.handleTextSubmission(submission, essay, student, null).submission();
        });
    }

    /**
     * Assesses the submission as the given tutor, the way the assessment editor does: opening the submission locks it with an empty result of the tutor, see
     * {@code TextAssessmentResource#retrieveParticipationForSubmission}, and submitting the assessment completes that result, see
     * {@code TextAssessmentResource#submitTextAssessment}. The feedback is general feedback, which does not refer to text blocks, so no text blocks are stored.
     */
    private Result assessEssay(TextExercise essay, TextSubmission submission, User tutor, List<GeneralFeedback> feedback) {
        return SecurityUtils.runAs(tutor, () -> {
            long resultId = textSubmissionService.lockTextSubmissionToBeAssessed(submission.getId(), 0).getResultForCorrectionRound(0).getId();
            TextSubmission lockedSubmission = textSubmissionRepository.getTextSubmissionWithResultAndTextBlocksAndFeedbackByResultIdElseThrow(resultId);
            return textAssessmentService.saveAndSubmitManualAssessment(essay, lockedSubmission, feedback.stream().map(TextDemoApi::toFeedback).toList(), resultId, null, true);
        });
    }

    /**
     * Changes a stored essay the way {@code TextExerciseCreationUpdateResource#updateTextExercise} stores a change: on the freshly loaded entity, validated before it is saved,
     * after which students are notified about the changed dates and the change is recorded as a new version.
     */
    private TextExercise updateEssay(long essayId, Consumer<TextExercise> change) {
        TextExercise essay = textExerciseRepository.findByIdElseThrow(essayId);
        ZonedDateTime originalReleaseDate = essay.getReleaseDate();
        ZonedDateTime originalAssessmentDueDate = essay.getAssessmentDueDate();
        change.accept(essay);
        essay.validateGeneralSettings();
        TextExercise updatedEssay = textExerciseRepository.save(essay);
        exerciseService.notifyAboutExerciseChanges(originalReleaseDate, originalAssessmentDueDate, updatedEssay.getProblemStatement(), updatedEssay, null);
        exerciseVersionService.createExerciseVersion(updatedEssay);
        return updatedEssay;
    }

    /**
     * Complains about the given result as the student it belongs to, like {@code ComplaintResource#createComplaint}, which passes the principal of the request on.
     */
    private void complain(Result result, User student) {
        SecurityUtils.runAs(student, () -> {
            complaintService.createComplaint(new ComplaintRequestDTO(result.getId(), COMPLAINT_TEXT, ComplaintType.COMPLAINT, Optional.empty()),
                    SecurityContextHolder.getContext().getAuthentication());
        });
    }

    /**
     * Creates the feedback the way the assessment editor sends general feedback, see {@code TextAssessmentResource#feedbackFromDto}.
     */
    private static Feedback toFeedback(GeneralFeedback generalFeedback) {
        Feedback feedback = new Feedback();
        feedback.setType(FeedbackType.MANUAL_UNREFERENCED);
        feedback.setCredits(generalFeedback.credits());
        feedback.setDetailText(generalFeedback.comment());
        return feedback;
    }
}
