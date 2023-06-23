import { Lecture } from 'app/entities/lecture.model';
import { Course } from 'app/entities/course.model';
import { generateUUID } from '../../support/utils';
import dayjs from 'dayjs/esm';
import { convertCourseAfterMultiPart } from '../../support/requests/CourseManagementRequests';
import { courseManagementRequest, lectureCreation, lectureManagement } from '../../support/artemis';
import { admin, instructor } from '../../support/users';

describe('Lecture management', () => {
    let course: Course;
    let lecture: Lecture;

    before(() => {
        cy.login(admin);
        courseManagementRequest.createCourse().then((response) => {
            course = convertCourseAfterMultiPart(response);
            courseManagementRequest.addInstructorToCourse(course, instructor);
        });
    });

    it('Creates a lecture', () => {
        const lectureTitle = 'Lecture ' + generateUUID();
        cy.login(instructor, '/course-management/' + course.id);
        cy.get('#lectures').click();
        lectureManagement.clickCreateLecture();
        lectureCreation.setTitle(lectureTitle);
        cy.fixture('loremIpsum.txt').then((text) => {
            lectureCreation.typeDescription(text);
        });
        lectureCreation.setStartDate(dayjs());
        lectureCreation.setEndDate(dayjs().add(1, 'hour'));
        lectureCreation.save().then((lectureResponse) => {
            lecture = lectureResponse.response!.body;
            expect(lectureResponse.response!.statusCode).to.eq(201);
        });
    });

    it('Deletes a lecture', () => {
        cy.login(instructor, '/course-management/' + course.id + '/lectures');
        courseManagementRequest.createLecture(course).then((lectureResponse) => {
            lecture = lectureResponse.body;
            lectureManagement.deleteLecture(lecture).then((resp) => {
                expect(resp.response!.statusCode).to.eq(200);
                lectureManagement.getLecture(lecture).should('not.exist');
            });
        });
    });

    describe('Handle existing lecture', () => {
        before('Create a lecture', () => {
            cy.login(instructor, '/course-management/' + course.id + '/lectures');
            courseManagementRequest.createLecture(course).then((lectureResponse) => {
                lecture = lectureResponse.body;
            });
        });

        it('Adds a text unit to the lecture', () => {
            cy.login(instructor, '/course-management/' + course.id + '/lectures');
            lectureManagement.openUnitsPage(lecture.id!);
            cy.fixture('loremIpsum.txt').then((text) => {
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

    after(() => {
        if (course) {
            cy.login(admin);
            courseManagementRequest.deleteCourse(course.id!);
        }
    });
});
