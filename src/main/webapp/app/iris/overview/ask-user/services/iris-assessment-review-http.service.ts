import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { BehaviorSubject, Observable, map, of, switchMap, take, tap } from 'rxjs';
import { QAExchangeDTO } from 'app/iris/shared/entities/iris-qa-exchange-dto.model';
import { IrisAssessment } from 'app/iris/shared/entities/iris-assessment.model';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { IrisInClassQuizDTO } from 'app/iris/shared/entities/iris-in-class-quiz-dto.model';
import dayjs from 'dayjs/esm';
import { convertDateFromServer } from 'app/foundation/util/date.utils';
import { PageableResult, SearchTermPageableSearch } from 'app/foundation/pagination/pageable-table';

export interface IrisAssessmentReviewParticipation extends ProgrammingExerciseStudentParticipation {
    exerciseId?: number;
}

export interface IrisAssessmentReviewSearch extends SearchTermPageableSearch {
    filterProps?: string[];
}

interface IrisAssessmentReviewPageResponse {
    participations?: IrisAssessmentReviewParticipation[];
    participationsPerFilter?: Record<string, number>;
}

export interface IrisAssessmentReviewPage extends PageableResult<IrisAssessmentReviewParticipation> {
    participationsPerFilter: ReadonlyMap<string, number>;
}

@Injectable({ providedIn: 'root' })
export class IrisAssessmentReviewHttpService {
    private readonly availableInClassQuizState = new BehaviorSubject<ReadonlyMap<number, IrisInClassQuizDTO>>(new Map());

    private http = inject(HttpClient);
    private participationService = inject(ParticipationService);

    public resourceUrl = 'api/iris/assessments';

    /**
     * accepts the answers of the last askUser mode chat and makes the submission points count
     * @param assessmentId The unique identifier of the assessment
     */
    acceptAnswers(assessmentId: number): Observable<HttpResponse<void>> {
        return this.http.patch<void>(`${this.resourceUrl}/${assessmentId}/accept`, {}, { observe: 'response' });
    }

    /**
     * rejects the answers of the last askUser mode chat and makes the submission points NOT count
     * @param assessmentId The unique identifier of the assessment
     */
    rejectAnswers(assessmentId: number): Observable<HttpResponse<void>> {
        return this.http.patch<void>(`${this.resourceUrl}/${assessmentId}/reject`, {}, { observe: 'response' });
    }

    /**
     * gets the QAExchange objects of the last askUser mode chat
     * @param assessmentId The unique identifier of the assessment
     * @param inClass Whether the wanted chat is part of an in-class quiz session
     */
    getAssessmentChat(assessmentId: number, inClass = false): Observable<HttpResponse<QAExchangeDTO[]>> {
        const params = inClass ? new HttpParams().set('inClass', inClass) : undefined;

        return this.http.get<QAExchangeDTO[]>(`${this.resourceUrl}/${assessmentId}/chat`, { observe: 'response', params });
    }

    findWithPoints(assessmentId: number): Observable<HttpResponse<IrisAssessment>> {
        return this.http.get<IrisAssessment>(`${this.resourceUrl}/${assessmentId}`, { observe: 'response' });
    }

    findAllParticipationsNonZeroLatestScoreByProgrammingExercise(exerciseId: number, inClass = false): Observable<HttpResponse<ProgrammingExerciseStudentParticipation[]>> {
        const params = inClass ? new HttpParams().set('inClass', inClass) : undefined;

        return this.http
            .get<ProgrammingExerciseStudentParticipation[]>(`api/iris/programming-exercises/${exerciseId}/participations/non-zero-latest-score`, {
                observe: 'response',
                params,
            })
            .pipe(map((res: HttpResponse<ProgrammingExerciseStudentParticipation[]>) => this.participationService.processParticipationEntityArrayResponseType(res)));
    }

    searchAssessmentReviewParticipations(courseId: number, search: IrisAssessmentReviewSearch, inClass = false): Observable<IrisAssessmentReviewPage> {
        let params = new HttpParams()
            .set('page', search.page)
            .set('pageSize', search.pageSize)
            .set('sortingOrder', search.sortingOrder)
            .set('sortedColumn', search.sortedColumn)
            .set('searchTerm', search.searchTerm);

        if (search.filterProps?.length) {
            params = params.set('filterProps', search.filterProps.join(','));
        }
        if (inClass) {
            params = params.set('inClass', inClass);
        }

        return this.http
            .get<IrisAssessmentReviewPageResponse>(`api/iris/courses/${courseId}/assessment-review/participations`, {
                observe: 'response',
                params,
            })
            .pipe(
                map((response) => {
                    const content = response.body?.participations ?? [];
                    content.forEach((participation) => {
                        participation.userIndependentRepositoryUri = participation.userIndependentRepositoryUri ?? participation.repositoryUri;
                    });

                    return {
                        content,
                        totalElements: Number(response.headers.get('X-Total-Count') ?? 0),
                        participationsPerFilter: new Map(Object.entries(response.body?.participationsPerFilter ?? {})),
                    };
                }),
            );
    }

    /**
     * makes the in-class quiz available for students of the exercise
     * @param exerciseId The unique identifier of the exercise
     */
    makeInClassQuizAvailable(exerciseId: number): Observable<HttpResponse<IrisInClassQuizDTO>> {
        return this.http.patch<IrisInClassQuizDTO>(`api/iris/programming-exercises/${exerciseId}/ask-user/in-class/available`, {}, { observe: 'response' }).pipe(
            map((response) => this.convertInClassQuizResponseFromServer(response)),
            tap((response) => this.setAvailableInClassQuiz(exerciseId, response.body ?? undefined)),
        );
    }

    availableInClassQuizForExercise(exerciseId: number): Observable<IrisInClassQuizDTO | undefined> {
        return this.currentAvailableInClassQuizForExercise(exerciseId).pipe(
            switchMap(() => this.availableInClassQuizState.pipe(map((availableInClassQuizzes) => availableInClassQuizzes.get(exerciseId)))),
        );
    }

    /**
     * gets the active instructor-controlled in-class quiz data for the exercise
     * if exercise has not been looked up in service state yet, a server request is made
     *
     * @param exerciseId The unique identifier of the exercise
     */
    currentAvailableInClassQuizForExercise(exerciseId: number): Observable<IrisInClassQuizDTO | undefined> {
        return this.availableInClassQuizState.pipe(
            take(1),
            switchMap((availableInClassQuizzes) => {
                const availableInClassQuiz = availableInClassQuizzes.get(exerciseId);

                if (availableInClassQuiz?.timerExpiresAt.isAfter(dayjs())) {
                    return of(availableInClassQuiz);
                }

                return this.getAvailableInClassQuiz(exerciseId).pipe(map((response) => response.body ?? undefined));
            }),
        );
    }

    getAvailableInClassQuiz(exerciseId: number): Observable<HttpResponse<IrisInClassQuizDTO>> {
        return this.http.get<IrisInClassQuizDTO>(`api/iris/programming-exercises/${exerciseId}/ask-user/in-class`, { observe: 'response' }).pipe(
            map((response) => this.convertInClassQuizResponseFromServer(response)),
            tap((response) => this.setAvailableInClassQuiz(exerciseId, response.body ?? undefined)),
        );
    }

    clearActiveInClassQuiz(exerciseId: number): void {
        this.setAvailableInClassQuiz(exerciseId, undefined);
    }

    convertInClassQuizResponseFromServer(response: HttpResponse<IrisInClassQuizDTO>): HttpResponse<IrisInClassQuizDTO> {
        if (!response.body) {
            return response;
        }

        const timerExpiresAt = convertDateFromServer(response.body.timerExpiresAt)!;

        return response.clone({
            body: {
                ...response.body,
                timerExpiresAt,
                timeLimit: response.body.timeLimit ?? Math.max(timerExpiresAt.diff(dayjs(), 'second'), 0),
            },
        });
    }

    private setAvailableInClassQuiz(exerciseId: number, inClassQuiz: IrisInClassQuizDTO | undefined): void {
        const activeInClassQuizzes = new Map(this.availableInClassQuizState.value);

        if (inClassQuiz?.timerExpiresAt.isAfter(dayjs())) {
            activeInClassQuizzes.set(exerciseId, inClassQuiz);
        } else {
            activeInClassQuizzes.delete(exerciseId);
        }

        this.availableInClassQuizState.next(activeInClassQuizzes);
    }
}
