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
import { UMLDiagramType, UMLElementType, UMLModel, UMLRelationshipType } from '@tumaet/apollon';
import { AssessmentNamesForModelId, getNamesForAssessments } from 'app/modeling/manage/assess/modeling-assessment.util';

const assessmentNames: AssessmentNamesForModelId = {
    '6': {
        name: 'Dominik',
        type: 'Class',
    },
    '7': {
        name: 'Dominik',
        type: 'Package',
    },
    '8': {
        name: 'Dominik',
        type: 'Interface',
    },
    '9': {
        name: 'Dominik',
        type: 'AbstractClass',
    },
    '10': {
        name: 'Dominik',
        type: 'Enumeration',
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
        type: 'ActivityInitialNode',
    },
    '14': {
        name: 'Dominik',
        type: 'ActivityFinalNode',
    },
    '15': {
        name: 'Dominik',
        type: 'ActivityObjectNode',
    },
    '16': {
        name: 'Dominik',
        type: 'ActivityActionNode',
    },
    '17': {
        name: 'Dominik',
        type: 'ActivityForkNode',
    },
    '18': {
        name: 'Dominik',
        type: 'ActivityMergeNode',
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
                    { id: 0, credits: 3, referenceId: '6', referenceType: UMLElementType.ActivityActionNode } as Feedback,
                    { id: 0, credits: 3, referenceId: '7', referenceType: UMLElementType.ActivityActionNode } as Feedback,
                    { id: 0, credits: 3, referenceId: '8', referenceType: UMLElementType.ActivityActionNode } as Feedback,
                    { id: 0, credits: 3, referenceId: '9', referenceType: UMLElementType.ActivityActionNode } as Feedback,
                    { id: 0, credits: 3, referenceId: '10', referenceType: UMLElementType.ActivityActionNode } as Feedback,
                    { id: 0, credits: 3, referenceId: '11', referenceType: UMLElementType.ActivityActionNode } as Feedback,
                    { id: 0, credits: 3, referenceId: '12', referenceType: UMLElementType.ActivityActionNode } as Feedback,
                    { id: 0, credits: 3, referenceId: '13', referenceType: UMLElementType.ActivityActionNode } as Feedback,
                    { id: 0, credits: 3, referenceId: '14', referenceType: UMLElementType.ActivityActionNode } as Feedback,
                    { id: 0, credits: 3, referenceId: '15', referenceType: UMLElementType.ActivityActionNode } as Feedback,
                    { id: 0, credits: 3, referenceId: '16', referenceType: UMLElementType.ActivityActionNode } as Feedback,
                    { id: 0, credits: 3, referenceId: '17', referenceType: UMLElementType.ActivityActionNode } as Feedback,
                    { id: 0, credits: 3, referenceId: '18', referenceType: UMLElementType.ActivityActionNode } as Feedback,
                    { id: 0, credits: 3, referenceId: '19', referenceType: UMLRelationshipType.ClassBidirectional } as Feedback,
                    { id: 0, credits: 3, referenceId: '20', referenceType: UMLRelationshipType.ClassBidirectional } as Feedback,
                    { id: 0, credits: 3, referenceId: '21', referenceType: UMLRelationshipType.ClassBidirectional } as Feedback,
                    { id: 0, credits: 3, referenceId: '22', referenceType: UMLRelationshipType.ClassBidirectional } as Feedback,
                    { id: 0, credits: 3, referenceId: '23', referenceType: UMLRelationshipType.ClassBidirectional } as Feedback,
                    { id: 0, credits: 3, referenceId: '24', referenceType: UMLRelationshipType.ClassBidirectional } as Feedback,
                    { id: 0, credits: 3, referenceId: '25', referenceType: UMLRelationshipType.ClassBidirectional } as Feedback,
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
                            type: UMLElementType.Class,
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
                            type: UMLElementType.Package,
                            position: { x: 0, y: 0 },
                            data: { name: 'Dominik' },
                            measured: { width: 80, height: 80 },
                        },
                        {
                            id: '8',
                            width: 80,
                            height: 80,
                            type: UMLElementType.Interface,
                            position: { x: 0, y: 0 },
                            data: { name: 'Dominik' },
                            measured: { width: 80, height: 80 },
                        },
                        {
                            id: '9',
                            width: 80,
                            height: 80,
                            type: UMLElementType.AbstractClass,
                            position: { x: 0, y: 0 },
                            data: { name: 'Dominik' },
                            measured: { width: 80, height: 80 },
                        },
                        {
                            id: '10',
                            width: 80,
                            height: 80,
                            type: UMLElementType.Enumeration,
                            position: { x: 0, y: 0 },
                            data: { name: 'Dominik' },
                            measured: { width: 80, height: 80 },
                        },
                        {
                            id: '13',
                            width: 40,
                            height: 40,
                            type: UMLElementType.ActivityInitialNode,
                            position: { x: 0, y: 0 },
                            data: { name: 'Dominik' },
                            measured: { width: 40, height: 40 },
                        },
                        {
                            id: '14',
                            width: 40,
                            height: 40,
                            type: UMLElementType.ActivityFinalNode,
                            position: { x: 0, y: 0 },
                            data: { name: 'Dominik' },
                            measured: { width: 40, height: 40 },
                        },
                        {
                            id: '15',
                            width: 60,
                            height: 60,
                            type: UMLElementType.ActivityObjectNode,
                            position: { x: 0, y: 0 },
                            data: { name: 'Dominik' },
                            measured: { width: 60, height: 60 },
                        },
                        {
                            id: '16',
                            width: 60,
                            height: 60,
                            type: UMLElementType.ActivityActionNode,
                            position: { x: 0, y: 0 },
                            data: { name: 'Dominik' },
                            measured: { width: 60, height: 60 },
                        },
                        {
                            id: '17',
                            width: 60,
                            height: 60,
                            type: UMLElementType.ActivityForkNode,
                            position: { x: 0, y: 0 },
                            data: { name: 'Dominik' },
                            measured: { width: 60, height: 60 },
                        },
                        {
                            id: '18',
                            width: 60,
                            height: 60,
                            type: UMLElementType.ActivityMergeNode,
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
                            type: UMLRelationshipType.ClassBidirectional,
                            sourceHandle: 'source',
                            targetHandle: 'target',
                            data: { points: [] },
                        },
                        {
                            id: '20',
                            source: '6',
                            target: '6',
                            type: UMLRelationshipType.ClassUnidirectional,
                            sourceHandle: 'source',
                            targetHandle: 'target',
                            data: { points: [] },
                        },
                        {
                            id: '21',
                            source: '6',
                            target: '6',
                            type: UMLRelationshipType.ClassAggregation,
                            sourceHandle: 'source',
                            targetHandle: 'target',
                            data: { points: [] },
                        },
                        {
                            id: '22',
                            source: '6',
                            target: '6',
                            type: UMLRelationshipType.ClassInheritance,
                            sourceHandle: 'source',
                            targetHandle: 'target',
                            data: { points: [] },
                        },
                        {
                            id: '23',
                            source: '6',
                            target: '6',
                            type: UMLRelationshipType.ClassDependency,
                            sourceHandle: 'source',
                            targetHandle: 'target',
                            data: { points: [] },
                        },
                        {
                            id: '24',
                            source: '6',
                            target: '6',
                            type: UMLRelationshipType.ClassComposition,
                            sourceHandle: 'source',
                            targetHandle: 'target',
                            data: { points: [] },
                        },
                        {
                            id: '25',
                            source: '6',
                            target: '6',
                            type: UMLRelationshipType.ActivityControlFlow,
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
            expect(result).toBeTrue();
            result = service.isFeedbackTextValid(feedbacks);
            expect(result).toBeTrue();
        });
    });
});
