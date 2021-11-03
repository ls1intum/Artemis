import { artemis } from '../../support/ArtemisTesting';
import { generateUUID } from '../../support/utils';

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
    let lectureTitle: string;

    before(() => {
        cy.login(admin);
        courseManagementRequests.createCourse().then((response) => {
            course = response.body;
            courseManagementRequests.addInstructorToCourse(course.id, instructor);
        });
    });

    beforeEach(() => {
        lectureTitle = 'exam' + generateUUID();
        cy.login(instructor, '/course-management/' + course.id);
    });

    after(() => {
        if (!!course) {
            cy.login(admin);
            courseManagementRequests.deleteCourse(course.id);
        }
    });

    it('creates a Lecture', () => {
        cy.contains('Lectures').click();
        lectureManagement.clickCreateLecture();
        lectureCreation.setTitle(lectureTitle);
        cy.fixture('loremIpsum.txt').then((text) => {
            lectureCreation.typeDescription(text);
        });
        lectureCreation.save().its('response.statusCode').should('eq', 201);
    });
});
