import dayjs from 'dayjs/esm';

import { Course } from 'app/entities/course.model';
import { Lecture } from 'app/entities/lecture.model';

import { courseManagementAPIRequest, exerciseAPIRequest, lectureCreation, lectureManagement } from '../../support/artemis';
import { admin, instructor } from '../../support/users';
import { convertModelAfterMultiPart, generateUUID } from '../../support/utils';

describe('Lecture management', () => {
    let course: Course;

    before('Create course', () => {
        cy.login(admin);
        courseManagementAPIRequest.createCourse().then((response) => {
            course = convertModelAfterMultiPart(response);
            courseManagementAPIRequest.addInstructorToCourse(course, instructor);
        });
    });

    it('Creates a lecture', () => {
        const lectureTitle = 'Lecture ' + generateUUID();
        cy.login(instructor, '/course-management/' + course.id);
        lectureManagement.getLectures().click();
        lectureManagement.clickCreateLecture();
        lectureCreation.setTitle(lectureTitle);
        cy.fixture('loremIpsum-short.txt').then((text) => {
            lectureCreation.typeDescription(text);
        });
        lectureCreation.setVisibleDate(dayjs());
        lectureCreation.setStartDate(dayjs());
        lectureCreation.setEndDate(dayjs().add(1, 'hour'));
        lectureCreation.save().then((lectureResponse) => {
            expect(lectureResponse.response!.statusCode).to.eq(201);
        });
    });

    it('Deletes a lecture', () => {
        let lecture: Lecture;
        cy.login(instructor, '/');
        courseManagementAPIRequest.createLecture(course).then((lectureResponse) => {
            lecture = lectureResponse.body;
            cy.visit('/course-management/' + course.id + '/lectures');
            lectureManagement.deleteLecture(lecture).then((resp) => {
                expect(resp.response!.statusCode).to.eq(200);
                lectureManagement.getLecture(lecture.id!).should('not.exist');
            });
        });
    });

    describe('Handle existing lecture', () => {
        let lecture: Lecture;

        beforeEach('Create a lecture', () => {
            cy.login(instructor, '/course-management/' + course.id + '/lectures');
            courseManagementAPIRequest.createLecture(course).then((lectureResponse) => {
                lecture = lectureResponse.body;
            });
        });

        it('Deletes an existing lecture', () => {
            lectureManagement.deleteLecture(lecture).then((resp) => {
                expect(resp.response!.statusCode).to.eq(200);
                lectureManagement.getLecture(lecture.id!).should('not.exist');
            });
        });

        it('Adds a text unit to the lecture', () => {
            cy.login(instructor, '/course-management/' + course.id + '/lectures');
            lectureManagement.openUnitsPage(lecture.id!);
            cy.fixture('loremIpsum-short.txt').then((text) => {
                lectureManagement.addTextUnit('Text unit', text);
            });
            cy.contains('Text unit').should('be.visible');
        });

        it('Adds a exercise unit to the lecture', () => {
            cy.login(instructor, '/course-management/' + course.id + '/lectures');
            exerciseAPIRequest.createModelingExercise({ course }).then((model) => {
                const exercise = model.body;
                lectureManagement.openUnitsPage(lecture.id!);
                lectureManagement.addExerciseUnit(exercise.id!);
                cy.contains(exercise.title!);
            });
        });
    });

    after('Delete course', () => {
        courseManagementAPIRequest.deleteCourse(course, admin);
    });
});
