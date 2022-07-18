import { Lecture } from 'app/entities/lecture.model';
import { Course } from 'app/entities/course.model';
import { artemis } from '../../support/ArtemisTesting';
import { generateUUID } from '../../support/utils';
import dayjs from 'dayjs/esm';

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
            course = response.body;
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

    describe('Filter lectures', () => {
        beforeEach('Create 3 lectures in the past, present and future', () => {
            cy.login(instructor, '/course-management/' + course.id + '/lectures');
            // create a lecture that started 2 hours ago and ended 1 hour ago
            courseManagementRequests.createLecture(course, 'lecture-in-the-past', dayjs().subtract(120, 'minutes'), dayjs().subtract(60, 'minutes')).then((lectureResponse) => {
                lecture = lectureResponse.body;
            });
            // create a lecture that starts now and ends 60 minutes from now, i.e. lecture is currently in progress
            courseManagementRequests.createLecture(course, 'lecture-in-progress', dayjs(), dayjs().add(60, 'minutes')).then((lectureResponse) => {
                lecture = lectureResponse.body;
            });
            // create a lecture the starts and ends in the future
            courseManagementRequests.createLecture(course, 'lecture-in-the-future', dayjs().add(60, 'minutes'), dayjs().add(120, 'minutes')).then((lectureResponse) => {
                lecture = lectureResponse.body;
            });
        });

        it('Only shows the past lecture when filtering for Past lectures', () => {
            // deselect checkboxes for Current, Future and Unspecified Dates lectures
            // filterPast lecture should then be the only checkbox ticked.
            cy.get('#filterCurrent').click();
            cy.get('#filterFuture').click();
            cy.get('#filterUnspecifiedDates').click();
            lectureManagement.getLectureContainer().children().should('have.length', 1);
            cy.contains('lecture-in-the-past').should('be.visible');
        });

        it('Only shows the current lecture when filtering for Current lectures', () => {
            // deselect checkbox for Past lectures
            cy.get('#filterPast').click();
            // select checkbox for Current lectures. Current lectures should then be the only checkbox ticked.
            cy.get('#filterCurrent').click();
            lectureManagement.getLectureContainer().children().should('have.length', 1);
            cy.contains('lecture-in-progress').should('be.visible');
        });

        it('Only shows the Future lecture when filtering for Future lectures', () => {
            // deselect checkbox for Current lectures
            cy.get('#filterCurrent').click();
            // select checkbox for Future lectures. Future lectures should then be the only checkbox ticked.
            cy.get('#filterFuture').click();
            lectureManagement.getLectureContainer().children().should('have.length', 1);
            cy.contains('lecture-in-the-future').should('be.visible');
        });
    });
});
