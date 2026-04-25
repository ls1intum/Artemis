import { describe, expect, it, vi } from 'vitest';
import dayjs from 'dayjs/esm';
import * as dateUtils from 'app/shared/util/date.utils';
import { toCourseCreateDTO, toCourseUpdateDTO } from 'app/core/course/shared/entities/course-update-dto.model';
import { Course, CourseInformationSharingConfiguration } from 'app/core/course/shared/entities/course.model';
import { ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';

describe('CourseUpdateDTO mapping', () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('toCourseCreateDTO', () => {
        it('should convert a course entity to a create DTO with all fields', () => {
            const convertSpy = vi.spyOn(dateUtils, 'convertDateFromClient').mockReturnValue('2024-01-01T00:00:00.000Z');

            const course = new Course();
            course.title = 'Test Course';
            course.shortName = 'TC';
            course.description = 'A test course';
            course.semester = 'WS2024';
            course.studentGroupName = 'students';
            course.teachingAssistantGroupName = 'tutors';
            course.editorGroupName = 'editors';
            course.instructorGroupName = 'instructors';
            course.startDate = dayjs('2024-01-01');
            course.endDate = dayjs('2024-06-30');
            course.testCourse = false;
            course.onlineCourse = true;
            course.language = undefined;
            course.defaultProgrammingLanguage = ProgrammingLanguage.JAVA;
            course.maxComplaints = 3;
            course.maxTeamComplaints = 3;
            course.maxComplaintTimeDays = 14;
            course.maxRequestMoreFeedbackTimeDays = 7;
            course.maxComplaintTextLimit = 2000;
            course.maxComplaintResponseTextLimit = 2000;
            course.color = '#00ff00';
            course.enrollmentEnabled = true;
            course.unenrollmentEnabled = false;
            course.learningPathsEnabled = false;
            course.studentCourseAnalyticsDashboardEnabled = false;
            course.restrictedAthenaModulesAccess = false;
            course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;

            const dto = toCourseCreateDTO(course);

            expect(dto.title).toBe('Test Course');
            expect(dto.shortName).toBe('TC');
            expect(dto.description).toBe('A test course');
            expect(dto.semester).toBe('WS2024');
            expect(dto.studentGroupName).toBe('students');
            expect(dto.testCourse).toBe(false);
            expect(dto.onlineCourse).toBe(true);
            expect(dto.defaultProgrammingLanguage).toBe(ProgrammingLanguage.JAVA);
            expect(dto.maxComplaints).toBe(3);
            expect(dto.maxComplaintTimeDays).toBe(14);
            expect(dto.color).toBe('#00ff00');
            expect(dto.enrollmentEnabled).toBe(true);
            expect(dto.unenrollmentEnabled).toBe(false);
            expect(dto.learningPathsEnabled).toBe(false);
            expect(dto.restrictedAthenaModulesAccess).toBe(false);
            expect(dto.courseInformationSharingConfiguration).toBe(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
            expect(convertSpy).toHaveBeenCalledTimes(5);
        });

        it('should use default values when course fields are undefined', () => {
            vi.spyOn(dateUtils, 'convertDateFromClient').mockReturnValue(undefined);

            const course = new Course();
            course.title = 'Minimal Course';
            course.shortName = 'MC';

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
    });

    describe('toCourseUpdateDTO', () => {
        it('should convert a course entity to an update DTO with id', () => {
            const convertSpy = vi.spyOn(dateUtils, 'convertDateFromClient').mockReturnValue('2024-03-01T00:00:00.000Z');

            const course = new Course();
            course.id = 42;
            course.title = 'Updated Course';
            course.shortName = 'UC';
            course.description = 'Updated description';
            course.semester = 'SS2024';
            course.courseIcon = 'icon.png';
            course.courseInformationSharingMessagingCodeOfConduct = 'Be nice';
            course.startDate = dayjs('2024-03-01');
            course.testCourse = true;
            course.maxComplaintTimeDays = 10;
            course.maxRequestMoreFeedbackTimeDays = 5;
            course.maxComplaintTextLimit = 1500;
            course.maxComplaintResponseTextLimit = 1500;
            course.unenrollmentEnabled = true;
            course.learningPathsEnabled = true;
            course.studentCourseAnalyticsDashboardEnabled = true;
            course.restrictedAthenaModulesAccess = true;
            course.timeZone = 'Europe/Berlin';

            const dto = toCourseUpdateDTO(course);

            expect(dto.id).toBe(42);
            expect(dto.title).toBe('Updated Course');
            expect(dto.shortName).toBe('UC');
            expect(dto.description).toBe('Updated description');
            expect(dto.courseIcon).toBe('icon.png');
            expect(dto.courseInformationSharingMessagingCodeOfConduct).toBe('Be nice');
            expect(dto.testCourse).toBe(true);
            expect(dto.maxComplaintTimeDays).toBe(10);
            expect(dto.unenrollmentEnabled).toBe(true);
            expect(dto.learningPathsEnabled).toBe(true);
            expect(dto.studentCourseAnalyticsDashboardEnabled).toBe(true);
            expect(dto.restrictedAthenaModulesAccess).toBe(true);
            expect(dto.timeZone).toBe('Europe/Berlin');
            expect(convertSpy).toHaveBeenCalledTimes(5);
        });

        it('should use default values for undefined fields', () => {
            vi.spyOn(dateUtils, 'convertDateFromClient').mockReturnValue(undefined);

            const course = new Course();
            course.id = 1;
            course.title = 'Minimal';
            course.shortName = 'M';

            const dto = toCourseUpdateDTO(course);

            expect(dto.id).toBe(1);
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
