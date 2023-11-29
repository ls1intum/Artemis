import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { CypressUserManagement, admin, studentOne, studentThree, studentTwo } from '../../../support/users';
import {
    courseManagement,
    courseManagementAPIRequest,
    examAPIRequests,
    examExerciseGroupCreation,
    examManagement,
    examNavigation,
    examParticipation,
    navigationBar,
    studentExamManagement,
} from '../../../support/artemis';
import { convertModelAfterMultiPart, generateUUID } from '../../../support/utils';
import { CypressCredentials } from '../../../support/users';
import { Exercise, ExerciseType } from '../../../support/constants';
import dayjs from 'dayjs/esm';

// Common primitives
const uid = generateUUID();
const examTitle = 'test-exam' + uid;
const textFixture = 'loremIpsum-short.txt';
const students: Array<CypressCredentials> = [studentOne, studentTwo, studentThree];
const studentNames: string[] = [];

const userManagement = new CypressUserManagement();

let examExercise: Exercise;

describe('Test Exam - student exams', () => {
    let course: Course;
    let exam: Exam;

    before('Create course and exam', () => {
        cy.login(admin);
        courseManagementAPIRequest.createCourse().then((response) => {
            course = convertModelAfterMultiPart(response);

            for (const student of students) {
                courseManagementAPIRequest.addStudentToCourse(course, student);
            }

            const examConfig: Exam = {
                course,
                title: examTitle,
                testExam: true,
                startDate: dayjs().subtract(1, 'day'),
                visibleDate: dayjs().subtract(2, 'days'),
                workingTime: 5,
                examMaxPoints: 10,
                numberOfCorrectionRoundsInExam: 1,
            };

            examAPIRequests.createExam(examConfig).then((examResponse) => {
                exam = examResponse.body;
                examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.TEXT, { textFixture }).then((response) => {
                    examExercise = response;
                    participateInExam(students.at(0)!, course, exam, true, true);
                    participateInExam(students.at(1)!, course, exam, true, false);
                    participateInExam(students.at(2)!, course, exam, false, false);
                });
            });
        });
    });

    before('Get student names', () => {
        cy.login(admin);
        for (let index = 0; index < students.length; index++) {
            userManagement.getUserInfo(students[index].username, (userInfo) => {
                studentNames[index] = userInfo.name;
            });
        }
    });

    beforeEach(() => {
        cy.login(admin);
    });

    describe('Check exam participants and their submissions', () => {
        it('Open the list of exam students', () => {
            cy.visit('/');
            navigationBar.openCourseManagement();
            courseManagement.openExamsOfCourse(course.id!);
            examManagement.openStudentExams(exam.id!);
            for (const student of students) {
                studentExamManagement.checkExamStudent(student.username);
            }
            studentExamManagement.getStudentExamRows().should('have.length', students.length);

            studentExamManagement.checkExamProperty(students.at(0)!.username, 'Started', 'Yes');
            studentExamManagement.checkExamProperty(students.at(1)!.username, 'Started', 'Yes');
            studentExamManagement.checkExamProperty(students.at(2)!.username, 'Started', 'No');

            studentExamManagement.checkExamProperty(students.at(0)!.username, 'Submitted', 'Yes');
            studentExamManagement.checkExamProperty(students.at(1)!.username, 'Submitted', 'No');
            studentExamManagement.checkExamProperty(students.at(2)!.username, 'Submitted', 'No');

            studentExamManagement.checkExamProperty(students.at(1)!.username, 'Used working time', '0s');
            studentExamManagement.checkExamProperty(students.at(2)!.username, 'Used working time', '0s');

            studentExamManagement.checkExamProperty(students.at(0)!.username, 'Student', studentNames.at(0)!.trim());
            studentExamManagement.checkExamProperty(students.at(1)!.username, 'Student', studentNames.at(1)!.trim());
            studentExamManagement.checkExamProperty(students.at(2)!.username, 'Student', studentNames.at(2)!.trim());
        });

        it('Search for a student in exams', () => {
            cy.visit('/');
            navigationBar.openCourseManagement();
            courseManagement.openExamsOfCourse(course.id!);
            examManagement.openStudentExams(exam.id!);

            let searchText = students.at(0)!.username + ', ' + students.at(1)!.username;
            studentExamManagement.typeSearchText(searchText);
            studentExamManagement.checkExamStudent(students.at(0)!.username);
            studentExamManagement.checkExamStudent(students.at(1)!.username);

            searchText = studentNames.at(0)! + ', ' + studentNames.at(1)!;
            studentExamManagement.typeSearchText(searchText);
            studentExamManagement.checkExamStudent(students.at(0)!.username);
            studentExamManagement.checkExamStudent(students.at(1)!.username);

            searchText = 'Artemis Test User';
            studentExamManagement.typeSearchText(searchText);
            studentExamManagement.checkExamStudent(students.at(0)!.username);
            studentExamManagement.checkExamStudent(students.at(1)!.username);
            studentExamManagement.checkExamStudent(students.at(2)!.username);
        });
    });

    function participateInExam(student: CypressCredentials, course: Course, exam: Exam, toStart: boolean, toSubmit: boolean) {
        if (!toStart) {
            examParticipation.openExam(student, course, exam);
        } else {
            examParticipation.startParticipation(student, course, exam);
            examNavigation.openExerciseAtIndex(0);
            examParticipation.makeSubmission(examExercise.id, examExercise.type, examExercise.additionalData);
        }

        if (toSubmit) {
            examParticipation.handInEarly();
        }
    }

    after('Delete course', () => {
        courseManagementAPIRequest.deleteCourse(course, admin);
    });
});
