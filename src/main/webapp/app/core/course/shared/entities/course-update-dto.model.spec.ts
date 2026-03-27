/* eslint-disable jest-extended/prefer-to-be-true, jest-extended/prefer-to-be-false */
import dayjs from 'dayjs/esm';
import { Course, CourseInformationSharingConfiguration, Language } from './course.model';
import { toCourseCreateDTO, toCourseUpdateDTO } from './course-update-dto.model';
import { ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';

describe('Course Update DTO', () => {
    function createFullCourse(): Course {
        const course = new Course();
        course.id = 1;
        course.title = 'Test Course';
        course.shortName = 'TC';
        course.description = 'A test course';
        course.semester = 'WS2024';
        course.studentGroupName = 'students';
        course.teachingAssistantGroupName = 'tutors';
        course.editorGroupName = 'editors';
        course.instructorGroupName = 'instructors';
        course.startDate = dayjs('2024-01-01T10:00:00.000Z');
        course.endDate = dayjs('2024-06-30T23:59:00.000Z');
        course.enrollmentStartDate = dayjs('2023-12-01T00:00:00.000Z');
        course.enrollmentEndDate = dayjs('2024-01-15T23:59:00.000Z');
        course.unenrollmentEndDate = dayjs('2024-02-01T23:59:00.000Z');
        course.testCourse = false;
        course.onlineCourse = true;
        course.language = Language.ENGLISH;
        course.defaultProgrammingLanguage = ProgrammingLanguage.JAVA;
        course.maxComplaints = 3;
        course.maxTeamComplaints = 3;
        course.maxComplaintTimeDays = 14;
        course.maxRequestMoreFeedbackTimeDays = 7;
        course.maxComplaintTextLimit = 3000;
        course.maxComplaintResponseTextLimit = 3000;
        course.color = '#FF0000';
        course.courseIcon = 'icon.png';
        course.enrollmentEnabled = true;
        course.enrollmentConfirmationMessage = 'Welcome!';
        course.unenrollmentEnabled = true;
        course.learningPathsEnabled = true;
        course.studentCourseAnalyticsDashboardEnabled = true;
        course.presentationScore = 10;
        course.maxPoints = 100;
        course.accuracyOfScores = 1;
        course.restrictedAthenaModulesAccess = false;
        course.timeZone = 'Europe/Berlin';
        course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;
        course.courseInformationSharingMessagingCodeOfConduct = 'Be nice';
        return course;
    }

    describe('toCourseCreateDTO', () => {
        it('should convert a fully populated course to a create DTO', () => {
            const course = createFullCourse();
            const dto = toCourseCreateDTO(course);

            expect(dto.title).toBe('Test Course');
            expect(dto.shortName).toBe('TC');
            expect(dto.description).toBe('A test course');
            expect(dto.semester).toBe('WS2024');
            expect(dto.studentGroupName).toBe('students');
            expect(dto.teachingAssistantGroupName).toBe('tutors');
            expect(dto.editorGroupName).toBe('editors');
            expect(dto.instructorGroupName).toBe('instructors');
            expect(dto.startDate).toBe(course.startDate!.toJSON());
            expect(dto.endDate).toBe(course.endDate!.toJSON());
            expect(dto.enrollmentStartDate).toBe(course.enrollmentStartDate!.toJSON());
            expect(dto.enrollmentEndDate).toBe(course.enrollmentEndDate!.toJSON());
            expect(dto.unenrollmentEndDate).toBe(course.unenrollmentEndDate!.toJSON());
            expect(dto.testCourse).toBe(false);
            expect(dto.onlineCourse).toBe(true);
            expect(dto.language).toBe(Language.ENGLISH);
            expect(dto.defaultProgrammingLanguage).toBe(ProgrammingLanguage.JAVA);
            expect(dto.maxComplaints).toBe(3);
            expect(dto.maxTeamComplaints).toBe(3);
            expect(dto.maxComplaintTimeDays).toBe(14);
            expect(dto.maxRequestMoreFeedbackTimeDays).toBe(7);
            expect(dto.maxComplaintTextLimit).toBe(3000);
            expect(dto.maxComplaintResponseTextLimit).toBe(3000);
            expect(dto.color).toBe('#FF0000');
            expect(dto.enrollmentEnabled).toBe(true);
            expect(dto.enrollmentConfirmationMessage).toBe('Welcome!');
            expect(dto.unenrollmentEnabled).toBe(true);
            expect(dto.learningPathsEnabled).toBe(true);
            expect(dto.studentCourseAnalyticsDashboardEnabled).toBe(true);
            expect(dto.presentationScore).toBe(10);
            expect(dto.maxPoints).toBe(100);
            expect(dto.accuracyOfScores).toBe(1);
            expect(dto.restrictedAthenaModulesAccess).toBe(false);
            expect(dto.timeZone).toBe('Europe/Berlin');
            expect(dto.courseInformationSharingConfiguration).toBe(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
        });

        it('should apply defaults for undefined fields', () => {
            const course = new Course();
            course.title = 'Minimal';
            course.shortName = 'MIN';

            const dto = toCourseCreateDTO(course);

            expect(dto.testCourse).toBe(false);
            expect(dto.maxComplaintTimeDays).toBe(7);
            expect(dto.maxRequestMoreFeedbackTimeDays).toBe(7);
            expect(dto.maxComplaintTextLimit).toBe(2000);
            expect(dto.maxComplaintResponseTextLimit).toBe(2000);
            expect(dto.unenrollmentEnabled).toBe(false);
            expect(dto.learningPathsEnabled).toBe(false);
            expect(dto.studentCourseAnalyticsDashboardEnabled).toBe(false);
            expect(dto.restrictedAthenaModulesAccess).toBe(false);
        });

        it('should handle undefined dates', () => {
            const course = new Course();
            course.title = 'No Dates';
            course.shortName = 'ND';

            const dto = toCourseCreateDTO(course);

            expect(dto.startDate).toBeUndefined();
            expect(dto.endDate).toBeUndefined();
            expect(dto.enrollmentStartDate).toBeUndefined();
            expect(dto.enrollmentEndDate).toBeUndefined();
            expect(dto.unenrollmentEndDate).toBeUndefined();
        });
    });

    describe('toCourseUpdateDTO', () => {
        it('should convert a fully populated course to an update DTO', () => {
            const course = createFullCourse();
            const dto = toCourseUpdateDTO(course);

            expect(dto.id).toBe(1);
            expect(dto.title).toBe('Test Course');
            expect(dto.shortName).toBe('TC');
            expect(dto.description).toBe('A test course');
            expect(dto.semester).toBe('WS2024');
            expect(dto.studentGroupName).toBe('students');
            expect(dto.teachingAssistantGroupName).toBe('tutors');
            expect(dto.editorGroupName).toBe('editors');
            expect(dto.instructorGroupName).toBe('instructors');
            expect(dto.startDate).toBe(course.startDate!.toJSON());
            expect(dto.endDate).toBe(course.endDate!.toJSON());
            expect(dto.courseIcon).toBe('icon.png');
            expect(dto.courseInformationSharingMessagingCodeOfConduct).toBe('Be nice');
            expect(dto.testCourse).toBe(false);
            expect(dto.unenrollmentEnabled).toBe(true);
        });

        it('should apply defaults for undefined fields', () => {
            const course = new Course();
            course.id = 42;
            course.title = 'Update';
            course.shortName = 'UP';

            const dto = toCourseUpdateDTO(course);

            expect(dto.id).toBe(42);
            expect(dto.testCourse).toBe(false);
            expect(dto.maxComplaintTimeDays).toBe(7);
            expect(dto.maxRequestMoreFeedbackTimeDays).toBe(7);
            expect(dto.maxComplaintTextLimit).toBe(2000);
            expect(dto.maxComplaintResponseTextLimit).toBe(2000);
            expect(dto.unenrollmentEnabled).toBe(false);
            expect(dto.learningPathsEnabled).toBe(false);
            expect(dto.studentCourseAnalyticsDashboardEnabled).toBe(false);
            expect(dto.restrictedAthenaModulesAccess).toBe(false);
        });
    });
});
