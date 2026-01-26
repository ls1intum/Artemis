import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { take } from 'rxjs/operators';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';
import { ModelingAssessmentService } from 'app/modeling/manage/assess/modeling-assessment.service';
import { ComplaintResponse } from 'app/assessment/shared/entities/complaint-response.model';
import { UMLDiagramType, UMLModel } from '@tumaet/apollon';
import { AssessmentNamesForModelId, getNamesForAssessments } from 'app/modeling/manage/assess/modeling-assessment.util';

const assessmentNames: AssessmentNamesForModelId = {
    '6': {
        name: 'Dominik',
        type: 'class',
    },
    '7': {
        name: 'Dominik',
        type: 'package',
    },
    '8': {
        name: 'Dominik',
        type: 'interface',
    },
    '9': {
        name: 'Dominik',
        type: 'abstractClass',
    },
    '10': {
        name: 'Dominik',
        type: 'enumeration',
    },
    '11': {
        name: 'Dominik::Dominik',
        type: 'attribute',
    },
    '12': {
        name: 'Dominik::Dominik()',
        type: 'method',
    },
    '13': {
        name: 'Dominik',
        type: 'activityInitialNode',
    },
    '14': {
        name: 'Dominik',
        type: 'activityFinalNode',
    },
    '15': {
        name: 'Dominik',
        type: 'activityObjectNode',
    },
    '16': {
        name: 'Dominik',
        type: 'activityActionNode',
    },
    '17': {
        name: 'Dominik',
        type: 'activityForkNode',
    },
    '18': {
        name: 'Dominik',
        type: 'activityMergeNode',
    },
    '19': {
        name: 'Dominik <-> Dominik',
        type: 'ClassBidirectional',
    },
    '20': {
        name: 'Dominik --> Dominik',
        type: 'ClassUnidirectional',
    },
    '21': {
        name: 'Dominik --◇ Dominik',
        type: 'ClassAggregation',
    },
    '22': {
        name: 'Dominik --▶ Dominik',
        type: 'ClassInheritance',
    },
    '23': {
        name: 'Dominik ⋯⋯> Dominik',
        type: 'ClassDependency',
    },
    '24': {
        name: 'Dominik --◆ Dominik',
        type: 'ClassComposition',
    },
    '25': {
        name: 'Dominik --> Dominik',
        type: 'ActivityControlFlow',
    },
};

describe('ModelingAssessmentService', () => {
    setupTestBed({ zoneless: true });

    let httpMock: HttpTestingController;
    let service: ModelingAssessmentService;
    let expectedResult: any;
    let httpExpectedResult: any;
    let elemDefault: Result;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), LocalStorageService, SessionStorageService, { provide: TranslateService, useClass: MockTranslateService }],
        });
        service = TestBed.inject(ModelingAssessmentService);
        httpMock = TestBed.inject(HttpTestingController);

        expectedResult = {} as Result;
        httpExpectedResult = {} as HttpResponse<Result>;
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('Service methods', () => {
        describe('methods returning a result', () => {
            elemDefault = new Result();
            elemDefault.id = 1;
            elemDefault.score = 5;
            elemDefault.hasComplaint = false;
            it('should save an assessment', async () => {
                const submissionId = 187;
                const feedbacks = [
                    {
                        id: 0,
                        credits: 3,
                        reference: 'reference',
                    } as Feedback,
                    {
                        id: 1,
                        credits: 1,
                    } as Feedback,
                ];
                const returnedFromService = Object.assign({}, elemDefault);
                service
                    .saveAssessment(1, feedbacks, submissionId)
                    .pipe(take(1))
                    .subscribe((resp) => (expectedResult = resp));
                const req = httpMock.expectOne({
                    url: `api/modeling/modeling-submissions/${submissionId}/result/${1}/assessment`,
                    method: 'PUT',
                });
                req.flush(returnedFromService);
                expect(expectedResult).toEqual(elemDefault);
            });

            it('should save an example assessment', async () => {
                const exampleSubmissionId = 187;
                const feedbacks = [
                    {
                        id: 0,
                        credits: 3,
                        reference: 'reference',
                    } as Feedback,
                    {
                        id: 1,
                        credits: 1,
                    } as Feedback,
                ];
                const returnedFromService = Object.assign({}, elemDefault);
                service
                    .saveExampleAssessment(feedbacks, exampleSubmissionId)
                    .pipe(take(1))
                    .subscribe((resp) => (expectedResult = resp));
                const req = httpMock.expectOne({
                    url: `api/modeling/modeling-submissions/${exampleSubmissionId}/example-assessment`,
                    method: 'PUT',
                });
                req.flush(returnedFromService);
                expect(expectedResult).toEqual(elemDefault);
            });

            it('should get an assessment', async () => {
                const submissionId = 187;
                const returnedFromService = Object.assign({}, elemDefault);
                service
                    .getAssessment(submissionId)
                    .pipe(take(1))
                    .subscribe((resp) => (expectedResult = resp));
                const req = httpMock.expectOne({
                    url: `api/modeling/modeling-submissions/${submissionId}/result`,
                    method: 'GET',
                });
                req.flush(returnedFromService);
                expect(expectedResult).toEqual(elemDefault);
            });

            it('should get an example assessment', async () => {
                const submissionId = 187;
                const exerciseId = 188;
                const returnedFromService = Object.assign({}, elemDefault);
                service
                    .getExampleAssessment(exerciseId, submissionId)
                    .pipe(take(1))
                    .subscribe((resp) => (expectedResult = resp));
                const req = httpMock.expectOne({
                    url: `api/modeling/exercise/${exerciseId}/modeling-submissions/${submissionId}/example-assessment`,
                    method: 'GET',
                });
                req.flush(returnedFromService);
                expect(expectedResult).toEqual(elemDefault);
            });

            it('should update assessment after complaint', async () => {
                const feedbacks = [
                    {
                        id: 0,
                        credits: 3,
                        reference: 'reference',
                    } as Feedback,
                    {
                        id: 1,
                        credits: 1,
                    } as Feedback,
                ];
                const complaintResponse = new ComplaintResponse();
                complaintResponse.id = 1;
                complaintResponse.responseText = 'That is true';
                const submissionId = 1;
                const returnedFromService = { ...elemDefault };
                const expected = { ...returnedFromService };
                service
                    .updateAssessmentAfterComplaint(feedbacks, complaintResponse, submissionId)
                    .pipe(take(1))
                    .subscribe((resp) => (httpExpectedResult = resp));
                const req = httpMock.expectOne({ url: `api/modeling/modeling-submissions/${submissionId}/assessment-after-complaint`, method: 'PUT' });
                req.flush(returnedFromService);
                expect(httpExpectedResult.body).toEqual(expected);
            });

            it('should get names for assessment', async () => {
                elemDefault.feedbacks = [
                    { id: 0, credits: 3, referenceId: '6', referenceType: 'ActivityActionNode' } as Feedback,
                    { id: 0, credits: 3, referenceId: '7', referenceType: 'ActivityActionNode' } as Feedback,
                    { id: 0, credits: 3, referenceId: '8', referenceType: 'ActivityActionNode' } as Feedback,
                    { id: 0, credits: 3, referenceId: '9', referenceType: 'ActivityActionNode' } as Feedback,
                    { id: 0, credits: 3, referenceId: '10', referenceType: 'ActivityActionNode' } as Feedback,
                    { id: 0, credits: 3, referenceId: '11', referenceType: 'ActivityActionNode' } as Feedback,
                    { id: 0, credits: 3, referenceId: '12', referenceType: 'ActivityActionNode' } as Feedback,
                    { id: 0, credits: 3, referenceId: '13', referenceType: 'ActivityActionNode' } as Feedback,
                    { id: 0, credits: 3, referenceId: '14', referenceType: 'ActivityActionNode' } as Feedback,
                    { id: 0, credits: 3, referenceId: '15', referenceType: 'ActivityActionNode' } as Feedback,
                    { id: 0, credits: 3, referenceId: '16', referenceType: 'ActivityActionNode' } as Feedback,
                    { id: 0, credits: 3, referenceId: '17', referenceType: 'ActivityActionNode' } as Feedback,
                    { id: 0, credits: 3, referenceId: '18', referenceType: 'ActivityActionNode' } as Feedback,
                    { id: 0, credits: 3, referenceId: '19', referenceType: 'ClassBidirectional' } as Feedback,
                    { id: 0, credits: 3, referenceId: '20', referenceType: 'ClassBidirectional' } as Feedback,
                    { id: 0, credits: 3, referenceId: '21', referenceType: 'ClassBidirectional' } as Feedback,
                    { id: 0, credits: 3, referenceId: '22', referenceType: 'ClassBidirectional' } as Feedback,
                    { id: 0, credits: 3, referenceId: '23', referenceType: 'ClassBidirectional' } as Feedback,
                    { id: 0, credits: 3, referenceId: '24', referenceType: 'ClassBidirectional' } as Feedback,
                    { id: 0, credits: 3, referenceId: '25', referenceType: 'ClassBidirectional' } as Feedback,
                ];
                const uml = {
                    version: '4.0.0',
                    id: 'model-id',
                    title: 'Class Diagram',
                    type: UMLDiagramType.ClassDiagram,
                    nodes: [
                        {
                            id: '6',
                            width: 100,
                            height: 100,
                            type: 'class',
                            position: { x: 0, y: 0 },
                            data: {
                                name: 'Dominik',
                                attributes: [{ id: '11', name: 'Dominik' }],
                                methods: [{ id: '12', name: 'Dominik' }],
                            },
                            measured: { width: 100, height: 100 },
                        },
                        {
                            id: '7',
                            width: 80,
                            height: 80,
                            type: 'package',
                            position: { x: 0, y: 0 },
                            data: { name: 'Dominik' },
                            measured: { width: 80, height: 80 },
                        },
                        {
                            id: '8',
                            width: 80,
                            height: 80,
                            type: 'interface',
                            position: { x: 0, y: 0 },
                            data: { name: 'Dominik' },
                            measured: { width: 80, height: 80 },
                        },
                        {
                            id: '9',
                            width: 80,
                            height: 80,
                            type: 'abstractClass',
                            position: { x: 0, y: 0 },
                            data: { name: 'Dominik' },
                            measured: { width: 80, height: 80 },
                        },
                        {
                            id: '10',
                            width: 80,
                            height: 80,
                            type: 'enumeration',
                            position: { x: 0, y: 0 },
                            data: { name: 'Dominik' },
                            measured: { width: 80, height: 80 },
                        },
                        {
                            id: '13',
                            width: 40,
                            height: 40,
                            type: 'activityInitialNode',
                            position: { x: 0, y: 0 },
                            data: { name: 'Dominik' },
                            measured: { width: 40, height: 40 },
                        },
                        {
                            id: '14',
                            width: 40,
                            height: 40,
                            type: 'activityFinalNode',
                            position: { x: 0, y: 0 },
                            data: { name: 'Dominik' },
                            measured: { width: 40, height: 40 },
                        },
                        {
                            id: '15',
                            width: 60,
                            height: 60,
                            type: 'activityObjectNode',
                            position: { x: 0, y: 0 },
                            data: { name: 'Dominik' },
                            measured: { width: 60, height: 60 },
                        },
                        {
                            id: '16',
                            width: 60,
                            height: 60,
                            type: 'activityActionNode',
                            position: { x: 0, y: 0 },
                            data: { name: 'Dominik' },
                            measured: { width: 60, height: 60 },
                        },
                        {
                            id: '17',
                            width: 60,
                            height: 60,
                            type: 'activityForkNode',
                            position: { x: 0, y: 0 },
                            data: { name: 'Dominik' },
                            measured: { width: 60, height: 60 },
                        },
                        {
                            id: '18',
                            width: 60,
                            height: 60,
                            type: 'activityMergeNode',
                            position: { x: 0, y: 0 },
                            data: { name: 'Dominik' },
                            measured: { width: 60, height: 60 },
                        },
                    ],
                    edges: [
                        {
                            id: '19',
                            source: '6',
                            target: '6',
                            type: 'ClassBidirectional',
                            sourceHandle: 'source',
                            targetHandle: 'target',
                            data: { points: [] },
                        },
                        {
                            id: '20',
                            source: '6',
                            target: '6',
                            type: 'ClassUnidirectional',
                            sourceHandle: 'source',
                            targetHandle: 'target',
                            data: { points: [] },
                        },
                        {
                            id: '21',
                            source: '6',
                            target: '6',
                            type: 'ClassAggregation',
                            sourceHandle: 'source',
                            targetHandle: 'target',
                            data: { points: [] },
                        },
                        {
                            id: '22',
                            source: '6',
                            target: '6',
                            type: 'ClassInheritance',
                            sourceHandle: 'source',
                            targetHandle: 'target',
                            data: { points: [] },
                        },
                        {
                            id: '23',
                            source: '6',
                            target: '6',
                            type: 'ClassDependency',
                            sourceHandle: 'source',
                            targetHandle: 'target',
                            data: { points: [] },
                        },
                        {
                            id: '24',
                            source: '6',
                            target: '6',
                            type: 'ClassComposition',
                            sourceHandle: 'source',
                            targetHandle: 'target',
                            data: { points: [] },
                        },
                        {
                            id: '25',
                            source: '6',
                            target: '6',
                            type: 'ActivityControlFlow',
                            sourceHandle: 'source',
                            targetHandle: 'target',
                            data: { points: [] },
                        },
                    ],
                    assessments: {},
                } as unknown as UMLModel;

                expectedResult = getNamesForAssessments(elemDefault, uml);
                expect(expectedResult).toEqual(assessmentNames);
            });
        });

        it('tests validFeedback check', async () => {
            const emptyfeedback: Feedback[] = [];
            const feedbacks = [
                {
                    id: 0,
                    credits: 3,
                    reference: 'reference',
                    text: 'text',
                    detailText: 'detailtext',
                } as Feedback,
                {
                    id: 1,
                    credits: 1,
                    text: 'text',
                    detailText: 'detailtext',
                } as Feedback,
            ];
            let result = service.isFeedbackTextValid(emptyfeedback);
            expect(result).toBe(true);
            result = service.isFeedbackTextValid(feedbacks);
            expect(result).toBe(true);
        });
    });
});
