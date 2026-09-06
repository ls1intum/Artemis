import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, ParamMap, Router, RouterModule, convertToParamMap } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { AssessmentLayoutComponent } from 'app/assessment/manage/assessment-layout/assessment-layout.component';
import { ComplaintService, EntityResponseType } from 'app/assessment/shared/services/complaint.service';
import { AccountService } from 'app/core/auth/account.service';
import { JhiLanguageHelper } from 'app/core/language/shared/language.helper';
import { ComplaintResponse } from 'app/assessment/shared/entities/complaint-response.model';
import { Complaint } from 'app/assessment/shared/entities/complaint.model';
import { Course } from 'app/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Feedback, FeedbackHighlightColor, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { Participation, ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { getLatestSubmissionResult } from 'app/exercise/shared/entities/submission/submission.model';
import { ModelingAssessmentEditorComponent } from 'app/modeling/manage/assess/modeling-assessment-editor/modeling-assessment-editor.component';
import { ModelingAssessmentService } from 'app/modeling/manage/assess/modeling-assessment.service';
import { ModelingSubmissionService } from 'app/modeling/overview/modeling-submission/modeling-submission.service';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { SubmissionService } from 'app/exercise/submission/submission.service';
import { MockComponent, MockProvider } from 'ng-mocks';
import { ModelingAssessmentComponent } from 'app/modeling/manage/assess/modeling-assessment.component';
import { UnreferencedFeedbackComponent } from 'app/exercise/unreferenced-feedback/unreferenced-feedback.component';
import { ExampleSubmissionService } from 'app/assessment/shared/services/example-submission.service';
import { ExampleSubmission } from 'app/assessment/shared/entities/example-submission.model';
import dayjs from 'dayjs/esm';
import { AssessmentAfterComplaint, ComplaintsForTutorComponent } from 'app/assessment/manage/complaints-for-tutor/complaints-for-tutor.component';
import { AssessmentComplaintAlertComponent } from 'app/assessment/manage/assessment-complaint-alert/assessment-complaint-alert.component';
import { AlertService } from 'app/foundation/service/alert.service';
import { ASSESSMENT_NOT_POSSIBLE_EXAM_RUNNING } from 'app/assessment/shared/util/assessment-availability.util';
import { ApollonEditor, UMLDiagramType } from '@tumaet/apollon';
import { By } from '@angular/platform-browser';
import { Location } from '@angular/common';
import { ModelingAssessmentTopLeftDirective } from 'app/modeling/manage/assess/modeling-assessment-top-left.directive';
import { FeedbackSuggestionsBannerComponent } from 'app/assessment/manage/feedback-suggestions-banner/feedback-suggestions-banner.component';
import { AthenaService } from 'app/assessment/shared/services/athena.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { TextAssessmentAnalytics } from 'app/text/manage/assess/analytics/text-assessment-analytics.service';
import { ComplaintDTO } from 'app/assessment/shared/entities/complaint-dto.model';
import { AiExperienceOptInService } from 'app/logos/ai-experience-opt-in.service';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { MODULE_FEATURE_ATHENA } from 'app/app.constants';
import { DeleteDialogService } from 'app/shared-ui/delete-dialog/service/delete-dialog.service';
import { deepClone } from 'app/foundation/util/deep-clone.util';

describe('ModelingAssessmentEditorComponent', () => {
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
    let queryParamMapSubject: BehaviorSubject<ParamMap>;

    beforeEach(() => {
        vi.spyOn(ApollonEditor.prototype, 'subscribeToModelChange').mockReturnValue(undefined as any);
        paramMapSubject = new BehaviorSubject(convertToParamMap({}));
        queryParamMapSubject = new BehaviorSubject(convertToParamMap({}));
        TestBed.configureTestingModule({
            imports: [
                RouterModule.forRoot([]),
                ModelingAssessmentEditorComponent,
                MockComponent(AssessmentLayoutComponent),
                MockComponent(ModelingAssessmentComponent),
                MockComponent(UnreferencedFeedbackComponent),
            ],
            providers: [
                JhiLanguageHelper,
                {
                    provide: ActivatedRoute,
                    useValue: {
                        paramMap: paramMapSubject.asObservable(),
                        queryParamMap: queryParamMapSubject.asObservable(),
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
                MockProvider(DeleteDialogService),
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
                    correctionRound: 0,
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

    it('should place the complaint banner and form beside the diagram, not in the scrolling feedback pane', async () => {
        vi.spyOn(modelingSubmissionService, 'getSubmission').mockReturnValue(of(getSubmissionWithData()));
        vi.spyOn(complaintService, 'findBySubmissionId').mockReturnValue(of({ body: { id: 1, complaintText: 'Why only 80%?' } as ComplaintDTO } as HttpResponse<ComplaintDTO>));

        component.ngOnInit();
        await fixture.whenStable();
        fixture.detectChanges();

        const layout = fixture.debugElement.query(By.directive(AssessmentLayoutComponent)).componentInstance as AssessmentLayoutComponent;
        expect(layout.showComplaintSection()).toBe(false);

        const canvas = fixture.debugElement.query(By.css('[assessmentWorkspaceCanvas]'));
        expect(canvas.query(By.directive(ComplaintsForTutorComponent))).not.toBeNull();

        const pane = fixture.debugElement.query(By.css('[assessmentWorkspaceDetails]'));
        expect(pane).not.toBeNull();
        expect(pane.query(By.directive(ComplaintsForTutorComponent))).toBeNull();
        expect(fixture.debugElement.query(By.directive(AssessmentComplaintAlertComponent))).toBeNull();
    });

    it('should rewrite only the new segment of the assessment path once a random submission is locked', async () => {
        const location = TestBed.inject(Location);
        vi.spyOn(location, 'path').mockReturnValue('/course-management/1/modeling-exercises/7/submissions/new/assessment?correction-round=0');
        const go = vi.spyOn(location, 'go').mockImplementation(() => {});
        vi.spyOn(modelingSubmissionService, 'getSubmissionWithoutAssessment').mockReturnValue(of(getSubmissionWithData()));

        component['loadRandomSubmission'](7);
        await fixture.whenStable();

        expect(go).toHaveBeenCalledExactlyOnceWith('/course-management/1/modeling-exercises/7/submissions/1/assessment?correction-round=0');
    });

    describe('ngOnInit tests', () => {
        it('ngOnInit', async () => {
            modelingSubmissionSpy = vi.spyOn(modelingSubmissionService, 'getSubmission');
            complaintSpy = vi.spyOn(complaintService, 'findBySubmissionId');
            const submission = getSubmissionWithData();

            modelingSubmissionSpy.mockReturnValue(of(submission));
            const complaintDTO = <ComplaintDTO>{ id: 1, complaintText: 'Why only 80%?' };
            complaintSpy.mockReturnValue(of({ body: complaintDTO } as HttpResponse<ComplaintDTO>));

            const handleFeedbackSpy = vi.spyOn(submissionService, 'handleFeedbackCorrectionRoundTag');
            const verifyFeedbackSpy = vi.spyOn(component, 'validateFeedback');

            component.ngOnInit();
            await fixture.whenStable();
            expect(modelingSubmissionSpy).toHaveBeenCalledOnce();
            expect(component.isLoading()).toBe(false);
            expect(component.complaint()).toMatchObject({
                id: complaintDTO.id,
                complaintText: complaintDTO.complaintText,
                result: component.result(),
            });
            modelingSubmissionSpy.mockRestore();
            expect(handleFeedbackSpy).toHaveBeenCalledTimes(2);
            expect(verifyFeedbackSpy).toHaveBeenCalledOnce();
            expect(component.assessmentsAreValid()).toBe(true);
        });

        it.each([
            { param: '1', expectedRound: 1, description: 'a usable round' },
            { param: undefined, expectedRound: 0, description: 'an absent round' },
            { param: '   ', expectedRound: 0, description: 'a whitespace only round' },
            { param: 'abc', expectedRound: 0, description: 'a round that is not a number' },
            { param: '1.5', expectedRound: 0, description: 'a fractional round' },
            { param: '-1', expectedRound: 0, description: 'a negative round' },
            { param: '1e3', expectedRound: 0, description: 'an exponential round' },
        ])('should request the submission for $description', async ({ param, expectedRound }) => {
            // The round is requested from the server and then used to index the loaded results, so an unusable value must
            // not travel on as NaN, a fraction or a negative number. The URL decides, and no usable value means the
            // first round, so the same URL always opens the same round (#13396).
            // The component is already initialized by the fixture, so the route is driven instead of calling ngOnInit
            // again, which would subscribe a second time and load twice.
            const getSubmissionSpy = vi.spyOn(modelingSubmissionService, 'getSubmission').mockReturnValue(of(getSubmissionWithData()));
            queryParamMapSubject.next(convertToParamMap(param === undefined ? {} : { 'correction-round': param }));
            paramMapSubject.next(convertToParamMap({ submissionId: '2', courseId: '1', exerciseId: '1' }));
            await fixture.whenStable();

            expect(component.correctionRound()).toBe(expectedRound);
            expect(getSubmissionSpy).toHaveBeenCalledExactlyOnceWith(2, expectedRound, 0);
        });

        it('should keep the round it loaded when only the correction round in the url changes', async () => {
            // This component has no resolver, so a `correction-round` that changes on its own — reachable only by
            // hand-editing the address bar — starts no new load. The round it shows must then stay the round the
            // submission was requested with, because the same value indexes the results of that submission.
            const getSubmissionSpy = vi.spyOn(modelingSubmissionService, 'getSubmission').mockReturnValue(of(getSubmissionWithData()));
            queryParamMapSubject.next(convertToParamMap({ 'correction-round': '1' }));
            paramMapSubject.next(convertToParamMap({ submissionId: '2', courseId: '1', exerciseId: '1' }));
            await fixture.whenStable();
            expect(component.correctionRound()).toBe(1);

            queryParamMapSubject.next(convertToParamMap({ 'correction-round': '0' }));
            await fixture.whenStable();

            expect(component.correctionRound()).toBe(1);
            expect(getSubmissionSpy).toHaveBeenCalledExactlyOnceWith(2, 1, 0);
        });

        it('should leave the loading state when there is no submission to assess', async () => {
            // Both loading flags start out set, and the empty state only renders once they are cleared, so returning
            // without clearing them left the page blank instead of saying that there is nothing to assess.
            vi.spyOn(modelingSubmissionService, 'getSubmissionWithoutAssessment').mockReturnValue(of(undefined));
            component.modelingExercise.set({ id: 1, feedbackSuggestionModule: 'module' } as ModelingExercise);
            component.isAssessor.set(true);
            component.hasAutomaticFeedback.set(true);

            paramMapSubject.next(convertToParamMap({ submissionId: 'new', courseId: '1', exerciseId: '1' }));
            await fixture.whenStable();

            expect(component.submission()).toBeUndefined();
            expect(component.loadingInitialSubmission()).toBe(false);
            expect(component.isLoading()).toBe(false);
            // Regression: stale exercise/assessor/feedback state from a previous submission must not keep the
            // opt-in banner alive, and fetchAndApplyFeedbackSuggestions() must no longer be callable without a submission.
            expect(component.modelingExercise()).toBeUndefined();
            expect(component.isAssessor()).toBe(false);
            expect(component.hasAutomaticFeedback()).toBe(false);
            expect(component.requiresAiExperienceOptIn()).toBe(false);
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

        it('should explain the wait on the page when the exam is not over yet, instead of claiming the submission was not found', async () => {
            const alertService = TestBed.inject(AlertService);
            const errorSpy = vi.spyOn(alertService, 'error');
            const response = new HttpErrorResponse({
                status: 403,
                error: { errorKey: ASSESSMENT_NOT_POSSIBLE_EXAM_RUNNING, params: { date: '2026-08-01T10:00:00Z' } },
            });
            vi.spyOn(modelingSubmissionService, 'getSubmission').mockReturnValue(throwError(() => response));

            component.ngOnInit();
            await fixture.whenStable();

            expect(component.assessmentNotPossibleYet()).toEqual({ translationKey: `error.${ASSESSMENT_NOT_POSSIBLE_EXAM_RUNNING}`, date: '2026-08-01T10:00:00Z' });
            expect(component.submission()).toBeUndefined();
            expect(errorSpy).not.toHaveBeenCalled();
        });

        it('should clear the explanation when the next submission is loaded into the reused component', async () => {
            const response = new HttpErrorResponse({
                status: 403,
                error: { errorKey: ASSESSMENT_NOT_POSSIBLE_EXAM_RUNNING, params: { date: '2026-08-01T10:00:00Z' } },
            });
            vi.spyOn(modelingSubmissionService, 'getSubmission')
                .mockReturnValueOnce(throwError(() => response))
                .mockReturnValue(of(getSubmissionWithData()));

            component.ngOnInit();
            await fixture.whenStable();
            expect(component.assessmentNotPossibleYet()).toBeDefined();

            paramMapSubject.next(convertToParamMap({ submissionId: '2', courseId: '1', exerciseId: '1' }));
            await fixture.whenStable();

            expect(component.assessmentNotPossibleYet()).toBeUndefined();
            expect(component.submission()).toBeDefined();
        });

        it('should clear feedback when the next submission has no feedback', async () => {
            const firstSubmission = getSubmissionWithData();
            const secondSubmission = deepClone(firstSubmission);
            secondSubmission.id = 2;
            secondSubmission.results = [{ id: 2375, correctionRound: 0, feedbacks: [] } as unknown as Result];
            vi.spyOn(modelingSubmissionService, 'getSubmission').mockReturnValueOnce(of(firstSubmission)).mockReturnValueOnce(of(secondSubmission));

            component.ngOnInit();
            await fixture.whenStable();
            expect(component.referencedFeedback).toHaveLength(1);
            component.loadingFeedbackSuggestions.set(true);
            component.highlightedElements.set(new Map([['element', 'red']]));
            component.feedbackSuggestions = [createTestFeedback()];
            component.hasAutomaticFeedback.set(true);

            paramMapSubject.next(convertToParamMap({ submissionId: '2', courseId: '1', exerciseId: '1' }));
            await fixture.whenStable();

            expect(component.referencedFeedback).toHaveLength(0);
            expect(component.unreferencedFeedback()).toHaveLength(0);
            expect(component.loadingFeedbackSuggestions()).toBe(false);
            expect(component.highlightedElements()).toBeUndefined();
            expect(component.feedbackSuggestions).toHaveLength(0);
            expect(component.hasAutomaticFeedback()).toBe(false);
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
            expect(component.submission()).toBe(mockSubmission);
            expect(component.assessmentsAreValid()).toBe(false);
        });

        it('should not automatically fetch feedback suggestions when the assessor has not accepted AI usage', async () => {
            vi.spyOn(TestBed.inject(ProfileService), 'getProfileInfo').mockReturnValue({ activeModuleFeatures: [MODULE_FEATURE_ATHENA] } as ProfileInfo);
            vi.spyOn(TestBed.inject(AiExperienceOptInService), 'hasAcceptedAiUsage').mockReturnValue(false);
            paramMapSubject.next(convertToParamMap({ submissionId: 'new', courseId: '1', exerciseId: '1' }));

            const mockSubmission: ModelingSubmission = {
                id: 123,
                submitted: true,
                participation: {
                    exercise: { id: 1, type: 'modeling', feedbackSuggestionModule: 'modeling' } as unknown as Exercise,
                },
            } as ModelingSubmission;
            vi.spyOn(modelingSubmissionService, 'getSubmissionWithoutAssessment').mockReturnValue(of(mockSubmission));
            const suggestionsSpy = vi.spyOn(athenaService, 'getModelingFeedbackSuggestions');

            component.ngOnInit();
            await fixture.whenStable();

            expect(suggestionsSpy).not.toHaveBeenCalled();
        });

        it('should automatically fetch feedback suggestions once Athena is active and the assessor has accepted AI usage', async () => {
            vi.spyOn(TestBed.inject(ProfileService), 'getProfileInfo').mockReturnValue({ activeModuleFeatures: [MODULE_FEATURE_ATHENA] } as ProfileInfo);
            vi.spyOn(TestBed.inject(AiExperienceOptInService), 'hasAcceptedAiUsage').mockReturnValue(true);
            paramMapSubject.next(convertToParamMap({ submissionId: 'new', courseId: '1', exerciseId: '1' }));

            const mockSubmission: ModelingSubmission = {
                id: 123,
                submitted: true,
                participation: {
                    exercise: { id: 1, type: 'modeling', feedbackSuggestionModule: 'modeling' } as unknown as Exercise,
                },
            } as ModelingSubmission;
            vi.spyOn(modelingSubmissionService, 'getSubmissionWithoutAssessment').mockReturnValue(of(mockSubmission));
            const suggestionsSpy = vi.spyOn(athenaService, 'getModelingFeedbackSuggestions').mockReturnValue(of([]));

            component.ngOnInit();
            await fixture.whenStable();

            expect(suggestionsSpy).toHaveBeenCalled();
        });
    });

    describe('should test the overwrite access rights and return true', () => {
        it('tests the method with instructor rights', async () => {
            const course = new Course();
            component.ngOnInit();
            await fixture.whenStable();
            component.modelingExercise.set(new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined));
            component.modelingExercise()!.isAtLeastInstructor = true;
            expect(component.canOverride).toBe(true);
        });

        it('tests the method with tutor rights and as assessor', async () => {
            const course = new Course();
            component.modelingExercise.set(new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined));
            component.modelingExercise()!.isAtLeastInstructor = false;
            component.isAssessor.set(true);
            component.complaint.set(new Complaint());
            component.complaint().id = 0;
            component.complaint().complaintText = 'complaint';
            component.ngOnInit();
            await fixture.whenStable();
            mockAuth.isAtLeastInstructorInCourse(course);
            component['checkPermissions']();
            fixture.changeDetectorRef.detectChanges();
            expect(component.modelingExercise()!.isAtLeastInstructor).toBe(false);
            expect(component.canOverride).toBe(false);
        });
    });

    describe('save and submit', () => {
        beforeEach(() => {
            const course = new Course();
            component.modelingExercise.set(new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined));
            component.modelingExercise()!.assessmentDueDate = dayjs().subtract(2, 'days');
            component.modelingExercise()!.maxPoints = 10;

            const feedback = createTestFeedback();
            component.unreferencedFeedback.set([feedback]);

            component.result.set({
                id: 2374,
                correctionRound: 0,
                score: 8,
                rated: true,
                hasComplaint: false,
            } as unknown as Result);

            component.submission.set({
                id: 1,
                submitted: true,
                type: 'MANUAL',
                text: 'Test\n\nTest\n\nTest',
            } as unknown as ModelingSubmission);
            component.submission()!.results = [component.result()!];
            getLatestSubmissionResult(component.submission())!.feedbacks = [
                {
                    id: 2,
                    detailText: 'Feedback',
                    credits: 1,
                } as Feedback,
            ];
        });

        it('should save assessment', async () => {
            const saveAssessmentSpy = vi.spyOn(service, 'saveAssessment').mockReturnValue(of(getLatestSubmissionResult(component.submission())!));

            component.ngOnInit();
            await fixture.whenStable();
            component.onSaveAssessment();
            expect(saveAssessmentSpy).toHaveBeenCalledOnce();
        });

        it('should try to submit assessment', async () => {
            vi.spyOn(service, 'saveAssessment').mockReturnValue(of(getLatestSubmissionResult(component.submission())!));
            vi.spyOn(window, 'confirm').mockReturnValue(false);

            component.ngOnInit();
            await fixture.whenStable();

            component.onSubmitAssessment();

            expect(window.confirm).toHaveBeenCalledOnce();
            expect(component.highlightMissingFeedback()).toBe(true);

            component.modelingExercise()!.isAtLeastInstructor = true;
            expect(component.canOverride).toBe(true);
        });

        it('should allow overriding directly after submitting', async () => {
            vi.spyOn(window, 'confirm').mockReturnValue(false);

            component.modelingExercise()!.isAtLeastInstructor = true;
            component.ngOnInit();
            await fixture.whenStable();

            component.onSubmitAssessment();
            expect(component.canOverride).toBe(true);
        });

        it('should not invalidate assessment after saving', async () => {
            component.submission.set(getSubmissionWithData());
            vi.spyOn(modelingSubmissionService, 'getSubmission').mockReturnValue(of(component.submission()!));

            component.ngOnInit();
            await fixture.whenStable();
            component.onSaveAssessment();
            expect(component.assessmentsAreValid()).toBe(true);
        });

        it('should submit the assessment', async () => {
            const submitMock = vi.spyOn(service, 'saveAssessment').mockReturnValue(of(component.result()!));
            vi.spyOn(window, 'confirm').mockReturnValue(true);

            component.validateFeedback();
            expect(component.assessmentsAreValid()).toBe(true);

            component.onSubmitAssessment();
            await fixture.whenStable();

            expect(submitMock).toHaveBeenCalledOnce();
        });

        describe('when the exam is not over yet', () => {
            const notPossibleYetResponse = () =>
                new HttpErrorResponse({
                    status: 403,
                    error: { errorKey: ASSESSMENT_NOT_POSSIBLE_EXAM_RUNNING, params: { date: '2026-08-01T10:00:00Z' } },
                });

            it('should explain when assessment is possible instead of reporting a failed save', async () => {
                const alertService = TestBed.inject(AlertService);
                const errorSpy = vi.spyOn(alertService, 'error');
                vi.spyOn(service, 'saveAssessment').mockReturnValue(throwError(() => notPossibleYetResponse()));

                component.ngOnInit();
                await fixture.whenStable();
                component.onSaveAssessment();

                expect(errorSpy).toHaveBeenCalledExactlyOnceWith(`error.${ASSESSMENT_NOT_POSSIBLE_EXAM_RUNNING}`, expect.anything());
                expect(errorSpy).not.toHaveBeenCalledWith('artemisApp.modelingAssessmentEditor.messages.saveFailed');
            });

            it('should explain when assessment is possible instead of reporting a failed submit', async () => {
                const alertService = TestBed.inject(AlertService);
                const errorSpy = vi.spyOn(alertService, 'error');
                vi.spyOn(service, 'saveAssessment').mockReturnValue(throwError(() => notPossibleYetResponse()));
                vi.spyOn(window, 'confirm').mockReturnValue(true);

                component.validateFeedback();
                component.onSubmitAssessment();
                await fixture.whenStable();

                expect(errorSpy).toHaveBeenCalledExactlyOnceWith(`error.${ASSESSMENT_NOT_POSSIBLE_EXAM_RUNNING}`, expect.anything());
                expect(errorSpy).not.toHaveBeenCalledWith('artemisApp.modelingAssessmentEditor.messages.submitFailed');
            });
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

        component.submission.set({
            id: 1,
            submitted: true,
            type: 'MANUAL',
            text: 'Test\n\nTest\n\nTest',
        } as unknown as ModelingSubmission);

        const changedResult = {
            id: 2374,
            correctionRound: 0,
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
        const validateSpy = vi.spyOn(component, 'validateFeedback').mockImplementation(() => component.assessmentsAreValid.set(true));

        component.onUpdateAssessmentAfterComplaint(assessmentAfterComplaint);

        expect(validateSpy).toHaveBeenCalledOnce();
        expect(serviceSpy).toHaveBeenCalledOnce();
        if (!errorKeyFromServer) {
            expect(errorSpy).not.toHaveBeenCalled();
            expect(component.result()).toEqual(changedResult);
        } else if (errorKeyFromServer === 'complaintLock') {
            expect(errorSpy).toHaveBeenCalledOnce();
            expect(errorSpy).toHaveBeenCalledWith(errorMessage, errorParams);
        } else {
            expect(errorSpy).toHaveBeenCalledOnce();
            expect(errorSpy).toHaveBeenCalledWith('artemisApp.modelingAssessmentEditor.messages.updateAfterComplaintFailed');
        }
        expect(onSuccessCalled).toBe(!errorKeyFromServer);
        expect(onErrorCalled).toBe(!!errorKeyFromServer);
    });

    it('should cancel the current assessment', async () => {
        const windowSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);

        component.submission.set({
            id: 2,
            submitted: true,
            type: 'MANUAL',
            text: 'Test\n\nTest\n\nTest',
        } as unknown as ModelingSubmission);

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
        component.modelingExercise.set(new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined));
        component.modelingExercise()!.maxPoints = 5;
        component.modelingExercise()!.bonusPoints = 5;
        const handleFeedbackSpy = vi.spyOn(submissionService, 'handleFeedbackCorrectionRoundTag');
        component.onFeedbackChanged(feedbacks);
        expect(component.referencedFeedback).toHaveLength(1);
        expect(component.totalScore()).toBe(3);
        expect(handleFeedbackSpy).toHaveBeenCalled();
    });

    describe('test assessNext', () => {
        it('should navigate to the next submission', async () => {
            const course = new Course();
            component.modelingExercise.set(new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined));
            component.modelingExercise()!.id = 1;

            const routerSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
            const modelingSubmission: ModelingSubmission = { id: 1 };
            const serviceSpy = vi.spyOn(modelingSubmissionService, 'getSubmissionWithoutAssessment').mockReturnValue(of(modelingSubmission));

            component.ngOnInit();

            const correctionRound = 1;
            const courseId = 1;
            const exerciseId = 1;
            component.correctionRound.set(correctionRound);
            component.courseId = courseId;
            component.modelingExercise.set({ id: exerciseId } as Exercise);
            component.exerciseId = exerciseId;
            const url = ['/course-management', courseId.toString(), 'modeling-exercises', exerciseId.toString(), 'submissions', modelingSubmission.id!.toString(), 'assessment'];
            const queryParams = { queryParams: { 'correction-round': correctionRound }, queryParamsHandling: 'merge' };

            await fixture.whenStable();
            component.assessNext();
            await fixture.whenStable();

            expect(serviceSpy).toHaveBeenCalledOnce();
            expect(routerSpy).toHaveBeenCalledWith(url, queryParams);
        });

        it('no submission left', () => {
            const course = new Course();
            component.modelingExercise.set(new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined));
            component.modelingExercise()!.id = 1;
            const routerSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
            vi.spyOn(modelingSubmissionService, 'getSubmissionWithoutAssessment').mockReturnValue(of(undefined));
            component.ngOnInit();

            component.assessNext();

            expect(component.submission()).toBeUndefined();
            expect(routerSpy).toHaveBeenCalledTimes(0);
        });

        it('throw error while assessNext', async () => {
            const course = new Course();
            component.modelingExercise.set(new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined));
            component.modelingExercise()!.id = 1;

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
        component.modelingExercise.set(new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined));
        component.modelingExercise()!.id = 1;
        component.submission.set({
            id: 2,
            submitted: true,
            type: 'MANUAL',
            text: 'Test\n\nTest\n\nTest',
        } as ModelingSubmission);

        const importSpy = vi.spyOn(exampleSubmissionService, 'import').mockReturnValue(of(new HttpResponse({ body: new ExampleSubmission() })));

        component.useStudentSubmissionAsExampleSubmission();

        expect(importSpy).toHaveBeenCalledOnce();
        expect(importSpy).toHaveBeenCalledWith(component.submission()!.id, component.modelingExercise()!.id);
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

        const validateSpy = vi.spyOn(component, 'validateFeedback').mockImplementation(() => component.assessmentsAreValid.set(false));

        component.onUpdateAssessmentAfterComplaint(assessmentAfterComplaint);
        expect(validateSpy).toHaveBeenCalledOnce();
        expect(errorSpy).toHaveBeenCalledOnce();
        expect(errorSpy).toHaveBeenCalledWith('artemisApp.modelingAssessment.invalidAssessments');
        expect(onSuccessCalled).toBe(false);
        expect(onErrorCalled).toBe(true);
    });

    it('should report feedback suggestions not enabled', () => {
        vi.spyOn(TestBed.inject(ProfileService), 'getProfileInfo').mockReturnValue({ activeModuleFeatures: [MODULE_FEATURE_ATHENA] } as ProfileInfo);
        component.modelingExercise.set(new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined));
        component.ngOnInit();
        expect(component.isFeedbackSuggestionsEnabled()).toBe(false);
    });

    it('should report feedback suggestions enabled', () => {
        vi.spyOn(TestBed.inject(ProfileService), 'getProfileInfo').mockReturnValue({ activeModuleFeatures: [MODULE_FEATURE_ATHENA] } as ProfileInfo);
        component.modelingExercise.set(new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined));
        component.modelingExercise()!.feedbackSuggestionModule = 'module_text_llm';
        component.ngOnInit();
        expect(component.isFeedbackSuggestionsEnabled()).toBe(true);
    });

    it('should report feedback suggestions not enabled when the Athena module is not active on this instance', () => {
        vi.spyOn(TestBed.inject(ProfileService), 'getProfileInfo').mockReturnValue({ activeModuleFeatures: [] } as unknown as ProfileInfo);
        component.modelingExercise.set(new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined));
        component.modelingExercise()!.feedbackSuggestionModule = 'module_text_llm';
        component.ngOnInit();
        expect(component.isFeedbackSuggestionsEnabled()).toBe(false);
    });

    describe('feedback suggestions chrome', () => {
        beforeEach(() => {
            vi.spyOn(TestBed.inject(ProfileService), 'getProfileInfo').mockReturnValue({ activeModuleFeatures: [MODULE_FEATURE_ATHENA] } as ProfileInfo);
            // These tests cover notice resolution once the assessor has already opted into AI usage; the opt-in gating itself is covered separately below.
            vi.spyOn(TestBed.inject(AiExperienceOptInService), 'hasAcceptedAiUsage').mockReturnValue(true);
        });

        const setNoticeInputs = (overrides: Partial<{ loading: boolean; automatic: boolean; assessor: boolean; enabled: boolean }> = {}) => {
            const exercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined);
            exercise.feedbackSuggestionModule = overrides.enabled ? 'module_modeling_llm' : undefined;
            component.modelingExercise.set(exercise);
            component.loadingFeedbackSuggestions.set(overrides.loading ?? false);
            component.hasAutomaticFeedback.set(overrides.automatic ?? false);
            component.isAssessor.set(overrides.assessor ?? false);
        };

        it.each([
            { name: 'nothing before anything is known', overrides: {}, expected: undefined },
            { name: 'loading while Athena is queried', overrides: { loading: true, enabled: true }, expected: 'loading' },
            { name: 'the suggestion notice once Athena answered', overrides: { automatic: true, assessor: true, enabled: true }, expected: 'suggestions' },
            { name: 'the automatic notice without Athena', overrides: { automatic: true, assessor: true }, expected: 'automaticAssessment' },
            { name: 'nothing for a tutor who is not the assessor', overrides: { automatic: true, enabled: true }, expected: undefined },
        ])('should resolve $name', ({ overrides, expected }) => {
            setNoticeInputs(overrides);

            expect(component.feedbackSuggestionsNotice()).toBe(expected);
        });

        it('should stop offering a notice once the assessment has been submitted', () => {
            setNoticeInputs({ automatic: true, assessor: true, enabled: true });
            expect(component.feedbackSuggestionsNotice()).toBe('suggestions');

            component.result.set({ id: 1, completionDate: dayjs() } as Result);

            expect(component.feedbackSuggestionsNotice()).toBeUndefined();
        });

        it('should mount the banner as canvas chrome rather than a band above the workspace, but only while loading', async () => {
            const submission = getSubmissionWithData();
            submission.participation!.exercise!.feedbackSuggestionModule = 'module_modeling_llm';
            component.submission.set(submission);
            setNoticeInputs({ loading: true, enabled: true });
            fixture.detectChanges();
            await fixture.whenStable();

            const banner = fixture.debugElement.query(By.directive(FeedbackSuggestionsBannerComponent));
            expect(banner).not.toBeNull();
            expect(banner.componentInstance.appearance()).toBe('chrome');
            expect(fixture.debugElement.query(By.directive(ModelingAssessmentComponent)).query(By.directive(FeedbackSuggestionsBannerComponent))).not.toBeNull();
            expect(banner.injector.get(ModelingAssessmentTopLeftDirective).occupied()).toBe(true);
        });

        it('should let the legend, not a second island, say that suggestions are available', async () => {
            const submission = getSubmissionWithData();
            submission.participation!.exercise!.feedbackSuggestionModule = 'module_modeling_llm';
            component.submission.set(submission);
            setNoticeInputs({ automatic: true, assessor: true, enabled: true });
            component.result.set({ id: 7 } as Result);
            fixture.detectChanges();
            await fixture.whenStable();

            const banner = fixture.debugElement.query(By.directive(FeedbackSuggestionsBannerComponent));
            expect(banner.injector.get(ModelingAssessmentTopLeftDirective).occupied()).toBe(false);
            expect(component.legendHighlights()).toEqual([
                {
                    color: FeedbackHighlightColor.CYAN,
                    text: 'artemisApp.modelingAssessment.legend.aiFeedbackSuggestions',
                    info: 'artemisApp.assessment.feedbackSuggestions.generativeAIAssessmentInfo',
                },
            ]);
        });

        it('should hand a referenced suggestion to the canvas, so Apollon can draw and highlight it', async () => {
            const submission = getSubmissionWithData();
            submission.participation!.exercise!.feedbackSuggestionModule = 'module_modeling_llm';
            component.submission.set(submission);
            component.modelingExercise.set({ id: 1, feedbackSuggestionModule: 'module_modeling_llm' } as ModelingExercise);
            component.result.set({ id: 7, feedbacks: [] } as unknown as Result);

            const referencedSuggestion = new Feedback();
            referencedSuggestion.type = FeedbackType.AUTOMATIC;
            referencedSuggestion.reference = 'Class:node-1';
            referencedSuggestion.referenceId = 'node-1';
            referencedSuggestion.referenceType = 'Class';
            vi.spyOn(athenaService, 'getModelingFeedbackSuggestions').mockReturnValue(of([referencedSuggestion]));

            await (component as any).fetchAndApplyFeedbackSuggestions();
            fixture.detectChanges();
            await fixture.whenStable();

            const canvas = fixture.debugElement.query(By.directive(ModelingAssessmentComponent));
            expect(canvas.componentInstance.resultFeedbacks()).toContain(referencedSuggestion);
            expect(component.highlightedElements().get('node-1')).toBeDefined();
        });

        it('should leave the region unoccupied, and the island unrendered, when there is no notice', async () => {
            component.submission.set(getSubmissionWithData());
            setNoticeInputs();
            fixture.detectChanges();
            await fixture.whenStable();

            const banner = fixture.debugElement.query(By.directive(FeedbackSuggestionsBannerComponent));
            expect(banner.injector.get(ModelingAssessmentTopLeftDirective).occupied()).toBe(false);
            expect(banner.query(By.css('.feedback-suggestions-chrome'))).toBeNull();
        });
    });

    it('should return unreferenced feedback only', () => {
        component.modelingExercise.set(new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined));
        component.modelingExercise()!.feedbackSuggestionModule = 'module_text_llm';
        component.ngOnInit();

        const unreferencedFeedback = createTestFeedback();
        const referencedFeedback = createTestFeedback();

        referencedFeedback.type = FeedbackType.MANUAL;
        referencedFeedback.reference = 'element_id';

        component.feedbackSuggestions = [unreferencedFeedback, referencedFeedback];

        expect(component.unreferencedFeedbackSuggestions).toHaveLength(1);
        expect(component.unreferencedFeedbackSuggestions[0]?.id).toBe(unreferencedFeedback.id);
    });

    describe('assessor AI Experience opt-in hint', () => {
        let aiExperienceOptInService: AiExperienceOptInService;

        beforeEach(() => {
            aiExperienceOptInService = TestBed.inject(AiExperienceOptInService);
            vi.spyOn(TestBed.inject(ProfileService), 'getProfileInfo').mockReturnValue({ activeModuleFeatures: [MODULE_FEATURE_ATHENA] } as ProfileInfo);
            component.modelingExercise.set(new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined));
            component.modelingExercise()!.feedbackSuggestionModule = 'module_text_llm';
        });

        it('should require opt-in when the assessor has not accepted AI usage', () => {
            vi.spyOn(aiExperienceOptInService, 'hasAcceptedAiUsage').mockReturnValue(false);
            expect(component.requiresAiExperienceOptIn()).toBe(true);
        });

        it('should not require opt-in when the assessor has accepted AI usage', () => {
            vi.spyOn(aiExperienceOptInService, 'hasAcceptedAiUsage').mockReturnValue(true);
            expect(component.requiresAiExperienceOptIn()).toBe(false);
        });

        it('should not require opt-in when the Athena module is not active on this instance', () => {
            vi.spyOn(TestBed.inject(ProfileService), 'getProfileInfo').mockReturnValue({ activeModuleFeatures: [] } as unknown as ProfileInfo);
            vi.spyOn(aiExperienceOptInService, 'hasAcceptedAiUsage').mockReturnValue(false);
            expect(component.requiresAiExperienceOptIn()).toBe(false);
        });

        it('should fetch feedback suggestions once the assessor opts in via the hint', () => {
            const suggestionsSpy = vi.spyOn(athenaService, 'getModelingFeedbackSuggestions').mockReturnValue(of([]));
            vi.spyOn(aiExperienceOptInService, 'promptForAiUsage').mockImplementation((onAccepted) => onAccepted());
            component.submission.set(getSubmissionWithData());
            component.result.set(getSubmissionWithData().results![0] as unknown as Result);

            component.onOptInToAiFeedbackSuggestions();

            expect(aiExperienceOptInService.promptForAiUsage).toHaveBeenCalled();
            expect(suggestionsSpy).toHaveBeenCalled();
        });
    });
});
