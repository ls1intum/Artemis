import { describe, expect, it } from 'vitest';
import { toCourseCreateDTO, toCourseUpdateDTO } from './course-update-dto.model';
import { Course, CourseInformationSharingConfiguration, Language } from './course.model';
import { ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import dayjs from 'dayjs/esm';

describe('CourseUpdateDTO', () => {
    const startDate = dayjs('2024-01-01T10:00:00');
    const endDate = dayjs('2024-06-30T23:59:00');
    const enrollmentStart = dayjs('2023-12-01T10:00:00');
    const enrollmentEnd = dayjs('2024-01-01T09:00:00');

    const fullCourse: Course = {
        id: 1,
        title: 'Test Course',
        shortName: 'TC',
        description: 'A test course',
        semester: 'WS2024',
        studentGroupName: 'students',
        teachingAssistantGroupName: 'tutors',
        editorGroupName: 'editors',
        instructorGroupName: 'instructors',
        startDate,
        endDate,
        enrollmentStartDate: enrollmentStart,
        enrollmentEndDate: enrollmentEnd,
        testCourse: false,
        onlineCourse: true,
        language: Language.ENGLISH,
        defaultProgrammingLanguage: ProgrammingLanguage.JAVA,
        maxComplaints: 3,
        maxTeamComplaints: 3,
        maxComplaintTimeDays: 14,
        maxRequestMoreFeedbackTimeDays: 7,
        maxComplaintTextLimit: 2000,
        maxComplaintResponseTextLimit: 2000,
        color: '#1e88e5',
        enrollmentEnabled: true,
        unenrollmentEnabled: false,
        learningPathsEnabled: false,
        studentCourseAnalyticsDashboardEnabled: true,
        maxPoints: 100,
        accuracyOfScores: 1,
        restrictedAthenaModulesAccess: false,
        timeZone: 'Europe/Berlin',
        courseInformationSharingConfiguration: CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING,
        courseIcon: 'icon.png',
        courseInformationSharingMessagingCodeOfConduct: 'Be nice',
    } as Course;

    describe('toCourseCreateDTO', () => {
        it('should convert a course to a create DTO', () => {
            const dto = toCourseCreateDTO(fullCourse);

            expect(dto.title).toBe('Test Course');
            expect(dto.shortName).toBe('TC');
            expect(dto.description).toBe('A test course');
            expect(dto.semester).toBe('WS2024');
            expect(dto.studentGroupName).toBe('students');
            expect(dto.testCourse).toBe(false);
            expect(dto.onlineCourse).toBe(true);
            expect(dto.language).toBe(Language.ENGLISH);
            expect(dto.defaultProgrammingLanguage).toBe(ProgrammingLanguage.JAVA);
            expect(dto.maxComplaints).toBe(3);
            expect(dto.maxComplaintTimeDays).toBe(14);
            expect(dto.color).toBe('#1e88e5');
            expect(dto.enrollmentEnabled).toBe(true);
            expect(dto.unenrollmentEnabled).toBe(false);
            expect(dto.startDate).toBe(startDate.toJSON());
            expect(dto.endDate).toBe(endDate.toJSON());
        });

        it('should use defaults for undefined fields', () => {
            const minimalCourse = { title: 'Min', shortName: 'M' } as Course;
            const dto = toCourseCreateDTO(minimalCourse);

            expect(dto.testCourse).toBe(false);
            expect(dto.unenrollmentEnabled).toBe(false);
            expect(dto.learningPathsEnabled).toBe(false);
            expect(dto.studentCourseAnalyticsDashboardEnabled).toBe(false);
            expect(dto.restrictedAthenaModulesAccess).toBe(false);
            expect(dto.maxComplaintTimeDays).toBe(7);
            expect(dto.maxRequestMoreFeedbackTimeDays).toBe(7);
            expect(dto.maxComplaintTextLimit).toBe(2000);
            expect(dto.maxComplaintResponseTextLimit).toBe(2000);
        });
    });

    describe('toCourseUpdateDTO', () => {
        it('should convert a course to an update DTO with id', () => {
            const dto = toCourseUpdateDTO(fullCourse);

            expect(dto.id).toBe(1);
            expect(dto.title).toBe('Test Course');
            expect(dto.shortName).toBe('TC');
            expect(dto.courseIcon).toBe('icon.png');
            expect(dto.courseInformationSharingMessagingCodeOfConduct).toBe('Be nice');
            expect(dto.startDate).toBe(startDate.toJSON());
        });

        it('should use defaults for undefined fields', () => {
            const minimalCourse = { id: 2, title: 'Min', shortName: 'M' } as Course;
            const dto = toCourseUpdateDTO(minimalCourse);

            expect(dto.id).toBe(2);
            expect(dto.testCourse).toBe(false);
            expect(dto.unenrollmentEnabled).toBe(false);
            expect(dto.learningPathsEnabled).toBe(false);
            expect(dto.restrictedAthenaModulesAccess).toBe(false);
        });
    });
});
