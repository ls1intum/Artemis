import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Provider } from '@angular/core';
import { type Mock, type Mocked, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { Subject, of, throwError } from 'rxjs';
import { CodeEditorInstructorAndEditorContainerComponent } from 'app/programming/manage/code-editor/instructor-and-editor-container/code-editor-instructor-and-editor-container.component';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { HyperionWebsocketService } from 'app/hyperion/services/hyperion-websocket.service';
import { CodeEditorRepositoryFileService, CodeEditorRepositoryService } from 'app/programming/shared/code-editor/services/code-editor-repository.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { ActivatedRoute, Router } from '@angular/router';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { MockProgrammingExerciseService } from 'test/helpers/mocks/service/mock-programming-exercise.service';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { MockCourseExerciseService } from 'test/helpers/mocks/service/mock-course-exercise.service';
import { DomainService } from 'app/programming/shared/code-editor/services/code-editor-domain.service';
import { Location } from '@angular/common';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { MockParticipationService } from 'test/helpers/mocks/service/mock-participation.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ArtemisIntelligenceService } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence.service';
import { ConsistencyCheckService } from 'app/programming/manage/consistency-check/consistency-check.service';
import { ConsistencyCheckResponse } from 'app/openapi/model/consistencyCheckResponse';
import { ProblemStatementRefinementResponse } from 'app/openapi/model/problemStatementRefinementResponse';
import { ConsistencyCheckError, ErrorType } from 'app/programming/shared/entities/consistency-check-result.model';
import { HyperionCodeGenerationApiService } from 'app/openapi/api/hyperionCodeGenerationApi.service';
import { HyperionProblemStatementApiService } from 'app/openapi/api/hyperionProblemStatementApi.service';
import { ConsistencyIssue } from 'app/openapi/model/consistencyIssue';
import { ArtifactLocation } from 'app/openapi/model/artifactLocation';
import { faCircleExclamation, faCircleInfo, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseEditableInstructionComponent } from 'app/programming/manage/instructions-editor/programming-exercise-editable-instruction.component';

/**
 * Creates a typed mock ProgrammingExercise for testing.
 * @param overrides Partial properties to override the default values
 */
function createMockExercise(overrides: Partial<ProgrammingExercise> = {}): ProgrammingExercise {
    const mockCourse = new Course();
    mockCourse.id = 1;

    const exercise = new ProgrammingExercise(mockCourse, undefined);
    exercise.id = 42;
    exercise.problemStatement = 'Test problem statement';

    return Object.assign(exercise, overrides);
}

/**
 * Creates base providers shared across all test suites.
 * @param additionalProviders Optional providers to include with the base set
 */
function getBaseProviders(additionalProviders: Provider[] = []): Provider[] {
    return [
        { provide: AlertService, useClass: MockAlertService },
        { provide: ProfileService, useClass: MockProfileService },
        { provide: Router, useClass: MockRouter },
        { provide: ProgrammingExerciseService, useClass: MockProgrammingExerciseService },
        { provide: CourseExerciseService, useClass: MockCourseExerciseService },
        { provide: DomainService, useValue: { setDomain: vi.fn() } },
        { provide: Location, useValue: { replaceState: vi.fn() } },
        { provide: ParticipationService, useClass: MockParticipationService },
        { provide: ActivatedRoute, useValue: { params: of({}) } },
        { provide: HyperionCodeGenerationApiService, useValue: { generateCode: vi.fn() } },
        { provide: NgbModal, useValue: { open: vi.fn(() => ({ componentInstance: {}, result: Promise.resolve() })) } },
        { provide: HyperionWebsocketService, useValue: { subscribeToJob: vi.fn(), unsubscribeFromJob: vi.fn() } },
        { provide: CodeEditorRepositoryService, useValue: { pull: vi.fn(() => of(void 0)) } },
        { provide: CodeEditorRepositoryFileService, useValue: { getRepositoryContent: vi.fn(() => of({})) } },
        { provide: TranslateService, useClass: MockTranslateService },
        { provide: ConsistencyCheckService, useValue: { checkConsistencyForProgrammingExercise: vi.fn() } },
        { provide: ArtemisIntelligenceService, useValue: { consistencyCheck: vi.fn(), isLoading: () => false } },
        ...additionalProviders,
    ];
}

/**
 * Configures TestBed with the standard component setup.
 * @param additionalProviders Optional providers to include with the base set
 */
async function configureTestBed(additionalProviders: Provider[] = []): Promise<void> {
    await TestBed.configureTestingModule({
        imports: [CodeEditorInstructorAndEditorContainerComponent],
        providers: getBaseProviders(additionalProviders),
    })
        .overrideComponent(CodeEditorInstructorAndEditorContainerComponent, {
            set: { template: '', imports: [] },
        })
        .compileComponents();
}

describe('CodeEditorInstructorAndEditorContainerComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<CodeEditorInstructorAndEditorContainerComponent>;
    let comp: CodeEditorInstructorAndEditorContainerComponent;

    let codeGenerationApi: Mocked<Pick<HyperionCodeGenerationApiService, 'generateCode'>>;
    let ws: Mocked<Pick<HyperionWebsocketService, 'subscribeToJob' | 'unsubscribeFromJob'>>;
    let alertService: AlertService;
    let repoService: CodeEditorRepositoryService;
    let profileService: ProfileService;
    let artemisIntelligenceService: ArtemisIntelligenceService;
    let consistencyCheckService: ConsistencyCheckService;

    const mockIssues: ConsistencyIssue[] = [
        {
            severity: ConsistencyIssue.SeverityEnum.High,
            category: ConsistencyIssue.CategoryEnum.ConstructorParameterMismatch,
            description: 'Problem statement inconsistency',
            suggestedFix: 'Review the problem statement file.',
            relatedLocations: [
                {
                    type: ArtifactLocation.TypeEnum.ProblemStatement,
                    filePath: 'problem_statement.md',
                    startLine: 1,
                    endLine: 42,
                },
            ],
        },
        {
            severity: ConsistencyIssue.SeverityEnum.Medium,
            category: ConsistencyIssue.CategoryEnum.MethodParameterMismatch,
            description: 'Template repository issue',
            suggestedFix: 'Fix template repository references.',
            relatedLocations: [
                {
                    type: ArtifactLocation.TypeEnum.TemplateRepository,
                    filePath: 'src/template/Example.java',
                    startLine: 5,
                    endLine: 50,
                },
            ],
        },
        {
            severity: ConsistencyIssue.SeverityEnum.Medium,
            category: ConsistencyIssue.CategoryEnum.AttributeTypeMismatch,
            description: 'Solution repository issue',
            suggestedFix: 'Fix solution repository references.',
            relatedLocations: [
                {
                    type: ArtifactLocation.TypeEnum.SolutionRepository,
                    filePath: 'src/solution/Solution.java',
                    startLine: 3,
                    endLine: 60,
                },
            ],
        },
        {
            severity: ConsistencyIssue.SeverityEnum.Low,
            category: ConsistencyIssue.CategoryEnum.IdentifierNamingInconsistency,
            description: 'Tests repository issue',
            suggestedFix: 'Adjust tests in test repository.',
            relatedLocations: [
                {
                    type: ArtifactLocation.TypeEnum.TestsRepository,
                    filePath: 'src/tests/ExampleTest.java',
                    startLine: 10,
                    endLine: 70,
                },
            ],
        },
        {
            // A multi-location issue for testing next/previous navigation
            severity: ConsistencyIssue.SeverityEnum.High,
            category: ConsistencyIssue.CategoryEnum.VisibilityMismatch,
            description: 'Multi-location navigation test issue',
            suggestedFix: 'Resolve inconsistencies across artifacts.',
            relatedLocations: [
                {
                    type: ArtifactLocation.TypeEnum.TestsRepository,
                    filePath: 'src/template/A.java',
                    startLine: 10,
                    endLine: 20,
                },
                {
                    type: ArtifactLocation.TypeEnum.TestsRepository,
                    filePath: 'src/template/B.java',
                    startLine: 30,
                    endLine: 40,
                },
                {
                    type: ArtifactLocation.TypeEnum.SolutionRepository,
                    filePath: 'src/template/C.java',
                    startLine: 50,
                    endLine: 60,
                },
            ],
        },
    ];

    beforeEach(async () => {
        await configureTestBed();

        alertService = TestBed.inject(AlertService);
        codeGenerationApi = TestBed.inject(HyperionCodeGenerationApiService) as unknown as Mocked<Pick<HyperionCodeGenerationApiService, 'generateCode'>>;
        ws = TestBed.inject(HyperionWebsocketService) as unknown as Mocked<Pick<HyperionWebsocketService, 'subscribeToJob' | 'unsubscribeFromJob'>>;
        profileService = TestBed.inject(ProfileService);
        repoService = TestBed.inject(CodeEditorRepositoryService);
        artemisIntelligenceService = TestBed.inject(ArtemisIntelligenceService);
        consistencyCheckService = TestBed.inject(ConsistencyCheckService);

        // Enable Hyperion by default so property initialization is deterministic
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);

        fixture = TestBed.createComponent(CodeEditorInstructorAndEditorContainerComponent);
        comp = fixture.componentInstance;

        // Minimal exercise setup used by generateCode
        comp.exercise = createMockExercise();

        // Mock codeEditorContainer and editableInstructions
        (comp as any).codeEditorContainer = {
            actions: { executeRefresh: vi.fn() },
            selectedFile: undefined as string | undefined,
            selectedRepository: vi.fn().mockReturnValue('SOLUTION'),
            problemStatementIdentifier: 'problem_statement.md',
            jumpToLine: vi.fn(),
        };

        (comp as any).editableInstructions = {
            jumpToLine: vi.fn(),
        };

        // Mock jump helper methods
        comp.selectTemplateParticipation = vi.fn().mockResolvedValue(undefined);
        comp.selectSolutionParticipation = vi.fn().mockResolvedValue(undefined);
        comp.selectTestRepository = vi.fn().mockResolvedValue(undefined);
    });

    afterEach(() => {
        fixture?.destroy();
        vi.clearAllMocks();
    });

    describe('Code Generation', () => {
        it('should not generate when no exercise id', async () => {
            comp.exercise = undefined as any;
            comp.selectedRepository = RepositoryType.TEMPLATE;

            comp.generateCode();
            await Promise.resolve();
            expect(codeGenerationApi.generateCode).not.toHaveBeenCalled();
        });

        it('should not generate when a generation is already running', async () => {
            comp.selectedRepository = RepositoryType.SOLUTION;
            comp.isGeneratingCode.set(true);

            comp.generateCode();
            await Promise.resolve();
            expect(codeGenerationApi.generateCode).not.toHaveBeenCalled();
        });

        it('should warn on unsupported repository types', () => {
            const addAlertSpy = vi.spyOn(alertService, 'addAlert');
            comp.selectedRepository = RepositoryType.ASSIGNMENT; // unsupported

            comp.generateCode();

            expect(codeGenerationApi.generateCode).not.toHaveBeenCalled();
            expect(addAlertSpy).toHaveBeenCalledWith(
                expect.objectContaining({ type: AlertType.WARNING, translationKey: 'artemisApp.programmingExercise.codeGeneration.unsupportedRepository' }),
            );
        });

        it('should call API, subscribe, and show success alert on DONE success', async () => {
            const addAlertSpy = vi.spyOn(alertService, 'addAlert');
            comp.selectedRepository = RepositoryType.TEMPLATE;

            (codeGenerationApi.generateCode as Mock).mockReturnValue(of({ jobId: 'job-1' }));
            const job$ = new Subject<any>();
            (ws.subscribeToJob as Mock).mockReturnValue(job$.asObservable());

            comp.generateCode();
            await Promise.resolve(); // resolve modal

            expect(codeGenerationApi.generateCode).toHaveBeenCalledWith(42, { repositoryType: RepositoryType.TEMPLATE });

            // Emit DONE success event
            job$.next({ type: 'DONE', success: true });

            expect(comp.isGeneratingCode()).toBeFalse();
            expect(addAlertSpy).toHaveBeenCalledWith(
                expect.objectContaining({
                    type: AlertType.SUCCESS,
                    translationKey: 'artemisApp.programmingExercise.codeGeneration.success',
                    translationParams: { repositoryType: RepositoryType.TEMPLATE },
                }),
            );
        });

        it('should call API, subscribe, and show partial success alert on DONE failure', async () => {
            const addAlertSpy = vi.spyOn(alertService, 'addAlert');
            comp.selectedRepository = RepositoryType.SOLUTION;

            (codeGenerationApi.generateCode as Mock).mockReturnValue(of({ jobId: 'job-2' }));
            const job$ = new Subject<any>();
            (ws.subscribeToJob as Mock).mockReturnValue(job$.asObservable());

            comp.generateCode();
            await Promise.resolve();

            expect(codeGenerationApi.generateCode).toHaveBeenCalledWith(42, { repositoryType: RepositoryType.SOLUTION });

            job$.next({ type: 'DONE', success: false });

            expect(comp.isGeneratingCode()).toBeFalse();
            expect(addAlertSpy).toHaveBeenCalledWith(
                expect.objectContaining({
                    type: AlertType.WARNING,
                    translationKey: 'artemisApp.programmingExercise.codeGeneration.partialSuccess',
                    translationParams: { repositoryType: RepositoryType.SOLUTION },
                }),
            );
        });

        it('should show error alert on API error', async () => {
            const addAlertSpy = vi.spyOn(alertService, 'addAlert');
            comp.selectedRepository = RepositoryType.TESTS;

            (codeGenerationApi.generateCode as Mock).mockReturnValue(throwError(() => new Error('fail')) as any);

            comp.generateCode();
            await Promise.resolve();

            expect(codeGenerationApi.generateCode).toHaveBeenCalledWith(42, { repositoryType: RepositoryType.TESTS });
            expect(comp.isGeneratingCode()).toBeFalse();
            expect(addAlertSpy).toHaveBeenCalledWith(
                expect.objectContaining({
                    type: AlertType.DANGER,
                    translationKey: 'artemisApp.programmingExercise.codeGeneration.error',
                }),
            );
        });

        it('should compute hyperionEnabled from profile service', async () => {
            // Already spied in beforeEach, but we can re-verify or override if needed
            // The component reads this property on initialization (property initializer).
            // Since beforeEach recreates the component, it should be true.
            expect(comp.hyperionEnabled).toBeTrue();
        });

        it('should compute hyperionEnabled as false when feature disabled', () => {
            vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(false);

            // Recreate the component so the property initializer runs with the new spy value
            fixture.destroy();
            fixture = TestBed.createComponent(CodeEditorInstructorAndEditorContainerComponent);
            comp = fixture.componentInstance;

            expect(comp.hyperionEnabled).toBeFalse();
        });

        it('should trigger repository pull on FILE_UPDATED and NEW_FILE events', async () => {
            comp.selectedRepository = RepositoryType.TEMPLATE;
            (codeGenerationApi.generateCode as Mock).mockReturnValue(of({ jobId: 'job-3' }));

            const job$ = new Subject<any>();
            (ws.subscribeToJob as Mock).mockReturnValue(job$.asObservable());
            const pullSpy = vi.spyOn(repoService, 'pull');

            comp.generateCode();
            await Promise.resolve();

            job$.next({ type: 'FILE_UPDATED' });
            job$.next({ type: 'NEW_FILE' });

            expect(pullSpy).toHaveBeenCalledTimes(2);
        });

        it('should call executeRefresh and cleanup on DONE', async () => {
            comp.selectedRepository = RepositoryType.SOLUTION;
            (codeGenerationApi.generateCode as Mock).mockReturnValue(of({ jobId: 'job-4' }));
            const job$ = new Subject<any>();
            (ws.subscribeToJob as Mock).mockReturnValue(job$.asObservable());

            const executeRefresh = (comp as any).codeEditorContainer.actions.executeRefresh;

            comp.generateCode();
            await Promise.resolve();

            job$.next({ type: 'DONE', success: true });
            await Promise.resolve();

            expect(executeRefresh).toHaveBeenCalled();
            expect(comp.isGeneratingCode()).toBeFalse();
            expect(ws.unsubscribeFromJob).toHaveBeenCalledWith('job-4');
        });

        it('should show danger alert and cleanup on ERROR event', async () => {
            const addAlertSpy = vi.spyOn(alertService, 'addAlert');
            comp.selectedRepository = RepositoryType.TEMPLATE;
            (codeGenerationApi.generateCode as Mock).mockReturnValue(of({ jobId: 'job-5' }));
            const job$ = new Subject<any>();
            (ws.subscribeToJob as Mock).mockReturnValue(job$.asObservable());

            comp.generateCode();
            await Promise.resolve();

            job$.next({ type: 'ERROR' });
            await Promise.resolve();

            expect(comp.isGeneratingCode()).toBeFalse();
            expect(ws.unsubscribeFromJob).toHaveBeenCalledWith('job-5');
            expect(addAlertSpy).toHaveBeenCalledWith(expect.objectContaining({ type: AlertType.DANGER, translationKey: 'artemisApp.programmingExercise.codeGeneration.error' }));
        });

        it('should show danger alert and cleanup when job stream errors', async () => {
            const addAlertSpy = vi.spyOn(alertService, 'addAlert');
            comp.selectedRepository = RepositoryType.TESTS;
            (codeGenerationApi.generateCode as Mock).mockReturnValue(of({ jobId: 'job-6' }));
            (ws.subscribeToJob as Mock).mockReturnValue(throwError(() => new Error('ws')));

            comp.generateCode();
            await Promise.resolve();

            expect(comp.isGeneratingCode()).toBeFalse();
            expect(ws.unsubscribeFromJob).toHaveBeenCalledWith('job-6');
            expect(addAlertSpy).toHaveBeenCalledWith(expect.objectContaining({ type: AlertType.DANGER, translationKey: 'artemisApp.programmingExercise.codeGeneration.error' }));
        });

        it('should show danger alert when response has no job id', async () => {
            const addAlertSpy = vi.spyOn(alertService, 'addAlert');
            comp.selectedRepository = RepositoryType.TEMPLATE;
            (codeGenerationApi.generateCode as Mock).mockReturnValue(of({}));

            comp.generateCode();
            await Promise.resolve();

            expect(comp.isGeneratingCode()).toBeFalse();
            expect(addAlertSpy).toHaveBeenCalledWith(expect.objectContaining({ type: AlertType.DANGER, translationKey: 'artemisApp.programmingExercise.codeGeneration.error' }));
        });

        it('should show timeout warning and cleanup when generation exceeds time limit', async () => {
            const addAlertSpy = vi.spyOn(alertService, 'addAlert');
            comp.selectedRepository = RepositoryType.SOLUTION;
            (codeGenerationApi.generateCode as Mock).mockReturnValue(of({ jobId: 'job-7' }));

            // Intercept setTimeout to capture the scheduled callback and invoke it immediately
            const originalSetTimeout = window.setTimeout;
            let timeoutCallback: (() => void) | undefined;
            // @ts-ignore
            window.setTimeout = ((fn: () => void, _delay?: number) => {
                timeoutCallback = fn;
                return 1 as any;
            }) as any;

            try {
                (ws.subscribeToJob as Mock).mockReturnValue(new Subject<any>().asObservable());
                comp.generateCode();
                await Promise.resolve();
                expect(comp.isGeneratingCode()).toBeTrue();

                // Simulate timeout
                if (timeoutCallback) {
                    timeoutCallback();
                }

                expect(comp.isGeneratingCode()).toBeFalse();
                expect(ws.unsubscribeFromJob).toHaveBeenCalledWith('job-7');
                expect(addAlertSpy).toHaveBeenCalledWith(
                    expect.objectContaining({ type: AlertType.WARNING, translationKey: 'artemisApp.programmingExercise.codeGeneration.timeout' }),
                );
            } finally {
                window.setTimeout = originalSetTimeout;
            }
        });
    });

    describe('Consistency Checks', () => {
        const error1 = new ConsistencyCheckError();
        error1.programmingExercise = { id: 42 } as any;
        error1.type = ErrorType.TEMPLATE_BUILD_PLAN_MISSING;

        it('runs full consistency check and shows success when no issues', () => {
            const check1Spy = vi.spyOn(consistencyCheckService, 'checkConsistencyForProgrammingExercise').mockReturnValue(of([]));
            const check2Spy = vi.spyOn(artemisIntelligenceService, 'consistencyCheck').mockReturnValue(of({ issues: [] } as ConsistencyCheckResponse));
            const successSpy = vi.spyOn(alertService, 'success');

            comp.checkConsistencies(comp.exercise!);
            expect(consistencyCheckService.checkConsistencyForProgrammingExercise).toHaveBeenCalledWith(42);
            expect(artemisIntelligenceService.consistencyCheck).toHaveBeenCalledWith(42);

            expect(check1Spy).toHaveBeenCalledOnce();
            expect(check2Spy).toHaveBeenCalledOnce();
            expect(successSpy).toHaveBeenCalledOnce();
        });

        it('error when first consistency check fails', () => {
            const check1Spy = vi.spyOn(consistencyCheckService, 'checkConsistencyForProgrammingExercise').mockReturnValue(of([error1]));
            const check2Spy = vi.spyOn(artemisIntelligenceService, 'consistencyCheck').mockReturnValue(of({ issues: [] } as ConsistencyCheckResponse));
            const failSpy = vi.spyOn(alertService, 'error');

            comp.checkConsistencies(comp.exercise!);
            expect(consistencyCheckService.checkConsistencyForProgrammingExercise).toHaveBeenCalledWith(42);

            expect(check1Spy).toHaveBeenCalledOnce();
            expect(check2Spy).not.toHaveBeenCalled();
            expect(failSpy).toHaveBeenCalledOnce();
        });

        it('error when exercise id undefined', () => {
            const check1Spy = vi.spyOn(consistencyCheckService, 'checkConsistencyForProgrammingExercise').mockReturnValue(of([error1]));
            const check2Spy = vi.spyOn(artemisIntelligenceService, 'consistencyCheck').mockReturnValue(of({ issues: [] } as ConsistencyCheckResponse));
            const failSpy = vi.spyOn(alertService, 'error');

            comp.checkConsistencies({ id: undefined } as any);

            expect(check1Spy).not.toHaveBeenCalled();
            expect(check2Spy).not.toHaveBeenCalled();
            expect(failSpy).toHaveBeenCalledOnce();
        });

        it('check isLoading propagates correctly', () => {
            (artemisIntelligenceService as any).isLoading = () => true;
            expect(comp.isCheckingConsistency()).toBeTrue();

            (artemisIntelligenceService as any).isLoading = () => false;
            expect(comp.isCheckingConsistency()).toBeFalse();
        });

        it('returns right icon', () => {
            expect(comp.getSeverityIcon(ConsistencyIssue.SeverityEnum.High)).toBe(faCircleExclamation);
            expect(comp.getSeverityIcon(ConsistencyIssue.SeverityEnum.Medium)).toBe(faTriangleExclamation);
            expect(comp.getSeverityIcon(ConsistencyIssue.SeverityEnum.Low)).toBe(faCircleInfo);
            expect(comp.getSeverityIcon(undefined as any)).toBe(faCircleInfo);
        });

        it('returns right color', () => {
            expect(comp.getSeverityColor(ConsistencyIssue.SeverityEnum.High)).toBe('text-danger');
            expect(comp.getSeverityColor(ConsistencyIssue.SeverityEnum.Medium)).toBe('text-warning');
            expect(comp.getSeverityColor(ConsistencyIssue.SeverityEnum.Low)).toBe('text-info');
            expect(comp.getSeverityColor(undefined as any)).toBe('text-secondary');
        });

        it('should toggle toolbar and select first issue if none selected', () => {
            comp.consistencyIssues.set(mockIssues);
            expect(comp.showConsistencyIssuesToolbar()).toBeFalse();

            comp.toggleConsistencyIssuesToolbar();
            expect(comp.showConsistencyIssuesToolbar()).toBeTrue();
            expect(comp.selectedIssue).toBe(mockIssues[0]);

            const sorted = comp.sortedIssues();
            expect(comp.selectedIssue).toBe(sorted[0]);
            expect(comp.locationIndex).toBe(0);
        });

        it('should navigate global next', () => {
            comp.consistencyIssues.set(mockIssues);
            const sorted = comp.sortedIssues();

            // Start at first issue
            comp.selectedIssue = sorted[0];
            comp.locationIndex = 0;

            const jumpSpy = vi.spyOn(comp as any, 'jumpToLocation').mockImplementation(() => {});

            // Next step
            comp.navigateGlobal(1);

            // Should go to sorted[1] (Issue 4), locIndex 0
            expect(comp.selectedIssue).toBe(sorted[1]);
            expect(comp.locationIndex).toBe(0);
            expect(jumpSpy).toHaveBeenCalledWith(sorted[1], 0);

            // Next step (Issue 4 has 3 locations)
            comp.navigateGlobal(1);
            expect(comp.selectedIssue).toBe(sorted[1]);
            expect(comp.locationIndex).toBe(1);

            comp.navigateGlobal(1);
            expect(comp.selectedIssue).toBe(sorted[1]);
            expect(comp.locationIndex).toBe(2);

            comp.navigateGlobal(1);
            // Should go to sorted[2] (Issue 1), locIndex 0
            expect(comp.selectedIssue).toBe(sorted[2]);
            expect(comp.locationIndex).toBe(0);
        });

        it('should navigate global previous and wrap around', () => {
            comp.consistencyIssues.set(mockIssues);
            const sorted = comp.sortedIssues();

            // Start at first issue
            comp.selectedIssue = sorted[0];
            comp.locationIndex = 0;

            const jumpSpy = vi.spyOn(comp as any, 'jumpToLocation').mockImplementation(() => {});

            // Previous step -> Wrap to last issue, last location
            // Last issue is sorted[4] (Issue 3 Low), 1 location.
            const lastIssue = sorted[sorted.length - 1];
            // Last issue has 1 location.
            const lastLocIndex = lastIssue.relatedLocations.length - 1;

            comp.navigateGlobal(-1);

            expect(comp.selectedIssue).toBe(lastIssue);
            expect(comp.locationIndex).toBe(lastLocIndex);
            expect(jumpSpy).toHaveBeenCalledWith(lastIssue, lastLocIndex);
        });

        it('navigates to PROBLEM_STATEMENT and calls jumpToLine', async () => {
            // Mock issue with ProblemStatement
            const issue = mockIssues[0]; // ProblemStatement issue
            const loc = issue.relatedLocations[0];

            comp.selectedIssue = issue;
            comp.locationIndex = 0;

            const jumpSpy = vi.spyOn((comp as any).editableInstructions, 'jumpToLine');

            (comp as any).jumpToLocation(issue, 0); // Corrected: use (comp as any)
            await Promise.resolve();

            expect((comp as any).codeEditorContainer.selectedFile).toBe('problem_statement.md');
            expect(jumpSpy).toHaveBeenCalledWith(loc.endLine);
        });

        it('onEditorLoaded calls onFileLoad immediately when file is already selected', () => {
            const targetFile = 'src/tests/ExampleTest.java';
            comp.fileToJumpOn = targetFile;
            (comp as any).codeEditorContainer.selectedFile = targetFile;

            const onFileLoadSpy = vi.spyOn(comp, 'onFileLoad');

            comp.onEditorLoaded();

            expect(onFileLoadSpy).toHaveBeenCalledWith(targetFile);
            expect((comp as any).codeEditorContainer.selectedFile).toBe(targetFile);
        });

        it('onEditorLoaded sets selectedFile when file is not selected yet', () => {
            const targetFile = 'src/tests/ExampleTest.java';
            comp.fileToJumpOn = targetFile;
            (comp as any).codeEditorContainer.selectedFile = 'some/other/file.java';

            const onFileLoadSpy = vi.spyOn(comp, 'onFileLoad');

            comp.onEditorLoaded();

            expect(onFileLoadSpy).not.toHaveBeenCalled();
            expect((comp as any).codeEditorContainer.selectedFile).toBe(targetFile);
        });

        it('onFileLoad jumps to line and clears lineJumpOnFileLoad when file matches', () => {
            const targetFile = 'src/solution/Solution.java';
            const targetLine = 60;

            comp.fileToJumpOn = targetFile;
            comp.lineJumpOnFileLoad = targetLine;

            comp.onFileLoad(targetFile);

            expect((comp as any).codeEditorContainer.jumpToLine).toHaveBeenCalledWith(targetLine);
            expect(comp.lineJumpOnFileLoad).toBeUndefined();
        });

        it('onFileLoad does nothing if file does not match fileToJumpOn', () => {
            comp.fileToJumpOn = 'src/solution/Solution.java';
            comp.lineJumpOnFileLoad = 60;

            comp.onFileLoad('src/tests/ExampleTest.java');

            expect((comp as any).codeEditorContainer.jumpToLine).not.toHaveBeenCalled();
            expect(comp.lineJumpOnFileLoad).toBe(60);
        });

        it('onFileLoad does nothing if lineJumpOnFileLoad is undefined', () => {
            const targetFile = 'src/solution/Solution.java';

            comp.fileToJumpOn = targetFile;
            comp.lineJumpOnFileLoad = undefined;

            comp.onFileLoad(targetFile);

            expect((comp as any).codeEditorContainer.jumpToLine).not.toHaveBeenCalled();
            expect(comp.lineJumpOnFileLoad).toBeUndefined();
        });

        it('shows error and clears jump state when repository selection fails', () => {
            const issue = mockIssues[3]; // TESTS_REPOSITORY
            (comp as any).codeEditorContainer.selectedRepository = vi.fn().mockReturnValue('SOLUTION');

            const error = new Error('repo selection failed');
            vi.spyOn(comp, 'selectTestRepository').mockImplementation(() => {
                throw error;
            });

            const alertErrorSpy = vi.spyOn(alertService, 'error');
            const onEditorLoadedSpy = vi.spyOn(comp, 'onEditorLoaded');

            (comp as any).jumpToLocation(issue, 0);

            expect(alertErrorSpy).toHaveBeenCalled();
            expect(comp.lineJumpOnFileLoad).toBeUndefined();
            expect(comp.fileToJumpOn).toBeUndefined();
            expect(onEditorLoadedSpy).not.toHaveBeenCalled();
        });

        it('should reset showConsistencyIssuesToolbar when re-running consistency check', () => {
            comp.consistencyIssues.set(mockIssues);
            (comp as any).showConsistencyIssuesToolbar.set(true);
            comp.selectedIssue = mockIssues[0];

            vi.spyOn(consistencyCheckService, 'checkConsistencyForProgrammingExercise').mockReturnValue(of([]));
            vi.spyOn(artemisIntelligenceService, 'consistencyCheck').mockReturnValue(of({ issues: [] } as ConsistencyCheckResponse));
            vi.spyOn(alertService, 'success');

            comp.checkConsistencies(comp.exercise!);

            expect(comp.showConsistencyIssuesToolbar()).toBeFalse();
            expect(comp.selectedIssue).toBeUndefined();
        });
    });
});

describe('CodeEditorInstructorAndEditorContainerComponent - Diff Editor', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<CodeEditorInstructorAndEditorContainerComponent>;
    let comp: CodeEditorInstructorAndEditorContainerComponent;

    beforeEach(async () => {
        await configureTestBed();

        fixture = TestBed.createComponent(CodeEditorInstructorAndEditorContainerComponent);
        comp = fixture.componentInstance;
        comp.exercise = createMockExercise({ problemStatement: 'Original' });
    });

    afterEach(() => {
        fixture?.destroy();
        vi.clearAllMocks();
    });

    it('should accept refinement and update problem statement', () => {
        // Simulate refinement setting up diff mode
        comp.showDiff.set(true);

        comp.closeDiff();

        expect(comp.showDiff()).toBeFalse();
    });

    it('should revert refinement', () => {
        comp.showDiff.set(true);
        // Mock the internal editableInstructions to have revertAll and getCurrentContent methods
        const mockEditable = {
            revertAll: vi.fn(),
            getCurrentContent: vi.fn().mockReturnValue('Reverted content'),
        };
        comp.editableInstructions = mockEditable as unknown as ProgrammingExerciseEditableInstructionComponent;

        comp.revertAllRefinement();

        expect(mockEditable.revertAll).toHaveBeenCalled();
        expect(comp.showDiff()).toBeFalse();
    });
});

describe('CodeEditorInstructorAndEditorContainerComponent - Problem Statement Refinement', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<CodeEditorInstructorAndEditorContainerComponent>;
    let comp: CodeEditorInstructorAndEditorContainerComponent;
    let alertService: AlertService;
    let hyperionApiService: Mocked<Pick<HyperionProblemStatementApiService, 'refineProblemStatementGlobally' | 'generateProblemStatement'>>;

    beforeEach(async () => {
        await configureTestBed([
            {
                provide: HyperionProblemStatementApiService,
                useValue: { refineProblemStatementGlobally: vi.fn(), generateProblemStatement: vi.fn() },
            },
        ]);

        alertService = TestBed.inject(AlertService);
        hyperionApiService = TestBed.inject(HyperionProblemStatementApiService) as unknown as Mocked<
            Pick<HyperionProblemStatementApiService, 'refineProblemStatementGlobally' | 'generateProblemStatement'>
        >;

        fixture = TestBed.createComponent(CodeEditorInstructorAndEditorContainerComponent);
        comp = fixture.componentInstance;
        comp.exercise = createMockExercise({ problemStatement: 'Original problem statement' });
    });

    afterEach(() => {
        fixture?.destroy();
        vi.clearAllMocks();
    });

    // Full Refinement Tests

    it('should toggle refinement prompt visibility', () => {
        expect(comp.showRefinementPrompt()).toBeFalse();
        comp.toggleRefinementPrompt();
        expect(comp.showRefinementPrompt()).toBeTrue();
        comp.toggleRefinementPrompt();
        expect(comp.showRefinementPrompt()).toBeFalse();
    });

    it('should clear refinement prompt when closing', () => {
        comp.showRefinementPrompt.set(true);
        comp.refinementPrompt.set('Some prompt');
        comp.toggleRefinementPrompt();
        expect(comp.showRefinementPrompt()).toBeFalse();
        expect(comp.refinementPrompt()).toBe('');
    });

    it('should submit full refinement successfully', () => {
        const successSpy = vi.spyOn(alertService, 'success');
        const mockResponse: ProblemStatementRefinementResponse = { refinedProblemStatement: 'Refined content' };
        (hyperionApiService.refineProblemStatementGlobally as Mock).mockReturnValue(of(mockResponse));

        comp.templateLoaded.set(true);
        comp.templateProblemStatement.set('Template');
        comp['currentProblemStatement'].set('Original problem statement');

        comp.refinementPrompt.set('Improve clarity');
        comp.submitRefinement();

        expect(hyperionApiService.refineProblemStatementGlobally).toHaveBeenCalledWith(
            1,
            expect.objectContaining({ problemStatementText: 'Original problem statement', userPrompt: 'Improve clarity' }),
        );
        expect(comp.showDiff()).toBeTrue();
        expect(successSpy).toHaveBeenCalledWith('artemisApp.programmingExercise.problemStatement.refinementSuccess');
    });

    it('should not submit when prompt is empty or whitespace', () => {
        comp.refinementPrompt.set('');
        comp.submitRefinement();
        expect(hyperionApiService.refineProblemStatementGlobally).not.toHaveBeenCalled();
        expect(hyperionApiService.generateProblemStatement).not.toHaveBeenCalled();

        comp.refinementPrompt.set('   ');
        comp.submitRefinement();
        expect(hyperionApiService.refineProblemStatementGlobally).not.toHaveBeenCalled();
        expect(hyperionApiService.generateProblemStatement).not.toHaveBeenCalled();
    });

    it('should not submit when no courseId for full refinement', () => {
        comp.exercise = createMockExercise({ problemStatement: 'Test' });
        comp.exercise.course = undefined;
        comp['currentProblemStatement'].set('Non-empty problem statement');
        comp.refinementPrompt.set('Improve');
        comp.submitRefinement();
        expect(hyperionApiService.refineProblemStatementGlobally).not.toHaveBeenCalled();
        expect(hyperionApiService.generateProblemStatement).not.toHaveBeenCalled();
    });

    it('should handle full refinement API error', () => {
        const errorSpy = vi.spyOn(alertService, 'error');
        (hyperionApiService.refineProblemStatementGlobally as Mock).mockReturnValue(throwError(() => new Error('API error')));

        comp.templateLoaded.set(true);
        comp.templateProblemStatement.set('Template');
        comp['currentProblemStatement'].set('Original problem statement');

        comp.refinementPrompt.set('Improve');
        comp.submitRefinement();

        expect(errorSpy).toHaveBeenCalledWith('artemisApp.programmingExercise.problemStatement.refinementError');
    });
});
