/**
 * Vitest tests for FileUploadAssessmentComponent.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Params, Router, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { MockComponent } from 'ng-mocks';
import dayjs from 'dayjs/esm';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

import 'app/shared/util/array.extension';

import { FileUploadAssessmentComponent } from './file-upload-assessment.component';
import { FileUploadAssessmentService } from './file-upload-assessment.service';
import { FileUploadSubmissionService } from 'app/fileupload/overview/file-upload-submission.service';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { FileUploadSubmission } from 'app/fileupload/shared/entities/file-upload-submission.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { Complaint } from 'app/assessment/shared/entities/complaint.model';
import { ComplaintResponse } from 'app/assessment/shared/entities/complaint-response.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { SubmissionExerciseType, SubmissionType, setLatestSubmissionResult } from 'app/exercise/shared/entities/submission/submission.model';
import { Course } from 'app/core/course/shared/entities/course.model';

import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/shared/service/alert.service';
import { ComplaintService } from 'app/assessment/shared/services/complaint.service';
import { SubmissionService } from 'app/exercise/submission/submission.service';
import { StructuredGradingCriterionService } from 'app/exercise/structured-grading-criterion/structured-grading-criterion.service';
import { FileService } from 'app/shared/service/file.service';

import { AssessmentLayoutComponent } from 'app/assessment/manage/assessment-layout/assessment-layout.component';
import { ResizeableContainerComponent } from 'app/shared/resizeable-container/resizeable-container.component';
import { ScoreDisplayComponent } from 'app/shared/score-display/score-display.component';
import { UnreferencedFeedbackComponent } from 'app/exercise/unreferenced-feedback/unreferenced-feedback.component';
import { AssessmentInstructionsComponent } from 'app/assessment/manage/assessment-instructions/assessment-instructions/assessment-instructions.component';

describe('FileUploadAssessmentComponent', () => {
    setupTestBed({ zoneless: true });

    let component: FileUploadAssessmentComponent;
    let fixture: ComponentFixture<FileUploadAssessmentComponent>;
    let httpMock: HttpTestingController;

    let fileUploadSubmissionService: FileUploadSubmissionService;
    let fileUploadAssessmentService: FileUploadAssessmentService;
    let complaintService: ComplaintService;
    let alertService: AlertService;
    let submissionService: SubmissionService;
    let router: Router;

    // Test data factory functions
    const createCourse = (id = 123): Course => {
        const course = new Course();
        course.id = id;
        return course;
    };

    const createExercise = (overrides?: Partial<FileUploadExercise>): FileUploadExercise => {
        const exercise = new FileUploadExercise(undefined, undefined);
        exercise.id = 20;
        exercise.type = ExerciseType.FILE_UPLOAD;
        exercise.maxPoints = 100;
        exercise.bonusPoints = 0;
        exercise.course = createCourse();
        if (overrides) {
            Object.assign(exercise, overrides);
        }
        return exercise;
    };

    const createParticipation = (exercise?: FileUploadExercise): StudentParticipation => {
        const participation = new StudentParticipation();
        participation.type = ParticipationType.STUDENT;
        participation.exercise = exercise ?? createExercise();
        return participation;
    };

    const createSubmission = (exercise?: FileUploadExercise): FileUploadSubmission => {
        const submission = new FileUploadSubmission();
        submission.submissionExerciseType = SubmissionExerciseType.FILE_UPLOAD;
        submission.id = 2278;
        submission.submitted = true;
        submission.type = SubmissionType.MANUAL;
        submission.submissionDate = dayjs('2019-07-09T10:47:33.244Z');
        submission.filePath = '/api/files/test.pdf';
        submission.participation = createParticipation(exercise);
        return submission;
    };

    const createResult = (submission?: FileUploadSubmission): Result => {
        const result = new Result();
        result.id = 2374;
        result.completionDate = dayjs('2019-07-09T11:51:23.251Z');
        result.successful = false;
        result.score = 1;
        result.rated = true;
        result.submission = submission;
        result.assessmentType = AssessmentType.MANUAL;
        result.exampleResult = false;
        result.hasComplaint = false;
        result.feedbacks = [];
        return result;
    };

    const createFeedback = (overrides?: Partial<Feedback>): Feedback => {
        const feedback = new Feedback();
        feedback.id = Math.floor(Math.random() * 1000);
        feedback.credits = 5;
        feedback.detailText = 'Test feedback';
        feedback.type = FeedbackType.MANUAL_UNREFERENCED;
        if (overrides) {
            Object.assign(feedback, overrides);
        }
        return feedback;
    };

    let routeParams$: BehaviorSubject<Params>;
    let routeQueryParams$: BehaviorSubject<Params>;

    beforeEach(async () => {
        routeParams$ = new BehaviorSubject({ exerciseId: 20, courseId: 123, submissionId: 7 });
        routeQueryParams$ = new BehaviorSubject(
            convertToParamMap({
                testRun: 'false',
                'correction-round': '0',
            }),
        );

        await TestBed.configureTestingModule({
            imports: [FileUploadAssessmentComponent, TranslateModule.forRoot()],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                provideRouter([]),
                {
                    provide: ActivatedRoute,
                    useValue: {
                        params: routeParams$.asObservable(),
                        queryParamMap: routeQueryParams$.asObservable(),
                    },
                },
                {
                    provide: AccountService,
                    useValue: {
                        identity: vi.fn().mockResolvedValue({ id: 1 }),
                        setAccessRightsForExercise: vi.fn(),
                        isAtLeastInstructorInCourse: vi.fn().mockReturnValue(false),
                    },
                },
                {
                    provide: ComplaintService,
                    useValue: {
                        findBySubmissionId: vi.fn().mockReturnValue(of({ body: null })),
                    },
                },
                {
                    provide: SubmissionService,
                    useValue: {
                        handleFeedbackCorrectionRoundTag: vi.fn(),
                    },
                },
                {
                    provide: StructuredGradingCriterionService,
                    useValue: {
                        computeTotalScore: vi.fn().mockReturnValue(0),
                    },
                },
                {
                    provide: FileService,
                    useValue: {
                        downloadFile: vi.fn(),
                    },
                },
            ],
        })
            .overrideComponent(FileUploadAssessmentComponent, {
                remove: {
                    imports: [AssessmentLayoutComponent, ResizeableContainerComponent, ScoreDisplayComponent, UnreferencedFeedbackComponent, AssessmentInstructionsComponent],
                },
                add: {
                    imports: [
                        MockComponent(AssessmentLayoutComponent),
                        MockComponent(ResizeableContainerComponent),
                        MockComponent(ScoreDisplayComponent),
                        MockComponent(UnreferencedFeedbackComponent),
                        MockComponent(AssessmentInstructionsComponent),
                    ],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(FileUploadAssessmentComponent);
        component = fixture.componentInstance;
        httpMock = TestBed.inject(HttpTestingController);

        fileUploadSubmissionService = TestBed.inject(FileUploadSubmissionService);
        fileUploadAssessmentService = fixture.componentRef.injector.get(FileUploadAssessmentService);
        complaintService = TestBed.inject(ComplaintService);
        alertService = TestBed.inject(AlertService);
        submissionService = TestBed.inject(SubmissionService);
        router = TestBed.inject(Router);
    });

    afterEach(() => {
        vi.clearAllMocks();
        httpMock.verify();
        if (fixture) {
            fixture.destroy();
        }
    });

    describe('initialization', () => {
        it('should extract test run flag and correction round from query params', () => {
            routeQueryParams$.next(
                convertToParamMap({
                    testRun: 'true',
                    'correction-round': '1',
                }),
            );
            const submission = createSubmission();
            const result = createResult(submission);
            setLatestSubmissionResult(submission, result);
            vi.spyOn(fileUploadSubmissionService, 'get').mockReturnValue(of(new HttpResponse({ body: submission })));

            component.ngOnInit();

            expect(component.isTestRun).toBe(true);
            expect(component.correctionRound).toBe(1);
        });

        it('should extract route params for course and exercise', () => {
            const submission = createSubmission();
            vi.spyOn(fileUploadSubmissionService, 'get').mockReturnValue(of(new HttpResponse({ body: submission })));

            component.ngOnInit();

            expect(component.courseId).toBe(123);
            expect(component.exerciseId).toBe(20);
        });

        it('should load submission by ID when submissionId is numeric', () => {
            const submission = createSubmission();
            const result = createResult(submission);
            setLatestSubmissionResult(submission, result);
            const getSpy = vi.spyOn(fileUploadSubmissionService, 'get').mockReturnValue(of(new HttpResponse({ body: submission })));

            component.ngOnInit();

            expect(getSpy).toHaveBeenCalledWith(7, 0, 0);
        });

        it('should request optimal submission when submissionId is "new"', () => {
            routeParams$.next({ exerciseId: 20, courseId: 123, submissionId: 'new' });
            const submission = createSubmission();
            const getWithoutAssessmentSpy = vi.spyOn(fileUploadSubmissionService, 'getSubmissionWithoutAssessment').mockReturnValue(of(submission));

            component.ngOnInit();

            expect(getWithoutAssessmentSpy).toHaveBeenCalledWith(20, true, 0);
        });

        it('should navigate back and show alert when no optimal submission is available', () => {
            routeParams$.next({ exerciseId: 20, courseId: 123, submissionId: 'new' });
            vi.spyOn(fileUploadSubmissionService, 'getSubmissionWithoutAssessment').mockReturnValue(of(undefined));
            const alertSpy = vi.spyOn(alertService, 'info');
            vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

            component.exercise = createExercise();
            component.ngOnInit();

            expect(alertSpy).toHaveBeenCalledWith('artemisApp.exerciseAssessmentDashboard.noSubmissions');
        });

        it('should handle locked submissions limit reached error', () => {
            routeParams$.next({ exerciseId: 20, courseId: 123, submissionId: 'new' });
            vi.spyOn(fileUploadSubmissionService, 'getSubmissionWithoutAssessment').mockReturnValue(
                throwError(() => new HttpErrorResponse({ error: { errorKey: 'lockedSubmissionsLimitReached' } })),
            );
            vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

            component.exercise = createExercise();
            component.ngOnInit();

            expect(component.loadingInitialSubmission).toBe(false);
        });

        it('should validate assessments after loading submission', () => {
            const submission = createSubmission();
            const result = createResult(submission);
            result.feedbacks = [createFeedback({ credits: 1, detailText: 'Valid feedback' })];
            setLatestSubmissionResult(submission, result);
            vi.spyOn(fileUploadSubmissionService, 'get').mockReturnValue(of(new HttpResponse({ body: submission })));

            component.ngOnInit();

            expect(component.assessmentsAreValid).toBe(true);
        });

        it('should set examId and exerciseGroupId from route params for exam exercises', () => {
            routeParams$.next({ exerciseId: 20, courseId: 123, submissionId: 7, examId: 5, exerciseGroupId: 10 });
            const submission = createSubmission();
            vi.spyOn(fileUploadSubmissionService, 'get').mockReturnValue(of(new HttpResponse({ body: submission })));

            component.ngOnInit();

            expect(component.examId).toBe(5);
            expect(component.exerciseGroupId).toBe(10);
        });
    });

    describe('loadSubmission', () => {
        it('should initialize all properties from loaded submission', () => {
            const exercise = createExercise();
            const submission = createSubmission(exercise);
            const result = createResult(submission);
            setLatestSubmissionResult(submission, result);
            vi.spyOn(fileUploadSubmissionService, 'get').mockReturnValue(of(new HttpResponse({ body: submission })));

            component.ngOnInit();

            expect(component.submission).toBe(submission);
            expect(component.participation).toBe(submission.participation);
            expect(component.exercise).toBe(exercise);
            expect(component.result).toEqual(result);
            expect(component.busy).toBe(false);
            expect(component.isLoading).toBe(false);
        });

        it('should load feedbacks from result into unreferencedFeedback', () => {
            const submission = createSubmission();
            const result = createResult(submission);
            const feedback1 = createFeedback({ credits: 5, detailText: 'Feedback 1' });
            const feedback2 = createFeedback({ credits: 10, detailText: 'Feedback 2' });
            result.feedbacks = [feedback1, feedback2];
            setLatestSubmissionResult(submission, result);
            vi.spyOn(fileUploadSubmissionService, 'get').mockReturnValue(of(new HttpResponse({ body: submission })));

            component.ngOnInit();

            expect(component.unreferencedFeedback).toHaveLength(2);
        });

        it('should load complaint if submission has complaint', () => {
            const submission = createSubmission();
            const result = createResult(submission);
            result.hasComplaint = true;
            setLatestSubmissionResult(submission, result);
            const complaint = new Complaint();
            complaint.id = 555;
            complaint.complaintText = 'Test complaint';
            vi.spyOn(fileUploadSubmissionService, 'get').mockReturnValue(of(new HttpResponse({ body: submission })));
            vi.spyOn(complaintService, 'findBySubmissionId').mockReturnValue(of({ body: complaint } as HttpResponse<Complaint>));

            component.ngOnInit();

            expect(component.complaint).toEqual(complaint);
        });

        it('should show lock alert for unassessed submission owned by current user', async () => {
            const submission = createSubmission();
            const result = createResult(submission);
            result.assessor = { id: 1, internal: false };
            result.completionDate = undefined;
            setLatestSubmissionResult(submission, result);
            vi.spyOn(fileUploadSubmissionService, 'get').mockReturnValue(of(new HttpResponse({ body: submission })));
            const alertInfoSpy = vi.spyOn(alertService, 'info');

            component.ngOnInit();
            // Wait for all async operations (identity promise and subscriptions) to complete
            await fixture.whenStable();

            expect(alertInfoSpy).toHaveBeenCalledWith('artemisApp.fileUploadAssessment.messages.lock');
        });

        it('should handle error when loading submission fails with 403', () => {
            vi.spyOn(fileUploadSubmissionService, 'get').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
            const alertErrorSpy = vi.spyOn(alertService, 'error');

            component.ngOnInit();

            expect(alertErrorSpy).toHaveBeenCalled();
        });
    });

    describe('feedback management', () => {
        beforeEach(() => {
            component.exercise = createExercise();
            component.submission = createSubmission();
        });

        it('should add a new empty feedback when addFeedback is called', () => {
            expect(component.unreferencedFeedback).toHaveLength(0);

            component.addFeedback();

            expect(component.unreferencedFeedback).toHaveLength(1);
            expect(component.unreferencedFeedback[0]).toBeInstanceOf(Feedback);
        });

        it('should remove feedback when deleteAssessment is called', () => {
            const feedback1 = createFeedback();
            const feedback2 = createFeedback();
            component.unreferencedFeedback = [feedback1, feedback2];

            component.deleteAssessment(feedback1);

            expect(component.unreferencedFeedback).toHaveLength(1);
            expect(component.unreferencedFeedback).not.toContain(feedback1);
            expect(component.unreferencedFeedback).toContain(feedback2);
        });

        it('should revalidate assessment after adding feedback', () => {
            const validateSpy = vi.spyOn(component, 'validateAssessment');

            component.addFeedback();

            expect(validateSpy).toHaveBeenCalled();
        });

        it('should revalidate assessment after deleting feedback', () => {
            const feedback = createFeedback();
            component.unreferencedFeedback = [feedback];
            const validateSpy = vi.spyOn(component, 'validateAssessment');

            component.deleteAssessment(feedback);

            expect(validateSpy).toHaveBeenCalled();
        });

        it('should return combined assessments from assessments getter', () => {
            const feedback1 = createFeedback();
            const feedback2 = createFeedback();
            component.unreferencedFeedback = [feedback1, feedback2];

            const assessments = component.assessments;

            expect(assessments).toHaveLength(2);
            expect(assessments).toContain(feedback1);
            expect(assessments).toContain(feedback2);
        });

        it('should update total score correctly after loading feedbacks', () => {
            const structuredGradingService = TestBed.inject(StructuredGradingCriterionService);
            vi.spyOn(structuredGradingService, 'computeTotalScore').mockReturnValue(15);
            component.unreferencedFeedback = [createFeedback({ credits: 5 }), createFeedback({ credits: 10 })];

            component.validateAssessment();

            expect(component.totalScore).toBe(15);
        });
    });

    describe('validateAssessment', () => {
        beforeEach(() => {
            component.exercise = createExercise();
            component.submission = createSubmission();
        });

        it('should set assessmentsAreValid to true when all feedbacks have credits and comments', () => {
            component.unreferencedFeedback = [createFeedback({ credits: 5, detailText: 'Valid feedback' })];

            component.validateAssessment();

            expect(component.assessmentsAreValid).toBe(true);
        });

        it('should set assessmentsAreValid to false when feedback lacks detailText', () => {
            component.unreferencedFeedback = [createFeedback({ credits: 5, detailText: undefined })];

            component.validateAssessment();

            expect(component.assessmentsAreValid).toBe(false);
        });

        it('should call submissionService.handleFeedbackCorrectionRoundTag', () => {
            component.correctionRound = 1;
            component.unreferencedFeedback = [];

            component.validateAssessment();

            expect(submissionService.handleFeedbackCorrectionRoundTag).toHaveBeenCalledWith(1, component.submission);
        });

        it('should calculate total score during validation', () => {
            const structuredGradingService = TestBed.inject(StructuredGradingCriterionService);
            vi.spyOn(structuredGradingService, 'computeTotalScore').mockReturnValue(50);

            component.validateAssessment();

            expect(structuredGradingService.computeTotalScore).toHaveBeenCalled();
        });
    });

    describe('onSaveAssessment', () => {
        beforeEach(() => {
            const exercise = createExercise();
            const submission = createSubmission(exercise);
            component.exercise = exercise;
            component.submission = submission;
            component.result = createResult(submission);
        });

        it('should save assessment and update result on success', () => {
            const savedResult = createResult();
            savedResult.id = 999;
            vi.spyOn(fileUploadAssessmentService, 'saveAssessment').mockReturnValue(of(savedResult));
            const alertSuccessSpy = vi.spyOn(alertService, 'success');
            const alertCloseSpy = vi.spyOn(alertService, 'closeAll');

            component.onSaveAssessment();

            expect(alertCloseSpy).toHaveBeenCalled();
            expect(alertSuccessSpy).toHaveBeenCalledWith('artemisApp.assessment.messages.saveSuccessful');
            expect(component.result).toBe(savedResult);
        });

        it('should show error alert on save failure', () => {
            vi.spyOn(fileUploadAssessmentService, 'saveAssessment').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
            const alertErrorSpy = vi.spyOn(alertService, 'error');

            component.onSaveAssessment();

            expect(alertErrorSpy).toHaveBeenCalledWith('artemisApp.assessment.messages.saveFailed');
        });

        it('should pass assessment note when saving', () => {
            const savedResult = createResult();
            savedResult.assessmentNote = { id: 1, note: 'Test note' };
            component.result = savedResult;
            const saveSpy = vi.spyOn(fileUploadAssessmentService, 'saveAssessment').mockReturnValue(of(savedResult));

            component.onSaveAssessment();

            expect(saveSpy).toHaveBeenCalledWith(expect.any(Array), component.submission!.id, 'Test note');
        });
    });

    describe('onSubmitAssessment', () => {
        beforeEach(() => {
            const exercise = createExercise();
            const submission = createSubmission(exercise);
            const result = createResult(submission);
            setLatestSubmissionResult(submission, result);
            component.exercise = exercise;
            component.submission = submission;
            component.result = result;
        });

        it('should not submit if assessments are invalid', () => {
            component.unreferencedFeedback = [createFeedback({ credits: 5, detailText: undefined })];
            const saveSpy = vi.spyOn(fileUploadAssessmentService, 'saveAssessment');
            const alertErrorSpy = vi.spyOn(alertService, 'error');

            component.onSubmitAssessment();

            expect(saveSpy).not.toHaveBeenCalled();
            expect(alertErrorSpy).toHaveBeenCalledWith('artemisApp.fileUploadAssessment.error.invalidAssessments');
        });

        it('should submit assessment with submit=true on valid assessments', () => {
            component.unreferencedFeedback = [createFeedback({ credits: 5, detailText: 'Valid' })];
            const savedResult = createResult();
            const saveSpy = vi.spyOn(fileUploadAssessmentService, 'saveAssessment').mockReturnValue(of(savedResult));

            component.onSubmitAssessment();

            expect(saveSpy).toHaveBeenCalledWith(expect.any(Array), component.submission!.id, undefined, true);
        });

        it('should show success alert on successful submission', () => {
            component.unreferencedFeedback = [createFeedback({ credits: 5, detailText: 'Valid' })];
            vi.spyOn(fileUploadAssessmentService, 'saveAssessment').mockReturnValue(of(createResult()));
            const alertSuccessSpy = vi.spyOn(alertService, 'success');

            component.onSubmitAssessment();

            expect(alertSuccessSpy).toHaveBeenCalledWith('artemisApp.assessment.messages.submitSuccessful');
        });

        it('should update participation with result after successful submission', () => {
            component.unreferencedFeedback = [createFeedback({ credits: 5, detailText: 'Valid' })];
            const savedResult = createResult();
            vi.spyOn(fileUploadAssessmentService, 'saveAssessment').mockReturnValue(of(savedResult));

            component.onSubmitAssessment();

            expect(component.result).toBe(savedResult);
        });
    });

    describe('onCancelAssessment', () => {
        beforeEach(() => {
            component.submission = createSubmission();
            component.exercise = createExercise();
        });

        it('should cancel assessment when user confirms', () => {
            vi.spyOn(window, 'confirm').mockReturnValue(true);
            const cancelSpy = vi.spyOn(fileUploadAssessmentService, 'cancelAssessment').mockReturnValue(of(void 0));
            vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

            component.onCancelAssessment();

            expect(cancelSpy).toHaveBeenCalledWith(component.submission!.id);
        });

        it('should not cancel assessment when user declines confirmation', () => {
            vi.spyOn(window, 'confirm').mockReturnValue(false);
            const cancelSpy = vi.spyOn(fileUploadAssessmentService, 'cancelAssessment');

            component.onCancelAssessment();

            expect(cancelSpy).not.toHaveBeenCalled();
        });

        it('should navigate back after canceling assessment', () => {
            vi.spyOn(window, 'confirm').mockReturnValue(true);
            vi.spyOn(fileUploadAssessmentService, 'cancelAssessment').mockReturnValue(of(void 0));
            const navigateSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

            component.onCancelAssessment();

            expect(navigateSpy).toHaveBeenCalled();
        });
    });

    describe('assessNext', () => {
        beforeEach(() => {
            component.exercise = createExercise();
            component.courseId = 123;
            component.exerciseId = 20;
        });

        it('should navigate to next submission when available', () => {
            const nextSubmission = createSubmission();
            nextSubmission.id = 999;
            const nextParticipation = createParticipation();
            nextParticipation.id = 888;
            nextSubmission.participation = nextParticipation;
            vi.spyOn(fileUploadSubmissionService, 'getSubmissionWithoutAssessment').mockReturnValue(of(nextSubmission));
            const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

            component.assessNext();

            expect(navigateSpy).toHaveBeenCalled();
            expect(component.isLoading).toBe(false);
        });

        it('should clear submission when no next submission is available', () => {
            vi.spyOn(fileUploadSubmissionService, 'getSubmissionWithoutAssessment').mockReturnValue(of(undefined));

            component.assessNext();

            expect(component.submission).toBeUndefined();
        });

        it('should show error alert on fetch failure', () => {
            vi.spyOn(fileUploadSubmissionService, 'getSubmissionWithoutAssessment').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
            const alertErrorSpy = vi.spyOn(alertService, 'error');

            component.assessNext();

            expect(alertErrorSpy).toHaveBeenCalled();
        });

        it('should reset unreferencedFeedback when loading next assessment', () => {
            component.unreferencedFeedback = [createFeedback()];
            vi.spyOn(fileUploadSubmissionService, 'getSubmissionWithoutAssessment').mockReturnValue(of(undefined));

            component.assessNext();

            expect(component.unreferencedFeedback).toHaveLength(0);
        });
    });

    describe('onUpdateAssessmentAfterComplaint', () => {
        beforeEach(() => {
            const exercise = createExercise();
            const submission = createSubmission(exercise);
            const result = createResult(submission);
            setLatestSubmissionResult(submission, result);
            component.exercise = exercise;
            component.submission = submission;
            component.result = result;
        });

        it('should not update if assessments are invalid', () => {
            component.unreferencedFeedback = [createFeedback({ credits: 5, detailText: undefined })];
            let onSuccessCalled = false;
            let onErrorCalled = false;
            const assessmentAfterComplaint = {
                complaintResponse: new ComplaintResponse(),
                onSuccess: () => (onSuccessCalled = true),
                onError: () => (onErrorCalled = true),
            };
            const updateSpy = vi.spyOn(fileUploadAssessmentService, 'updateAssessmentAfterComplaint');
            const alertErrorSpy = vi.spyOn(alertService, 'error');

            component.onUpdateAssessmentAfterComplaint(assessmentAfterComplaint);

            expect(updateSpy).not.toHaveBeenCalled();
            expect(onErrorCalled).toBe(true);
            expect(onSuccessCalled).toBe(false);
            expect(alertErrorSpy).toHaveBeenCalledWith('artemisApp.fileUploadAssessment.error.invalidAssessments');
        });

        it('should update assessment after complaint on valid assessments', () => {
            component.unreferencedFeedback = [createFeedback({ credits: 5, detailText: 'Valid' })];
            const updatedResult = createResult();
            vi.spyOn(fileUploadAssessmentService, 'updateAssessmentAfterComplaint').mockReturnValue(of(new HttpResponse({ body: updatedResult })));
            let onSuccessCalled = false;
            const assessmentAfterComplaint = {
                complaintResponse: new ComplaintResponse(),
                onSuccess: () => (onSuccessCalled = true),
                onError: () => {},
            };
            const alertSuccessSpy = vi.spyOn(alertService, 'success');

            component.onUpdateAssessmentAfterComplaint(assessmentAfterComplaint);

            expect(onSuccessCalled).toBe(true);
            expect(alertSuccessSpy).toHaveBeenCalledWith('artemisApp.assessment.messages.updateAfterComplaintSuccessful');
        });

        it('should handle complaint lock error specifically', () => {
            component.unreferencedFeedback = [createFeedback({ credits: 5, detailText: 'Valid' })];
            vi.spyOn(fileUploadAssessmentService, 'updateAssessmentAfterComplaint').mockReturnValue(
                throwError(
                    () =>
                        new HttpErrorResponse({
                            error: { errorKey: 'complaintLock', message: 'Locked', params: {} },
                        }),
                ),
            );
            let onErrorCalled = false;
            const assessmentAfterComplaint = {
                complaintResponse: new ComplaintResponse(),
                onSuccess: () => {},
                onError: () => (onErrorCalled = true),
            };
            const alertErrorSpy = vi.spyOn(alertService, 'error');

            component.onUpdateAssessmentAfterComplaint(assessmentAfterComplaint);

            expect(onErrorCalled).toBe(true);
            expect(alertErrorSpy).toHaveBeenCalledWith('Locked', {});
        });

        it('should show generic error message on non-complaint-lock error', () => {
            component.unreferencedFeedback = [createFeedback({ credits: 5, detailText: 'Valid' })];
            vi.spyOn(fileUploadAssessmentService, 'updateAssessmentAfterComplaint').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
            const assessmentAfterComplaint = {
                complaintResponse: new ComplaintResponse(),
                onSuccess: () => {},
                onError: () => {},
            };
            const alertErrorSpy = vi.spyOn(alertService, 'error');

            component.onUpdateAssessmentAfterComplaint(assessmentAfterComplaint);

            expect(alertErrorSpy).toHaveBeenCalledWith('artemisApp.assessment.messages.updateAfterComplaintFailed');
        });
    });

    describe('canOverride', () => {
        beforeEach(() => {
            component.exercise = createExercise();
            component.result = createResult();
            component.userId = 1;
        });

        it('should return true for instructors', () => {
            component.exercise!.isAtLeastInstructor = true;

            expect(component.canOverride).toBe(true);
        });

        it('should return false when there is a complaint and user is the assessor', () => {
            component.complaint = new Complaint();
            component.complaint.id = 1;
            component.isAssessor = true;

            expect(component.canOverride).toBe(false);
        });

        it('should return false when assessment due date has passed', () => {
            component.exercise!.assessmentDueDate = dayjs().subtract(1, 'day');
            component.isAssessor = true;

            expect(component.canOverride).toBe(false);
        });

        it('should return true for assessor before assessment due date without complaint', () => {
            component.exercise!.assessmentDueDate = dayjs().add(1, 'day');
            component.isAssessor = true;
            component.complaint = undefined!;

            expect(component.canOverride).toBe(true);
        });

        it('should return false when exercise is undefined', () => {
            component.exercise = undefined;

            expect(component.canOverride).toBe(false);
        });

        it('should return true when no assessment due date is set', () => {
            component.exercise!.assessmentDueDate = undefined;
            component.isAssessor = true;

            expect(component.canOverride).toBe(true);
        });
    });

    describe('attachmentExtension', () => {
        it('should extract file extension from path', () => {
            expect(component.attachmentExtension('this/is/a/filepath/file.png')).toBe('png');
            expect(component.attachmentExtension('document.pdf')).toBe('pdf');
            expect(component.attachmentExtension('/path/to/file.docx')).toBe('docx');
        });

        it('should return N/A for empty file path', () => {
            expect(component.attachmentExtension('')).toBe('N/A');
        });

        it('should return N/A for undefined file path', () => {
            // Testing runtime edge case - the method handles falsy values even though typed as string
            // Using type assertion to test defensive programming in the implementation
            expect(component.attachmentExtension(undefined as unknown as string)).toBe('N/A');
        });

        it('should handle files with multiple dots', () => {
            expect(component.attachmentExtension('file.name.with.dots.pdf')).toBe('pdf');
        });
    });

    describe('getComplaint', () => {
        beforeEach(() => {
            component.submission = createSubmission();
        });

        it('should load complaint successfully', () => {
            const complaint = new Complaint();
            complaint.id = 123;
            complaint.complaintText = 'Test complaint';
            vi.spyOn(complaintService, 'findBySubmissionId').mockReturnValue(of({ body: complaint } as HttpResponse<Complaint>));

            component.getComplaint();

            expect(component.complaint).toEqual(complaint);
        });

        it('should not set complaint when response body is null', () => {
            vi.spyOn(complaintService, 'findBySubmissionId').mockReturnValue(of({ body: null } as HttpResponse<Complaint>));

            component.getComplaint();

            expect(component.complaint).toBeUndefined();
        });

        it('should show error alert on failure', () => {
            vi.spyOn(complaintService, 'findBySubmissionId').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
            const alertErrorSpy = vi.spyOn(alertService, 'error');

            component.getComplaint();

            expect(alertErrorSpy).toHaveBeenCalled();
        });
    });

    describe('navigateBack', () => {
        beforeEach(() => {
            component.exercise = createExercise();
            component.submission = createSubmission();
        });

        it('should navigate back when called', () => {
            const navigateSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

            component.navigateBack();

            expect(navigateSpy).toHaveBeenCalled();
        });
    });

    describe('downloadFile', () => {
        it('should call file service to download file', () => {
            const fileService = TestBed.inject(FileService);

            component.downloadFile('/path/to/file.pdf');

            expect(fileService.downloadFile).toHaveBeenCalledWith('/path/to/file.pdf');
        });
    });

    describe('updateAssessment', () => {
        it('should call validateAssessment when updateAssessment is called', () => {
            component.exercise = createExercise();
            component.submission = createSubmission();
            const validateSpy = vi.spyOn(component, 'validateAssessment');

            component.updateAssessment();

            expect(validateSpy).toHaveBeenCalled();
        });
    });
});
