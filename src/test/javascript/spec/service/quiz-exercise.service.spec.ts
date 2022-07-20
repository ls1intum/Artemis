import { TranslateService } from '@ngx-translate/core';
import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { HttpResponse } from '@angular/common/http';
import { SessionStorageService } from 'ngx-webstorage';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { QuizBatch, QuizExercise, QuizStatus } from 'app/entities/quiz/quiz-exercise.model';
import { Course } from 'app/entities/course.model';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { ArtemisTestModule } from '../test.module';
import * as downloadUtil from 'app/shared/util/download.util';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import dayjs from 'dayjs/esm';
import { firstValueFrom } from 'rxjs';

/**
 * create a QuizExercise that when used as an HTTP response can be deserialized as an equal object
 */
const makeQuiz = () => {
    const quizEx = new QuizExercise(new Course(), undefined);
    quizEx.releaseDate = dayjs('1000-01-01T00:00:00Z');
    quizEx.dueDate = dayjs('2000-01-01T00:00:00Z');
    quizEx.assessmentDueDate = undefined;
    quizEx.exampleSolutionPublicationDate = undefined;
    quizEx.studentParticipations = [];
    delete quizEx.exerciseGroup;
    return quizEx;
};

describe('QuizExercise Service', () => {
    let service: QuizExerciseService;
    let httpMock: HttpTestingController;
    let elemDefault: QuizExercise;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule],
            providers: [
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
        service = TestBed.inject(QuizExerciseService);
        httpMock = TestBed.inject(HttpTestingController);

        elemDefault = makeQuiz();
    });

    it('should find an element', async () => {
        const returnedFromService = Object.assign({}, elemDefault);
        const result = firstValueFrom(service.find(123));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        expect((await result)?.body).toEqual(elemDefault);
    });

    it('should create a QuizExercise', async () => {
        const returnedFromService = Object.assign(
            {
                id: 0,
            },
            elemDefault,
        );
        const expected = Object.assign({}, returnedFromService);
        const result = firstValueFrom(service.create(new QuizExercise(undefined, undefined)));
        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        expect((await result)?.body).toEqual(expected);
    });

    it('should update a QuizExercise', async () => {
        const returnedFromService = Object.assign(
            {
                description: 'BBBBBB',
                explanation: 'BBBBBB',
                randomizeQuestionOrder: true,
                allowedNumberOfAttempts: 1,
                isVisibleBeforeStart: true,
                isOpenForPractice: true,
                isPlannedToStart: true,
                duration: 1,
            },
            elemDefault,
        );
        const expected = Object.assign({}, returnedFromService);
        const result = firstValueFrom(service.update(expected));
        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        expect((await result)?.body).toEqual(expected);
    });

    it('should return a list of QuizExercise', async () => {
        const returnedFromService = Object.assign(
            {
                description: 'BBBBBB',
                explanation: 'BBBBBB',
                randomizeQuestionOrder: true,
                allowedNumberOfAttempts: 1,
                isVisibleBeforeStart: true,
                isOpenForPractice: true,
                isPlannedToStart: true,
                duration: 1,
            },
            elemDefault,
        );
        const expected = Object.assign({}, returnedFromService);
        const result = firstValueFrom(service.query());
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush([returnedFromService]);
        expect((await result)?.body).toEqual([expected]);
    });

    const batch = new QuizBatch();
    const quizEx = makeQuiz();
    it.each([
        ['delete', [123], {}, 'DELETE', ''],
        ['reset', [123], {}, 'DELETE', '/reset'],
        ['join', [123, '12345678'], batch, 'POST', '/join'],
        ['addBatch', [123], batch, 'PUT', '/add-batch'],
        ['startBatch', [123], batch, 'PUT', '/start-batch'],
        ['setVisible', [123], quizEx, 'PUT', '/set-visible'],
        ['end', [123], quizEx, 'PUT', '/end-now'],
        ['start', [123], quizEx, 'PUT', '/start-now'],
        ['openForPractice', [123], quizEx, 'PUT', '/open-for-practice'],
        ['findForStudent', [123], quizEx, 'GET', '/for-student'],
        ['findForExam', [123], [quizEx], 'GET', '/quiz-exercises'],
        ['findForCourse', [123], [quizEx], 'GET', '/quiz-exercises'],
        ['recalculate', [123], quizEx, 'GET', '/recalculate-statistics'],
        ['find', [123], quizEx, 'GET', ''],
    ])('should perform a http request for %p', async (method, args, response, httpMethod, urlSuffix) => {
        const result = firstValueFrom(service[method].apply(service, args)) as Promise<HttpResponse<unknown>>;
        const req = httpMock.expectOne({ method: httpMethod });
        expect(req.request.url).toEndWith(urlSuffix);
        req.flush(response);
        const resp = await result;
        expect(resp.ok).toBe(true);
        expect(resp.body).toEqual(response);
    });

    it.each([
        [QuizStatus.INVISIBLE, false, false, false, false],
        [QuizStatus.VISIBLE, true, false, false, false],
        [QuizStatus.CLOSED, true, true, false, false],
        [QuizStatus.OPEN_FOR_PRACTICE, true, true, false, true],
        [QuizStatus.ACTIVE, true, false, true, false],
        // all other combinations are not valid
    ])('should get status %p', (result, quizStarted, quizEnded, started, practice) => {
        elemDefault.quizStarted = quizStarted;
        elemDefault.quizEnded = quizEnded;
        elemDefault.isOpenForPractice = practice;
        if (started !== undefined) {
            elemDefault.quizBatches = [{ started }];
        }
        expect(service.getStatus(elemDefault)).toBe(result);
    });

    const q = new MultipleChoiceQuestion();
    it.each([
        [[], false, undefined, 0],
        [[], true, undefined, 0],
        [[], false, 'test', 0],
        [[], true, 'test', 0],
        [
            [
                { ...q, exportQuiz: false },
                { ...q, exportQuiz: true },
            ],
            false,
            undefined,
            1,
        ],
        [
            [
                { ...q, exportQuiz: false },
                { ...q, exportQuiz: true },
            ],
            true,
            undefined,
            2,
        ],
        [
            [
                { ...q, exportQuiz: false },
                { ...q, exportQuiz: true },
            ],
            false,
            'test',
            1,
        ],
        [
            [
                { ...q, exportQuiz: false },
                { ...q, exportQuiz: true },
            ],
            true,
            'test',
            2,
        ],
    ])('should export a quiz (%#)', async (questions, exportAll, filename, count) => {
        const spy = jest.spyOn(downloadUtil, 'downloadFile').mockReturnValue();

        service.exportQuiz(questions, exportAll, filename);

        if (count === 0) {
            expect(spy).toHaveBeenCalledTimes(0);
        } else {
            expect(spy).toHaveBeenCalledOnce();
            const [blob, file] = spy.mock.calls[0];
            const data = await new Promise((resolve) => {
                const reader = new FileReader();
                reader.readAsText(blob);
                reader.onloadend = () => {
                    resolve(JSON.parse(reader.result as string));
                };
            });
            expect(blob.type).toBe('application/json');
            expect(data).toBeArrayOfSize(count);
            expect(file).toEndWith('.json');
        }
    });

    afterEach(() => {
        httpMock.verify();
        jest.restoreAllMocks();
    });
});
