import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { HttpResponse } from '@angular/common/http';
import { take } from 'rxjs/operators';
import { ArtemisTestModule } from '../test.module';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { Result } from 'app/entities/result.model';
import { Feedback } from 'app/entities/feedback.model';
import { ModelingAssessmentService } from 'app/exercises/modeling/assess/modeling-assessment.service';
import { ComplaintResponse } from 'app/entities/complaint-response.model';
import { UMLElementType, UMLModel, UMLRelationshipType } from '@ls1intum/apollon';
import { getNamesForAssessments } from 'app/exercises/modeling/assess/modeling-assessment.util';

describe('Modeling Assessment Service', () => {
    let httpMock: HttpTestingController;
    let service: ModelingAssessmentService;
    let expectedResult: any;
    let httpExpectedResult: any;
    let elemDefault: Result;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
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
                    url: `${SERVER_API_URL}api/modeling-submissions/${submissionId}/result/${1}/assessment`,
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
                    url: `${SERVER_API_URL}api/modeling-submissions/${exampleSubmissionId}/example-assessment`,
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
                    url: `${SERVER_API_URL}api/modeling-submissions/${submissionId}/result`,
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
                    url: `${SERVER_API_URL}api/exercise/${exerciseId}/modeling-submissions/${submissionId}/example-assessment`,
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
                const req = httpMock.expectOne({ url: `${SERVER_API_URL}api/modeling-submissions/${submissionId}/assessment-after-complaint`, method: 'PUT' });
                req.flush(returnedFromService);
                expect(httpExpectedResult.body).toEqual(expected);
            });

            it('should get names for assessment', async () => {
                const expected = new Map();
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
                    elements: [
                        { id: '6', name: 'Dominik', type: UMLElementType.Class },
                        { id: '7', name: 'Dominik', type: UMLElementType.Package },
                        { id: '8', name: 'Dominik', type: UMLElementType.Interface },
                        { id: '9', name: 'Dominik', type: UMLElementType.AbstractClass },
                        { id: '10', name: 'Dominik', type: UMLElementType.Enumeration },
                        { id: '11', name: 'Dominik', type: UMLElementType.ClassAttribute },
                        { id: '12', name: 'Dominik', type: UMLElementType.ClassMethod },
                        { id: '13', name: 'Dominik', type: UMLElementType.ActivityInitialNode },
                        { id: '14', name: 'Dominik', type: UMLElementType.ActivityFinalNode },
                        { id: '15', name: 'Dominik', type: UMLElementType.ActivityObjectNode },
                        { id: '16', name: 'Dominik', type: UMLElementType.ActivityActionNode },
                        { id: '17', name: 'Dominik', type: UMLElementType.ActivityForkNode },
                        { id: '18', name: 'Dominik', type: UMLElementType.ActivityMergeNode },
                    ],
                    relationships: [
                        { id: '19', type: UMLRelationshipType.ClassBidirectional, source: { element: '6' }, target: { element: '6' } },
                        { id: '20', type: UMLRelationshipType.ClassUnidirectional, source: { element: '6' }, target: { element: '6' } },
                        { id: '21', type: UMLRelationshipType.ClassAggregation, source: { element: '6' }, target: { element: '6' } },
                        { id: '22', type: UMLRelationshipType.ClassInheritance, source: { element: '6' }, target: { element: '6' } },
                        { id: '23', type: UMLRelationshipType.ClassDependency, source: { element: '6' }, target: { element: '6' } },
                        { id: '24', type: UMLRelationshipType.ClassComposition, source: { element: '6' }, target: { element: '6' } },
                        { id: '25', type: UMLRelationshipType.ActivityControlFlow, source: { element: '6' }, target: { element: '6' } },
                    ],
                } as unknown as UMLModel;

                expectedResult = getNamesForAssessments(elemDefault, uml);
                expect(expectedResult).toEqual(expected);
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
