import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Course } from 'app/core/course/shared/entities/course.model';

export interface CampusOnlineCourseDTO {
    campusOnlineCourseId: string;
    title: string;
    semester?: string;
    language?: string;
    responsibleInstructor?: string;
    department?: string;
    studyProgram?: string;
    alreadyImported: boolean;
}

export interface CampusOnlineLinkRequest {
    campusOnlineCourseId: string;
    responsibleInstructor?: string;
    department?: string;
    studyProgram?: string;
}

export interface CampusOnlineSyncResultDTO {
    coursesSynced: number;
    coursesFailed: number;
    usersAdded: number;
    usersNotFound: number;
}

@Injectable({ providedIn: 'root' })
export class CampusOnlineService {
    private http = inject(HttpClient);
    private resourceUrl = 'api/core/admin/campus-online';

    searchCourses(query: string, semester?: string): Observable<CampusOnlineCourseDTO[]> {
        let url = `${this.resourceUrl}/courses/search?query=${encodeURIComponent(query)}`;
        if (semester) {
            url += `&semester=${encodeURIComponent(semester)}`;
        }
        return this.http.get<CampusOnlineCourseDTO[]>(url);
    }

    linkCourse(courseId: number, request: CampusOnlineLinkRequest): Observable<HttpResponse<Course>> {
        return this.http.put<Course>(`${this.resourceUrl}/courses/${courseId}/link`, request, { observe: 'response' });
    }

    unlinkCourse(courseId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/courses/${courseId}/link`, { observe: 'response' });
    }

    syncCourse(courseId: number): Observable<CampusOnlineSyncResultDTO> {
        return this.http.post<CampusOnlineSyncResultDTO>(`${this.resourceUrl}/courses/${courseId}/sync`, null);
    }

    syncAllCourses(): Observable<CampusOnlineSyncResultDTO> {
        return this.http.post<CampusOnlineSyncResultDTO>(`${this.resourceUrl}/sync`, null);
    }
}
