import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { Course } from 'app/entities/course.model';
import { LectureService } from 'app/lecture/lecture.service';
import { AccountService } from 'app/core/auth/account.service';
import { EntityTitleService } from 'app/shared/layouts/navbar/entity-title.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';

export type EntityResponseType = HttpResponse<Course>;
export type EntityArrayResponseType = HttpResponse<Course[]>;

@Injectable({ providedIn: 'root' })
export class CourseAdminService {
    private resourceUrl = SERVER_API_URL + 'api/admin/courses';

    constructor(
        private http: HttpClient,
        private lectureService: LectureService,
        private accountService: AccountService,
        private entityTitleService: EntityTitleService,
        private courseManagementService: CourseManagementService,
    ) {}

    /**
     * creates a course using a POST request
     * @param course - the course to be created on the server
     */
    create(course: Course): Observable<EntityResponseType> {
        const copy = CourseManagementService.convertCourseDatesFromClient(course);
        return this.http
            .post<Course>(`${this.resourceUrl}`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.courseManagementService.processCourseEntityResponseType(res)));
    }

    /**
     * deletes the course corresponding to the given unique identifier using a DELETE request
     * @param courseId - the id of the course to be deleted
     */
    delete(courseId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/${courseId}`, { observe: 'response' });
    }
}
