import { Injectable, inject } from '@angular/core';
import { Observable, finalize, of, shareReplay, tap } from 'rxjs';
import { ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { ExamForOverview } from 'app/exam/shared/entities/exam-for-overview.model';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { LectureForOverview } from 'app/lecture/shared/entities/lecture-for-overview.model';

type StoredTabData<T> = { courseId: number; data: T };
type InFlightTabRequest<T> = { courseId: number; observable: Observable<T> };

/**
 * Holds the independently loaded lecture and exam overview responses for one course visit.
 *
 * Routed tab components are destroyed whenever the user opens a sibling tab. Keeping these responses in the
 * components would therefore repeat the same REST request when the user returns. This service retains only the
 * lightweight overview DTOs, shares concurrent requests, and is cleared by the course container on a course switch
 * or when the visit ends.
 */
@Injectable({ providedIn: 'root' })
export class CourseOverviewTabDataService {
    private readonly lectureService = inject(LectureService);
    private readonly examParticipationService = inject(ExamParticipationService);

    private activeCourseId?: number;
    private lectures?: StoredTabData<LectureForOverview[]>;
    private exams?: StoredTabData<ExamForOverview[]>;
    private lecturesRequest?: InFlightTabRequest<LectureForOverview[]>;
    private examsRequest?: InFlightTabRequest<ExamForOverview[]>;

    loadLecturesIfNeeded(courseId: number): Observable<LectureForOverview[]> {
        this.activateCourse(courseId);
        if (this.lectures?.courseId === courseId) {
            return of(this.lectures.data);
        }
        if (this.lecturesRequest?.courseId === courseId) {
            return this.lecturesRequest.observable;
        }

        const observable = this.lectureService.findAllByCourseIdForOverview(courseId).pipe(
            tap((lectures) => {
                if (this.activeCourseId === courseId) {
                    this.lectures = { courseId, data: lectures };
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
        if (this.exams?.courseId === courseId) {
            return of(this.exams.data);
        }
        if (this.examsRequest?.courseId === courseId) {
            return this.examsRequest.observable;
        }

        const observable = this.examParticipationService.getExamsForOverview(courseId).pipe(
            tap((exams) => {
                if (this.activeCourseId === courseId) {
                    this.exams = { courseId, data: exams };
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
