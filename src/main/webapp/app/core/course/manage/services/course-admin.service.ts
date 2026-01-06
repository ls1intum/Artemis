import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { Course } from 'app/core/course/shared/entities/course.model';
import { CourseCreateDTO } from 'app/core/course/shared/entities/course-create-dto.model';
import { objectToJsonBlob } from 'app/shared/util/blob-util';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { CourseSummaryDTO } from 'app/core/course/shared/entities/course-summary.model';
import { convertDateFromClient } from 'app/shared/util/date.utils';

export type EntityResponseType = HttpResponse<Course>;
export type EntityArrayResponseType = HttpResponse<Course[]>;

@Injectable({ providedIn: 'root' })
export class CourseAdminService {
    private http = inject(HttpClient);
    private courseManagementService = inject(CourseManagementService);

    private resourceUrl = 'api/core/admin/courses';

    /**
     * finds all groups for all courses using a GET request
     */
    getAllGroupsForAllCourses(): Observable<HttpResponse<string[]>> {
        return this.http.get<string[]>(this.resourceUrl + '/groups', { observe: 'response' });
    }

    /**
     * creates a course using a POST request
     * @param course - the course to be created on the server
     * @param courseImage - the course icon file
     */
    create(course: Course, courseImage?: Blob): Observable<EntityResponseType> {
        const courseCreateDTO = this.convertCourseToCourseCreateDTO(course);
        const formData = new FormData();
        formData.append('course', objectToJsonBlob(courseCreateDTO));
        if (courseImage) {
            // The image was cropped by us and is a blob, so we need to set a placeholder name for the server check
            formData.append('file', courseImage, 'placeholderName.png');
        }

        return this.http
            .post<Course>(this.resourceUrl, formData, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.courseManagementService.processCourseEntityResponseType(res)));
    }

    private convertCourseToCourseCreateDTO(course: Course): CourseCreateDTO {
        const enrollmentConfig = course.enrollmentConfiguration;
        const complaintConfig = course.complaintConfiguration;
        const extendedSettings = course.extendedSettings;
        const enrollmentConfiguration = enrollmentConfig
            ? {
                  enrollmentEnabled: enrollmentConfig.enrollmentEnabled,
                  enrollmentStartDate: convertDateFromClient(enrollmentConfig.enrollmentStartDate),
                  enrollmentEndDate: convertDateFromClient(enrollmentConfig.enrollmentEndDate),
                  enrollmentConfirmationMessage: enrollmentConfig.enrollmentConfirmationMessage,
                  unenrollmentEnabled: enrollmentConfig.unenrollmentEnabled,
                  unenrollmentEndDate: convertDateFromClient(enrollmentConfig.unenrollmentEndDate),
              }
            : undefined;
        const complaintConfiguration = complaintConfig
            ? {
                  maxComplaints: complaintConfig.maxComplaints,
                  maxTeamComplaints: complaintConfig.maxTeamComplaints,
                  maxComplaintTimeDays: complaintConfig.maxComplaintTimeDays,
                  maxRequestMoreFeedbackTimeDays: complaintConfig.maxRequestMoreFeedbackTimeDays,
                  maxComplaintTextLimit: complaintConfig.maxComplaintTextLimit,
                  maxComplaintResponseTextLimit: complaintConfig.maxComplaintResponseTextLimit,
              }
            : undefined;
        const extendedSettingsDTO = extendedSettings
            ? {
                  description: extendedSettings.description,
                  messagingCodeOfConduct: extendedSettings.messagingCodeOfConduct,
                  courseArchivePath: extendedSettings.courseArchivePath,
              }
            : undefined;
        return {
            title: course.title,
            shortName: course.shortName,
            semester: course.semester,
            studentGroupName: course.studentGroupName,
            teachingAssistantGroupName: course.teachingAssistantGroupName,
            editorGroupName: course.editorGroupName,
            instructorGroupName: course.instructorGroupName,
            startDate: convertDateFromClient(course.startDate),
            endDate: convertDateFromClient(course.endDate),
            testCourse: course.testCourse,
            onlineCourse: course.onlineCourse,
            language: course.language,
            defaultProgrammingLanguage: course.defaultProgrammingLanguage,
            color: course.color,
            faqEnabled: course.faqEnabled,
            learningPathsEnabled: course.learningPathsEnabled,
            studentCourseAnalyticsDashboardEnabled: course.studentCourseAnalyticsDashboardEnabled,
            presentationScore: course.presentationScore,
            maxPoints: course.maxPoints,
            accuracyOfScores: course.accuracyOfScores,
            restrictedAthenaModulesAccess: course.restrictedAthenaModulesAccess,
            timeZone: course.timeZone,
            courseInformationSharingConfiguration: course.courseInformationSharingConfiguration,
            enrollmentConfiguration,
            complaintConfiguration,
            extendedSettings: extendedSettingsDTO,
        };
    }

    /**
     * deletes the course corresponding to the given unique identifier using a DELETE request
     * @param courseId - the id of the course to be deleted
     */
    delete(courseId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/${courseId}`, { observe: 'response' });
    }

    /**
     * Returns a comprehensive summary for the course containing all relevant data counts.
     * Used by both deletion and reset confirmation dialogs.
     * @param courseId - the id of the course to get the summary for
     */
    getCourseSummary(courseId: number): Observable<HttpResponse<CourseSummaryDTO>> {
        return this.http.get<CourseSummaryDTO>(`${this.resourceUrl}/${courseId}/summary`, { observe: 'response' });
    }

    /**
     * Resets the course by removing all student data while preserving the course structure.
     * @param courseId - the id of the course to reset
     */
    reset(courseId: number): Observable<HttpResponse<void>> {
        return this.http.post<void>(`${this.resourceUrl}/${courseId}/reset`, null, { observe: 'response' });
    }
}
