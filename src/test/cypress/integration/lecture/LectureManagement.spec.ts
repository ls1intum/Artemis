import { Lecture } from 'app/entities/lecture.model';
import { Course } from 'app/entities/course.model';
import { artemis } from '../../support/ArtemisTesting';
import { generateUUID } from '../../support/utils';
import dayjs from 'dayjs/esm';
import { convertCourseAfterMultiPart } from '../../support/requests/CourseManagementRequests';

// Requests
const courseManagementRequests = artemis.requests.courseManagement;

// User management
const admin = artemis.users.getAdmin();
const instructor = artemis.users.getInstructor();

// Pageobjects
const lectureManagement = artemis.pageobjects.lecture.management;
const lectureCreation = artemis.pageobjects.lecture.creation;

describe('Lecture management', () => {
    let course: Course;
    let lecture: Lecture | undefined;

    before(() => {
        cy.login(admin);
        courseManagementRequests.createCourse().then((response) => {
            course = convertCourseAfterMultiPart(response);
            courseManagementRequests.addInstructorToCourse(course, instructor);
        });
    });

    after(() => {
        if (!!course) {
            cy.login(admin);
            courseManagementRequests.deleteCourse(course.id!);
        }
    });

    afterEach('Delete lecture', () => {
        if (lecture) {
            courseManagementRequests.deleteLecture(lecture.id!);
        }
    });

    it('creates a lecture', () => {
        const lectureTitle = 'exam' + generateUUID();
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

    describe('Handle existing lecture', () => {
        beforeEach('Create a lecture', () => {
            cy.login(instructor, '/course-management/' + course.id + '/lectures');
            courseManagementRequests.createLecture(course).then((lectureResponse) => {
                lecture = lectureResponse.body;
            });
        });

        it('Deletes an existing lecture', () => {
            lectureManagement.deleteLecture(lecture!.title!, 0).then((resp) => {
                expect(resp.response!.statusCode).to.eq(200);
                lectureManagement.getLectureContainer().children().should('have.length', 0);
                lecture = undefined;
            });
        });

        it('Adds a text unit to the lecture', () => {
            lectureManagement.openUnitsPage(0);
            cy.fixture('loremIpsum.txt').then((text) => {
                lectureManagement.addTextUnit('Text unit', text);
            });
            cy.contains('Text unit').should('be.visible');
        });

        it('Adds a exercise unit to the lecture', () => {
            courseManagementRequests.createModelingExercise({ course }).then((model) => {
                const exercise = model.body;
                lectureManagement.openUnitsPage(0);
                lectureManagement.addExerciseUnit(exercise.id!);
                cy.contains(exercise.title!);
            });
        });
    });
});
