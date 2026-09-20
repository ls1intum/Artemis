import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, finalize, of, shareReplay, tap } from 'rxjs';
import { ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { ExamForOverview } from 'app/exam/shared/entities/exam-for-overview.model';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { LectureForOverview } from 'app/lecture/shared/entities/lecture-for-overview.model';
import { currentNavigationId } from 'app/course/overview/services/navigation-scope';

type StoredTabData<T> = { courseId: number; navigationId: number; data: T };
type InFlightTabRequest<T> = { courseId: number; observable: Observable<T> };

/**
 * Holds the independently loaded lecture and exam overview responses for one course visit.
 *
 * The route guard and the tab component both run for one tab selection, so this service shares the response between
 * them. It is scoped to that single navigation: selecting the tab again — which is how a student refreshes — asks the
 * server, so a lecture that was published or an exam that became visible appears without a page reload.
 */
@Injectable({ providedIn: 'root' })
export class CourseOverviewTabDataService {
    private readonly lectureService = inject(LectureService);
    private readonly examParticipationService = inject(ExamParticipationService);
    private readonly router = inject(Router);

    private activeCourseId?: number;
    private lectures?: StoredTabData<LectureForOverview[]>;
    private exams?: StoredTabData<ExamForOverview[]>;
    private lecturesRequest?: InFlightTabRequest<LectureForOverview[]>;
    private examsRequest?: InFlightTabRequest<ExamForOverview[]>;

    loadLecturesIfNeeded(courseId: number): Observable<LectureForOverview[]> {
        this.activateCourse(courseId);
        if (this.lectures?.courseId === courseId && this.lectures.navigationId === currentNavigationId(this.router)) {
            return of(this.lectures.data);
        }
        if (this.lecturesRequest?.courseId === courseId) {
            return this.lecturesRequest.observable;
        }

        const observable = this.lectureService.findAllByCourseIdForOverview(courseId).pipe(
            tap((lectures) => {
                if (this.activeCourseId === courseId) {
                    this.lectures = { courseId, navigationId: currentNavigationId(this.router), data: lectures };
                }
            }),
            finalize(() => {
                if (this.lecturesRequest?.observable === observable) {
                    this.lecturesRequest = undefined;
                }
            }),
            shareReplay({ bufferSize: 1, refCount: true }),
        );
        this.lecturesRequest = { courseId, observable };
        return observable;
    }

    loadExamsIfNeeded(courseId: number): Observable<ExamForOverview[]> {
        this.activateCourse(courseId);
        if (this.exams?.courseId === courseId && this.exams.navigationId === currentNavigationId(this.router)) {
            return of(this.exams.data);
        }
        if (this.examsRequest?.courseId === courseId) {
            return this.examsRequest.observable;
        }

        const observable = this.examParticipationService.getExamsForOverview(courseId).pipe(
            tap((exams) => {
                if (this.activeCourseId === courseId) {
                    this.exams = { courseId, navigationId: currentNavigationId(this.router), data: exams };
                }
            }),
            finalize(() => {
                if (this.examsRequest?.observable === observable) {
                    this.examsRequest = undefined;
                }
            }),
            shareReplay({ bufferSize: 1, refCount: true }),
        );
        this.examsRequest = { courseId, observable };
        return observable;
    }

    /** Drops all per-visit tab data so the next course visit fetches fresh responses. */
    clear(): void {
        this.activeCourseId = undefined;
        this.lectures = undefined;
        this.exams = undefined;
        this.lecturesRequest = undefined;
        this.examsRequest = undefined;
    }

    private activateCourse(courseId: number): void {
        if (this.activeCourseId === courseId) {
            return;
        }
        this.clear();
        this.activeCourseId = courseId;
    }
}
