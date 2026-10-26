import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { BehaviorSubject, Subject, of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
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
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TemplateProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/template-programming-exercise-participation.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { SolutionProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/solution-programming-exercise-participation.model';
import { InitializationState, Participation, ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
import { FeatureToggle, FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';

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
    const quizExercise = { id: 100, type: ExerciseType.QUIZ } as Exercise;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: { params: of({ participationId: 1, exerciseId: 42 }) } },
                MockProvider(ParticipationService),
                MockProvider(ProgrammingSubmissionService),
                MockProvider(ParticipationWebsocketService, { subscribeForLatestResultOfParticipation: () => new BehaviorSubject<Result | undefined>(undefined) }),
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

    describe('submission table controls', () => {
        let buildStates: BehaviorSubject<ProgrammingSubmissionStateObj>;
        let buildService: ProgrammingSubmissionService;

        beforeEach(() => {
            vi.spyOn(comp, 'setupPage').mockImplementation(() => {});
            buildStates = new BehaviorSubject<ProgrammingSubmissionStateObj>({
                participationId: 1,
                submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION,
            });
            buildService = TestBed.inject(ProgrammingSubmissionService);
            vi.spyOn(buildService, 'getLatestPendingSubmissionByParticipationId').mockReturnValue(buildStates);
            vi.spyOn(buildService, 'fetchQueueReleaseDateEstimationByParticipationId').mockReturnValue(of(dayjs().add(1, 'minute')));
            const exercise = new ProgrammingExercise(undefined, undefined);
            exercise.id = 100;
            exercise.isAtLeastInstructor = true;
            exercise.dueDate = dayjs().add(1, 'day');
            comp.exercise.set(exercise);
            comp.participation.set({ id: 1, type: ParticipationType.PROGRAMMING, initializationState: InitializationState.INITIALIZED });
            comp.isLoading.set(false);
            TestBed.inject(FeatureToggleService).initializeFeatureToggles([FeatureToggle.ProgrammingExercises]);
        });

        function render() {
            fixture.detectChanges();
            fixture.detectChanges();
        }

        function rowIds(): number[] {
            return Array.from(fixture.nativeElement.querySelectorAll('td[data-column="id"]'), (cell: HTMLTableCellElement) => Number(cell.textContent?.trim()));
        }

        function sortBy(headerKey: string) {
            const header = fixture.debugElement.queryAll(By.css('th')).find((element) => element.nativeElement.textContent.includes(headerKey));
            expect(header).toBeDefined();
            header!.nativeElement.click();
            render();
        }

        it('shows newest submissions first and toggles submission-date ordering without changing the loaded snapshot', () => {
            const submissions: Submission[] = [
                { id: 30, submissionDate: dayjs('2026-01-02') },
                { id: 10, submissionDate: dayjs('2026-01-03') },
                { id: 20, submissionDate: dayjs('2026-01-01') },
            ];
            comp.submissions.set(submissions);
            render();

            expect(rowIds()).toEqual([10, 30, 20]);
            expect(submissions.map((submission) => submission.id)).toEqual([30, 10, 20]);

            sortBy('artemisApp.participation.participationSubmission.submissionDate');
            expect(rowIds()).toEqual([20, 30, 10]);
            sortBy('artemisApp.participation.participationSubmission.submissionDate');
            expect(rowIds()).toEqual([10, 30, 20]);
        });

        it('preserves submission-date ordering when a live submission contains an ISO date string', () => {
            comp.submissions.set([
                { id: 1, submissionDate: dayjs('2026-01-01') },
                { id: 3, submissionDate: dayjs('2026-01-03') },
            ]);
            render();
            sortBy('artemisApp.participation.participationSubmission.submissionDate');
            expect(rowIds()).toEqual([1, 3]);

            buildStates.next(
                JSON.parse(`{
                    "participationId": 1,
                    "submissionState": "${ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION}",
                    "submission": {"id": 2, "submissionDate": "2026-01-02T00:00:00Z"}
                }`),
            );
            render();
            expect(rowIds()).toEqual([1, 2, 3]);
            sortBy('artemisApp.participation.participationSubmission.submissionDate');
            expect(rowIds()).toEqual([3, 2, 1]);
        });

        it('sorts by the latest completed result and preserves that order when a live submission arrives', () => {
            comp.submissions.set([
                { id: 1, submissionDate: dayjs('2026-01-01'), results: [{ completionDate: dayjs('2026-01-04') }, { completionDate: dayjs('2026-01-02') }] },
                { id: 2, submissionDate: dayjs('2026-01-03'), results: [{ completionDate: dayjs('2026-01-03') }] },
                { id: 3, submissionDate: dayjs('2026-01-02') },
            ]);
            render();

            sortBy('artemisApp.exercise.completionDate');
            expect(rowIds()).toEqual([3, 2, 1]);
            sortBy('artemisApp.exercise.completionDate');
            expect(rowIds()).toEqual([1, 2, 3]);

            buildStates.next({
                participationId: 1,
                submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION,
                submission: { id: 4, submissionDate: dayjs('2026-01-05') },
            });
            render();
            expect(rowIds()).toEqual([1, 2, 4, 3]);
        });

        it('sorts completion dates received as ISO strings through the submissions API conversion', () => {
            const submissions: Submission[] = JSON.parse(`[
                {"id": 1, "submissionDate": "2026-01-03T00:00:00Z", "results": [{"completionDate": "2026-01-04T00:00:00Z"}]},
                {"id": 2, "submissionDate": "2026-01-02T00:00:00Z", "results": [{"completionDate": "2026-01-05T00:00:00Z"}]}
            ]`);
            const realSubmissionService = TestBed.runInInjectionContext(() => new SubmissionService());
            realSubmissionService.convertSubmissionArrayDatesFromServer(submissions);
            comp.submissions.set(submissions);
            render();

            expect(rowIds()).toEqual([1, 2]);
            sortBy('artemisApp.exercise.completionDate');
            expect(rowIds()).toEqual([1, 2]);
            sortBy('artemisApp.exercise.completionDate');
            expect(rowIds()).toEqual([2, 1]);
        });

        it.each([false, true])('shows submission IDs only for admins (isAdmin=%s)', (isAdmin) => {
            vi.spyOn(TestBed.inject(AccountService), 'isAdmin').mockReturnValue(isAdmin);
            comp.submissions.set([{ id: 7 }]);
            render();

            expect(rowIds()).toEqual(isAdmin ? [7] : []);
            const headers = fixture.debugElement.queryAll(By.css('th')).map((header) => header.nativeElement.textContent.trim());
            expect(headers.includes('Id')).toBe(isAdmin);
        });

        it.each([ProgrammingSubmissionState.IS_QUEUED, ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION])(
            'triggers an instructor build from the page toolbar and disables it in state %s',
            (submissionState) => {
                const trigger = vi.spyOn(buildService, 'triggerBuild').mockReturnValue(of({}));
                render();
                const button: HTMLButtonElement | null = fixture.nativeElement.querySelector('[data-testid="trigger-build"] button');
                expect(button).not.toBeNull();
                button!.click();
                expect(trigger).toHaveBeenCalledExactlyOnceWith(1, SubmissionType.INSTRUCTOR);

                buildStates.next({ participationId: 1, submissionState, submission: { id: 8 } });
                render();
                expect(button!.disabled).toBe(true);
            },
        );

        it('does not offer a build action to tutors or for non-programming exercises', () => {
            const tutorExercise = new ProgrammingExercise(undefined, undefined);
            tutorExercise.id = 100;
            tutorExercise.isAtLeastInstructor = false;
            comp.exercise.set(tutorExercise);
            render();
            expect(fixture.nativeElement.querySelector('[data-testid="trigger-build"]')).toBeNull();

            const textExercise = new TextExercise(undefined, undefined);
            textExercise.id = 100;
            textExercise.isAtLeastInstructor = true;
            comp.exercise.set(textExercise);
            render();
            expect(fixture.nativeElement.querySelector('[data-testid="trigger-build"]')).toBeNull();
        });

        it('releases shared build state after both the toolbar and table unsubscribe', () => {
            render();
            const observedAtRelease: boolean[] = [];
            vi.spyOn(buildService, 'unsubscribeForLatestSubmissionOfParticipation').mockImplementation(() => observedAtRelease.push(buildStates.observed));

            fixture.destroy();

            expect(buildStates.observed).toBe(false);
            expect(observedAtRelease.at(-1)).toBe(false);
        });
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
            vi.spyOn(exerciseService, 'findForStaff').mockReturnValue(of(new HttpResponse({ body: programmingExercise1 })));
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

        it.each([ProgrammingSubmissionState.IS_QUEUED, ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION])(
            'retains a new %s submission after failure and reconciles its late result without duplicate rows',
            (initialState) => {
                fixture.detectChanges();
                const liveSubmission: ProgrammingSubmission = { id: 3, commitHash: 'failed-live', submissionDate: dayjs(), results: [] };
                states.next({ participationId: 1, submissionState: initialState, submission: liveSubmission });
                fixture.detectChanges();
                fixture.detectChanges();
                expect(comp.displayedSubmissions().map((submission) => submission.id)).toEqual([3, 2, 1]);
                expect(progress()).toHaveLength(1);

                states.next({ participationId: 1, submissionState: ProgrammingSubmissionState.HAS_FAILED_SUBMISSION, submission: liveSubmission });
                fixture.detectChanges();

                expect(comp.displayedSubmissions().map((submission) => submission.id)).toEqual([3, 2, 1]);
                expect(fixture.nativeElement.textContent).toContain('failed-live');
                expect(progress()).toHaveLength(0);
                expect(comp.activeBuild()).toBeUndefined();
                expect(comp.submissions().map((submission) => submission.id)).toEqual([2, 1]);
                expect(findAllSubmissionsOfParticipationStub).toHaveBeenCalledOnce();
                expect(fixture.debugElement.queryAll(By.directive(ResultComponent)).some((element) => element.componentInstance.result()?.id === 11)).toBe(true);

                states.next({ participationId: 1, submissionState: ProgrammingSubmissionState.HAS_FAILED_SUBMISSION, submission: liveSubmission });
                fixture.detectChanges();
                expect(comp.displayedSubmissions().map((submission) => submission.id)).toEqual([3, 2, 1]);
                findAllSubmissionsOfParticipationStub.mockReturnValue(
                    of(new HttpResponse({ body: [{ ...liveSubmission, results: [{ id: 33, score: 100, rated: true }] }, pendingSubmission, completedSubmission] })),
                );
                vi.mocked(participationService.getBuildJobIdsForResultsOfParticipation).mockReturnValue(of({ '33': 'late-build-33' }));
                states.next({ participationId: 1, submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION });
                fixture.detectChanges();
                fixture.detectChanges();

                expect(comp.displayedSubmissions().map((submission) => submission.id)).toEqual([3, 2, 1]);
                expect(comp.submissions()[0].results?.[0]).toMatchObject({ id: 33, buildJobId: 'late-build-33' });
                expect(findAllSubmissionsOfParticipationStub).toHaveBeenCalledTimes(2);
                expect(progress()).toHaveLength(0);
                expect(fixture.nativeElement.textContent).toContain('failed-live');
            },
        );

        it('keeps a successfully deleted failed live submission absent after reloads and repeated build events', () => {
            fixture.detectChanges();
            const liveSubmission: ProgrammingSubmission = { id: 3, commitHash: 'del-live', submissionDate: dayjs(), results: [] };
            states.next({ participationId: 1, submissionState: ProgrammingSubmissionState.HAS_FAILED_SUBMISSION, submission: liveSubmission });
            fixture.detectChanges();
            expect(fixture.nativeElement.textContent).toContain('del-live');
            const deletion = new Subject<HttpResponse<void>>();
            const deleteRequest = vi.spyOn(submissionService, 'delete').mockReturnValue(deletion);
            const reload = new Subject<HttpResponse<Submission[]>>();
            findAllSubmissionsOfParticipationStub.mockReturnValue(reload);

            comp.deleteSubmission(3);
            fixture.detectChanges();
            expect(fixture.nativeElement.textContent).toContain('del-live');
            deletion.next(new HttpResponse<void>());
            expect(findAllSubmissionsOfParticipationStub).toHaveBeenCalledTimes(2);
            reload.next(new HttpResponse({ body: [pendingSubmission, completedSubmission] }));
            fixture.detectChanges();
            fixture.detectChanges();

            expect(deleteRequest).toHaveBeenCalledExactlyOnceWith(3);
            expect(comp.displayedSubmissions().map((submission) => submission.id)).toEqual([2, 1]);
            expect(fixture.nativeElement.textContent).not.toContain('del-live');
            expect(fixture.nativeElement.querySelectorAll('tbody tr')).toHaveLength(2);
            states.next({ participationId: 1, submissionState: ProgrammingSubmissionState.HAS_FAILED_SUBMISSION, submission: liveSubmission });
            // An older in-flight snapshot must not undo the confirmed deletion either.
            reload.next(new HttpResponse({ body: [liveSubmission, pendingSubmission, completedSubmission] }));
            fixture.detectChanges();
            expect(comp.displayedSubmissions().map((submission) => submission.id)).toEqual([2, 1]);
            expect(fixture.nativeElement.textContent).not.toContain('del-live');
            expect(fixture.nativeElement.querySelectorAll('tbody tr')).toHaveLength(2);
            expect(progress()).toHaveLength(0);
            deletion.complete();
            reload.complete();
        });

        it('retains a failed live submission when its deletion fails', () => {
            fixture.detectChanges();
            const liveSubmission: ProgrammingSubmission = { id: 3, commitHash: 'not-deleted', submissionDate: dayjs() };
            states.next({ participationId: 1, submissionState: ProgrammingSubmissionState.HAS_FAILED_SUBMISSION, submission: liveSubmission });
            fixture.detectChanges();
            vi.spyOn(submissionService, 'delete').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403, statusText: 'Forbidden' })));
            const errors: string[] = [];
            const errorSubscription = comp.dialogError$.subscribe((error) => errors.push(error));

            comp.deleteSubmission(3);
            fixture.detectChanges();

            expect(comp.displayedSubmissions().map((submission) => submission.id)).toEqual([3, 2, 1]);
            expect(fixture.nativeElement.textContent).toContain('not-deleted');
            expect(fixture.nativeElement.querySelectorAll('tbody tr')).toHaveLength(3);
            expect(errors).toHaveLength(1);
            expect(errors[0]).toContain('403');
            expect(findAllSubmissionsOfParticipationStub).toHaveBeenCalledOnce();
            errorSubscription.unsubscribe();
        });

        it('retains both consecutive live submissions after failures and reconciles their loaded results by ID', () => {
            fixture.detectChanges();
            const first: ProgrammingSubmission = { id: 3, commitHash: 'first-live', submissionDate: dayjs(), results: [] };
            const second: ProgrammingSubmission = { id: 4, commitHash: 'second-live', submissionDate: dayjs().add(1, 'second'), results: [] };
            states.next({ participationId: 1, submissionState: ProgrammingSubmissionState.IS_QUEUED, submission: first });
            states.next({ participationId: 1, submissionState: ProgrammingSubmissionState.HAS_FAILED_SUBMISSION, submission: first });
            states.next({ participationId: 1, submissionState: ProgrammingSubmissionState.IS_QUEUED, submission: second });
            fixture.detectChanges();
            fixture.detectChanges();

            expect(comp.displayedSubmissions().map((submission) => submission.id)).toEqual([4, 3, 2, 1]);
            expect(fixture.nativeElement.textContent).toContain('first-live');
            expect(fixture.nativeElement.textContent).toContain('second-live');
            expect(fixture.nativeElement.querySelectorAll('tbody tr')).toHaveLength(4);
            expect(progress()).toHaveLength(1);
            expect(comp.activeBuild()?.submission?.id).toBe(4);
            states.next({ participationId: 1, submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: second });
            states.next({ participationId: 1, submissionState: ProgrammingSubmissionState.HAS_FAILED_SUBMISSION, submission: second });
            states.next({ participationId: 1, submissionState: ProgrammingSubmissionState.HAS_FAILED_SUBMISSION, submission: second });
            fixture.detectChanges();
            expect(comp.displayedSubmissions().map((submission) => submission.id)).toEqual([4, 3, 2, 1]);
            expect(fixture.nativeElement.querySelectorAll('tbody tr')).toHaveLength(4);
            expect(progress()).toHaveLength(0);
            expect(findAllSubmissionsOfParticipationStub).toHaveBeenCalledOnce();
            expect(comp.submissions().map((submission) => submission.id)).toEqual([2, 1]);
            expect(fixture.debugElement.queryAll(By.directive(ResultComponent)).some((element) => element.componentInstance.result()?.id === 11)).toBe(true);

            findAllSubmissionsOfParticipationStub.mockReturnValue(
                of(
                    new HttpResponse({
                        body: [
                            { ...second, results: [{ id: 44, score: 100, rated: true }] },
                            { ...first, results: [{ id: 33, score: 50, rated: true }] },
                            pendingSubmission,
                            completedSubmission,
                        ],
                    }),
                ),
            );
            vi.mocked(participationService.getBuildJobIdsForResultsOfParticipation).mockReturnValue(of({ '33': 'first-job', '44': 'second-job' }));
            states.next({ participationId: 1, submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION });
            fixture.detectChanges();
            fixture.detectChanges();
            expect(comp.displayedSubmissions().map((submission) => submission.id)).toEqual([4, 3, 2, 1]);
            expect(fixture.nativeElement.querySelectorAll('tbody tr')).toHaveLength(4);
            expect(comp.displayedSubmissions()[0].results?.[0]).toMatchObject({ id: 44, buildJobId: 'second-job' });
            expect(comp.displayedSubmissions()[1].results?.[0]).toMatchObject({ id: 33, buildJobId: 'first-job' });
        });

        it('releases retained live submissions when switching to another participation', () => {
            fixture.detectChanges();
            const first: ProgrammingSubmission = { id: 3, commitHash: 'oldpart', submissionDate: dayjs() };
            states.next({ participationId: 1, submissionState: ProgrammingSubmissionState.HAS_FAILED_SUBMISSION, submission: first });
            fixture.detectChanges();
            expect(fixture.nativeElement.textContent).toContain('oldpart');
            const nextStates = new BehaviorSubject<ProgrammingSubmissionStateObj>({ participationId: 2, submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION });
            vi.mocked(buildService.getLatestPendingSubmissionByParticipationId).mockReturnValue(nextStates);
            TestBed.inject(ActivatedRoute).params = of({ participationId: 2, exerciseId: 42 });
            vi.mocked(participationService.find).mockReturnValue(of(new HttpResponse({ body: { id: 2, type: ParticipationType.PROGRAMMING } })));
            findAllSubmissionsOfParticipationStub.mockReturnValue(of(new HttpResponse({ body: [] })));

            comp.setupPage();
            fixture.detectChanges();
            fixture.detectChanges();

            expect(comp.displayedSubmissions()).toEqual([]);
            expect(fixture.nativeElement.textContent).not.toContain('oldpart');
            expect(states.observed).toBe(false);
            expect(nextStates.observed).toBe(true);
            const nextSubmission: ProgrammingSubmission = { id: 5, commitHash: 'newpart', submissionDate: dayjs() };
            nextStates.next({ participationId: 2, submissionState: ProgrammingSubmissionState.HAS_FAILED_SUBMISSION, submission: nextSubmission });
            fixture.detectChanges();
            expect(comp.displayedSubmissions().map((submission) => submission.id)).toEqual([5]);
            expect(fixture.nativeElement.textContent).toContain('newpart');
            expect(fixture.nativeElement.textContent).not.toContain('oldpart');
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
            vi.spyOn(exerciseService, 'findForStaff').mockReturnValue(of(new HttpResponse({ body: textExercise })));
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
        vi.spyOn(exerciseService, 'findForStaff').mockReturnValue(of(new HttpResponse({ body: exercise })));
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
            vi.spyOn(exerciseService, 'findForStaff').mockReturnValue(of(new HttpResponse({ body: fileUploadExercise })));
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
            vi.spyOn(exerciseService, 'findForStaff').mockReturnValue(of(new HttpResponse({ body: modelingExercise })));
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
            vi.spyOn(exerciseService, 'findForStaff').mockReturnValue(of(new HttpResponse({ body: programmingExercise1 })));
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
            vi.spyOn(exerciseService, 'findForStaff').mockReturnValue(of(new HttpResponse({ body: textExercise })));
            comp.ngOnInit();
            expect(findAllSubmissionsOfParticipationStub).toHaveBeenCalledOnce();
            comp.deleteResult(comp.submissions()[0], comp.submissions()[0].results![1]);
            expect(comp.submissions()).toHaveLength(1);
            expect(comp.submissions()[0].results).toHaveLength(1);
            expect(comp.submissions()[0].results![0].id).toBe(44);
        });

        it('should delete result of quizSubmission through the quiz result endpoint and close the dialog', () => {
            const httpMock = TestBed.inject(HttpTestingController);
            const { submission, participation } = createSubmissionFixture(77);
            findAllSubmissionsOfParticipationStub.mockReturnValue(of({ body: [submission] }));
            vi.spyOn(participationService, 'find').mockReturnValue(of(new HttpResponse({ body: participation })));
            vi.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: quizExercise })));
            const dialogErrors: string[] = [];
            comp.dialogError$.subscribe((dialogError) => dialogErrors.push(dialogError));
            comp.ngOnInit();

            comp.deleteResult(comp.submissions()[0], comp.submissions()[0].results![1]);
            httpMock.expectOne({ method: 'DELETE', url: '/api/quiz/participations/1/quiz-submissions/77/results/45' }).flush(null);

            expect(comp.submissions()[0].results!.map((result) => result.id)).toEqual([44]);
            // an empty dialog error closes the delete dialog
            expect(dialogErrors).toEqual(['']);
            httpMock.verify();
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
            vi.spyOn(exerciseService, 'findForStaff').mockReturnValue(of(new HttpResponse({ body: fileUploadExercise })));
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
            vi.spyOn(exerciseService, 'findForStaff').mockReturnValue(of(new HttpResponse({ body: fileUploadExercise })));
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
            vi.spyOn(exerciseService, 'findForStaff').mockReturnValue(of(new HttpResponse({ body: modelingExercise })));
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
            vi.spyOn(exerciseService, 'findForStaff').mockReturnValue(of(new HttpResponse({ body: programmingExercise1 })));
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
            vi.spyOn(exerciseService, 'findForStaff').mockReturnValue(of(new HttpResponse({ body: textExercise })));
            comp.ngOnInit();
            expect(findAllSubmissionsOfParticipationStub).toHaveBeenCalledOnce();
            comp.deleteResult(comp.submissions()[0], comp.submissions()[0].results![1]);
            expect(comp.submissions()).toHaveLength(1);
            expect(comp.submissions()[0].results).toHaveLength(2);
            expect(comp.submissions()[0].results![0].id).toBe(44);
        });
    });
});
