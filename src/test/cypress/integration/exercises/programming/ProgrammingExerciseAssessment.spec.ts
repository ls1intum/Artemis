import { Interception } from 'cypress/types/net-stubbing';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import { CypressAssessmentType, convertCourseAfterMultiPart } from '../../../support/requests/CourseManagementRequests';
import { artemis } from 'src/test/cypress/support/ArtemisTesting';
import dayjs from 'dayjs/esm';

// The user management object
const users = artemis.users;
const student = users.getStudentOne();
const tutor = users.getTutor();
const admin = users.getAdmin();
const instructor = users.getInstructor();

// Requests
const courseManagement = artemis.requests.courseManagement;

// PageObjects
const coursesPage = artemis.pageobjects.course.management;
const courseAssessment = artemis.pageobjects.assessment.course;
const exerciseAssessment = artemis.pageobjects.assessment.exercise;
const programmingAssessment = artemis.pageobjects.assessment.programming;
const exerciseResult = artemis.pageobjects.exercise.result;
const programmingFeedback = artemis.pageobjects.exercise.programming.feedback;
const onlineEditor = artemis.pageobjects.exercise.programming.editor;

describe('Programming exercise assessment', () => {
    let course: Course;
    let exercise: ProgrammingExercise;
    const tutorFeedback = 'You are missing some classes! The classes, which you implemented look good though.';
    const tutorFeedbackPoints = 5;
    const tutorCodeFeedback = 'The input parameter should be mentioned in javadoc!';
    const tutorCodeFeedbackPoints = -2;
    const complaint = "That feedback wasn't very useful!";
    let dueDate: dayjs.Dayjs;
    let assessmentDueDate: dayjs.Dayjs;

    before('Creates a programming exercise and makes a student submission', () => {
        createCourseWithProgrammingExercise().then(() => {
            makeProgrammingSubmissionAsStudent();
        });
    });

    it('Assesses the programming exercise submission and verifies it', () => {
        assessSubmission();
        verifyAssessmentAsStudent();
        acceptComplaintAsInstructor();
    });

    after(() => {
        if (!!course) {
            cy.login(users.getAdmin());
            courseManagement.deleteCourse(course.id!);
        }
    });

    function assessSubmission() {
        cy.login(tutor, '/course-management');
        coursesPage.openAssessmentDashboardOfCourse(course.shortName!);
        courseAssessment.clickExerciseDashboardButton();
        exerciseAssessment.clickHaveReadInstructionsButton();
        exerciseAssessment.clickStartNewAssessment();
        onlineEditor.openFileWithName('BubbleSort.java');
        programmingAssessment.provideFeedbackOnCodeLine(9, tutorCodeFeedbackPoints, tutorCodeFeedback);
        programmingAssessment.addNewFeedback(tutorFeedbackPoints, tutorFeedback);
        programmingAssessment.submit().then((request: Interception) => {
            expect(request.response!.statusCode).to.eq(200);
            // Wait until the assessment due date is over
            const now = dayjs();
            if (now.isBefore(assessmentDueDate)) {
                cy.wait(assessmentDueDate.diff(now, 'ms'));
            }
        });
    }

    function verifyAssessmentAsStudent() {
        cy.login(student, `/courses/${course.id}/exercises/${exercise.id}`);
        const totalPoints = tutorFeedbackPoints + tutorCodeFeedbackPoints;
        const percentage = totalPoints * 10;
        exerciseResult.shouldShowExerciseTitle(exercise.title!);
        programmingFeedback.complain(complaint);
        exerciseResult.clickOpenCodeEditor(exercise.id!);
        programmingFeedback.shouldShowRepositoryLockedWarning();
        programmingFeedback.shouldShowAdditionalFeedback(tutorFeedbackPoints, tutorFeedback);
        programmingFeedback.shouldShowScore(totalPoints, exercise.maxPoints!, percentage);
        programmingFeedback.shouldShowCodeFeedback('BubbleSort.java', tutorCodeFeedback, '-2', onlineEditor);
    }

    function acceptComplaintAsInstructor() {
        cy.login(instructor, `/course-management/${course.id}/complaints`);
        programmingAssessment.acceptComplaint('Makes sense').then((request: Interception) => {
            expect(request.response!.statusCode).to.equal(200);
        });
    }

    function createCourseWithProgrammingExercise() {
        cy.login(admin);
        return courseManagement.createCourse(true).then((response) => {
            course = convertCourseAfterMultiPart(response);
            courseManagement.addStudentToCourse(course, student);
            courseManagement.addTutorToCourse(course, tutor);
            courseManagement.addInstructorToCourse(course, instructor);
            dueDate = dayjs().add(25, 'seconds');
            assessmentDueDate = dueDate.add(30, 'seconds');
            courseManagement
                .createProgrammingExercise({ course }, undefined, false, dayjs(), dueDate, undefined, undefined, undefined, assessmentDueDate, CypressAssessmentType.SEMI_AUTOMATIC)
                .then((programmingResponse) => {
                    exercise = programmingResponse.body;
                });
        });
    }

    function makeProgrammingSubmissionAsStudent() {
        cy.login(student);
        courseManagement
            .startExerciseParticipation(exercise.id!)
            .its('body.id')
            .then((participationId) => {
                courseManagement.makeProgrammingExerciseSubmission(participationId);
                // Wait until the due date is in the past
                const now = dayjs();
                if (now.isBefore(dueDate)) {
                    cy.wait(dueDate.diff(now, 'ms'));
                }
            });
    }
});
