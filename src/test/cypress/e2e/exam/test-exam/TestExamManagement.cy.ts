import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';

import {
    courseManagement,
    courseManagementAPIRequest,
    examAPIRequests,
    examExerciseGroupCreation,
    examExerciseGroups,
    examManagement,
    modelingExerciseCreation,
    navigationBar,
    programmingExerciseCreation,
    quizExerciseCreation,
    textExerciseCreation,
} from '../../../support/artemis';
import { admin, instructor, studentOne } from '../../../support/users';
import { convertModelAfterMultiPart, generateUUID } from '../../../support/utils';

// Common primitives
const uid = generateUUID();
const examTitle = 'test-exam' + uid;

describe('Test Exam management', () => {
    let course: Course;
    let exam: Exam;
    let createdGroup: ExerciseGroup;
    let groupCount = 0;

    before('Create course', () => {
        cy.login(admin);
        courseManagementAPIRequest.createCourse({ customizeGroups: true }).then((response) => {
            course = convertModelAfterMultiPart(response);
            courseManagementAPIRequest.addStudentToCourse(course, studentOne);
            examAPIRequests.createExam({ course, title: examTitle, testExam: true }).then((examResponse) => {
                exam = examResponse.body;
            });
        });
    });

    describe('Manage Group', () => {
        let exerciseGroup: ExerciseGroup;

        before(() => {
            cy.login(instructor);
            examAPIRequests.addExerciseGroupForExam(exam).then((response) => {
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
            courseManagement.openExamsOfCourse(course.id!);
            examManagement.openExerciseGroups(exam.id!);
            examExerciseGroups.shouldShowNumberOfExerciseGroups(groupCount);
            examExerciseGroups.clickAddExerciseGroup();
            const groupName = 'Group 1';
            examExerciseGroupCreation.typeTitle(groupName);
            examExerciseGroupCreation.isMandatoryBoxShouldBeChecked();
            examExerciseGroupCreation.clickSave().then((interception) => {
                const group = interception.response!.body;
                groupCount++;
                examExerciseGroups.shouldHaveTitle(group.id, groupName);
                examExerciseGroups.shouldShowNumberOfExerciseGroups(groupCount);
                createdGroup = group;
            });
        });

        it('Adds a text exercise', { scrollBehavior: 'center' }, () => {
            cy.visit(`/course-management/${course.id}/exams`);
            examManagement.openExerciseGroups(exam.id!);
            examExerciseGroups.clickAddTextExercise(exerciseGroup.id!);
            const textExerciseTitle = 'text' + uid;
            textExerciseCreation.typeTitle(textExerciseTitle);
            textExerciseCreation.typeMaxPoints(10);
            textExerciseCreation.create().its('response.statusCode').should('eq', 201);
            examExerciseGroups.visitPageViaUrl(course.id!, exam.id!);
            examExerciseGroups.shouldContainExerciseWithTitle(exerciseGroup.id!, textExerciseTitle);
        });

        it('Adds a quiz exercise', () => {
            cy.visit(`/course-management/${course.id}/exams`);
            examManagement.openExerciseGroups(exam.id!);
            examExerciseGroups.clickAddQuizExercise(exerciseGroup.id!);
            const quizExerciseTitle = 'quiz' + uid;
            quizExerciseCreation.setTitle(quizExerciseTitle);
            quizExerciseCreation.addMultipleChoiceQuestion(quizExerciseTitle, 10);
            quizExerciseCreation.saveQuiz().its('response.statusCode').should('eq', 201);
            examExerciseGroups.visitPageViaUrl(course.id!, exam.id!);
            examExerciseGroups.shouldContainExerciseWithTitle(exerciseGroup.id!, quizExerciseTitle);
        });

        it('Adds a modeling exercise', { scrollBehavior: 'center' }, () => {
            cy.visit(`/course-management/${course.id}/exams`);
            examManagement.openExerciseGroups(exam.id!);
            examExerciseGroups.clickAddModelingExercise(exerciseGroup.id!);
            const modelingExerciseTitle = 'modeling' + uid;
            modelingExerciseCreation.setTitle(modelingExerciseTitle);
            modelingExerciseCreation.setPoints(10);
            modelingExerciseCreation.save().its('response.statusCode').should('eq', 201);
            examExerciseGroups.visitPageViaUrl(course.id!, exam.id!);
            examExerciseGroups.shouldContainExerciseWithTitle(exerciseGroup.id!, modelingExerciseTitle);
        });

        it('Adds a programming exercise', { scrollBehavior: 'center' }, () => {
            cy.visit(`/course-management/${course.id}/exams`);
            examManagement.openExerciseGroups(exam.id!);
            examExerciseGroups.clickAddProgrammingExercise(exerciseGroup.id!);
            const programmingExerciseTitle = 'programming' + uid;
            programmingExerciseCreation.setTitle(programmingExerciseTitle);
            programmingExerciseCreation.setShortName(programmingExerciseTitle);
            programmingExerciseCreation.setPackageName('de.test');
            programmingExerciseCreation.setPoints(10);
            programmingExerciseCreation.generate().its('response.statusCode').should('eq', 201);
            examExerciseGroups.visitPageViaUrl(course.id!, exam.id!);
            examExerciseGroups.shouldContainExerciseWithTitle(exerciseGroup.id!, programmingExerciseTitle);
        });

        it('Edits an exercise group', () => {
            cy.visit(`/course-management/${course.id}/exams`);
            examManagement.openExerciseGroups(exam.id!);
            examExerciseGroups.shouldHaveTitle(exerciseGroup.id!, exerciseGroup.title!);
            examExerciseGroups.clickEditGroup(exerciseGroup.id!);
            const newGroupName = 'Group 3';
            examExerciseGroupCreation.typeTitle(newGroupName);
            examExerciseGroupCreation.update();
            examExerciseGroups.shouldHaveTitle(exerciseGroup.id!, newGroupName);
            exerciseGroup.title = newGroupName;
        });

        it('Delete an exercise group', () => {
            cy.visit('/');
            navigationBar.openCourseManagement();
            courseManagement.openExamsOfCourse(course.id!);
            examManagement.openExerciseGroups(exam.id!);
            // If the group in the "Create group test" was created successfully, we delete it so there is no group with no exercise
            let group = exerciseGroup;
            if (createdGroup) {
                group = createdGroup;
            }
            examExerciseGroups.clickDeleteGroup(group.id!, group.title!);
            examExerciseGroups.shouldNotExist(group.id!);
        });
    });

    after('Delete course', () => {
        courseManagementAPIRequest.deleteCourse(course, admin);
    });
});
