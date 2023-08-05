import { Lecture } from 'app/entities/lecture.model';
import { Course } from 'app/entities/course.model';
import { generateUUID } from '../../support/utils';
import dayjs from 'dayjs/esm';
import { convertModelAfterMultiPart } from '../../support/requests/CourseManagementRequests';
import { courseManagementRequest, lectureCreation, lectureManagement } from '../../support/artemis';
import { admin, instructor } from '../../support/users';

describe('Lecture management', () => {
    let course: Course;

    before('Create course', () => {
        cy.login(admin);
        courseManagementRequest.createCourse().then((response) => {
            course = convertModelAfterMultiPart(response);
            courseManagementRequest.addInstructorToCourse(course, instructor);
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
        cy.login(instructor, '/course-management/' + course.id + '/lectures');
        courseManagementRequest.createLecture(course).then((lectureResponse) => {
            lecture = lectureResponse.body;
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
            courseManagementRequest.createLecture(course).then((lectureResponse) => {
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
            courseManagementRequest.createModelingExercise({ course }).then((model) => {
                const exercise = model.body;
                lectureManagement.openUnitsPage(lecture.id!);
                lectureManagement.addExerciseUnit(exercise.id!);
                cy.contains(exercise.title!);
            });
        });
    });

    after('Delete course', () => {
        courseManagementRequest.deleteCourse(course, admin);
    });
});
