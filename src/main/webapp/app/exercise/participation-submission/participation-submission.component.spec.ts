import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { BehaviorSubject, Subject, of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';
import { MockProvider } from 'ng-mocks';
import { By } from '@angular/platform-browser';
import { EventManager } from 'app/foundation/service/event-manager.service';
import { DialogService } from 'primeng/dynamicdialog';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ProgrammingSubmissionService, ProgrammingSubmissionState, ProgrammingSubmissionStateObj } from 'app/programming/shared/services/programming-submission.service';
import { ResultComponent } from 'app/exercise/result/result.component';
import { ResultProgressBarComponent } from 'app/exercise/result/result-progress-bar/result-progress-bar.component';

import { ParticipationSubmissionComponent } from 'app/exercise/participation-submission/participation-submission.component';
import { SubmissionService } from 'app/exercise/submission/submission.service';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { TextAssessmentService } from 'app/text/manage/assess/service/text-assessment.service';
import { FileUploadAssessmentService } from 'app/fileupload/manage/assess/file-upload-assessment.service';
import { ProgrammingAssessmentManualResultService } from 'app/programming/manage/assess/manual-result/programming-assessment-manual-result.service';
import { ModelingAssessmentService } from 'app/modeling/manage/assess/modeling-assessment.service';

import { Submission, SubmissionExerciseType, SubmissionType } from 'app/exercise/shared/entities/submission/submission.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TemplateProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/template-programming-exercise-participation.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { SolutionProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/solution-programming-exercise-participation.model';
import { Participation, ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ParticipationSubmissionComponent', () => {
    let comp: ParticipationSubmissionComponent;
    let fixture: ComponentFixture<ParticipationSubmissionComponent>;
    let participationService: ParticipationService;
    let submissionService: SubmissionService;
    let textAssessmentService: TextAssessmentService;
    let fileUploadAssessmentService: FileUploadAssessmentService;
    let programmingAssessmentService: ProgrammingAssessmentManualResultService;
    let modelingAssessmentService: ModelingAssessmentService;
    let exerciseService: ExerciseService;
    let programmingExerciseService: ProgrammingExerciseService;
    let findAllSubmissionsOfParticipationStub: ReturnType<typeof vi.spyOn>;

    function createSubmissionFixture(submissionId: number) {
        const result1 = { id: 44 } as Result;
        const result2 = { id: 45 } as Result;
        const participation = { id: 66 } as Participation;
        const submission = { id: submissionId, results: [result1, result2], participation } as Submission;
        return { result1, result2, participation, submission };
    }

    const programmingExercise1 = { id: 100, type: ExerciseType.PROGRAMMING } as Exercise;
    const modelingExercise = { id: 100, type: ExerciseType.MODELING } as Exercise;
    const fileUploadExercise = { id: 100, type: ExerciseType.FILE_UPLOAD } as Exercise;
    const textExercise = { id: 100, type: ExerciseType.TEXT } as Exercise;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: { params: of({ participationId: 1, exerciseId: 42 }) } },
                MockProvider(ParticipationService),
                MockProvider(ProgrammingSubmissionService),
                MockProvider(ProfileService, { isProfileActive: () => true }),
                MockProvider(DialogService),
                { provide: AccountService, useClass: MockAccountService },
                MockProvider(SubmissionService),
                MockProvider(ExerciseService),
                MockProvider(ProgrammingExerciseService),
                MockProvider(TextAssessmentService),
                MockProvider(FileUploadAssessmentService),
                MockProvider(ProgrammingAssessmentManualResultService),
                MockProvider(ModelingAssessmentService),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ParticipationSubmissionComponent);
                comp = fixture.componentInstance;
                participationService = TestBed.inject(ParticipationService);
                submissionService = TestBed.inject(SubmissionService);
                textAssessmentService = TestBed.inject(TextAssessmentService);
                modelingAssessmentService = TestBed.inject(ModelingAssessmentService);
                programmingAssessmentService = TestBed.inject(ProgrammingAssessmentManualResultService);
                fileUploadAssessmentService = TestBed.inject(FileUploadAssessmentService);
                exerciseService = TestBed.inject(ExerciseService);
                programmingExerciseService = TestBed.inject(ProgrammingExerciseService);
                findAllSubmissionsOfParticipationStub = vi.spyOn(submissionService, 'findAllSubmissionsOfParticipation');
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('live programming builds', () => {
        let states: BehaviorSubject<ProgrammingSubmissionStateObj>;
        let pendingSubmission: ProgrammingSubmission;
        let completedSubmission: ProgrammingSubmission;
        let buildService: ProgrammingSubmissionService;

        beforeEach(() => {
            pendingSubmission = { id: 2, submissionDate: dayjs(), commitHash: 'pending-commit', results: [] };
            completedSubmission = { id: 1, results: [{ id: 11, score: 50, rated: true }] };
            states = new BehaviorSubject<ProgrammingSubmissionStateObj>({ participationId: 1, submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION });
            buildService = TestBed.inject(ProgrammingSubmissionService);
            vi.spyOn(buildService, 'getLatestPendingSubmissionByParticipationId').mockReturnValue(states);
            vi.spyOn(buildService, 'fetchQueueReleaseDateEstimationByParticipationId').mockReturnValue(of(dayjs().add(30, 'seconds')));
            vi.spyOn(participationService, 'find').mockReturnValue(of(new HttpResponse({ body: { id: 1, type: ParticipationType.PROGRAMMING } })));
            vi.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: programmingExercise1 })));
            vi.spyOn(participationService, 'getBuildJobIdsForResultsOfParticipation').mockReturnValue(of({}));
            findAllSubmissionsOfParticipationStub.mockReturnValue(of(new HttpResponse({ body: [pendingSubmission, completedSubmission] })));
        });

        function progress() {
            return fixture.debugElement.queryAll(By.directive(ResultProgressBarComponent));
        }

        it('shows queued and running progress only in the matching submission row', () => {
            const estimatedCompletionDate = dayjs().add(1, 'minute');
            const buildStartDate = dayjs();
            states.next({ participationId: 1, submissionState: ProgrammingSubmissionState.IS_QUEUED, submission: pendingSubmission });
            fixture.detectChanges();
            fixture.detectChanges();

            expect(buildService.getLatestPendingSubmissionByParticipationId).toHaveBeenCalledWith(1, 100, false);
            expect(progress()).toHaveLength(1);
            expect(progress()[0].componentInstance.isQueued()).toBe(true);
            const results = fixture.debugElement.queryAll(By.directive(ResultComponent)).map((element) => element.componentInstance as ResultComponent);
            expect(results.find((result) => result.result()?.id === 11)?.isBuilding()).toBe(false);

            states.next({
                participationId: 1,
                submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION,
                submission: pendingSubmission,
                buildTimingInfo: { buildStartDate, estimatedCompletionDate },
            });
            fixture.detectChanges();

            expect(progress()).toHaveLength(1);
            expect(progress()[0].componentInstance.isBuilding()).toBe(true);
            expect(progress()[0].componentInstance.isQueued()).toBe(false);
            const runningResult = fixture.debugElement
                .queryAll(By.directive(ResultComponent))
                .map((element) => element.componentInstance as ResultComponent)
                .find((result) => result.isBuilding());
            expect(runningResult?.buildStartDate()).toEqual(buildStartDate);
            expect(runningResult?.estimatedCompletionDate()).toEqual(estimatedCompletionDate);
        });

        it('adds a newly submitted build to the table without removing existing results', () => {
            fixture.detectChanges();
            const newSubmission = { id: 3, commitHash: 'new-commit', submissionDate: dayjs() };
            states.next({ participationId: 1, submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: newSubmission });
            fixture.detectChanges();
            fixture.detectChanges();

            expect(comp.displayedSubmissions().map((submission) => submission.id)).toEqual([3, 2, 1]);
            expect(progress()).toHaveLength(1);
            expect(fixture.nativeElement.textContent).toContain('new-commit');
            states.next({ participationId: 1, submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: newSubmission });
            expect(comp.displayedSubmissions()).toHaveLength(3);
        });

        it('refreshes results and build log links when the active build finishes', () => {
            states.next({ participationId: 1, submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: pendingSubmission });
            fixture.detectChanges();
            fixture.detectChanges();
            const newResult = { id: 22, score: 100, rated: true };
            findAllSubmissionsOfParticipationStub.mockReturnValue(of(new HttpResponse({ body: [{ ...pendingSubmission, results: [newResult] }, completedSubmission] })));
            vi.spyOn(participationService, 'getBuildJobIdsForResultsOfParticipation').mockReturnValue(of({ '22': 'build-job-22' }));

            states.next({ participationId: 1, submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION });
            fixture.detectChanges();
            fixture.detectChanges();

            expect(progress()).toHaveLength(0);
            expect(comp.submissions()[0].results?.[0]).toMatchObject({ id: 22, buildJobId: 'build-job-22' });
            expect(findAllSubmissionsOfParticipationStub).toHaveBeenCalledTimes(2);
        });

        it('stops visualizing failed builds and releases subscriptions on destroy', () => {
            states.next({ participationId: 1, submissionState: ProgrammingSubmissionState.IS_QUEUED, submission: pendingSubmission });
            fixture.detectChanges();
            fixture.detectChanges();
            expect(states.observed).toBe(true);

            states.next({ participationId: 1, submissionState: ProgrammingSubmissionState.HAS_FAILED_SUBMISSION, submission: pendingSubmission });
            fixture.detectChanges();
            expect(progress()).toHaveLength(0);
            const unsubscribe = vi.spyOn(buildService, 'unsubscribeForLatestSubmissionOfParticipation');
            fixture.destroy();
            expect(states.observed).toBe(false);
            expect(unsubscribe).toHaveBeenCalledWith(1);
        });

        it('refreshes a late result after the build was reported as failed', () => {
            states.next({ participationId: 1, submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: pendingSubmission });
            fixture.detectChanges();
            states.next({ participationId: 1, submissionState: ProgrammingSubmissionState.HAS_FAILED_SUBMISSION, submission: pendingSubmission });
            fixture.detectChanges();
            findAllSubmissionsOfParticipationStub.mockReturnValue(of(new HttpResponse({ body: [{ ...pendingSubmission, results: [{ id: 22 }] }] })));
            states.next({ participationId: 1, submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION });
            fixture.detectChanges();

            expect(comp.submissions()[0].results?.[0]?.id).toBe(22);
        });

        it('keeps a new active submission visible when a reload returns an older snapshot', () => {
            fixture.detectChanges();
            const reload = new Subject<HttpResponse<Submission[]>>();
            findAllSubmissionsOfParticipationStub.mockReturnValue(reload);
            comp.setupPage();
            const newSubmission = { id: 3, commitHash: 'new-commit', submissionDate: dayjs() };
            states.next({ participationId: 1, submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: newSubmission });
            reload.next(new HttpResponse({ body: [pendingSubmission, completedSubmission] }));
            fixture.detectChanges();
            fixture.detectChanges();

            expect(progress()).toHaveLength(1);
            expect(fixture.nativeElement.textContent).toContain('new-commit');
            expect(buildService.getLatestPendingSubmissionByParticipationId).toHaveBeenCalledOnce();
        });

        it('ignores a delayed queue estimate after processing starts', () => {
            const queueEstimate = new Subject<dayjs.Dayjs>();
            vi.spyOn(buildService, 'fetchQueueReleaseDateEstimationByParticipationId').mockReturnValue(queueEstimate);
            states.next({ participationId: 1, submissionState: ProgrammingSubmissionState.IS_QUEUED, submission: pendingSubmission });
            fixture.detectChanges();
            fixture.detectChanges();
            expect(progress()[0].componentInstance.isQueued()).toBe(true);
            states.next({ participationId: 1, submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: pendingSubmission });
            queueEstimate.next(dayjs().add(30, 'seconds'));
            fixture.detectChanges();

            expect(progress()[0].componentInstance.isBuilding()).toBe(true);
            expect(progress()[0].componentInstance.isQueued()).toBe(false);
            expect(queueEstimate.observed).toBe(false);
        });

        it('preserves previous results while rebuilding the same submission', () => {
            pendingSubmission.results = [{ id: 12, score: 75, rated: true }];
            states.next({ participationId: 1, submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: pendingSubmission });
            fixture.detectChanges();
            fixture.detectChanges();

            expect(progress()).toHaveLength(1);
            const results = fixture.debugElement.queryAll(By.directive(ResultComponent)).map((element) => element.componentInstance as ResultComponent);
            expect(results.find((result) => result.result()?.id === 12)?.isBuilding()).toBe(false);
        });

        it('shows a running indicator without a progress bar when LocalCI is disabled', () => {
            vi.spyOn(TestBed.inject(ProfileService), 'isProfileActive').mockReturnValue(false);
            fixture.destroy();
            fixture = TestBed.createComponent(ParticipationSubmissionComponent);
            comp = fixture.componentInstance;
            states.next({ participationId: 1, submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: pendingSubmission });
            fixture.detectChanges();
            fixture.detectChanges();

            expect(progress()).toHaveLength(0);
            expect(fixture.nativeElement.querySelector('#test-building')).not.toBeNull();
        });

        it.each([ParticipationType.TEMPLATE, ParticipationType.SOLUTION])('shows live progress for %s participations', (type) => {
            const route = TestBed.inject(ActivatedRoute);
            route.queryParams = of({ isTmpOrSolutionProgrParticipation: 'true' });
            const participation = { id: 1, type, submissions: [pendingSubmission] };
            const exercise = {
                id: 100,
                type: ExerciseType.PROGRAMMING,
                templateParticipation: type === ParticipationType.TEMPLATE ? participation : undefined,
                solutionParticipation: type === ParticipationType.SOLUTION ? participation : undefined,
            } as ProgrammingExercise;
            vi.spyOn(programmingExerciseService, 'findWithTemplateAndSolutionParticipation').mockReturnValue(of(new HttpResponse({ body: exercise })));
            states.next({ participationId: 1, submissionState: ProgrammingSubmissionState.IS_QUEUED, submission: pendingSubmission });
            fixture.detectChanges();
            fixture.detectChanges();

            expect(progress()).toHaveLength(1);
            expect(progress()[0].componentInstance.isQueued()).toBe(true);
            expect(buildService.getLatestPendingSubmissionByParticipationId).toHaveBeenCalledWith(1, 100, false);
        });

        it('keeps build progress visible if refreshing the participation fails', () => {
            states.next({ participationId: 1, submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: pendingSubmission });
            fixture.detectChanges();
            vi.spyOn(participationService, 'find').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
            comp.setupPage();
            fixture.detectChanges();
            fixture.detectChanges();

            expect(comp.isLoading()).toBe(false);
            expect(progress()).toHaveLength(1);
            expect(progress()[0].componentInstance.isBuilding()).toBe(true);
        });

        it('keeps the build subscription when a submission modification reloads the page', () => {
            fixture.detectChanges();
            TestBed.inject(EventManager).broadcast('submissionsModification');
            fixture.detectChanges();

            expect(findAllSubmissionsOfParticipationStub).toHaveBeenCalledTimes(2);
            expect(buildService.getLatestPendingSubmissionByParticipationId).toHaveBeenCalledOnce();
        });

        it('does not subscribe to build state for non-programming exercises', () => {
            vi.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: textExercise })));
            fixture.detectChanges();
            expect(buildService.getLatestPendingSubmissionByParticipationId).not.toHaveBeenCalled();
            expect(progress()).toHaveLength(0);
        });
    });

    it('Submissions are correctly loaded from server', () => {
        const participation = new StudentParticipation();
        participation.id = 1;
        vi.spyOn(participationService, 'find').mockReturnValue(of(new HttpResponse({ body: participation })));
        const submissions = [
            {
                submissionExerciseType: SubmissionExerciseType.TEXT,
                id: 2278,
                submitted: true,
                type: SubmissionType.MANUAL,
                submissionDate: dayjs('2019-07-09T10:47:33.244Z'),
                text: 'My TextSubmission',
                participation,
            },
        ] as TextSubmission[];
        const exercise = { type: ExerciseType.TEXT } as Exercise;
        exercise.isAtLeastInstructor = true;
        vi.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: exercise })));
        findAllSubmissionsOfParticipationStub.mockReturnValue(of({ body: submissions }));
        vi.spyOn(participationService, 'getBuildJobIdsForResultsOfParticipation').mockReturnValue(of({ '4': '2' }));

        comp.ngOnInit();

        expect(comp.isLoading()).toBe(false);
        expect(findAllSubmissionsOfParticipationStub).toHaveBeenCalledOnce();
        expect(comp.participation()).toEqual(participation);
        expect(comp.submissions()).toEqual(submissions);
        expect(comp.participation()?.submissions).toEqual(submissions);
    });

    it('Template Submission is correctly loaded', () => {
        const route = TestBed.inject(ActivatedRoute);
        route.params = of({ participationId: 2, exerciseId: 42 });
        route.queryParams = of({ isTmpOrSolutionProgrParticipation: 'true' });

        const templateParticipation = new TemplateProgrammingExerciseParticipation();
        templateParticipation.id = 2;
        templateParticipation.submissions = [
            {
                submissionExerciseType: SubmissionExerciseType.PROGRAMMING,
                id: 3,
                submitted: true,
                type: SubmissionType.MANUAL,
                submissionDate: dayjs('2019-07-09T10:47:33.244Z'),
                commitHash: '123456789',
                participation: templateParticipation,
            },
        ] as ProgrammingSubmission[];
        const programmingExercise = { type: ExerciseType.PROGRAMMING, projectKey: 'SUBMISSION1', templateParticipation } as ProgrammingExercise;
        const findWithTemplateAndSolutionParticipationStub = vi
            .spyOn(programmingExerciseService, 'findWithTemplateAndSolutionParticipation')
            .mockReturnValue(of(new HttpResponse({ body: programmingExercise })));
        vi.spyOn(participationService, 'getBuildJobIdsForResultsOfParticipation').mockReturnValue(of({ '4': '2' }));

        comp.ngOnInit();

        expect(comp.isLoading()).toBe(false);
        expect(findWithTemplateAndSolutionParticipationStub).toHaveBeenCalledOnce();
        expect(comp.exercise()).toEqual(programmingExercise);
        expect(comp.participation()).toEqual(templateParticipation);
        expect(comp.submissions()).toEqual(templateParticipation.submissions);
    });

    it('Solution Submission is correctly loaded', () => {
        const route = TestBed.inject(ActivatedRoute);
        route.params = of({ participationId: 3, exerciseId: 42 });
        route.queryParams = of({ isTmpOrSolutionProgrParticipation: 'true' });

        const solutionParticipation = new SolutionProgrammingExerciseParticipation();
        solutionParticipation.id = 3;
        solutionParticipation.submissions = [
            {
                submissionExerciseType: SubmissionExerciseType.PROGRAMMING,
                id: 4,
                submitted: true,
                type: SubmissionType.MANUAL,
                submissionDate: dayjs('2019-07-09T10:47:33.244Z'),
                commitHash: '123456789',
                participation: solutionParticipation,
            },
        ] as ProgrammingSubmission[];
        const programmingExercise = { type: ExerciseType.PROGRAMMING, projectKey: 'SUBMISSION1', solutionParticipation } as ProgrammingExercise;
        const findWithTemplateAndSolutionParticipationStub = vi
            .spyOn(programmingExerciseService, 'findWithTemplateAndSolutionParticipation')
            .mockReturnValue(of(new HttpResponse({ body: programmingExercise })));
        vi.spyOn(participationService, 'getBuildJobIdsForResultsOfParticipation').mockReturnValue(of({ '4': '2' }));

        comp.ngOnInit();

        expect(comp.isLoading()).toBe(false);
        expect(findWithTemplateAndSolutionParticipationStub).toHaveBeenCalledOnce();
        expect(comp.participation()).toEqual(solutionParticipation);
        expect(comp.submissions()).toEqual(solutionParticipation.submissions);
    });

    describe('should delete', () => {
        beforeEach(() => {
            vi.spyOn(fileUploadAssessmentService, 'deleteAssessment').mockReturnValue(of(void 0));
            vi.spyOn(textAssessmentService, 'deleteAssessment').mockReturnValue(of(void 0));
            vi.spyOn(modelingAssessmentService, 'deleteAssessment').mockReturnValue(of(void 0));
            vi.spyOn(programmingAssessmentService, 'deleteAssessment').mockReturnValue(of(void 0));
            vi.spyOn(participationService, 'getBuildJobIdsForResultsOfParticipation').mockReturnValue(of({ '4': '2' }));
        });

        it('should delete result of fileUploadSubmission', () => {
            const { submission, participation } = createSubmissionFixture(77);
            findAllSubmissionsOfParticipationStub.mockReturnValue(of({ body: [submission] }));
            vi.spyOn(participationService, 'find').mockReturnValue(of(new HttpResponse({ body: participation })));
            vi.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: fileUploadExercise })));
            comp.ngOnInit();
            comp.deleteResult(comp.submissions()[0], comp.submissions()[0].results![1]);
            expect(comp.submissions()).toHaveLength(1);
            expect(comp.submissions()[0].results).toHaveLength(1);
            expect(comp.submissions()[0].results![0].id).toBe(44);
        });

        it('should delete result of modelingSubmission', () => {
            const { submission, participation } = createSubmissionFixture(77);
            findAllSubmissionsOfParticipationStub.mockReturnValue(of({ body: [submission] }));
            vi.spyOn(participationService, 'find').mockReturnValue(of(new HttpResponse({ body: participation })));
            vi.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: modelingExercise })));
            comp.ngOnInit();
            comp.deleteResult(comp.submissions()[0], comp.submissions()[0].results![1]);
            expect(comp.submissions()).toHaveLength(1);
            expect(comp.submissions()[0].results).toHaveLength(1);
            expect(comp.submissions()[0].results![0].id).toBe(44);
        });

        it('should delete result of programmingSubmission', () => {
            const { submission, participation } = createSubmissionFixture(77);
            findAllSubmissionsOfParticipationStub.mockReturnValue(of({ body: [submission] }));
            vi.spyOn(participationService, 'find').mockReturnValue(of(new HttpResponse({ body: participation })));
            vi.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: programmingExercise1 })));
            comp.ngOnInit();
            comp.deleteResult(comp.submissions()[0], comp.submissions()[0].results![1]);
            expect(comp.submissions()).toHaveLength(1);
            expect(comp.submissions()[0].results).toHaveLength(1);
            expect(comp.submissions()[0].results![0].id).toBe(44);
        });

        it('should delete result of textSubmission', () => {
            const { submission, participation } = createSubmissionFixture(77);
            findAllSubmissionsOfParticipationStub.mockReturnValue(of({ body: [submission] }));
            vi.spyOn(participationService, 'find').mockReturnValue(of(new HttpResponse({ body: participation })));
            vi.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: textExercise })));
            comp.ngOnInit();
            expect(findAllSubmissionsOfParticipationStub).toHaveBeenCalledOnce();
            comp.deleteResult(comp.submissions()[0], comp.submissions()[0].results![1]);
            expect(comp.submissions()).toHaveLength(1);
            expect(comp.submissions()[0].results).toHaveLength(1);
            expect(comp.submissions()[0].results![0].id).toBe(44);
        });
    });

    describe('should handle failed delete', () => {
        beforeEach(() => {
            const error = { message: '400 error', error: { message: 'error.hasComplaint' } } as HttpErrorResponse;
            vi.spyOn(fileUploadAssessmentService, 'deleteAssessment').mockReturnValue(throwError(() => error));
            vi.spyOn(programmingAssessmentService, 'deleteAssessment').mockReturnValue(throwError(() => error));
            vi.spyOn(modelingAssessmentService, 'deleteAssessment').mockReturnValue(throwError(() => error));
            vi.spyOn(textAssessmentService, 'deleteAssessment').mockReturnValue(throwError(() => error));
            vi.spyOn(participationService, 'getBuildJobIdsForResultsOfParticipation').mockReturnValue(of({ '4': '2' }));
        });

        it('should not delete result of fileUploadSubmission because of server error', () => {
            const { submission, participation } = createSubmissionFixture(78);
            findAllSubmissionsOfParticipationStub.mockReturnValue(of({ body: [submission] }));
            vi.spyOn(participationService, 'find').mockReturnValue(of(new HttpResponse({ body: participation })));
            const error2 = { message: '403 error', error: { message: 'error.badAuthentication' } } as HttpErrorResponse;
            vi.spyOn(fileUploadAssessmentService, 'deleteAssessment').mockReturnValue(throwError(() => error2));
            vi.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: fileUploadExercise })));
            comp.ngOnInit();
            comp.deleteResult(comp.submissions()[0], comp.submissions()[0].results![1]);
            expect(comp.submissions()).toHaveLength(1);
            expect(comp.submissions()[0].results).toHaveLength(2);
            expect(comp.submissions()[0].results![0].id).toBe(44);
        });

        it('should not delete result of fileUploadSubmission', () => {
            const { submission, participation } = createSubmissionFixture(78);
            findAllSubmissionsOfParticipationStub.mockReturnValue(of({ body: [submission] }));
            vi.spyOn(participationService, 'find').mockReturnValue(of(new HttpResponse({ body: participation })));
            vi.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: fileUploadExercise })));
            comp.ngOnInit();
            comp.deleteResult(comp.submissions()[0], comp.submissions()[0].results![1]);
            expect(comp.submissions()).toHaveLength(1);
            expect(comp.submissions()[0].results).toHaveLength(2);
            expect(comp.submissions()[0].results![0].id).toBe(44);
        });

        it('should not delete result of modelingSubmission', () => {
            const { submission, participation } = createSubmissionFixture(78);
            findAllSubmissionsOfParticipationStub.mockReturnValue(of({ body: [submission] }));
            vi.spyOn(participationService, 'find').mockReturnValue(of(new HttpResponse({ body: participation })));
            vi.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: modelingExercise })));
            comp.ngOnInit();
            comp.deleteResult(comp.submissions()[0], comp.submissions()[0].results![1]);
            expect(comp.submissions()).toHaveLength(1);
            expect(comp.submissions()[0].results).toHaveLength(2);
            expect(comp.submissions()[0].results![0].id).toBe(44);
        });

        it('should not delete result of programmingSubmission', () => {
            const { submission, participation } = createSubmissionFixture(78);
            findAllSubmissionsOfParticipationStub.mockReturnValue(of({ body: [submission] }));
            vi.spyOn(participationService, 'find').mockReturnValue(of(new HttpResponse({ body: participation })));
            vi.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: programmingExercise1 })));
            comp.ngOnInit();
            comp.deleteResult(comp.submissions()[0], comp.submissions()[0].results![1]);
            expect(comp.submissions()).toHaveLength(1);
            expect(comp.submissions()[0].results).toHaveLength(2);
            expect(comp.submissions()[0].results![0].id).toBe(44);
        });

        it('should not delete result of textSubmission', () => {
            const { submission, participation } = createSubmissionFixture(78);
            findAllSubmissionsOfParticipationStub.mockReturnValue(of({ body: [submission] }));
            vi.spyOn(participationService, 'find').mockReturnValue(of(new HttpResponse({ body: participation })));
            vi.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: textExercise })));
            comp.ngOnInit();
            expect(findAllSubmissionsOfParticipationStub).toHaveBeenCalledOnce();
            comp.deleteResult(comp.submissions()[0], comp.submissions()[0].results![1]);
            expect(comp.submissions()).toHaveLength(1);
            expect(comp.submissions()[0].results).toHaveLength(2);
            expect(comp.submissions()[0].results![0].id).toBe(44);
        });
    });
});
