import { Course } from 'app/entities/course.model';
import { courseManagementRequest, loginPage, navigationBar } from '../support/artemis';
import { convertCourseAfterMultiPart } from '../support/requests/CourseManagementRequests';
import { admin, instructor, studentOne, studentThree, studentTwo, tutor, users } from '../support/users';

describe('Login page tests', () => {
    let course: Course;

    before('Login as admin and create a course', () => {
        cy.login(admin);
        courseManagementRequest.createCourse(true).then((response) => {
            course = convertCourseAfterMultiPart(response);
            courseManagementRequest.addInstructorToCourse(course, instructor);
            courseManagementRequest.addTutorToCourse(course, tutor);
            courseManagementRequest.addStudentToCourse(course, studentOne);
            courseManagementRequest.addStudentToCourse(course, studentTwo);
            courseManagementRequest.addStudentToCourse(course, studentThree);
        });
    });

    it('Logs in via the UI', () => {
        cy.clearAllCookies();
        cy.visit('/');
        loginPage.login(studentOne);
        cy.url().should('include', '/courses');
        cy.getCookie('jwt').should('exist');
        cy.getCookie('jwt').should('have.property', 'value');
        cy.getCookie('jwt').should('have.property', 'httpOnly', true);
        cy.getCookie('jwt').should('have.property', 'sameSite', 'lax');
        // TODO: Uncomment once cypress is using https - cy.getCookie('jwt').should('have.property', 'secure', true);
    });

    it('Logs in programmatically and logs out via the UI', () => {
        cy.login(studentOne, '/courses');
        cy.url().should('include', '/courses');
        cy.get('#account-menu').click().get('#logout').click();
        cy.url().should('equal', Cypress.config().baseUrl + '/');
        cy.getCookie('jwt').should('not.exist');
    });

    it('Displays error messages on wrong password', () => {
        cy.visit('/');
        loginPage.login({ username: 'some_user_name', password: 'lorem-ipsum' });
        cy.location('pathname').should('eq', '/');
        cy.get('.alert').should('exist').and('have.text', 'Failed to sign in! Please check your username and password and try again.');
        cy.get('.btn').click();
        cy.get('.btn').click();
    });

    it('Fails to access protected resource without login', () => {
        cy.visit('/course-management');
        cy.location('pathname').should('eq', '/');
    });

    it('Checks if students have correct permissions', () => {
        const students = [studentOne, studentTwo, studentThree];
        students.forEach((student) => {
            cy.login(student, '/courses');
            navigationBar.checkCourseManagementMenuItem().should('not.exist');
            navigationBar.checkServerAdministrationMenuItem().should('not.exist');
            users.getAccountInfo((response) => {
                expect(response.authorities).to.have.members(['ROLE_USER']);
            });
        });
    });

    it('Checks if tutor has correct permissions', () => {
        cy.login(tutor, '/courses');
        navigationBar.checkCourseManagementMenuItem().should('exist');
        navigationBar.checkServerAdministrationMenuItem().should('not.exist');
        users.getAccountInfo((response) => {
            expect(response.authorities).to.have.members(['ROLE_USER', 'ROLE_TA']);
        });
    });

    it('Checks if instructor has correct permissions', () => {
        cy.login(instructor, '/courses');
        navigationBar.checkCourseManagementMenuItem().should('exist');
        navigationBar.checkServerAdministrationMenuItem().should('not.exist');
        users.getAccountInfo((response) => {
            expect(response.authorities).to.have.members(['ROLE_USER', 'ROLE_INSTRUCTOR']);
        });
    });

    it('Checks if admin has correct permissions', () => {
        cy.login(admin, '/courses');
        navigationBar.checkCourseManagementMenuItem().should('exist');
        navigationBar.checkServerAdministrationMenuItem().should('exist');
        users.getAccountInfo((response) => {
            expect(response.authorities).to.have.members(['ROLE_USER', 'ROLE_ADMIN']);
        });
    });

    it('Verify footer content', () => {
        cy.visit('/');
        loginPage.shouldShowFooter();
        loginPage.shouldShowAboutUsInFooter();
        loginPage.shouldShowRequestChangeInFooter();
        loginPage.shouldShowReleaseNotesInFooter();
        loginPage.shouldShowPrivacyStatementInFooter();
        loginPage.shouldShowImprintInFooter();
    });

    after('Delete course', () => {
        if (course) {
            cy.login(admin);
            courseManagementRequest.deleteCourse(course.id!);
        }
    });
});
