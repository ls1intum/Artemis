import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { Course } from 'app/entities/course.model';
import { objectToJsonBlob } from 'app/utils/blob-util';
import { CourseManagementService } from 'app/course/manage/course-management.service';

export type EntityResponseType = HttpResponse<Course>;
export type EntityArrayResponseType = HttpResponse<Course[]>;

@Injectable({ providedIn: 'root' })
export class CourseAdminService {
    private resourceUrl = SERVER_API_URL + 'api/admin/courses';

    constructor(private http: HttpClient, private courseManagementService: CourseManagementService) {}

    /**
     * creates a course using a POST request
     * @param course - the course to be created on the server
     * @param courseImage - the course icon file
     */
    create(course: Course, courseImage?: Blob): Observable<EntityResponseType> {
        const copy = CourseManagementService.convertCourseDatesFromClient(course);
        const formData = new FormData();
        formData.append('course', objectToJsonBlob(copy));
        if (courseImage) {
            // The image was cropped by us and is a blob, so we need to set a placeholder name for the server check
            formData.append('file', courseImage, 'placeholderName.png');
        }

        return this.http
            .post<Course>(this.resourceUrl, formData, { observe: 'response' })
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
