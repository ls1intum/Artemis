import { Interception } from 'cypress/types/net-stubbing';
import { TextExercise } from 'app/entities/text-exercise.model';
import { Course } from 'app/entities/course.model';
import { artemis } from 'src/test/cypress/support/ArtemisTesting';
import { parseCourseAfterMultiPart } from '../../../support/requests/CourseManagementRequests';

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
const textAssessment = artemis.pageobjects.assessment.text;
const exerciseResult = artemis.pageobjects.exercise.result;
const textFeedback = artemis.pageobjects.exercise.text.feedback;

describe('Text exercise assessment', () => {
    let course: Course;
    let exercise: TextExercise;
    const tutorFeedback = 'Try to use some newlines next time!';
    const tutorFeedbackPoints = 4;
    const tutorTextFeedback = 'Nice ending of the sentence!';
    const tutorTextFeedbackPoints = 2;
    const complaint = "That feedback wasn't very useful!";

    before('Creates a text exercise and makes a student submission', () => {
        createCourseWithTextExercise().then(() => {
            makeTextSubmissionAsStudent();
        });
    });

    after(() => {
        if (!!course) {
            cy.login(users.getAdmin());
            courseManagement.deleteCourse(course.id!);
        }
    });

    it('Assesses the text exercise submission', () => {
        cy.login(tutor, '/course-management');
        coursesPage.openAssessmentDashboardOfCourse(course.shortName!);
        courseAssessment.clickExerciseDashboardButton();
        exerciseAssessment.clickHaveReadInstructionsButton();
        exerciseAssessment.clickStartNewAssessment();
        textAssessment.getInstructionsRootElement().contains(exercise.title!).should('be.visible');
        textAssessment.getInstructionsRootElement().contains(exercise.problemStatement!).should('be.visible');
        textAssessment.getInstructionsRootElement().contains(exercise.exampleSolution!).should('be.visible');
        textAssessment.getInstructionsRootElement().contains(exercise.gradingInstructions!).should('be.visible');
        // Assert the correct word and character count without relying on translations
        textAssessment.getWordCountElement().should('contain.text', 100).and('be.visible');
        textAssessment.getCharacterCountElement().should('contain.text', 591).and('be.visible');
        textAssessment.provideFeedbackOnTextSection(1, tutorTextFeedbackPoints, tutorTextFeedback);
        textAssessment.addNewFeedback(tutorFeedbackPoints, tutorFeedback);
        textAssessment.submit().then((request: Interception) => {
            expect(request.response!.statusCode).to.eq(200);
        });
    });

    describe('Feedback', () => {
        it('Student sees feedback after assessment due date and complains', () => {
            cy.login(student, `/courses/${course.id}/exercises/${exercise.id}`);
            const totalPoints = tutorFeedbackPoints + tutorTextFeedbackPoints;
            const percentage = totalPoints * 10;
            exerciseResult.shouldShowExerciseTitle(exercise.title!);
            exerciseResult.shouldShowProblemStatement(exercise.problemStatement!);
            exerciseResult.shouldShowScore(percentage);
            exerciseResult.clickViewSubmission();
            textFeedback.shouldShowTextFeedback(1, tutorTextFeedback);
            textFeedback.shouldShowAdditionalFeedback(tutorFeedbackPoints, tutorFeedback);
            textFeedback.shouldShowScore(totalPoints, exercise.maxPoints!, percentage);
            textFeedback.complain(complaint);
        });

        it('Instructor can see complaint and reject it', () => {
            cy.login(instructor, `/course-management/${course.id}/complaints`);
            textAssessment.acceptComplaint('Makes sense').its('response.statusCode').should('eq', 200);
        });
    });

    function createCourseWithTextExercise() {
        cy.login(admin);
        return courseManagement.createCourse().then((response) => {
            course = parseCourseAfterMultiPart(response);
            courseManagement.addStudentToCourse(course, student);
            courseManagement.addTutorToCourse(course, tutor);
            courseManagement.addInstructorToCourse(course, instructor);
            courseManagement.createTextExercise({ course }).then((textResponse) => {
                exercise = textResponse.body;
            });
        });
    }

    function makeTextSubmissionAsStudent() {
        cy.login(student);
        courseManagement.startExerciseParticipation(exercise.id!);
        cy.fixture('loremIpsum.txt').then((submission) => {
            courseManagement.makeTextExerciseSubmission(exercise.id!, submission);
        });
    }
});
