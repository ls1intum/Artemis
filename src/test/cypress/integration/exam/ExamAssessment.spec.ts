import { generateUUID } from '../../support/utils';
import { artemis } from '../../support/ArtemisTesting';
import { CypressExamBuilder } from '../../support/requests/CourseManagementRequests';
import modelingExerciseTemplate from '../../fixtures/requests/modelingExercise_template.json';
import dayjs from 'dayjs';

// requests
const courseManagementRequests = artemis.requests.courseManagement;

// page objects
const examStartEnd = artemis.pageobjects.examStartEnd;

// Common primitives
const uid = generateUUID();
const courseName = 'Cypress course' + uid;
const courseShortName = 'cypress' + uid;

describe('Exam Assessment', () => {
    let course: any;
    let examTitle: string;
    before('Create a course', () => {
        cy.login(artemis.users.getAdmin());
        courseManagementRequests.createCourse(courseName, courseShortName).then((response) => {
            course = response.body;
            courseManagementRequests.addStudentToCourse(course.id, artemis.users.getStudentOne().username);
        });
    });

    beforeEach('Generate new exam Name', () => {
        examTitle = 'exam' + generateUUID();
        cy.login(artemis.users.getAdmin());
    });

    after('Delete Course', () => {
       courseManagementRequests.deleteCourse(course.id);
    });

    describe('Exam Assessment', () => {
        let exam: any;
        const student = artemis.users.getStudentOne();
        let exerciseGroup: any;

        beforeEach('Create Exam', () => {
            const examContent = new CypressExamBuilder(course)
                .title(examTitle)
                .visibleDate(dayjs().subtract(3, 'days'))
                .startDate(dayjs().subtract(2, 'days'))
                .endDate(dayjs().add(15, 'seconds'))
                .build();
            courseManagementRequests.createExam(examContent).then((examResponse) => {
                exam = examResponse.body;
                courseManagementRequests.registerStudentForExam(course, exam, student);
                courseManagementRequests.addExerciseGroupForExam(course, exam, 'group 1', true).then((groupResponse) => {
                    exerciseGroup = groupResponse.body;
                });
            });
        });

        afterEach('Delete Exam', () => {
            cy.login(artemis.users.getAdmin());
            courseManagementRequests.deleteExam(course, exam);
        });

        it('assess a modeling exercise submission', () => {
            courseManagementRequests.createModelingExercise(modelingExerciseTemplate, null, exerciseGroup).then((modelingResponse) => {
                const modelingExercise = modelingResponse.body;
                courseManagementRequests.generateMissingIndividualExams(course, exam);
                courseManagementRequests.prepareExerciseStartForExam(course, exam);
                // TODO: in the future this might become redundant and should be replaced with requests handling the submission creation
                cy.login(student, '/courses/' + course.id + '/exams/' + exam.id);
                examStartEnd.startExam();
            });
        });
    });

    describe('Exam Complaint Assessment', () => {

    });
});
