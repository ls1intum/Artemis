import { TestBed } from '@angular/core/testing';
import { Subject, firstValueFrom, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CourseOverviewTabDataService } from 'app/course/overview/services/course-overview-tab-data.service';
import { ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { ExamForOverview } from 'app/exam/shared/entities/exam-for-overview.model';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { LectureForOverview } from 'app/lecture/shared/entities/lecture-for-overview.model';

describe('CourseOverviewTabDataService', () => {
    let service: CourseOverviewTabDataService;
    let findLectures: ReturnType<typeof vi.fn>;
    let findExams: ReturnType<typeof vi.fn>;

    beforeEach(() => {
        findLectures = vi.fn();
        findExams = vi.fn();
        TestBed.configureTestingModule({
            providers: [
                CourseOverviewTabDataService,
                { provide: LectureService, useValue: { findAllByCourseIdForOverview: findLectures } },
                { provide: ExamParticipationService, useValue: { getExamsForOverview: findExams } },
            ],
        });
        service = TestBed.inject(CourseOverviewTabDataService);
    });

    it('should reuse loaded lectures for the active course visit', async () => {
        const lectures: LectureForOverview[] = [{ id: 11, title: 'Lecture 11' }];
        findLectures.mockReturnValue(of(lectures));

        const first = await firstValueFrom(service.loadLecturesIfNeeded(1));
        const second = await firstValueFrom(service.loadLecturesIfNeeded(1));

        expect(findLectures).toHaveBeenCalledExactlyOnceWith(1);
        expect(first).toBe(lectures);
        expect(second).toBe(lectures);
    });

    it('should reuse loaded exams for the active course visit', async () => {
        const exams: ExamForOverview[] = [{ id: 21, title: 'Exam 21' }];
        findExams.mockReturnValue(of(exams));

        const first = await firstValueFrom(service.loadExamsIfNeeded(1));
        const second = await firstValueFrom(service.loadExamsIfNeeded(1));

        expect(findExams).toHaveBeenCalledExactlyOnceWith(1);
        expect(first).toBe(exams);
        expect(second).toBe(exams);
    });

    it('should share an in-flight request between concurrent tab consumers', () => {
        const response = new Subject<LectureForOverview[]>();
        const lectures: LectureForOverview[] = [{ id: 12, title: 'Concurrent lecture' }];
        const firstValues: LectureForOverview[][] = [];
        const secondValues: LectureForOverview[][] = [];
        findLectures.mockReturnValue(response.asObservable());

        const firstRequest = service.loadLecturesIfNeeded(1);
        const secondRequest = service.loadLecturesIfNeeded(1);
        firstRequest.subscribe((value) => firstValues.push(value));
        secondRequest.subscribe((value) => secondValues.push(value));
        response.next(lectures);
        response.complete();

        expect(findLectures).toHaveBeenCalledExactlyOnceWith(1);
        expect(secondRequest).toBe(firstRequest);
        expect(firstValues).toEqual([lectures]);
        expect(secondValues).toEqual([lectures]);
    });

    it('should not retain a stale response after switching courses', async () => {
        const oldResponse = new Subject<LectureForOverview[]>();
        const oldLectures: LectureForOverview[] = [{ id: 1, title: 'Old response' }];
        const newCourseLectures: LectureForOverview[] = [{ id: 2, title: 'New course' }];
        const refreshedOldCourseLectures: LectureForOverview[] = [{ id: 3, title: 'Refetched old course' }];
        findLectures.mockReturnValueOnce(oldResponse).mockReturnValueOnce(of(newCourseLectures)).mockReturnValueOnce(of(refreshedOldCourseLectures));

        service.loadLecturesIfNeeded(1).subscribe();
        expect(await firstValueFrom(service.loadLecturesIfNeeded(2))).toBe(newCourseLectures);
        oldResponse.next(oldLectures);
        oldResponse.complete();

        expect(await firstValueFrom(service.loadLecturesIfNeeded(1))).toBe(refreshedOldCourseLectures);
        expect(findLectures).toHaveBeenCalledTimes(3);
        expect(findLectures).toHaveBeenNthCalledWith(1, 1);
        expect(findLectures).toHaveBeenNthCalledWith(2, 2);
        expect(findLectures).toHaveBeenNthCalledWith(3, 1);
    });

    it('should share an in-flight exam request without letting its late response survive a course switch', async () => {
        const oldResponse = new Subject<ExamForOverview[]>();
        const staleExams: ExamForOverview[] = [{ id: 20, title: 'Stale exam' }];
        const refreshedExams: ExamForOverview[] = [{ id: 21, title: 'Refetched exam' }];
        const firstValues: ExamForOverview[][] = [];
        const secondValues: ExamForOverview[][] = [];
        findExams.mockReturnValueOnce(oldResponse).mockReturnValueOnce(of(refreshedExams));
        findLectures.mockReturnValue(of([]));

        const firstRequest = service.loadExamsIfNeeded(1);
        const secondRequest = service.loadExamsIfNeeded(1);
        firstRequest.subscribe((value) => firstValues.push(value));
        secondRequest.subscribe((value) => secondValues.push(value));
        await firstValueFrom(service.loadLecturesIfNeeded(2));
        oldResponse.next(staleExams);
        oldResponse.complete();

        expect(await firstValueFrom(service.loadExamsIfNeeded(1))).toBe(refreshedExams);
        expect(secondRequest).toBe(firstRequest);
        expect(firstValues).toEqual([staleExams]);
        expect(secondValues).toEqual([staleExams]);
        expect(findExams).toHaveBeenCalledTimes(2);
        expect(findExams).toHaveBeenNthCalledWith(1, 1);
        expect(findExams).toHaveBeenNthCalledWith(2, 1);
    });

    it('should retry a failed load instead of caching the error', async () => {
        const lectures: LectureForOverview[] = [{ id: 13, title: 'Retry result' }];
        findLectures.mockReturnValueOnce(throwError(() => new Error('load failed'))).mockReturnValueOnce(of(lectures));

        await expect(firstValueFrom(service.loadLecturesIfNeeded(1))).rejects.toThrow('load failed');
        await expect(firstValueFrom(service.loadLecturesIfNeeded(1))).resolves.toBe(lectures);

        expect(findLectures).toHaveBeenCalledTimes(2);
        expect(findLectures).toHaveBeenNthCalledWith(1, 1);
        expect(findLectures).toHaveBeenNthCalledWith(2, 1);
    });

    it('should refetch both tab responses after the course visit is cleared', async () => {
        const lectures: LectureForOverview[] = [{ id: 14, title: 'Lecture' }];
        const exams: ExamForOverview[] = [{ id: 24, title: 'Exam' }];
        findLectures.mockReturnValue(of(lectures));
        findExams.mockReturnValue(of(exams));

        await firstValueFrom(service.loadLecturesIfNeeded(1));
        await firstValueFrom(service.loadExamsIfNeeded(1));
        service.clear();
        await firstValueFrom(service.loadLecturesIfNeeded(1));
        await firstValueFrom(service.loadExamsIfNeeded(1));

        expect(findLectures).toHaveBeenCalledTimes(2);
        expect(findExams).toHaveBeenCalledTimes(2);
        expect(findLectures).toHaveBeenNthCalledWith(2, 1);
        expect(findExams).toHaveBeenNthCalledWith(2, 1);
    });
});
