import { Interception } from 'cypress/types/net-stubbing';
import { Exam } from 'app/entities/exam.model';
import { Course } from 'app/entities/course.model';
import { CypressExamBuilder, convertCourseAfterMultiPart } from '../../support/requests/CourseManagementRequests';
import { artemis } from '../../support/ArtemisTesting';
import { generateUUID } from '../../support/utils';
import { ExerciseGroup } from 'app/entities/exercise-group.model';

// Users
const users = artemis.users;
const admin = users.getAdmin();
const studentOne = users.getStudentOne();

// Requests
const courseManagementRequests = artemis.requests.courseManagement;

// User management
const users = artemis.users;
const admin = users.getAdmin();
const instructor = users.getInstructor();
const studentOne = users.getStudentOne();

// PageObjects
const navigationBar = artemis.pageobjects.navigationBar;
const courseManagement = artemis.pageobjects.course.management;
const examManagement = artemis.pageobjects.exam.management;
const textCreation = artemis.pageobjects.exercise.text.creation;
const programmingCreation = artemis.pageobjects.exercise.programming.creation;
const quizCreation = artemis.pageobjects.exercise.quiz.creation;
const modelingCreation = artemis.pageobjects.exercise.modeling.creation;
const exerciseGroups = artemis.pageobjects.exam.exerciseGroups;
const exerciseGroupCreation = artemis.pageobjects.exam.exerciseGroupCreation;
const studentExamManagement = artemis.pageobjects.exam.studentExamManagement;

// Common primitives
const uid = generateUUID();
const examTitle = 'exam' + uid;
let groupCount = 0;
let createdGroup: ExerciseGroup;

describe('Exam management', () => {
    let course: Course;
    let exam: Exam;

    before(() => {
        cy.login(admin);
        courseManagementRequests.createCourse(true).then((response) => {
            course = convertCourseAfterMultiPart(response);
            courseManagementRequests.addStudentToCourse(course, studentOne);
            const examConfig = new CypressExamBuilder(course).title(examTitle).build();
            courseManagementRequests.createExam(examConfig).then((examResponse) => {
                exam = examResponse.body;
            });
        });
    });

    describe('Manage Group', () => {
        let exerciseGroup: ExerciseGroup;

        before(() => {
            cy.login(instructor);
            courseManagementRequests.addExerciseGroupForExam(exam).then((response) => {
                exerciseGroup = response.body;
                groupCount++;
            });
        });

        beforeEach(() => {
            cy.login(instructor);
        });

        it('Create exercise group', () => {
            cy.visit('/');
            navigationBar.openCourseManagement();
            courseManagement.openExamsOfCourse(course.shortName!);
            examManagement.openExerciseGroups(exam.id!);
            exerciseGroups.shouldShowNumberOfExerciseGroups(groupCount);
            exerciseGroups.clickAddExerciseGroup();
            const groupName = 'Group 1';
            exerciseGroupCreation.typeTitle(groupName);
            exerciseGroupCreation.isMandatoryBoxShouldBeChecked();
            exerciseGroupCreation.clickSave().then((interception) => {
                const group = interception.response!.body;
                groupCount++;
                exerciseGroups.shouldHaveTitle(group.id, groupName);
                exerciseGroups.shouldShowNumberOfExerciseGroups(groupCount);
                createdGroup = group;
            });
        });

        it('Adds a text exercise', () => {
            cy.visit(`/course-management/${course.id}/exams`);
            examManagement.openExerciseGroups(exam.id!);
            exerciseGroups.clickAddTextExercise(exerciseGroup.id!);
            const textExerciseTitle = 'text' + uid;
            textCreation.typeTitle(textExerciseTitle);
            textCreation.typeMaxPoints(10);
            textCreation.create().its('response.statusCode').should('eq', 201);
            exerciseGroups.visitPageViaUrl(course.id!, exam.id!);
            exerciseGroups.shouldContainExerciseWithTitle(exerciseGroup.id!, textExerciseTitle);
        });

        it('Adds a quiz exercise', () => {
            cy.visit(`/course-management/${course.id}/exams`);
            examManagement.openExerciseGroups(exam.id!);
            exerciseGroups.clickAddQuizExercise(exerciseGroup.id!);
            const quizExerciseTitle = 'quiz' + uid;
            quizCreation.setTitle(quizExerciseTitle);
            quizCreation.addMultipleChoiceQuestion(quizExerciseTitle, 10);
            quizCreation.saveQuiz().its('response.statusCode').should('eq', 201);
            exerciseGroups.visitPageViaUrl(course.id!, exam.id!);
            exerciseGroups.shouldContainExerciseWithTitle(exerciseGroup.id!, quizExerciseTitle);
        });

        it('Adds a modeling exercise', () => {
            cy.visit(`/course-management/${course.id}/exams`);
            examManagement.openExerciseGroups(exam.id!);
            exerciseGroups.clickAddModelingExercise(exerciseGroup.id!);
            const modelingExerciseTitle = 'modeling' + uid;
            modelingCreation.setTitle(modelingExerciseTitle);
            modelingCreation.setPoints(10);
            modelingCreation.save().its('response.statusCode').should('eq', 201);
            exerciseGroups.visitPageViaUrl(course.id!, exam.id!);
            exerciseGroups.shouldContainExerciseWithTitle(exerciseGroup.id!, modelingExerciseTitle);
        });

        it('Adds a programming exercise', () => {
            cy.visit(`/course-management/${course.id}/exams`);
            examManagement.openExerciseGroups(exam.id!);
            exerciseGroups.clickAddProgrammingExercise(exerciseGroup.id!);
            const programmingExerciseTitle = 'programming' + uid;
            programmingCreation.setTitle(programmingExerciseTitle);
            programmingCreation.setShortName(programmingExerciseTitle);
            programmingCreation.setPackageName('de.test');
            programmingCreation.setPoints(10);
            programmingCreation.generate().its('response.statusCode').should('eq', 201);
            exerciseGroups.visitPageViaUrl(course.id!, exam.id!);
            exerciseGroups.shouldContainExerciseWithTitle(exerciseGroup.id!, programmingExerciseTitle);
        });

        it('Edits an exercise group', () => {
            cy.visit(`/course-management/${course.id}/exams`);
            examManagement.openExerciseGroups(exam.id!);
            exerciseGroups.shouldHaveTitle(exerciseGroup.id!, exerciseGroup.title!);
            exerciseGroups.clickEditGroup(exerciseGroup.id!);
            const newGroupName = 'Group 3';
            exerciseGroupCreation.typeTitle(newGroupName);
            exerciseGroupCreation.update();
            exerciseGroups.shouldHaveTitle(exerciseGroup.id!, newGroupName);
            exerciseGroup.title = newGroupName;
        });

        it('Delete an exercise group', () => {
            cy.visit('/');
            navigationBar.openCourseManagement();
            courseManagement.openExamsOfCourse(course.shortName!);
            examManagement.openExerciseGroups(exam.id!);
            // If the group in the "Create group test" was created successfully, we delete it so there is no group with no exercise
            let group = exerciseGroup;
            if (createdGroup) {
                group = createdGroup;
            }
            exerciseGroups.clickDeleteGroup(group.id!, group.title!);
            exerciseGroups.shouldNotExist(group.id!);
        });
    });

    describe('Manage Students', () => {
        beforeEach(() => {
            cy.login(instructor);
        });

        it('Registers the course students for the exam', () => {
            // We already verified in the previous test that we can navigate here
            cy.visit(`/course-management/${course.id}/exams`);
            examManagement.openStudentRegistration(exam.id!);
            studentExamManagement.clickRegisterCourseStudents().then((request: Interception) => {
                expect(request.response!.statusCode).to.eq(200);
            });
            cy.get('#registered-students').contains(studentOne.username).should('be.visible');
        });

        it('Generates student exams', () => {
            cy.visit(`/course-management/${course.id}/exams`);
            examManagement.openStudentExams(exam.id!);
            studentExamManagement.clickGenerateStudentExams();
            cy.get('#generateMissingStudentExamsButton').should('be.disabled');
        });
    });

    after(() => {
        if (course) {
            cy.login(admin);
            courseManagementRequests.deleteCourse(course.id!);
        }
    });
});
