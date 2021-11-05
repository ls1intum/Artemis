import { artemis } from '../../support/ArtemisTesting';
import { generateUUID } from '../../support/utils';
import dayjs from 'dayjs';

// Requests
const courseManagementRequests = artemis.requests.courseManagement;

// User management
const admin = artemis.users.getAdmin();
const instructor = artemis.users.getInstructor();

// Pageobjects
const lectureManagement = artemis.pageobjects.lecture.management;
const lectureCreation = artemis.pageobjects.lecture.creation;

describe('Lecture management', () => {
    let course: any;
    let lecture: any;

    before(() => {
        cy.login(admin);
        courseManagementRequests.createCourse().then((response) => {
            course = response.body;
            courseManagementRequests.addInstructorToCourse(course.id, instructor);
        });
    });

    beforeEach(() => {
        cy.login(instructor, '/course-management/' + course.id);
    });

    after(() => {
        if (!!course) {
            cy.login(admin);
            courseManagementRequests.deleteCourse(course.id);
        }
    });

    afterEach('Delete lecture', () => {
        if (lecture) {
            courseManagementRequests.deleteLecture(lecture.id);
        }
    });

    it('creates a lecture', () => {
        const lectureTitle = 'exam' + generateUUID();
        cy.contains('Lectures').click();
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
            courseManagementRequests.createLecture(course).then((lectureResponse) => {
                lecture = lectureResponse.body;
            });
        });

        it('Deletes an existing lecture', () => {
            cy.login(instructor, '/course-management/' + course.id + '/lectures');
            lectureManagement.deleteLecture(lecture.title).then((resp) => {
                expect(resp.response!.statusCode).to.eq(200);
                lecture = null;
            });
        });
    });
});
