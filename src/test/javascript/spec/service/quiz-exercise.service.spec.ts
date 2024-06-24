import { TranslateService } from '@ngx-translate/core';
import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController, TestRequest } from '@angular/common/http/testing';
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
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { QuizQuestion, QuizQuestionType } from 'app/entities/quiz/quiz-question.model';
import dayjs from 'dayjs/esm';
import { firstValueFrom } from 'rxjs';
import JSZip from 'jszip';

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
    const fileMap = new Map<string, Blob>();
    fileMap.set('file.jpg', new Blob());

    let service: QuizExerciseService;
    let httpMock: HttpTestingController;
    let elemDefault: QuizExercise;
    let mockJSZip: jest.Mocked<JSZip>;

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

    afterEach(() => {
        httpMock.verify();
        jest.restoreAllMocks();
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
        const result = firstValueFrom(service.create(new QuizExercise(undefined, undefined), fileMap));
        const req = httpMock.expectOne({ method: 'POST', url: 'api/quiz-exercises' });
        validateFormData(req);
        req.flush(returnedFromService);
        expect((await result)?.body).toEqual(expected);
    });

    it('should import a QuizExercise', async () => {
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
        const quizExercise = new QuizExercise(undefined, undefined);
        quizExercise.id = 42;

        const expected = Object.assign({}, returnedFromService);
        const result = firstValueFrom(service.import(quizExercise, fileMap));
        const req = httpMock.expectOne({ method: 'POST', url: 'api/quiz-exercises/import/42' });
        validateFormData(req);
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
        const result = firstValueFrom(service.update(1, expected, fileMap));
        const req = httpMock.expectOne({ method: 'PUT', url: 'api/quiz-exercises/1' });
        validateFormData(req);
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
        const req = httpMock.expectOne({ method: 'GET', url: 'api/quiz-exercises' });
        req.flush([returnedFromService]);
        expect((await result)?.body).toEqual([expected]);
    });

    const batch = new QuizBatch();
    const quizEx = makeQuiz();
    it.each([
        ['delete', [123], {}, 'DELETE', ''],
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
        // eslint-disable-next-line prefer-spread
        const result = firstValueFrom(service[method].apply(service, args)) as Promise<HttpResponse<unknown>>;
        const req = httpMock.expectOne({ method: httpMethod });
        expect(req.request.url).toEndWith(urlSuffix);
        req.flush(response);
        const resp = await result;
        expect(resp.ok).toBeTrue();
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
    ])('should export a quiz with no assets as json (%#)', async (questions, exportAll, filename, count) => {
        const spy = jest.spyOn(downloadUtil, 'downloadFile').mockReturnValue();
        service.exportQuiz(questions, exportAll, filename);

        if (count === 0) {
            expect(spy).not.toHaveBeenCalled();
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

    it('should fetch correct image names and paths from drag and drop questions', async () => {
        const spy = jest.spyOn(service, 'fetchFilePromise').mockResolvedValue();
        const questions: QuizQuestion[] = [
            {
                type: QuizQuestionType.DRAG_AND_DROP,
                text: '![image](path/to/image.png)',
                backgroundFilePath: 'path/to/background.png',
                dragItems: [{ pictureFilePath: 'path/to/dragItem1.png' }, { pictureFilePath: 'path/to/dragItem2.png' }],
            } as DragAndDropQuestion,
        ];
        service.exportAssetsFromAllQuestions(questions, 'output.zip');
        expect(spy).toHaveBeenCalledTimes(4);
        expect(spy).toHaveBeenNthCalledWith(1, 'q0_background.png', expect.anything(), 'path/to/background.png');
        expect(spy).toHaveBeenNthCalledWith(2, 'q0_dragItem-0.png', expect.anything(), 'path/to/dragItem1.png');
        expect(spy).toHaveBeenNthCalledWith(3, 'q0_dragItem-1.png', expect.anything(), 'path/to/dragItem2.png');
        expect(spy).toHaveBeenNthCalledWith(4, 'q0_image', expect.anything(), 'path/to/image.png');
    });

    it('should handle markdown without images', async () => {
        const description = 'No images here, just text';
        const regexArray = service.findImagesInMarkdown(description);
        expect(regexArray).toHaveLength(0);
    });

    it('should export images from multiple choice options', async () => {
        const spy = jest.spyOn(service, 'fetchFilePromise').mockResolvedValue();
        const questions: QuizQuestion[] = [
            {
                type: QuizQuestionType.MULTIPLE_CHOICE,
                answerOptions: [
                    { text: ' text ![option1](path/to/option1.png)', invalid: false },
                    { text: '![option2](path/to/option2.png)random', invalid: false },
                ],
                randomizeOrder: true,
                invalid: false,
                exportQuiz: false,
            } as MultipleChoiceQuestion,
        ];
        service.exportAssetsFromAllQuestions(questions, 'output.zip');
        expect(spy).toHaveBeenCalledTimes(2);
        expect(spy).toHaveBeenNthCalledWith(1, 'q0_option1', expect.anything(), 'path/to/option1.png');
        expect(spy).toHaveBeenNthCalledWith(2, 'q0_option2', expect.anything(), 'path/to/option2.png');
    });

    it('should export images from short answer questions', async () => {
        const spy = jest.spyOn(service, 'fetchFilePromise').mockResolvedValue();
        const questions: QuizQuestion[] = [
            {
                type: QuizQuestionType.SHORT_ANSWER,
                text: 'This is some text, text ![image](path/to/image.png) image there , no image here [txt](link)',
                randomizeOrder: true,
                invalid: false,
                exportQuiz: false,
            } as ShortAnswerQuestion,
        ];
        service.exportAssetsFromAllQuestions(questions, 'output.zip');
        expect(spy).toHaveBeenCalledOnce();
        expect(spy).toHaveBeenCalledWith('q0_image', expect.anything(), 'path/to/image.png');
    });

    it('should not try to fetch files if there are no images to export', async () => {
        const spy = jest.spyOn(service, 'fetchFilePromise').mockResolvedValue();
        const spyDownload = jest.spyOn(downloadUtil, 'downloadFile').mockReturnValue();

        const questions: QuizQuestion[] = [
            {
                type: QuizQuestionType.SHORT_ANSWER,
                text: 'This is some text image there , no image here [txt](link)',
                randomizeOrder: true,
                invalid: false,
                exportQuiz: false,
            } as ShortAnswerQuestion,
            {
                type: QuizQuestionType.MULTIPLE_CHOICE,
                answerOptions: [
                    { text: ' some option', invalid: false },
                    { text: ' some other option ', invalid: false },
                ],
                randomizeOrder: true,
                invalid: false,
                exportQuiz: false,
            } as MultipleChoiceQuestion,
        ];
        service.exportAssetsFromAllQuestions(questions, 'output.zip');
        expect(spy).not.toHaveBeenCalled();
        expect(spyDownload).toHaveBeenCalled();
    });
    it('should throw an error if file fails to fetch', async () => {
        const fileName = 'mockFile.png';
        const filePath = 'path/to/mockFile.png';
        const errorMessage = 'File with name: mockFile.png at path: path/to/mockFile.png could not be fetched';
        jest.spyOn(service, 'fetchFilePromise').mockRejectedValue(new Error(errorMessage));

        await expect(service.fetchFilePromise(fileName, mockJSZip, filePath)).rejects.toThrow(`File with name: ${fileName} at path: ${filePath} could not be fetched`);
    });
    function validateFormData(req: TestRequest) {
        expect(req.request.body).toBeInstanceOf(FormData);
        expect(req.request.body.get('exercise')).toBeInstanceOf(Blob);
        const fileArray = req.request.body.getAll('files');
        expect(fileArray).toBeArrayOfSize(1);
        expect(fileArray[0]).toBeInstanceOf(Blob);
    }
});
