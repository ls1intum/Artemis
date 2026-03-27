/* eslint-disable jest-extended/prefer-to-be-true, jest-extended/prefer-to-be-false */
import dayjs from 'dayjs/esm';
import { Course, CourseInformationSharingConfiguration, Language } from './course.model';
import { CourseCreateDTO, CourseUpdateDTO, toCourseCreateDTO, toCourseUpdateDTO } from './course-update-dto.model';
import { ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';

describe('Course Update DTO Model', () => {
    const baseDate = dayjs('2025-06-01T10:00:00.000Z');

    const fullCourse: Course = {
        id: 42,
        title: 'Test Course',
        shortName: 'TC',
        description: 'A test course',
        semester: 'SS2025',
        studentGroupName: 'students',
        teachingAssistantGroupName: 'tutors',
        editorGroupName: 'editors',
        instructorGroupName: 'instructors',
        startDate: baseDate,
        endDate: baseDate.add(3, 'months'),
        enrollmentStartDate: baseDate.subtract(1, 'month'),
        enrollmentEndDate: baseDate,
        unenrollmentEndDate: baseDate.add(1, 'month'),
        testCourse: false,
        onlineCourse: true,
        language: Language.ENGLISH,
        defaultProgrammingLanguage: ProgrammingLanguage.JAVA,
        maxComplaints: 3,
        maxTeamComplaints: 2,
        maxComplaintTimeDays: 14,
        maxRequestMoreFeedbackTimeDays: 10,
        maxComplaintTextLimit: 3000,
        maxComplaintResponseTextLimit: 4000,
        color: '#ff0000',
        courseIcon: 'icon.png',
        enrollmentEnabled: true,
        enrollmentConfirmationMessage: 'Welcome!',
        unenrollmentEnabled: true,
        courseInformationSharingMessagingCodeOfConduct: 'Be nice',
        onboardingDone: true,
        learningPathsEnabled: true,
        studentCourseAnalyticsDashboardEnabled: true,
        presentationScore: 10,
        maxPoints: 100,
        accuracyOfScores: 1,
        restrictedAthenaModulesAccess: true,
        timeZone: 'Europe/Berlin',
        courseInformationSharingConfiguration: CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING,
    };

    describe('toCourseCreateDTO', () => {
        it('should convert a full course entity to a create DTO', () => {
            const dto: CourseCreateDTO = toCourseCreateDTO(fullCourse);

            expect(dto.title).toBe('Test Course');
            expect(dto.shortName).toBe('TC');
            expect(dto.description).toBe('A test course');
            expect(dto.semester).toBe('SS2025');
            expect(dto.studentGroupName).toBe('students');
            expect(dto.teachingAssistantGroupName).toBe('tutors');
            expect(dto.editorGroupName).toBe('editors');
            expect(dto.instructorGroupName).toBe('instructors');
            expect(dto.startDate).toBe(baseDate.toJSON());
            expect(dto.endDate).toBe(baseDate.add(3, 'months').toJSON());
            expect(dto.enrollmentStartDate).toBe(baseDate.subtract(1, 'month').toJSON());
            expect(dto.enrollmentEndDate).toBe(baseDate.toJSON());
            expect(dto.unenrollmentEndDate).toBe(baseDate.add(1, 'month').toJSON());
            expect(dto.testCourse).toBe(false);
            expect(dto.onlineCourse).toBe(true);
            expect(dto.language).toBe(Language.ENGLISH);
            expect(dto.defaultProgrammingLanguage).toBe(ProgrammingLanguage.JAVA);
            expect(dto.maxComplaints).toBe(3);
            expect(dto.maxTeamComplaints).toBe(2);
            expect(dto.maxComplaintTimeDays).toBe(14);
            expect(dto.maxRequestMoreFeedbackTimeDays).toBe(10);
            expect(dto.maxComplaintTextLimit).toBe(3000);
            expect(dto.maxComplaintResponseTextLimit).toBe(4000);
            expect(dto.color).toBe('#ff0000');
            expect(dto.enrollmentEnabled).toBe(true);
            expect(dto.enrollmentConfirmationMessage).toBe('Welcome!');
            expect(dto.unenrollmentEnabled).toBe(true);
            expect(dto.learningPathsEnabled).toBe(true);
            expect(dto.studentCourseAnalyticsDashboardEnabled).toBe(true);
            expect(dto.presentationScore).toBe(10);
            expect(dto.maxPoints).toBe(100);
            expect(dto.accuracyOfScores).toBe(1);
            expect(dto.restrictedAthenaModulesAccess).toBe(true);
            expect(dto.timeZone).toBe('Europe/Berlin');
            expect(dto.courseInformationSharingConfiguration).toBe(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
        });

        it('should apply default values for undefined fields', () => {
            const minimalCourse: Course = {
                title: 'Minimal',
                shortName: 'MIN',
            };
            const dto = toCourseCreateDTO(minimalCourse);

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

        it('should not include id field', () => {
            const dto = toCourseCreateDTO(fullCourse) as any;
            expect(dto.id).toBeUndefined();
        });

        it('should handle undefined dates gracefully', () => {
            const courseNoDate: Course = { title: 'No Dates', shortName: 'ND' };
            const dto = toCourseCreateDTO(courseNoDate);
            expect(dto.startDate).toBeUndefined();
            expect(dto.endDate).toBeUndefined();
            expect(dto.enrollmentStartDate).toBeUndefined();
            expect(dto.enrollmentEndDate).toBeUndefined();
            expect(dto.unenrollmentEndDate).toBeUndefined();
        });
    });

    describe('toCourseUpdateDTO', () => {
        it('should convert a full course entity to an update DTO', () => {
            const dto: CourseUpdateDTO = toCourseUpdateDTO(fullCourse);

            expect(dto.id).toBe(42);
            expect(dto.title).toBe('Test Course');
            expect(dto.shortName).toBe('TC');
            expect(dto.description).toBe('A test course');
            expect(dto.semester).toBe('SS2025');
            expect(dto.studentGroupName).toBe('students');
            expect(dto.teachingAssistantGroupName).toBe('tutors');
            expect(dto.editorGroupName).toBe('editors');
            expect(dto.instructorGroupName).toBe('instructors');
            expect(dto.startDate).toBe(baseDate.toJSON());
            expect(dto.endDate).toBe(baseDate.add(3, 'months').toJSON());
            expect(dto.enrollmentStartDate).toBe(baseDate.subtract(1, 'month').toJSON());
            expect(dto.enrollmentEndDate).toBe(baseDate.toJSON());
            expect(dto.unenrollmentEndDate).toBe(baseDate.add(1, 'month').toJSON());
            expect(dto.testCourse).toBe(false);
            expect(dto.onlineCourse).toBe(true);
            expect(dto.language).toBe(Language.ENGLISH);
            expect(dto.defaultProgrammingLanguage).toBe(ProgrammingLanguage.JAVA);
            expect(dto.maxComplaints).toBe(3);
            expect(dto.maxTeamComplaints).toBe(2);
            expect(dto.maxComplaintTimeDays).toBe(14);
            expect(dto.maxRequestMoreFeedbackTimeDays).toBe(10);
            expect(dto.maxComplaintTextLimit).toBe(3000);
            expect(dto.maxComplaintResponseTextLimit).toBe(4000);
            expect(dto.color).toBe('#ff0000');
            expect(dto.courseIcon).toBe('icon.png');
            expect(dto.enrollmentEnabled).toBe(true);
            expect(dto.enrollmentConfirmationMessage).toBe('Welcome!');
            expect(dto.unenrollmentEnabled).toBe(true);
            expect(dto.courseInformationSharingMessagingCodeOfConduct).toBe('Be nice');
            expect(dto.onboardingDone).toBe(true);
            expect(dto.learningPathsEnabled).toBe(true);
            expect(dto.studentCourseAnalyticsDashboardEnabled).toBe(true);
            expect(dto.presentationScore).toBe(10);
            expect(dto.maxPoints).toBe(100);
            expect(dto.accuracyOfScores).toBe(1);
            expect(dto.restrictedAthenaModulesAccess).toBe(true);
            expect(dto.timeZone).toBe('Europe/Berlin');
            expect(dto.courseInformationSharingConfiguration).toBe(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
        });

        it('should apply default values for undefined fields', () => {
            const minimalCourse: Course = {
                id: 1,
                title: 'Minimal',
                shortName: 'MIN',
            };
            const dto = toCourseUpdateDTO(minimalCourse);

            expect(dto.id).toBe(1);
            expect(dto.testCourse).toBe(false);
            expect(dto.maxComplaintTimeDays).toBe(7);
            expect(dto.maxRequestMoreFeedbackTimeDays).toBe(7);
            expect(dto.maxComplaintTextLimit).toBe(2000);
            expect(dto.maxComplaintResponseTextLimit).toBe(2000);
            expect(dto.unenrollmentEnabled).toBe(false);
            expect(dto.onboardingDone).toBe(false);
            expect(dto.learningPathsEnabled).toBe(false);
            expect(dto.studentCourseAnalyticsDashboardEnabled).toBe(false);
            expect(dto.restrictedAthenaModulesAccess).toBe(false);
        });
    });
});
