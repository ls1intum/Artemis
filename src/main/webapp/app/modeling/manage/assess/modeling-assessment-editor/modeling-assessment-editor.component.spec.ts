import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, ParamMap, Router, RouterModule, convertToParamMap } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { AssessmentLayoutComponent } from 'app/assessment/manage/assessment-layout/assessment-layout.component';
import { ComplaintService, EntityResponseType } from 'app/assessment/shared/services/complaint.service';
import { AccountService } from 'app/core/auth/account.service';
import { JhiLanguageHelper } from 'app/core/language/shared/language.helper';
import { User } from 'app/core/user/user.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { ComplaintResponse } from 'app/assessment/shared/entities/complaint-response.model';
import { Complaint } from 'app/assessment/shared/entities/complaint.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { Participation, ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { getLatestSubmissionResult } from 'app/exercise/shared/entities/submission/submission.model';
import { ModelingAssessmentEditorComponent } from 'app/modeling/manage/assess/modeling-assessment-editor/modeling-assessment-editor.component';
import { ModelingAssessmentService } from 'app/modeling/manage/assess/modeling-assessment.service';
import { ModelingSubmissionService } from 'app/modeling/overview/modeling-submission/modeling-submission.service';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { SubmissionService } from 'app/exercise/submission/submission.service';
import { MockComponent, MockProvider } from 'ng-mocks';
import { ModelingAssessmentComponent } from 'app/modeling/manage/assess/modeling-assessment.component';
import { CollapsableAssessmentInstructionsComponent } from 'app/assessment/manage/assessment-instructions/collapsable-assessment-instructions/collapsable-assessment-instructions.component';
import { UnreferencedFeedbackComponent } from 'app/exercise/unreferenced-feedback/unreferenced-feedback.component';
import { ExampleSubmissionService } from 'app/assessment/shared/services/example-submission.service';
import { ExampleSubmission } from 'app/assessment/shared/entities/example-submission.model';
import dayjs from 'dayjs/esm';
import { AssessmentAfterComplaint } from 'app/assessment/manage/complaints-for-tutor/complaints-for-tutor.component';
import { AlertService } from 'app/shared/service/alert.service';
import { UMLDiagramType } from '@ls1intum/apollon';
import { AthenaService } from 'app/assessment/shared/services/athena.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { TextAssessmentAnalytics } from 'app/text/manage/assess/analytics/text-assessment-analytics.service';

describe('ModelingAssessmentEditorComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ModelingAssessmentEditorComponent;
    let fixture: ComponentFixture<ModelingAssessmentEditorComponent>;
    let service: ModelingAssessmentService;
    let mockAuth: MockAccountService;
    let modelingSubmissionService: ModelingSubmissionService;
    let athenaService: AthenaService;
    let complaintService: ComplaintService;
    let modelingSubmissionSpy: ReturnType<typeof vi.spyOn>;
    let complaintSpy: ReturnType<typeof vi.spyOn>;
    let router: Router;
    let submissionService: SubmissionService;
    let exampleSubmissionService: ExampleSubmissionService;
    let paramMapSubject: BehaviorSubject<ParamMap>;

    beforeEach(() => {
        paramMapSubject = new BehaviorSubject(convertToParamMap({}));
        TestBed.configureTestingModule({
            imports: [
                RouterModule.forRoot([]),
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
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
                MockProvider(TextAssessmentAnalytics),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        });

        fixture = TestBed.createComponent(ModelingAssessmentEditorComponent);
        component = fixture.componentInstance;
        service = TestBed.inject(ModelingAssessmentService);
        modelingSubmissionService = TestBed.inject(ModelingSubmissionService);
        athenaService = TestBed.inject(AthenaService);
        complaintService = TestBed.inject(ComplaintService);
        submissionService = TestBed.inject(SubmissionService);
        mockAuth = TestBed.inject(AccountService) as any as MockAccountService;
        exampleSubmissionService = TestBed.inject(ExampleSubmissionService);
        mockAuth.hasAnyAuthorityDirect([]);
        mockAuth.identity();
        fixture.detectChanges();

        router = TestBed.inject(Router);
    });

    afterEach(() => {
        vi.restoreAllMocks();
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
        it('ngOnInit', async () => {
            modelingSubmissionSpy = vi.spyOn(modelingSubmissionService, 'getSubmission');
            complaintSpy = vi.spyOn(complaintService, 'findBySubmissionId');
            const submission = getSubmissionWithData();

            modelingSubmissionSpy.mockReturnValue(of(submission));
            const user = <User>{ id: 99, groups: ['instructorGroup'] };
            const result: Result = {
                feedbacks: [new Feedback()],
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

            const handleFeedbackSpy = vi.spyOn(submissionService, 'handleFeedbackCorrectionRoundTag');
            const verifyFeedbackSpy = vi.spyOn(component, 'validateFeedback');

            component.ngOnInit();
            await fixture.whenStable();
            expect(modelingSubmissionSpy).toHaveBeenCalledOnce();
            expect(component.isLoading).toBe(false);
            expect(component.complaint).toEqual(complaint);
            modelingSubmissionSpy.mockRestore();
            // called twice, since the feedback is additionally verified during the component initialization
            expect(handleFeedbackSpy).toHaveBeenCalledTimes(2);
            expect(verifyFeedbackSpy).toHaveBeenCalledOnce();
            expect(component.assessmentsAreValid).toBe(true);
        });

        it('wrongly call ngOnInit and throw exception', async () => {
            modelingSubmissionSpy = vi.spyOn(modelingSubmissionService, 'getSubmission');
            const response = new HttpErrorResponse({ status: 403 });
            modelingSubmissionSpy.mockReturnValue(throwError(() => response));

            component.ngOnInit();
            await fixture.whenStable();
            expect(modelingSubmissionSpy).toHaveBeenCalledOnce();
            modelingSubmissionSpy.mockRestore();
        });
        it('call ngOnInit with submissionId set to new', async () => {
            paramMapSubject.next(
                convertToParamMap({
                    submissionId: 'new',
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
                        feedbackSuggestionModule: 'modeling',
                    } as unknown as Exercise,
                },
            } as ModelingSubmission;

            const modelingSubmissionSpy = vi.spyOn(modelingSubmissionService, 'getSubmissionWithoutAssessment').mockReturnValue(of(mockSubmission));
            vi.spyOn(athenaService, 'getModelingFeedbackSuggestions').mockReturnValue(of([new Feedback(), new Feedback()]));

            component.ngOnInit();
            await fixture.whenStable();

            expect(modelingSubmissionSpy).toHaveBeenCalledOnce();
            expect(component.submission).toBe(mockSubmission);
            expect(component.assessmentsAreValid).toBe(false);
        });
    });

    describe('should test the overwrite access rights and return true', () => {
        it('tests the method with instructor rights', async () => {
            const course = new Course();
            component.ngOnInit();
            await fixture.whenStable();
            component.modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
            component.modelingExercise.isAtLeastInstructor = true;
            expect(component.canOverride).toBe(true);
        });

        it('tests the method with tutor rights and as assessor', async () => {
            const course = new Course();
            component.modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
            component.modelingExercise.isAtLeastInstructor = false;
            component.isAssessor = true;
            component.complaint = new Complaint();
            component.complaint.id = 0;
            component.complaint.complaintText = 'complaint';
            component.ngOnInit();
            await fixture.whenStable();
            mockAuth.isAtLeastInstructorInCourse(course);
            component['checkPermissions']();
            fixture.changeDetectorRef.detectChanges();
            expect(component.modelingExercise.isAtLeastInstructor).toBe(false);
            expect(component.canOverride).toBe(false);
        });
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

        it('should save assessment', async () => {
            const saveAssessmentSpy = vi.spyOn(service, 'saveAssessment').mockReturnValue(of(getLatestSubmissionResult(component.submission)!));

            component.ngOnInit();
            await fixture.whenStable();
            component.onSaveAssessment();
            expect(saveAssessmentSpy).toHaveBeenCalledOnce();
        });

        it('should try to submit assessment', async () => {
            vi.spyOn(service, 'saveAssessment').mockReturnValue(of(getLatestSubmissionResult(component.submission)!));
            vi.spyOn(window, 'confirm').mockReturnValue(false);

            component.ngOnInit();
            await fixture.whenStable();

            component.onSubmitAssessment();

            expect(window.confirm).toHaveBeenCalledOnce();
            expect(component.highlightMissingFeedback).toBe(true);

            component.modelingExercise!.isAtLeastInstructor = true;
            expect(component.canOverride).toBe(true);
        });

        it('should allow overriding directly after submitting', async () => {
            vi.spyOn(window, 'confirm').mockReturnValue(false);

            component.modelingExercise!.isAtLeastInstructor = true;
            component.ngOnInit();
            await fixture.whenStable();

            component.onSubmitAssessment();
            expect(component.canOverride).toBe(true);
        });

        it('should not invalidate assessment after saving', async () => {
            component.submission = getSubmissionWithData();
            vi.spyOn(modelingSubmissionService, 'getSubmission').mockReturnValue(of(component.submission));

            component.ngOnInit();
            await fixture.whenStable();
            component.onSaveAssessment();
            expect(component.assessmentsAreValid).toBe(true);
        });

        it('should submit the assessment', async () => {
            const submitMock = vi.spyOn(service, 'saveAssessment').mockReturnValue(of(component.result!));
            vi.spyOn(window, 'confirm').mockReturnValue(true);

            component.validateFeedback();
            expect(component.assessmentsAreValid).toBe(true);

            component.onSubmitAssessment();
            await fixture.whenStable();

            expect(submitMock).toHaveBeenCalledOnce();
        });
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

    it.each([undefined, 'genericErrorKey', 'complaintLock'])('should update assessment after complaint, errorKeyFromServer=%s', async (errorKeyFromServer: string | undefined) => {
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
        } as unknown as Result;

        const errorMessage = 'errMsg';
        const errorParams = ['errParam1', 'errParam2'];

        const serviceSpy = vi.spyOn(service, 'updateAssessmentAfterComplaint');

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
        await fixture.whenStable();

        let onSuccessCalled = false;
        let onErrorCalled = false;
        const assessmentAfterComplaint: AssessmentAfterComplaint = {
            complaintResponse,
            onSuccess: () => (onSuccessCalled = true),
            onError: () => (onErrorCalled = true),
        };

        const alertService = TestBed.inject(AlertService);
        const errorSpy = vi.spyOn(alertService, 'error');
        const validateSpy = vi.spyOn(component, 'validateFeedback').mockImplementation(() => (component.assessmentsAreValid = true));

        component.onUpdateAssessmentAfterComplaint(assessmentAfterComplaint);

        expect(validateSpy).toHaveBeenCalledOnce();
        expect(serviceSpy).toHaveBeenCalledOnce();
        if (!errorKeyFromServer) {
            expect(errorSpy).not.toHaveBeenCalled();
            expect(component.result).toEqual(changedResult);
        } else if (errorKeyFromServer === 'complaintLock') {
            expect(errorSpy).toHaveBeenCalledOnce();
            expect(errorSpy).toHaveBeenCalledWith(errorMessage, errorParams);
        } else {
            // Handle all other errors
            expect(errorSpy).toHaveBeenCalledOnce();
            expect(errorSpy).toHaveBeenCalledWith('artemisApp.modelingAssessmentEditor.messages.updateAfterComplaintFailed');
        }
        expect(onSuccessCalled).toBe(!errorKeyFromServer);
        expect(onErrorCalled).toBe(!!errorKeyFromServer);
    });

    it('should cancel the current assessment', async () => {
        const windowSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);

        component.submission = {
            id: 2,
            submitted: true,
            type: 'MANUAL',
            text: 'Test\n\nTest\n\nTest',
        } as unknown as ModelingSubmission;

        const serviceSpy = vi.spyOn(service, 'cancelAssessment').mockReturnValue(of());

        component.ngOnInit();
        await fixture.whenStable();

        component.onCancelAssessment();
        expect(windowSpy).toHaveBeenCalledOnce();
        expect(serviceSpy).toHaveBeenCalledOnce();
    });

    it('should handle changed feedback', async () => {
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
        await fixture.whenStable();

        const course = new Course();
        component.modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
        component.modelingExercise.maxPoints = 5;
        component.modelingExercise.bonusPoints = 5;
        const handleFeedbackSpy = vi.spyOn(submissionService, 'handleFeedbackCorrectionRoundTag');
        component.onFeedbackChanged(feedbacks);
        expect(component.referencedFeedback).toHaveLength(1);
        expect(component.totalScore).toBe(3);
        expect(handleFeedbackSpy).toHaveBeenCalled();
    });

    describe('test assessNext', () => {
        it('should navigate to the next submission', async () => {
            const course = new Course();
            component.modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
            component.modelingExercise.id = 1;

            const routerSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
            const modelingSubmission: ModelingSubmission = { id: 1 };
            const serviceSpy = vi.spyOn(modelingSubmissionService, 'getSubmissionWithoutAssessment').mockReturnValue(of(modelingSubmission));

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

            await fixture.whenStable();
            component.assessNext();
            await fixture.whenStable();

            expect(serviceSpy).toHaveBeenCalledOnce();
            expect(routerSpy).toHaveBeenCalledWith(url, queryParams);
        });

        it('no submission left', () => {
            const course = new Course();
            component.modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
            component.modelingExercise.id = 1;
            const routerSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
            vi.spyOn(modelingSubmissionService, 'getSubmissionWithoutAssessment').mockReturnValue(of(undefined));
            component.ngOnInit();

            component.assessNext();

            expect(component.submission).toBeUndefined();
            expect(routerSpy).toHaveBeenCalledTimes(0);
        });

        it('throw error while assessNext', async () => {
            const course = new Course();
            component.modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
            component.modelingExercise.id = 1;

            const response = new HttpErrorResponse({ status: 403 });
            const serviceSpy = vi.spyOn(modelingSubmissionService, 'getSubmissionWithoutAssessment').mockReturnValue(throwError(() => response));

            component.ngOnInit();
            await fixture.whenStable();
            component.assessNext();
            expect(serviceSpy).toHaveBeenCalledOnce();
        });
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

        const importSpy = vi.spyOn(exampleSubmissionService, 'import').mockReturnValue(of(new HttpResponse({ body: new ExampleSubmission() })));

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
        const errorSpy = vi.spyOn(alertService, 'error');

        const validateSpy = vi.spyOn(component, 'validateFeedback').mockImplementation(() => (component.assessmentsAreValid = false));

        component.onUpdateAssessmentAfterComplaint(assessmentAfterComplaint);
        expect(validateSpy).toHaveBeenCalledOnce();
        expect(errorSpy).toHaveBeenCalledOnce();
        expect(errorSpy).toHaveBeenCalledWith('artemisApp.modelingAssessment.invalidAssessments');
        expect(onSuccessCalled).toBe(false);
        expect(onErrorCalled).toBe(true);
    });

    it('should report feedback suggestions not enabled', () => {
        component.modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined);
        component.ngOnInit();
        expect(component.isFeedbackSuggestionsEnabled).toBe(false);
    });

    it('should report feedback suggestions enabled', () => {
        component.modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined);
        component.modelingExercise.feedbackSuggestionModule = 'module_text_llm';
        component.ngOnInit();
        expect(component.isFeedbackSuggestionsEnabled).toBe(true);
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
