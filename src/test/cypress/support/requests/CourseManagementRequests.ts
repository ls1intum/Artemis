import { Participation } from 'app/entities/participation/participation.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { Exam } from 'app/entities/exam.model';
import { Exercise } from 'app/entities/exercise.model';
import { Exercise as CypressExercise } from 'src/test/cypress/support/pageobjects/exam/ExamParticipation';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Course, CourseInformationSharingConfiguration } from 'app/entities/course.model';
import { BASE_API, CourseWideContext, DELETE, EXERCISE_TYPE, GET, POST, PUT } from '../constants';
import programmingExerciseTemplate from '../../fixtures/exercise/programming/template.json';
import { dayjsToString, generateUUID, parseArrayBufferAsJsonObject, titleLowercase } from '../utils';
import examTemplate from '../../fixtures/exam/template.json';
import day from 'dayjs/esm';
import { CypressCredentials } from '../users';
import textExerciseTemplate from '../../fixtures/exercise/text/template.json';
import modelingExerciseTemplate from '../../fixtures/exercise/modeling/template.json';
import assessment_submission from '../../fixtures/exercise/programming/assessment/submission.json';
import quizTemplate from '../../fixtures/exercise/quiz/template.json';
import multipleChoiceSubmissionTemplate from '../../fixtures/exercise/quiz/multiple_choice/submission.json';
import shortAnswerSubmissionTemplate from '../../fixtures/exercise/quiz/short_answer/submission.json';
import modelingExerciseSubmissionTemplate from '../../fixtures/exercise/modeling/submission.json';
import lectureTemplate from '../../fixtures/lecture/template.json';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { Channel } from 'app/entities/metis/conversation/channel.model';
import { Post } from 'app/entities/metis/post.model';
import { Lecture } from 'app/entities/lecture.model';
import { GroupChat } from 'app/entities/metis/conversation/group-chat.model';

export const COURSE_BASE = BASE_API + 'courses/';
export const COURSE_ADMIN_BASE = BASE_API + 'admin/courses';
export const EXERCISE_BASE = BASE_API + 'exercises/';
export const PROGRAMMING_EXERCISE_BASE = BASE_API + 'programming-exercises/';
export const QUIZ_EXERCISE_BASE = BASE_API + 'quiz-exercises/';
export const TEXT_EXERCISE_BASE = BASE_API + 'text-exercises/';
export const MODELING_EXERCISE_BASE = BASE_API + 'modeling-exercises';

/**
 * A class which encapsulates all cypress requests related to course management.
 */
export class CourseManagementRequests {
    /**
     * Deletes the course with the specified id.
     * @param course the course
     * @param admin the admin user
     * @returns <Chainable> request response
     */
    deleteCourse(course: Course, admin: CypressCredentials) {
        // Sometimes the server fails with a ConstraintViolationError if we delete the course immediately after a login
        cy.wait(20000);
        if (course) {
            cy.login(admin);
            return cy.request({ method: DELETE, url: `${COURSE_ADMIN_BASE}/${course.id}` });
        }
    }

    /**
     * Creates a course with the specified title and short name.
     * @param customizeGroups whether the predefined groups should be used (so we don't have to wait more than a minute between course and programming exercise creation)
     * @param courseName the title of the course (will generate default name if not provided)
     * @param courseShortName the short name (will generate default name if not provided)
     * @param start the start date of the course (default: now() - 2 hours)
     * @param end the end date of the course (default: now() + 2 hours)
     * @param fileName the course icon file name (default: undefined)
     * @param file the course icon file blob (default: undefined)
     * @returns <Chainable> request response
     */
    createCourse(
        customizeGroups = false,
        courseName = 'Course ' + generateUUID(),
        courseShortName = 'cypress' + generateUUID(),
        start = day().subtract(2, 'hours'),
        end = day().add(2, 'hours'),
        fileName?: string,
        file?: Blob,
        allowCommunication = true,
        allowMessaging = true,
    ): Cypress.Chainable<Cypress.Response<Course>> {
        const course = new Course();
        course.title = courseName;
        course.shortName = courseShortName;
        course.testCourse = true;
        course.startDate = start;
        course.endDate = end;

        if (allowCommunication && allowMessaging) {
            course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;
        } else if (allowCommunication) {
            course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_ONLY;
        } else if (allowMessaging) {
            course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.MESSAGING_ONLY;
        } else {
            course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.DISABLED;
        }

        const allowGroupCustomization: boolean = Cypress.env('allowGroupCustomization');
        if (customizeGroups && allowGroupCustomization) {
            course.studentGroupName = Cypress.env('studentGroupName');
            course.teachingAssistantGroupName = Cypress.env('tutorGroupName');
            course.editorGroupName = Cypress.env('editorGroupName');
            course.instructorGroupName = Cypress.env('instructorGroupName');
        }
        const formData = new FormData();
        formData.append('course', new File([JSON.stringify(course)], 'course', { type: 'application/json' }));
        if (file) {
            formData.append('file', file, fileName);
        }
        return cy.request({
            url: COURSE_ADMIN_BASE,
            method: POST,
            body: formData,
        });
    }

    /**
     * Creates a programming exercise with the specified title and other data.
     * @param body an object containing either the course or exercise group the exercise will be added to
     * @param scaMaxPenalty? the max percentage (0-100) static code analysis can reduce from the points (if sca should be disabled pass null)
     * @param recordTestwiseCoverage enable testwise coverage analysis for this exercise (default is false)
     * @param releaseDate when the programming exercise should be available (default is now)
     * @param dueDate when the programming exercise should be due (default is now + 1 day)
     * @param title the title of the programming exercise
     * @param programmingShortName the short name of the programming exercise
     * @param packageName the package name of the programming exercise
     * @param assessmentDate the due date of the assessment
     * @param assessmentType the assessment type of the exercise (default is AUTOMATIC)
     * @returns <Chainable> request response
     */
    createProgrammingExercise(
        body: { course: Course } | { exerciseGroup: ExerciseGroup },
        scaMaxPenalty?: number,
        recordTestwiseCoverage = false,
        releaseDate = day(),
        dueDate = day().add(1, 'day'),
        title = 'Programming ' + generateUUID(),
        programmingShortName = 'programming' + generateUUID(),
        packageName = 'de.test',
        assessmentDate = day().add(2, 'days'),
        assessmentType = ProgrammingExerciseAssessmentType.AUTOMATIC,
    ): Cypress.Chainable<Cypress.Response<ProgrammingExercise>> {
        const template = {
            ...programmingExerciseTemplate,
            title,
            shortName: programmingShortName,
            packageName,
            channelName: 'exercise-' + titleLowercase(title),
            assessmentType: ProgrammingExerciseAssessmentType[assessmentType],
        };
        const exercise: ProgrammingExercise = Object.assign({}, template, body) as ProgrammingExercise;
        // eslint-disable-next-line no-prototype-builtins
        const isExamExercise = body.hasOwnProperty('exerciseGroup');
        if (!isExamExercise) {
            exercise.releaseDate = releaseDate;
            exercise.dueDate = dueDate;
            exercise.assessmentDueDate = assessmentDate;
        }

        if (scaMaxPenalty) {
            exercise.staticCodeAnalysisEnabled = true;
            exercise.maxStaticCodeAnalysisPenalty = scaMaxPenalty;
        }

        exercise.testwiseCoverageEnabled = recordTestwiseCoverage;

        return cy.request({
            url: PROGRAMMING_EXERCISE_BASE + 'setup',
            method: POST,
            body: exercise,
        });
    }

    /**
     * Submits the example submission to the specified repository.
     * @param repositoryId the repository id. The repository id is equal to the participation id.
     * @returns <Chainable> request
     */
    makeProgrammingExerciseSubmission(repositoryId: number) {
        // TODO: For now it is enough to submit the one prepared json file, but in the future this method should support different package names and submissions.
        return cy.request({
            url: `${BASE_API}repository/${repositoryId}/files?commit=yes`,
            method: PUT,
            body: assessment_submission,
        });
    }

    updateModelingExerciseDueDate(exercise: ModelingExercise, due = day()) {
        exercise.dueDate = due;
        return this.updateExercise(exercise, EXERCISE_TYPE.Modeling);
    }

    private updateExercise(exercise: Exercise, type: EXERCISE_TYPE) {
        let url: string;
        switch (type) {
            case EXERCISE_TYPE.Programming:
                url = PROGRAMMING_EXERCISE_BASE;
                break;
            case EXERCISE_TYPE.Text:
                url = TEXT_EXERCISE_BASE;
                break;
            case EXERCISE_TYPE.Modeling:
                url = MODELING_EXERCISE_BASE;
                break;
            case EXERCISE_TYPE.Quiz:
            default:
                throw new Error(`Exercise type '${type}' is not supported yet!`);
        }
        return cy.request({
            url,
            method: PUT,
            body: exercise,
        });
    }

    /**
     * Adds the specified student to the course.
     * @param course the course
     * @param user the user
     * @returns <Chainable> request response
     */
    addStudentToCourse(course: Course, user: CypressCredentials) {
        return this.addUserToCourse(course.id!, user.username, 'students');
    }

    /**
     * Adds the specified user to the tutor group in the course
     */
    addTutorToCourse(course: Course, user: CypressCredentials) {
        return this.addUserToCourse(course.id!, user.username, 'tutors');
    }

    /**
     * Adds the specified user to the instructor group in the course
     */
    addInstructorToCourse(course: Course, user: CypressCredentials) {
        return this.addUserToCourse(course.id!, user.username, 'instructors');
    }

    private addUserToCourse(courseId: number, username: string, roleIdentifier: string) {
        return cy.request({ method: POST, url: `${COURSE_BASE}${courseId}/${roleIdentifier}/${username}` });
    }

    createCoursePost(course: Course, title: string, content: string, context: CourseWideContext) {
        const body = {
            content,
            course: {
                id: course.id,
                title: course.title,
            },
            courseWideContext: context,
            displayPriority: 'NONE',
            title,
            tags: [],
            visibleForStudents: true,
        };
        return cy.request({ method: POST, url: `${COURSE_BASE}${course.id}/posts`, body });
    }

    createCourseMessageChannel(course: Course, name: string, description: string, isAnnouncementChannel: boolean, isPublic: boolean) {
        const body = {
            description,
            isAnnouncementChannel,
            isPublic,
            name,
            type: 'channel',
        };
        return cy.request({ method: POST, url: `${COURSE_BASE}${course.id}/channels`, body });
    }

    getExerciseChannel(courseId: number, exerciseId: number) {
        return cy.request({ method: GET, url: `${COURSE_BASE}${courseId}/exercises/${exerciseId}/channel` });
    }

    getLectureChannel(courseId: number, exerciseId: number) {
        return cy.request({ method: GET, url: `${COURSE_BASE}${courseId}/lectures/${exerciseId}/channel` });
    }

    createCourseMessageGroupChat(course: Course, users: Array<string>) {
        const body = users;
        return cy.request({ method: POST, url: `${COURSE_BASE}${course.id}/group-chats`, body });
    }

    createCourseMessage(course: Course, targetId: number, type: string, message: string) {
        const body = {
            content: message,
            conversation: {
                id: targetId,
                type,
            },
            displayPriority: 'NONE',
            visibleForStudents: true,
        };
        return cy.request({ method: POST, url: `${COURSE_BASE}${course.id}/messages`, body });
    }

    updateCourseMessageGroupChatName(course: Course, groupChat: GroupChat, name: string) {
        const body = {
            name,
            type: 'groupChat',
        };
        return cy.request({ method: PUT, url: `${COURSE_BASE}${course.id}/group-chats/${groupChat.id}`, body });
    }

    joinUserIntoChannel(course: Course, channel: Channel, user: CypressCredentials) {
        const body = [`${user.username}`];
        return cy.request({ method: POST, url: `${COURSE_BASE}${course.id}/channels/${channel.id}/register`, body });
    }

    createCoursePostReply(course: Course, post: Post, content: string) {
        const body = {
            content,
            post,
            resolvesPost: true,
        };
        return cy.request({ method: POST, url: `${COURSE_BASE}${course.id}/answer-posts`, body });
    }

    createCourseExercisePost(course: Course, exercise: Exercise, title: string, content: string) {
        const body = {
            content,
            displayPriority: 'NONE',
            exercise: {
                id: exercise.id,
                title: exercise.title,
                type: exercise.type,
            },
            tags: [],
            title,
            visibleForStudents: true,
        };
        return cy.request({ method: POST, url: `${COURSE_BASE}${course.id}/posts`, body });
    }

    createCourseLecturePost(course: Course, lecture: Lecture, title: string, content: string) {
        const body = {
            content,
            displayPriority: 'NONE',
            lecture: {
                id: lecture.id,
                title: lecture.title,
            },
            tags: [],
            title,
            visibleForStudents: true,
        };
        return cy.request({ method: POST, url: `${COURSE_BASE}${course.id}/posts`, body });
    }

    /**
     * Creates an exam with the provided settings.
     * @param exam the exam object created by a {@link ExamBuilder}
     * @returns <Chainable> request response
     */
    createExam(exam: Exam) {
        return cy.request({ url: COURSE_BASE + exam.course!.id + '/exams', method: POST, body: exam });
    }

    /**
     * Deletes the exam with the given parameters
     * @returns <Chainable> request response
     * */
    deleteExam(exam: Exam) {
        return cy.request({ method: DELETE, url: COURSE_BASE + exam.course!.id + '/exams/' + exam.id });
    }

    /**
     * register the student for the exam
     * @returns <Chainable> request response
     */
    registerStudentForExam(exam: Exam, student: CypressCredentials) {
        return cy.request({ method: POST, url: COURSE_BASE + exam.course!.id + '/exams/' + exam.id + '/students/' + student.username });
    }

    /**
     * Creates an exam with the provided settings.
     * @param exam the exam object created by a {@link ExamBuilder}
     * @param exerciseArray an array of exercises
     * @param workingTime the working time in seconds
     * @returns <Chainable> request response
     */
    createExamTestRun(exam: Exam, exerciseArray: Array<CypressExercise>, workingTime = 1080) {
        const courseId = exam.course!.id;
        const examId = exam.id!;
        const body = {
            exam,
            exerciseArray,
            workingTime,
        };
        return cy.request({ url: COURSE_BASE + courseId + '/exams/' + examId + '/test-run', method: POST, body });
    }

    /**
     * add exercise group to exam
     * @returns <Chainable> request response
     * */
    addExerciseGroupForExam(exam: Exam, title = 'Group ' + generateUUID(), mandatory = true) {
        const exerciseGroup = new ExerciseGroup();
        exerciseGroup.exam = exam;
        exerciseGroup.title = title;
        exerciseGroup.isMandatory = mandatory;
        return cy.request({ method: POST, url: COURSE_BASE + exam.course!.id + '/exams/' + exam.id + '/exerciseGroups', body: exerciseGroup });
    }

    /**
     * add text exercise to an exercise group in exam or to a course
     * @returns <Chainable> request response
     */
    createTextExercise(body: { course: Course } | { exerciseGroup: ExerciseGroup }, title = 'Text ' + generateUUID()) {
        const template = {
            ...textExerciseTemplate,
            title,
            channelName: 'exercise-' + titleLowercase(title),
        };
        const textExercise = Object.assign({}, template, body);
        return cy.request({ method: POST, url: TEXT_EXERCISE_BASE, body: textExercise });
    }

    deleteTextExercise(exerciseId: number) {
        return cy.request({
            url: TEXT_EXERCISE_BASE + exerciseId,
            method: DELETE,
        });
    }

    /**
     * generate all missing individual exams
     * @returns <Chainable> request response
     */
    generateMissingIndividualExams(exam: Exam) {
        return cy.request({ method: POST, url: COURSE_BASE + exam.course!.id + '/exams/' + exam.id + '/generate-missing-student-exams' });
    }

    /**
     * Prepares individual exercises for exam start
     * @returns <Chainable> request response
     */
    prepareExerciseStartForExam(exam: Exam) {
        return cy.request({ method: POST, url: COURSE_BASE + exam.course!.id + '/exams/' + exam.id + '/student-exams/start-exercises' });
    }

    createModelingExercise(
        body: { course: Course } | { exerciseGroup: ExerciseGroup },
        title = 'Modeling ' + generateUUID(),
        releaseDate = day(),
        dueDate = day().add(1, 'days'),
        assessmentDueDate = day().add(2, 'days'),
    ) {
        const templateCopy = {
            ...modelingExerciseTemplate,
            title,
            channelName: 'exercise-' + titleLowercase(title),
        };
        const dates = {
            releaseDate: dayjsToString(releaseDate),
            dueDate: dayjsToString(dueDate),
            assessmentDueDate: dayjsToString(assessmentDueDate),
        };
        let newModelingExercise;
        // eslint-disable-next-line no-prototype-builtins
        if (body.hasOwnProperty('course')) {
            newModelingExercise = Object.assign({}, templateCopy, dates, body);
        } else {
            newModelingExercise = Object.assign({}, templateCopy, body);
        }
        return cy.request({
            url: MODELING_EXERCISE_BASE,
            method: POST,
            body: newModelingExercise,
        });
    }

    updateModelingExerciseAssessmentDueDate(exercise: ModelingExercise, due = day()) {
        exercise.assessmentDueDate = due;
        return this.updateExercise(exercise, EXERCISE_TYPE.Modeling);
    }

    deleteModelingExercise(exerciseID: number) {
        return cy.request({
            url: `${MODELING_EXERCISE_BASE}/${exerciseID}`,
            method: DELETE,
        });
    }

    makeModelingExerciseSubmission(exerciseID: number, participation: Participation) {
        return cy.request({
            url: `${EXERCISE_BASE}${exerciseID}/modeling-submissions`,
            method: PUT,
            body: {
                ...modelingExerciseSubmissionTemplate,
                id: participation.submissions![0].id,
                participation,
            },
        });
    }

    deleteQuizExercise(exerciseId: number) {
        return cy.request({
            url: QUIZ_EXERCISE_BASE + exerciseId,
            method: DELETE,
        });
    }

    /**
     * Creates a quiz exercise
     * @param body an object containing either the course or exercise group the exercise will be added to
     * @param quizQuestions list of quizQuestion objects that make up the Quiz. Can be multiple choice, short answer or drag and drop quizzes.
     * @param title the title for the Quiz
     * @param releaseDate time of release for the quiz
     * @param duration the duration in seconds that the student gets to complete the quiz
     * @returns <Chainable> request response
     */
    createQuizExercise(
        body: { course: Course } | { exerciseGroup: ExerciseGroup },
        quizQuestions: [any],
        title = 'Quiz ' + generateUUID(),
        releaseDate = day().add(1, 'year'),
        duration = 600,
    ) {
        const quizExercise: any = {
            ...quizTemplate,
            title,
            quizQuestions,
            duration,
            channelName: 'exercise-' + titleLowercase(title),
        };
        let newQuizExercise;
        const dates = {
            releaseDate: dayjsToString(releaseDate),
        };
        // eslint-disable-next-line no-prototype-builtins
        if (body.hasOwnProperty('course')) {
            newQuizExercise = Object.assign({}, quizExercise, dates, body);
        } else {
            newQuizExercise = Object.assign({}, quizExercise, body);
        }
        return cy.request({
            url: QUIZ_EXERCISE_BASE,
            method: POST,
            body: newQuizExercise,
        });
    }

    setQuizVisible(quizId: number) {
        return cy.request({
            url: `${QUIZ_EXERCISE_BASE}${quizId}/set-visible`,
            method: PUT,
        });
    }

    startQuizNow(quizId: number) {
        return cy.request({
            url: `${QUIZ_EXERCISE_BASE}${quizId}/start-now`,
            method: PUT,
        });
    }

    evaluateExamQuizzes(exam: Exam) {
        return cy.request({
            url: `${COURSE_BASE}${exam.course!.id}/exams/${exam.id}/student-exams/evaluate-quiz-exercises`,
            method: POST,
        });
    }

    makeTextExerciseSubmission(exerciseId: number, text: string) {
        return cy.request({
            url: `${EXERCISE_BASE}${exerciseId}/text-submissions`,
            method: PUT,
            body: { submissionExerciseType: 'text', text, id: null },
        });
    }

    /**
     * Creates a submission for a Quiz with only one multiple-choice quiz question
     * @param quizExercise the response body of a quiz exercise
     * @param tickOptions a list describing which of the 0..n boxes are to be ticked in the submission
     */
    createMultipleChoiceSubmission(quizExercise: any, tickOptions: number[]) {
        const submittedAnswers = [
            {
                ...multipleChoiceSubmissionTemplate.submittedAnswers[0],
                quizQuestion: quizExercise.quizQuestions![0],
                selectedOptions: tickOptions.map((option) => quizExercise.quizQuestions[0].answerOptions[option]),
            },
        ];
        const multipleChoiceSubmission = {
            ...multipleChoiceSubmissionTemplate,
            submittedAnswers,
        };
        return cy.request({
            url: EXERCISE_BASE + quizExercise.id + '/submissions/live',
            method: POST,
            body: multipleChoiceSubmission,
        });
    }

    /**
     * Creates a submission for a Quiz with only one short-answer quiz question
     * @param quizExercise the response body of the quiz exercise
     * @param textAnswers a list containing the answers to be filled into the gaps. In numerical order.
     */
    createShortAnswerSubmission(quizExercise: any, textAnswers: string[]) {
        const submittedTexts = textAnswers.map((answer, index) => {
            return {
                text: answer,
                spot: {
                    id: quizExercise.quizQuestions[0].spots[index].id,
                    spotNr: quizExercise.quizQuestions[0].spots[index].spotNr,
                    width: quizExercise.quizQuestions[0].spots[index].width,
                    invalid: quizExercise.quizQuestions[0].spots[index].invalid,
                },
            };
        });
        const submittedAnswers = [
            {
                ...shortAnswerSubmissionTemplate.submittedAnswers[0],
                quizQuestion: quizExercise.quizQuestions[0],
                submittedTexts,
            },
        ];
        const shortAnswerSubmission = {
            ...shortAnswerSubmissionTemplate,
            submittedAnswers,
        };
        return cy.request({
            url: EXERCISE_BASE + quizExercise.id + '/submissions/live',
            method: POST,
            body: shortAnswerSubmission,
        });
    }

    getExerciseParticipation(exerciseId: number) {
        return cy.request({
            url: EXERCISE_BASE + exerciseId + '/participation',
            method: GET,
        });
    }

    startExerciseParticipation(exerciseId: number) {
        return cy.request({
            url: EXERCISE_BASE + exerciseId + '/participations',
            method: POST,
        });
    }

    updateTextExerciseDueDate(exercise: TextExercise, due = day()) {
        exercise.dueDate = due;
        return this.updateExercise(exercise, EXERCISE_TYPE.Text);
    }

    updateTextExerciseAssessmentDueDate(exercise: TextExercise, due = day()) {
        exercise.assessmentDueDate = due;
        return this.updateExercise(exercise, EXERCISE_TYPE.Text);
    }

    deleteLecture(lectureId: number) {
        return cy.request({
            url: `${BASE_API}lectures/${lectureId}`,
            method: DELETE,
        });
    }

    createLecture(course: Course, title = 'Lecture ' + generateUUID(), startDate = day(), endDate = day().add(10, 'minutes')) {
        const body = {
            ...lectureTemplate,
            course,
            title,
            startDate,
            endDate,
            channelName: 'lecture-' + titleLowercase(title),
        };
        return cy.request({
            url: `${BASE_API}lectures`,
            method: POST,
            body,
        });
    }
}

/**
 * Helper class to construct exam objects for the {@link CourseManagementRequests.createExam} method.
 */
export class ExamBuilder {
    readonly template: any = examTemplate;

    /**
     * Initializes the exam builder.
     * @param course the course dto of a previous createCourse request
     */
    constructor(course: any) {
        this.template.course = course;
        this.template.title = 'exam' + generateUUID();
        this.template.visibleDate = dayjsToString(day());
        this.template.startDate = dayjsToString(day().add(1, 'day'));
        this.template.endDate = dayjsToString(day().add(2, 'day'));
        this.template.workingTime = 86400;
        this.template.channelName = titleLowercase(this.template.title);
    }

    /**
     * @param title the title of the exam
     */
    title(title: string) {
        this.template.title = title;
        return this;
    }

    testExam() {
        this.template.testExam = true;
        this.template.numberOfCorrectionRoundsInExam = 0;
        return this;
    }

    workingTime(workingTime: number) {
        this.template.workingTime = workingTime;
        return this;
    }

    /**
     * @param randomize if the exercise order should be randomized
     */
    randomizeOrder(randomize: boolean) {
        this.template.randomizeExerciseOrder = randomize;
        return this;
    }

    /**
     * @param rounds how many correction rounds there are for this exam (default is 1)
     */
    correctionRounds(rounds: number) {
        this.template.numberOfCorrectionRoundsInExam = rounds;
        return this;
    }

    /**
     * @param points the maximum amount of points achieved in the exam (default is 10)
     */
    examMaxPoints(points: number) {
        this.template.examMaxPoints = points;
        return this;
    }

    /**
     * @param period the grace period in seconds for this exam (default is 30)
     */
    gracePeriod(period: number) {
        this.template.gracePeriod = period;
        return this;
    }

    /**
     * @param amount the amount of exercises in this exam
     */
    numberOfExercises(amount: number) {
        this.template.numberOfExercisesInExam = amount;
        return this;
    }

    /**
     * @param date the date when the exam should be visible
     */
    visibleDate(date: day.Dayjs) {
        this.template.visibleDate = dayjsToString(date);
        return this;
    }

    /**
     *
     * @param date the date when the exam should start
     */
    startDate(date: day.Dayjs) {
        this.template.startDate = dayjsToString(date);
        return this;
    }

    /**
     *
     * @param date the date when the exam should end
     */
    endDate(date: day.Dayjs) {
        this.template.endDate = dayjsToString(date);
        return this;
    }

    publishResultsDate(date: day.Dayjs) {
        this.template.publishResultsDate = dayjsToString(date);
        return this;
    }

    examStudentReviewStart(date: day.Dayjs) {
        this.template.examStudentReviewStart = dayjsToString(date);
        return this;
    }

    examStudentReviewEnd(date: day.Dayjs) {
        this.template.examStudentReviewEnd = dayjsToString(date);
        return this;
    }

    /**
     * @returns the exam object
     */
    build() {
        return this.template;
    }
}

export enum ProgrammingExerciseAssessmentType {
    AUTOMATIC,
    SEMI_AUTOMATIC,
    MANUAL,
}

export function convertModelAfterMultiPart(response: Cypress.Response<Course>): Course {
    // Cypress currently has some issues with our multipart request, parsing this not as an object but as an ArrayBuffer
    // Once this is fixed (and hence the expect statements below fail), we can remove the additional parsing
    expect(response.body).not.to.be.an('object');
    expect(response.body).to.be.an('ArrayBuffer');

    return parseArrayBufferAsJsonObject(response.body as ArrayBuffer);
}
