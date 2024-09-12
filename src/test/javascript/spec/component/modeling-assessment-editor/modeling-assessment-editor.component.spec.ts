import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, ParamMap, Router, convertToParamMap } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { TranslateService } from '@ngx-translate/core';
import { AssessmentLayoutComponent } from 'app/assessment/assessment-layout/assessment-layout.component';
import { ComplaintService, EntityResponseType } from 'app/complaints/complaint.service';
import { AccountService } from 'app/core/auth/account.service';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { User } from 'app/core/user/user.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { ComplaintResponse } from 'app/entities/complaint-response.model';
import { Complaint } from 'app/entities/complaint.model';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam/exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { Exercise } from 'app/entities/exercise.model';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { Participation, ParticipationType } from 'app/entities/participation/participation.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ProgrammingSubmission } from 'app/entities/programming/programming-submission.model';
import { Result } from 'app/entities/result.model';
import { getLatestSubmissionResult } from 'app/entities/submission.model';
import { ModelingAssessmentEditorComponent } from 'app/exercises/modeling/assess/modeling-assessment-editor/modeling-assessment-editor.component';
import { ModelingAssessmentService } from 'app/exercises/modeling/assess/modeling-assessment.service';
import { ModelingSubmissionService } from 'app/exercises/modeling/participate/modeling-submission.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../test.module';
import { SubmissionService } from 'app/exercises/shared/submission/submission.service';
import { MockComponent } from 'ng-mocks';
import { ModelingAssessmentComponent } from 'app/exercises/modeling/assess/modeling-assessment.component';
import { CollapsableAssessmentInstructionsComponent } from 'app/assessment/assessment-instructions/collapsable-assessment-instructions/collapsable-assessment-instructions.component';
import { UnreferencedFeedbackComponent } from 'app/exercises/shared/unreferenced-feedback/unreferenced-feedback.component';
import { ExampleSubmissionService } from 'app/exercises/shared/example-submission/example-submission.service';
import { ExampleSubmission } from 'app/entities/example-submission.model';
import dayjs from 'dayjs/esm';
import { AssessmentAfterComplaint } from 'app/complaints/complaints-for-tutor/complaints-for-tutor.component';
import { AlertService } from 'app/core/util/alert.service';
import { UMLDiagramType } from '@ls1intum/apollon';

describe('ModelingAssessmentEditorComponent', () => {
    let component: ModelingAssessmentEditorComponent;
    let fixture: ComponentFixture<ModelingAssessmentEditorComponent>;
    let service: ModelingAssessmentService;
    let mockAuth: MockAccountService;
    let modelingSubmissionService: ModelingSubmissionService;
    let complaintService: ComplaintService;
    let modelingSubmissionSpy: jest.SpyInstance;
    let complaintSpy: jest.SpyInstance;
    let router: Router;
    let submissionService: SubmissionService;
    let exampleSubmissionService: ExampleSubmissionService;
    let paramMapSubject: BehaviorSubject<ParamMap>;

    beforeEach(() => {
        paramMapSubject = new BehaviorSubject(convertToParamMap({}));
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule],
            declarations: [
                ModelingAssessmentEditorComponent,
                MockComponent(AssessmentLayoutComponent),
                MockComponent(ModelingAssessmentComponent),
                MockComponent(CollapsableAssessmentInstructionsComponent),
                MockComponent(UnreferencedFeedbackComponent),
            ],
            providers: [
                JhiLanguageHelper,
                {
                    provide: ActivatedRoute,
                    useValue: {
                        paramMap: paramMapSubject.asObservable(),
                        queryParamMap: of(convertToParamMap({})),
                        params: of({}),
                        queryParams: of({}),
                        snapshot: {
                            paramMap: convertToParamMap({}),
                            queryParamMap: convertToParamMap({}),
                        },
                        parent: {
                            paramMap: of(convertToParamMap({})),
                        },
                    },
                },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ModelingAssessmentEditorComponent);
                component = fixture.componentInstance;
                service = TestBed.inject(ModelingAssessmentService);
                modelingSubmissionService = TestBed.inject(ModelingSubmissionService);
                complaintService = TestBed.inject(ComplaintService);
                submissionService = TestBed.inject(SubmissionService);
                mockAuth = fixture.debugElement.injector.get(AccountService) as any as MockAccountService;
                exampleSubmissionService = TestBed.inject(ExampleSubmissionService);
                mockAuth.hasAnyAuthorityDirect([]);
                mockAuth.identity();
                fixture.detectChanges();
            });
        router = TestBed.inject(Router);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    const getSubmissionWithData = (): ModelingSubmission => {
        return {
            id: 1,
            submitted: true,
            type: 'MANUAL',
            text: 'Test\n\nTest\n\nTest',
            participation: {
                type: ParticipationType.SOLUTION,
                exercise: {
                    id: 1,
                    problemStatement: 'problem',
                    gradingInstructions: 'grading',
                    title: 'title',
                    shortName: 'name',
                    exerciseGroup: {
                        exam: {
                            course: new Course(),
                        } as unknown as Exam,
                    } as unknown as ExerciseGroup,
                } as unknown as Exercise,
            } as unknown as Participation,
            results: [
                {
                    id: 2374,
                    score: 8,
                    rated: true,
                    hasComplaint: true,
                    feedbacks: [
                        {
                            id: 2,
                            detailText: 'Feedback',
                            credits: 1,
                            reference: 'path',
                        } as Feedback,
                    ],
                } as unknown as Result,
            ],
        } as unknown as ModelingSubmission;
    };

    describe('ngOnInit tests', () => {
        it('ngOnInit', fakeAsync(() => {
            modelingSubmissionSpy = jest.spyOn(modelingSubmissionService, 'getSubmission');
            complaintSpy = jest.spyOn(complaintService, 'findBySubmissionId');
            const submission = getSubmissionWithData();

            modelingSubmissionSpy.mockReturnValue(of(submission));
            const user = <User>{ id: 99, groups: ['instructorGroup'] };
            const result: Result = {
                feedbacks: [new Feedback()],
                participation: new StudentParticipation(),
                score: 80,
                successful: true,
                submission: new ProgrammingSubmission(),
                assessor: user,
                hasComplaint: true,
                assessmentType: AssessmentType.SEMI_AUTOMATIC,
                id: 2,
            };
            const complaint = <Complaint>{ id: 1, complaintText: 'Why only 80%?', result };
            complaintSpy.mockReturnValue(of({ body: complaint } as HttpResponse<Complaint>));

            const handleFeedbackSpy = jest.spyOn(submissionService, 'handleFeedbackCorrectionRoundTag');
            const verifyFeedbackSpy = jest.spyOn(component, 'validateFeedback');

            component.ngOnInit();
            tick(500);
            expect(modelingSubmissionSpy).toHaveBeenCalledOnce();
            expect(component.isLoading).toBeFalse();
            expect(component.complaint).toEqual(complaint);
            modelingSubmissionSpy.mockRestore();
            // called twice, since the feedback is additionally verified during the component initialization
            expect(handleFeedbackSpy).toHaveBeenCalledTimes(2);
            expect(verifyFeedbackSpy).toHaveBeenCalledOnce();
            expect(component.assessmentsAreValid).toBeTrue();
        }));

        it('wrongly call ngOnInit and throw exception', fakeAsync(() => {
            modelingSubmissionSpy = jest.spyOn(modelingSubmissionService, 'getSubmission');
            const response = new HttpErrorResponse({ status: 403 });
            modelingSubmissionSpy.mockReturnValue(throwError(() => response));

            component.ngOnInit();
            tick(500);
            expect(modelingSubmissionSpy).toHaveBeenCalledOnce();
            modelingSubmissionSpy.mockRestore();
        }));

        it('call ngOnInit with submissionId set to new', fakeAsync(() => {
            paramMapSubject.next(
                convertToParamMap({
                    submissionId: 'new',
                    // Include other necessary params here
                    courseId: '1',
                    exerciseId: '1',
                }),
            );

            const mockSubmission: ModelingSubmission = {
                id: 123,
                submitted: true,
                participation: {
                    exercise: {
                        id: 1,
                        type: 'modeling',
                    },
                },
            } as ModelingSubmission;

            const modelingSubmissionSpy = jest.spyOn(modelingSubmissionService, 'getSubmissionWithoutAssessment').mockReturnValue(of(mockSubmission));

            // Spy on the relevant service methods
            const handleFeedbackSpy = jest.spyOn(submissionService, 'handleFeedbackCorrectionRoundTag');

            // Initialize the component
            component.ngOnInit();
            tick(500);
            expect(modelingSubmissionSpy).toHaveBeenCalledOnce();
            expect(component.isLoading).toBeFalse();
            expect(component.submission).toBeDefined();
            expect(handleFeedbackSpy).toHaveBeenCalledOnce();
            expect(component.assessmentsAreValid).toBeFalse();
        }));
    });

    describe('should test the overwrite access rights and return true', () => {
        it('tests the method with instructor rights', fakeAsync(() => {
            const course = new Course();
            component.ngOnInit();
            tick(500);
            component.modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
            component.modelingExercise.isAtLeastInstructor = true;
            expect(component.canOverride).toBeTrue();
        }));

        it('tests the method with tutor rights and as assessor', fakeAsync(() => {
            const course = new Course();
            component.modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
            component.modelingExercise.isAtLeastInstructor = false;
            component.isAssessor = true;
            component.complaint = new Complaint();
            component.complaint.id = 0;
            component.complaint.complaintText = 'complaint';
            component.ngOnInit();
            tick(500);
            mockAuth.isAtLeastInstructorInCourse(course);
            component['checkPermissions']();
            fixture.detectChanges();
            expect(component.modelingExercise.isAtLeastInstructor).toBeFalse();
            expect(component.canOverride).toBeFalse();
        }));
    });

    describe('save and submit', () => {
        beforeEach(() => {
            const course = new Course();
            component.modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
            component.modelingExercise.assessmentDueDate = dayjs().subtract(2, 'days');
            component.modelingExercise.maxPoints = 10;

            const feedback = createTestFeedback();
            component.unreferencedFeedback = [feedback];

            component.result = {
                id: 2374,
                score: 8,
                rated: true,
                hasComplaint: false,
            } as unknown as Result;
            component.result.participation = {
                results: [component.result],
            } as unknown as Participation;

            component.submission = {
                id: 1,
                submitted: true,
                type: 'MANUAL',
                text: 'Test\n\nTest\n\nTest',
            } as unknown as ModelingSubmission;
            component.submission.results = [component.result];
            getLatestSubmissionResult(component.submission)!.feedbacks = [
                {
                    id: 2,
                    detailText: 'Feedback',
                    credits: 1,
                } as Feedback,
            ];
        });

        it('should save assessment', fakeAsync(() => {
            const saveAssessmentSpy = jest.spyOn(service, 'saveAssessment').mockReturnValue(of(getLatestSubmissionResult(component.submission)!));

            component.ngOnInit();
            tick(500);
            component.onSaveAssessment();
            expect(saveAssessmentSpy).toHaveBeenCalledOnce();
        }));

        it('should try to submit assessment', fakeAsync(() => {
            jest.spyOn(service, 'saveAssessment').mockReturnValue(of(getLatestSubmissionResult(component.submission)!));
            jest.spyOn(window, 'confirm').mockReturnValue(false);

            component.ngOnInit();
            tick(500);

            component.onSubmitAssessment();

            expect(window.confirm).toHaveBeenCalledOnce();
            expect(component.highlightMissingFeedback).toBeTrue();

            component.modelingExercise!.isAtLeastInstructor = true;
            expect(component.canOverride).toBeTrue();
        }));

        it('should allow overriding directly after submitting', fakeAsync(() => {
            jest.spyOn(window, 'confirm').mockReturnValue(false);

            component.modelingExercise!.isAtLeastInstructor = true;
            component.ngOnInit();
            tick(500);

            component.onSubmitAssessment();
            expect(component.canOverride).toBeTrue();
        }));

        it('should not invalidate assessment after saving', fakeAsync(() => {
            component.submission = getSubmissionWithData();
            jest.spyOn(modelingSubmissionService, 'getSubmission').mockReturnValue(of(component.submission));

            component.ngOnInit();
            tick(500);
            component.onSaveAssessment();
            expect(component.assessmentsAreValid).toBeTrue();
        }));

        it('should submit the assessment', fakeAsync(() => {
            const submitMock = jest.spyOn(service, 'saveAssessment').mockReturnValue(of(component.result!));
            jest.spyOn(window, 'confirm').mockReturnValue(true);

            component.validateFeedback();
            expect(component.assessmentsAreValid).toBeTrue();

            component.onSubmitAssessment();
            tick(500);

            expect(submitMock).toHaveBeenCalledOnce();
        }));
    });

    const createTestFeedback = (): Feedback => {
        const feedback = new Feedback();
        feedback.id = 2;
        feedback.text = 'This is a test feedback';
        feedback.detailText = 'Feedback';
        feedback.credits = 1;
        feedback.type = FeedbackType.MANUAL_UNREFERENCED;
        return feedback;
    };

    it.each([undefined, 'genericErrorKey', 'complaintLock'])(
        'should update assessment after complaint, errorKeyFromServer=%s',
        fakeAsync((errorKeyFromServer: string | undefined) => {
            const complaintResponse = new ComplaintResponse();
            complaintResponse.id = 1;
            complaintResponse.responseText = 'response';

            component.submission = {
                id: 1,
                submitted: true,
                type: 'MANUAL',
                text: 'Test\n\nTest\n\nTest',
            } as unknown as ModelingSubmission;

            const changedResult = {
                id: 2374,
                score: 8,
                rated: true,
                hasComplaint: false,
                participation: {
                    type: ParticipationType.SOLUTION,
                    results: [],
                } as unknown as Participation,
            } as unknown as Result;

            const errorMessage = 'errMsg';
            const errorParams = ['errParam1', 'errParam2'];

            const serviceSpy = jest.spyOn(service, 'updateAssessmentAfterComplaint');

            if (errorKeyFromServer) {
                serviceSpy.mockReturnValue(
                    throwError(
                        () =>
                            new HttpErrorResponse({
                                status: 400,
                                error: { message: errorMessage, errorKey: errorKeyFromServer, params: errorParams },
                            }),
                    ),
                );
            } else {
                serviceSpy.mockReturnValue(of({ body: changedResult } as EntityResponseType));
            }

            component.ngOnInit();
            tick(500);

            let onSuccessCalled = false;
            let onErrorCalled = false;
            const assessmentAfterComplaint: AssessmentAfterComplaint = {
                complaintResponse,
                onSuccess: () => (onSuccessCalled = true),
                onError: () => (onErrorCalled = true),
            };

            const alertService = TestBed.inject(AlertService);
            const errorSpy = jest.spyOn(alertService, 'error');
            const validateSpy = jest.spyOn(component, 'validateFeedback').mockImplementation(() => (component.assessmentsAreValid = true));

            component.onUpdateAssessmentAfterComplaint(assessmentAfterComplaint);

            expect(validateSpy).toHaveBeenCalledOnce();
            expect(serviceSpy).toHaveBeenCalledOnce();
            if (!errorKeyFromServer) {
                expect(errorSpy).not.toHaveBeenCalled();
                expect(component.result?.participation?.results).toEqual([changedResult]);
            } else if (errorKeyFromServer === 'complaintLock') {
                expect(errorSpy).toHaveBeenCalledOnce();
                expect(errorSpy).toHaveBeenCalledWith(errorMessage, errorParams);
                expect(component.result?.participation?.results).toBeUndefined();
            } else {
                // Handle all other errors
                expect(errorSpy).toHaveBeenCalledOnce();
                expect(errorSpy).toHaveBeenCalledWith('artemisApp.modelingAssessmentEditor.messages.updateAfterComplaintFailed');
                expect(component.result?.participation?.results).toBeUndefined();
            }
            expect(onSuccessCalled).toBe(!errorKeyFromServer);
            expect(onErrorCalled).toBe(!!errorKeyFromServer);
        }),
    );

    it('should cancel the current assessment', fakeAsync(() => {
        const windowSpy = jest.spyOn(window, 'confirm').mockReturnValue(true);

        component.submission = {
            id: 2,
            submitted: true,
            type: 'MANUAL',
            text: 'Test\n\nTest\n\nTest',
        } as unknown as ModelingSubmission;

        const serviceSpy = jest.spyOn(service, 'cancelAssessment').mockReturnValue(of());

        component.ngOnInit();
        tick(500);

        component.onCancelAssessment();
        expect(windowSpy).toHaveBeenCalledOnce();
        expect(serviceSpy).toHaveBeenCalledOnce();
    }));

    it('should handle changed feedback', fakeAsync(() => {
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

        component.ngOnInit();
        tick(500);

        const course = new Course();
        component.modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
        component.modelingExercise.maxPoints = 5;
        component.modelingExercise.bonusPoints = 5;
        const handleFeedbackSpy = jest.spyOn(submissionService, 'handleFeedbackCorrectionRoundTag');
        component.onFeedbackChanged(feedbacks);
        expect(component.referencedFeedback).toHaveLength(1);
        expect(component.totalScore).toBe(3);
        expect(handleFeedbackSpy).toHaveBeenCalled();
    }));

    describe('test assessNext', () => {
        it('should navigate to the next submission', fakeAsync(() => {
            const course = new Course();
            component.modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
            component.modelingExercise.id = 1;

            const routerSpy = jest.spyOn(router, 'navigate').mockImplementation();
            const modelingSubmission: ModelingSubmission = { id: 1 };
            const serviceSpy = jest.spyOn(modelingSubmissionService, 'getSubmissionWithoutAssessment').mockReturnValue(of(modelingSubmission));

            component.ngOnInit();

            const correctionRound = 1;
            const courseId = 1;
            const exerciseId = 1;
            component.correctionRound = correctionRound;
            component.courseId = courseId;
            component.modelingExercise = { id: exerciseId } as Exercise;
            component.exerciseId = exerciseId;
            const url = ['/course-management', courseId.toString(), 'modeling-exercises', exerciseId.toString(), 'submissions', modelingSubmission.id!.toString(), 'assessment'];
            const queryParams = { queryParams: { 'correction-round': correctionRound } };

            tick(500);
            component.assessNext();
            tick(500);

            expect(serviceSpy).toHaveBeenCalledOnce();
            expect(routerSpy).toHaveBeenCalledWith(url, queryParams);
        }));

        it('no submission left', () => {
            const course = new Course();
            component.modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
            component.modelingExercise.id = 1;
            const routerSpy = jest.spyOn(router, 'navigate').mockImplementation();
            jest.spyOn(modelingSubmissionService, 'getSubmissionWithoutAssessment').mockReturnValue(of(undefined));
            component.ngOnInit();

            component.assessNext();

            expect(component.submission).toBeUndefined();
            expect(routerSpy).toHaveBeenCalledTimes(0);
        });

        it('throw error while assessNext', fakeAsync(() => {
            const course = new Course();
            component.modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
            component.modelingExercise.id = 1;

            const response = new HttpErrorResponse({ status: 403 });
            const serviceSpy = jest.spyOn(modelingSubmissionService, 'getSubmissionWithoutAssessment').mockReturnValue(throwError(() => response));

            component.ngOnInit();
            tick(500);
            component.assessNext();
            expect(serviceSpy).toHaveBeenCalledOnce();
        }));
    });

    it('should invoke import example submission', () => {
        const course = new Course();
        component.modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
        component.modelingExercise.id = 1;
        component.submission = {
            id: 2,
            submitted: true,
            type: 'MANUAL',
            text: 'Test\n\nTest\n\nTest',
        } as ModelingSubmission;

        const importSpy = jest.spyOn(exampleSubmissionService, 'import').mockReturnValue(of(new HttpResponse({ body: new ExampleSubmission() })));

        component.useStudentSubmissionAsExampleSubmission();

        expect(importSpy).toHaveBeenCalledOnce();
        expect(importSpy).toHaveBeenCalledWith(component.submission.id, component.modelingExercise!.id);
    });

    it('should display error when complaint resolved but assessment invalid', () => {
        let onSuccessCalled = false;
        let onErrorCalled = false;
        const assessmentAfterComplaint: AssessmentAfterComplaint = {
            complaintResponse: new ComplaintResponse(),
            onSuccess: () => (onSuccessCalled = true),
            onError: () => (onErrorCalled = true),
        };
        const alertService = TestBed.inject(AlertService);
        const errorSpy = jest.spyOn(alertService, 'error');

        const validateSpy = jest.spyOn(component, 'validateFeedback').mockImplementation(() => (component.assessmentsAreValid = false));

        component.onUpdateAssessmentAfterComplaint(assessmentAfterComplaint);
        expect(validateSpy).toHaveBeenCalledOnce();
        expect(errorSpy).toHaveBeenCalledOnce();
        expect(errorSpy).toHaveBeenCalledWith('artemisApp.modelingAssessment.invalidAssessments');
        expect(onSuccessCalled).toBeFalse();
        expect(onErrorCalled).toBeTrue();
    });

    it('should report feedback suggestions not enabled', () => {
        component.modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined);
        component.ngOnInit();
        expect(component.isFeedbackSuggestionsEnabled).toBeFalse();
    });

    it('should report feedback suggestions enabled', () => {
        component.modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined);
        component.modelingExercise.feedbackSuggestionModule = 'module_text_llm';
        component.ngOnInit();
        expect(component.isFeedbackSuggestionsEnabled).toBeTrue();
    });

    it('should return unreferenced feedback only', () => {
        component.modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined);
        component.modelingExercise.feedbackSuggestionModule = 'module_text_llm';
        component.ngOnInit();

        const unreferencedFeedback = createTestFeedback();
        const referencedFeedback = createTestFeedback();

        referencedFeedback.type = FeedbackType.MANUAL;
        referencedFeedback.reference = 'element_id';

        component.feedbackSuggestions = [unreferencedFeedback, referencedFeedback];

        expect(component.unreferencedFeedbackSuggestions).toHaveLength(1);
        expect(component.unreferencedFeedbackSuggestions[0]?.id).toBe(unreferencedFeedback.id);
    });
});
