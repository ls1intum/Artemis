import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpHeaders } from '@angular/common/http';
import { provideHttpClient } from '@angular/common/http';
import { take } from 'rxjs';
import dayjs from 'dayjs/esm';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ParticipationService } from 'app/exercise/participation/participation.service';
import { IrisAssessmentReviewHttpService } from 'app/iris/overview/ask-user/services/iris-assessment-review-http.service';
import { IrisInClassQuizDTO } from 'app/iris/shared/entities/iris-in-class-quiz-dto.model';
import { IrisVerdict } from 'app/iris/shared/entities/iris-verdict.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { SortingOrder } from 'app/foundation/pagination/pageable-table';
import { IrisAssessment } from 'app/iris/shared/entities/iris-assessment.model';
import { QAExchangeDTO } from 'app/iris/shared/entities/iris-qa-exchange-dto.model';

describe('IrisAssessmentReviewHttpService', () => {
    let service: IrisAssessmentReviewHttpService;
    let httpMock: HttpTestingController;
    let participationService: { processParticipationEntityArrayResponseType: ReturnType<typeof vi.fn> };

    const exerciseId = 42;

    beforeEach(() => {
        participationService = { processParticipationEntityArrayResponseType: vi.fn((response) => response) };

        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), IrisAssessmentReviewHttpService, { provide: ParticipationService, useValue: participationService }],
        });

        service = TestBed.inject(IrisAssessmentReviewHttpService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    it('should accept assessment answers', () => {
        service.acceptAnswers(7).subscribe((response) => expect(response.status).toBe(204));

        const request = httpMock.expectOne('api/iris/assessments/7/accept');
        expect(request.request.method).toBe('PATCH');
        expect(request.request.body).toEqual({});
        request.flush(null);
    });

    it('should reject assessment answers', () => {
        service.rejectAnswers(7).subscribe((response) => expect(response.status).toBe(204));

        const request = httpMock.expectOne('api/iris/assessments/7/reject');
        expect(request.request.method).toBe('PATCH');
        expect(request.request.body).toEqual({});
        request.flush(null);
    });

    it('should get the assessment chat with in-class parameter', () => {
        const rows: QAExchangeDTO[] = [{ id: 1, question: 'Question', answer: 'Answer', reasoning: 'Reasoning' }];

        service.getAssessmentChat(7, true).subscribe((response) => expect(response.body).toEqual(rows));

        const request = httpMock.expectOne((req) => req.method === 'GET' && req.url === 'api/iris/assessments/7/chat');
        expect(request.request.params.get('inClass')).toBe('true');
        request.flush(rows);
    });

    it('should not add an in-class parameter when getting a regular assessment chat', () => {
        service.getAssessmentChat(7).subscribe((response) => expect(response.body).toEqual([]));

        const request = httpMock.expectOne((req) => req.method === 'GET' && req.url === 'api/iris/assessments/7/chat');
        expect(request.request.params.has('inClass')).toBe(false);
        request.flush([]);
    });

    it('should find an assessment with points', () => {
        const assessment = { id: 7, verdict: IrisVerdict.SUSPICIOUS } as IrisAssessment;

        service.findWithStudent(7).subscribe((response) => expect(response.body).toEqual(assessment));

        const request = httpMock.expectOne('api/iris/assessments/7');
        expect(request.request.method).toBe('GET');
        request.flush(assessment);
    });

    it('should search assessment review participations and map headers and filter counts', () => {
        const participation = {
            id: 17,
            repositoryUri: 'repository-url',
        } as ProgrammingExerciseStudentParticipation;

        service
            .searchAssessmentReviewParticipations(
                5,
                {
                    page: 1,
                    pageSize: 25,
                    sortingOrder: SortingOrder.DESCENDING,
                    sortedColumn: 'student.login',
                    searchTerm: 'student',
                    filterProps: ['SUSPICIOUS'],
                },
                true,
            )
            .subscribe((page) => {
                expect(page.content).toEqual([{ ...participation, userIndependentRepositoryUri: 'repository-url' }]);
                expect(page.totalElements).toBe(3);
                expect(page.participationsPerFilter.get('SUSPICIOUS')).toBe(2);
            });

        const request = httpMock.expectOne((req) => req.method === 'GET' && req.url === 'api/iris/courses/5/assessment-review/participations');
        expect(request.request.params.get('page')).toBe('1');
        expect(request.request.params.get('pageSize')).toBe('25');
        expect(request.request.params.get('sortingOrder')).toBe(SortingOrder.DESCENDING);
        expect(request.request.params.get('sortedColumn')).toBe('student.login');
        expect(request.request.params.get('searchTerm')).toBe('student');
        expect(request.request.params.get('filterProps')).toBe('SUSPICIOUS');
        expect(request.request.params.get('inClass')).toBe('true');
        request.flush(
            {
                participations: [participation],
                participationsPerFilter: { SUSPICIOUS: 2 },
            },
            { headers: new HttpHeaders({ 'X-Total-Count': '3' }) },
        );
    });

    it('should use empty defaults when search response omits optional values', () => {
        service
            .searchAssessmentReviewParticipations(5, {
                page: 0,
                pageSize: 25,
                sortingOrder: SortingOrder.ASCENDING,
                sortedColumn: 'id',
                searchTerm: '',
            })
            .subscribe((page) => {
                expect(page.content).toEqual([]);
                expect(page.totalElements).toBe(0);
                expect(page.participationsPerFilter.size).toBe(0);
            });

        const request = httpMock.expectOne((req) => req.method === 'GET' && req.url === 'api/iris/courses/5/assessment-review/participations');
        expect(request.request.params.has('filterProps')).toBe(false);
        expect(request.request.params.has('inClass')).toBe(false);
        request.flush({});
    });

    it('should make the in-class quiz available and expose the active timer', () => {
        const expiresAt = dayjs().add(2, 'minutes');

        service.makeInClassQuizAvailable(exerciseId).subscribe((response) => {
            expect(response.body?.timerExpiresAt.isSame(expiresAt)).toBe(true);
            expect(response.body?.timeLimit).toBeGreaterThan(0);
        });

        const request = httpMock.expectOne(`api/iris/programming-exercises/${exerciseId}/ask-user/in-class/available`);
        expect(request.request.method).toBe('PATCH');
        request.flush({ timerExpiresAt: expiresAt.toJSON() });

        service
            .availableInClassQuizForExercise(exerciseId)
            .pipe(take(1))
            .subscribe((activeInClassQuiz) => expect(activeInClassQuiz?.timerExpiresAt.isSame(expiresAt)).toBe(true));
    });

    it('should get the active in-class quiz window from the server', () => {
        const expiresAt = dayjs().add(90, 'seconds');
        const timeLimit = 90;

        service.getAvailableInClassQuiz(exerciseId).subscribe((response) => {
            expect(response.body?.timerExpiresAt.isSame(expiresAt)).toBe(true);
            expect(response.body?.timeLimit).toBe(timeLimit);
        });

        const request = httpMock.expectOne(`api/iris/programming-exercises/${exerciseId}/ask-user/in-class`);
        expect(request.request.method).toBe('GET');
        request.flush({ timerExpiresAt: expiresAt.toJSON(), timeLimit });
    });

    it('should clear the active in-class quiz window', () => {
        const expiresAt = dayjs().add(2, 'minutes');
        const activeInClassQuizzes: (IrisInClassQuizDTO | undefined)[] = [];

        service.makeInClassQuizAvailable(exerciseId).subscribe();
        const request = httpMock.expectOne(`api/iris/programming-exercises/${exerciseId}/ask-user/in-class/available`);
        request.flush({ timerExpiresAt: expiresAt.toJSON() });

        const subscription = service.availableInClassQuizForExercise(exerciseId).subscribe((activeInClassQuiz) => activeInClassQuizzes.push(activeInClassQuiz));

        service.clearActiveInClassQuiz(exerciseId);

        expect(activeInClassQuizzes.at(-1)).toBeUndefined();

        subscription.unsubscribe();
    });

    it('should ignore expired in-class quiz data from the server', () => {
        const expiresAt = dayjs().subtract(1, 'minute');

        service.getAvailableInClassQuiz(exerciseId).subscribe((response) => {
            expect(response.body?.timerExpiresAt.isSame(expiresAt)).toBe(true);
            expect(response.body?.timeLimit).toBe(0);
        });

        const request = httpMock.expectOne(`api/iris/programming-exercises/${exerciseId}/ask-user/in-class`);
        request.flush({ timerExpiresAt: expiresAt.toJSON() });

        service
            .availableInClassQuizForExercise(exerciseId)
            .pipe(take(1))
            .subscribe((activeInClassQuiz) => expect(activeInClassQuiz).toBeUndefined());

        httpMock.expectOne(`api/iris/programming-exercises/${exerciseId}/ask-user/in-class`).flush(null);
    });
});
