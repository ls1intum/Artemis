import { Service, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CourseHome, CourseHomeGeneratedWelcome, CourseHomeTeachingTeam } from './course-home.model';

/** Stateless transport: bounded Home summaries never enter the full course/exercise cache. */
@Service()
export class CourseHomeService {
    private readonly http = inject(HttpClient);

    load(courseId: number, since?: string): Observable<CourseHome> {
        return this.http.get<CourseHome>(this.url(courseId), { params: since ? new HttpParams().set('since', since) : new HttpParams() });
    }
    acknowledgeVisit(courseId: number, snapshotAt: string, observedVariantCursor: number): Observable<void> {
        return this.http.post<void>(`${this.url(courseId)}/visits`, { snapshotAt, observedVariantCursor });
    }
    generateWelcome(courseId: number, contextFingerprint: string, locale: string): Observable<CourseHomeGeneratedWelcome> {
        return this.http.post<CourseHomeGeneratedWelcome>(`${this.url(courseId)}/welcome`, { contextFingerprint, locale });
    }
    teachingTeam(courseId: number, page: number): Observable<CourseHomeTeachingTeam> {
        return this.http.get<CourseHomeTeachingTeam>(`${this.url(courseId)}/teaching-team`, { params: { page } });
    }
    private url(courseId: number): string {
        return `api/course/courses/${courseId}/home`;
    }
}
