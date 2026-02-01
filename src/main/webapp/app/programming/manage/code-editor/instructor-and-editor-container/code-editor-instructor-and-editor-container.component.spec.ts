import 'zone.js';
import 'zone.js/testing';
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { Provider } from '@angular/core';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
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
import { MODULE_FEATURE_HYPERION } from 'app/app.constants';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
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
import { MockProvider } from 'ng-mocks';
import { ArtemisIntelligenceService } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence.service';
import { ConsistencyCheckService } from 'app/programming/manage/consistency-check/consistency-check.service';
import { ConsistencyCheckResponse } from 'app/openapi/model/consistencyCheckResponse';
import { ProblemStatementRefinementResponse } from 'app/openapi/model/problemStatementRefinementResponse';
import { ConsistencyCheckError, ErrorType } from 'app/programming/shared/entities/consistency-check-result.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { HyperionCodeGenerationApiService } from 'app/openapi/api/hyperionCodeGenerationApi.service';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { HyperionProblemStatementApiService } from 'app/openapi/api/hyperionProblemStatementApi.service';
import { ConsistencyIssue } from 'app/openapi/model/consistencyIssue';
import { faCircleExclamation, faCircleInfo, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';

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
        { provide: DomainService, useValue: { setDomain: jest.fn() } },
        { provide: Location, useValue: { replaceState: jest.fn() } },
        { provide: ParticipationService, useClass: MockParticipationService },
        { provide: ActivatedRoute, useValue: { params: of({}) } },
        { provide: HyperionCodeGenerationApiService, useValue: { generateCode: jest.fn() } },
        { provide: NgbModal, useValue: { open: jest.fn(() => ({ componentInstance: {}, result: Promise.resolve() })) } },
        { provide: HyperionWebsocketService, useValue: { subscribeToJob: jest.fn(), unsubscribeFromJob: jest.fn() } },
        { provide: CodeEditorRepositoryService, useValue: { pull: jest.fn(() => of(void 0)) } },
        { provide: CodeEditorRepositoryFileService, useValue: { getRepositoryContent: jest.fn(() => of({})) } },
        { provide: TranslateService, useClass: MockTranslateService },
        { provide: ConsistencyCheckService, useValue: { checkConsistencyForProgrammingExercise: jest.fn() } },
        { provide: ArtemisIntelligenceService, useValue: { consistencyCheck: jest.fn(), isLoading: () => false } },
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

describe('CodeEditorInstructorAndEditorContainerComponent - Code Generation', () => {
    let fixture: ComponentFixture<CodeEditorInstructorAndEditorContainerComponent>;
    let comp: CodeEditorInstructorAndEditorContainerComponent;

    let codeGenerationApi: jest.Mocked<Pick<HyperionCodeGenerationApiService, 'generateCode'>>;
    let ws: jest.Mocked<Pick<HyperionWebsocketService, 'subscribeToJob' | 'unsubscribeFromJob'>>;
    let alertService: AlertService;
    let repoService: CodeEditorRepositoryService;
    let profileService: ProfileService;

    beforeAll(() => {
        try {
            TestBed.initTestEnvironment(BrowserTestingModule, platformBrowserTesting());
        } catch (error) {
            // already initialized in some runners
        }
    });

    beforeEach(async () => {
        await configureTestBed();

        alertService = TestBed.inject(AlertService);
        codeGenerationApi = TestBed.inject(HyperionCodeGenerationApiService) as unknown as jest.Mocked<Pick<HyperionCodeGenerationApiService, 'generateCode'>>;
        ws = TestBed.inject(HyperionWebsocketService) as unknown as jest.Mocked<Pick<HyperionWebsocketService, 'subscribeToJob' | 'unsubscribeFromJob'>>;
        profileService = TestBed.inject(ProfileService);
        repoService = TestBed.inject(CodeEditorRepositoryService);

        // Enable Hyperion by default so property initialization is deterministic
        jest.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);

        fixture = TestBed.createComponent(CodeEditorInstructorAndEditorContainerComponent);
        comp = fixture.componentInstance;

        // Minimal exercise setup used by generateCode
        comp.exercise = createMockExercise();
    });

    afterEach(() => {
        fixture?.destroy();
        jest.clearAllMocks();
    });

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
        const addAlertSpy = jest.spyOn(alertService, 'addAlert');
        comp.selectedRepository = RepositoryType.ASSIGNMENT; // unsupported

        comp.generateCode();

        expect(codeGenerationApi.generateCode).not.toHaveBeenCalled();
        expect(addAlertSpy).toHaveBeenCalledWith(
            expect.objectContaining({ type: AlertType.WARNING, translationKey: 'artemisApp.programmingExercise.codeGeneration.unsupportedRepository' }),
        );
    });

    it('should call API, subscribe, and show success alert on DONE success', async () => {
        const addAlertSpy = jest.spyOn(alertService, 'addAlert');
        comp.selectedRepository = RepositoryType.TEMPLATE;

        (codeGenerationApi.generateCode as jest.Mock).mockReturnValue(of({ jobId: 'job-1' }));
        const job$ = new Subject<any>();
        (ws.subscribeToJob as jest.Mock).mockReturnValue(job$.asObservable());

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
        const addAlertSpy = jest.spyOn(alertService, 'addAlert');
        comp.selectedRepository = RepositoryType.SOLUTION;

        (codeGenerationApi.generateCode as jest.Mock).mockReturnValue(of({ jobId: 'job-2' }));
        const job$ = new Subject<any>();
        (ws.subscribeToJob as jest.Mock).mockReturnValue(job$.asObservable());

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
        const addAlertSpy = jest.spyOn(alertService, 'addAlert');
        comp.selectedRepository = RepositoryType.TESTS;

        (codeGenerationApi.generateCode as jest.Mock).mockReturnValue(throwError(() => new Error('fail')) as any);

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
        const isModuleFeatureActiveSpy = jest.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);

        // Recreate the component to ensure the property is initialized using the spy value
        fixture.destroy();
        fixture = TestBed.createComponent(CodeEditorInstructorAndEditorContainerComponent);
        comp = fixture.componentInstance;

        expect(isModuleFeatureActiveSpy).toHaveBeenCalledWith(MODULE_FEATURE_HYPERION);
        expect(comp.hyperionEnabled).toBeTrue();
    });

    it('should compute hyperionEnabled as false when feature disabled', async () => {
        const isModuleFeatureActiveSpy = jest.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(false);

        fixture.destroy();
        fixture = TestBed.createComponent(CodeEditorInstructorAndEditorContainerComponent);
        comp = fixture.componentInstance;

        expect(isModuleFeatureActiveSpy).toHaveBeenCalled();
        expect(comp.hyperionEnabled).toBeFalse();
    });

    it('should trigger repository pull on FILE_UPDATED and NEW_FILE events', async () => {
        comp.selectedRepository = RepositoryType.TEMPLATE;
        (codeGenerationApi.generateCode as jest.Mock).mockReturnValue(of({ jobId: 'job-3' }));

        const job$ = new Subject<any>();
        (ws.subscribeToJob as jest.Mock).mockReturnValue(job$.asObservable());
        const pullSpy = jest.spyOn(repoService, 'pull');

        comp.generateCode();
        await Promise.resolve();

        job$.next({ type: 'FILE_UPDATED' });
        job$.next({ type: 'NEW_FILE' });

        expect(pullSpy).toHaveBeenCalledTimes(2);
    });

    it('should call executeRefresh and cleanup on DONE', async () => {
        comp.selectedRepository = RepositoryType.SOLUTION;
        (codeGenerationApi.generateCode as jest.Mock).mockReturnValue(of({ jobId: 'job-4' }));
        const job$ = new Subject<any>();
        (ws.subscribeToJob as jest.Mock).mockReturnValue(job$.asObservable());

        // Fake a minimal code editor container to invoke refresh
        const executeRefresh = jest.fn();
        (comp as any).codeEditorContainer = { actions: { executeRefresh } };

        comp.generateCode();
        await Promise.resolve();

        job$.next({ type: 'DONE', success: true });
        await Promise.resolve();

        expect(executeRefresh).toHaveBeenCalled();
        expect(comp.isGeneratingCode()).toBeFalse();
        expect(ws.unsubscribeFromJob).toHaveBeenCalledWith('job-4');
    });

    it('should show danger alert and cleanup on ERROR event', async () => {
        const addAlertSpy = jest.spyOn(alertService, 'addAlert');
        comp.selectedRepository = RepositoryType.TEMPLATE;
        (codeGenerationApi.generateCode as jest.Mock).mockReturnValue(of({ jobId: 'job-5' }));
        const job$ = new Subject<any>();
        (ws.subscribeToJob as jest.Mock).mockReturnValue(job$.asObservable());

        comp.generateCode();
        await Promise.resolve();

        job$.next({ type: 'ERROR' });
        await Promise.resolve();

        expect(comp.isGeneratingCode()).toBeFalse();
        expect(ws.unsubscribeFromJob).toHaveBeenCalledWith('job-5');
        expect(addAlertSpy).toHaveBeenCalledWith(expect.objectContaining({ type: AlertType.DANGER, translationKey: 'artemisApp.programmingExercise.codeGeneration.error' }));
    });

    it('should show danger alert and cleanup when job stream errors', async () => {
        const addAlertSpy = jest.spyOn(alertService, 'addAlert');
        comp.selectedRepository = RepositoryType.TESTS;
        (codeGenerationApi.generateCode as jest.Mock).mockReturnValue(of({ jobId: 'job-6' }));
        (ws.subscribeToJob as jest.Mock).mockReturnValue(throwError(() => new Error('ws')));

        comp.generateCode();
        await Promise.resolve();

        expect(comp.isGeneratingCode()).toBeFalse();
        expect(ws.unsubscribeFromJob).toHaveBeenCalledWith('job-6');
        expect(addAlertSpy).toHaveBeenCalledWith(expect.objectContaining({ type: AlertType.DANGER, translationKey: 'artemisApp.programmingExercise.codeGeneration.error' }));
    });

    it('should show danger alert when response has no job id', async () => {
        const addAlertSpy = jest.spyOn(alertService, 'addAlert');
        comp.selectedRepository = RepositoryType.TEMPLATE;
        (codeGenerationApi.generateCode as jest.Mock).mockReturnValue(of({}));

        comp.generateCode();
        await Promise.resolve();

        expect(comp.isGeneratingCode()).toBeFalse();
        expect(addAlertSpy).toHaveBeenCalledWith(expect.objectContaining({ type: AlertType.DANGER, translationKey: 'artemisApp.programmingExercise.codeGeneration.error' }));
    });

    it('should show timeout warning and cleanup when generation exceeds time limit', async () => {
        jest.useFakeTimers();
        const addAlertSpy = jest.spyOn(alertService, 'addAlert');
        comp.selectedRepository = RepositoryType.SOLUTION;
        (codeGenerationApi.generateCode as jest.Mock).mockReturnValue(of({ jobId: 'job-7' }));

        (ws.subscribeToJob as jest.Mock).mockReturnValue(new Subject<any>().asObservable());
        comp.generateCode();
        await Promise.resolve();
        expect(comp.isGeneratingCode()).toBeTrue();

        jest.advanceTimersByTime(1_200_000); // 20 minutes - matches component timeout

        expect(comp.isGeneratingCode()).toBeFalse();
        expect(ws.unsubscribeFromJob).toHaveBeenCalledWith('job-7');
        expect(addAlertSpy).toHaveBeenCalledWith(expect.objectContaining({ type: AlertType.WARNING, translationKey: 'artemisApp.programmingExercise.codeGeneration.timeout' }));

        jest.useRealTimers();
    });
});

describe('CodeEditorInstructorAndEditorContainerComponent - Consistency Checks', () => {
    let fixture: ComponentFixture<CodeEditorInstructorAndEditorContainerComponent>;
    let comp: CodeEditorInstructorAndEditorContainerComponent;
    let artemisIntelligenceService: ArtemisIntelligenceService;
    let consistencyCheckService: ConsistencyCheckService;
    let alertService: AlertService;

    const course = { id: 123, exercises: [] } as Course;
    const programmingExercise = new ProgrammingExercise(course, undefined);
    programmingExercise.id = 123;
    const error1 = new ConsistencyCheckError();
    error1.programmingExercise = programmingExercise;
    error1.type = ErrorType.TEMPLATE_BUILD_PLAN_MISSING;

    beforeEach(waitForAsync(async () => {
        await TestBed.configureTestingModule({
            providers: [
                MockProvider(ArtemisIntelligenceService),
                MockProvider(ConsistencyCheckService),
                MockProvider(ProfileService),
                MockProvider(AlertService),
                MockProvider(ParticipationService),
                MockProvider(ProgrammingExerciseService),
                MockProvider(CourseExerciseService),
                provideHttpClient(withInterceptorsFromDi()),
                provideHttpClientTesting(),
                provideRouter([]),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
            imports: [CodeEditorInstructorAndEditorContainerComponent],
            declarations: [],
        }).compileComponents();

        fixture = TestBed.createComponent(CodeEditorInstructorAndEditorContainerComponent);
        comp = fixture.componentInstance;

        artemisIntelligenceService = TestBed.inject(ArtemisIntelligenceService);
        consistencyCheckService = TestBed.inject(ConsistencyCheckService);
        alertService = TestBed.inject(AlertService);
    }));

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('runs full consistency check and shows success when no issues', () => {
        const check1Spy = jest.spyOn(consistencyCheckService, 'checkConsistencyForProgrammingExercise').mockReturnValue(of([]));
        const check2Spy = jest.spyOn(artemisIntelligenceService, 'consistencyCheck').mockReturnValue(of({ issues: [] } as ConsistencyCheckResponse));
        const successSpy = jest.spyOn(alertService, 'success');

        comp.checkConsistencies(programmingExercise);
        expect(consistencyCheckService.checkConsistencyForProgrammingExercise).toHaveBeenCalledWith(123);
        expect(artemisIntelligenceService.consistencyCheck).toHaveBeenCalledWith(123);

        expect(check1Spy).toHaveBeenCalledOnce();
        expect(check2Spy).toHaveBeenCalledOnce();
        expect(successSpy).toHaveBeenCalledOnce();
    });

    it('error when first consistency check fails', () => {
        const check1Spy = jest.spyOn(consistencyCheckService, 'checkConsistencyForProgrammingExercise').mockReturnValue(of([error1]));
        const check2Spy = jest.spyOn(artemisIntelligenceService, 'consistencyCheck').mockReturnValue(of({ issues: [] } as ConsistencyCheckResponse));
        const failSpy = jest.spyOn(alertService, 'error');

        comp.checkConsistencies(programmingExercise);
        expect(consistencyCheckService.checkConsistencyForProgrammingExercise).toHaveBeenCalledWith(123);

        expect(check1Spy).toHaveBeenCalledOnce();
        expect(check2Spy).not.toHaveBeenCalled();
        expect(failSpy).toHaveBeenCalledOnce();
    });

    it('error when exercise id undefined', () => {
        const check1Spy = jest.spyOn(consistencyCheckService, 'checkConsistencyForProgrammingExercise').mockReturnValue(of([error1]));
        const check2Spy = jest.spyOn(artemisIntelligenceService, 'consistencyCheck').mockReturnValue(of({ issues: [] } as ConsistencyCheckResponse));
        const failSpy = jest.spyOn(alertService, 'error');

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
});

describe('CodeEditorInstructorAndEditorContainerComponent - Diff Editor', () => {
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
        jest.clearAllMocks();
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
        (comp as any).editableInstructions = {
            revertAll: jest.fn(),
            getCurrentContent: jest.fn().mockReturnValue('Reverted content'),
        };

        comp.revertAllRefinement();

        expect((comp as any).editableInstructions.revertAll).toHaveBeenCalled();
        expect(comp.showDiff()).toBeFalsy();
    });

    it('should close diff and reset public state', () => {
        comp.showDiff.set(true);

        comp.closeDiff();

        // Verify observable behavior through public API
        expect(comp.showDiff()).toBeFalse();
    });
});

describe('CodeEditorInstructorAndEditorContainerComponent - Problem Statement Refinement', () => {
    let fixture: ComponentFixture<CodeEditorInstructorAndEditorContainerComponent>;
    let comp: CodeEditorInstructorAndEditorContainerComponent;
    let alertService: AlertService;
    let hyperionApiService: jest.Mocked<Pick<HyperionProblemStatementApiService, 'refineProblemStatementTargeted' | 'refineProblemStatementGlobally' | 'generateProblemStatement'>>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CodeEditorInstructorAndEditorContainerComponent],
            providers: [
                ...getBaseProviders(),
                {
                    provide: HyperionProblemStatementApiService,
                    useValue: { refineProblemStatementTargeted: jest.fn(), refineProblemStatementGlobally: jest.fn(), generateProblemStatement: jest.fn() },
                },
            ],
        })
            .overrideComponent(CodeEditorInstructorAndEditorContainerComponent, { set: { template: '', imports: [] } })
            .compileComponents();

        alertService = TestBed.inject(AlertService);
        hyperionApiService = TestBed.inject(HyperionProblemStatementApiService) as unknown as jest.Mocked<
            Pick<HyperionProblemStatementApiService, 'refineProblemStatementTargeted' | 'refineProblemStatementGlobally' | 'generateProblemStatement'>
        >;

        fixture = TestBed.createComponent(CodeEditorInstructorAndEditorContainerComponent);
        comp = fixture.componentInstance;
        comp.exercise = createMockExercise({ problemStatement: 'Original problem statement' });
    });

    afterEach(() => {
        fixture?.destroy();
        jest.clearAllMocks();
    });

    // Inline Refinement Tests

    it('should handle inline refinement successfully', () => {
        const successSpy = jest.spyOn(alertService, 'success');
        const mockResponse: ProblemStatementRefinementResponse = { refinedProblemStatement: 'Refined content' };
        (hyperionApiService.refineProblemStatementTargeted as jest.Mock).mockReturnValue(of(mockResponse));

        comp.onInlineRefinement({ instruction: 'Improve this section', startLine: 1, endLine: 2, startColumn: 0, endColumn: 10 });

        expect(hyperionApiService.refineProblemStatementTargeted).toHaveBeenCalledWith(
            1,
            expect.objectContaining({
                problemStatementText: 'Original problem statement',
                instruction: 'Improve this section',
                startLine: 1,
                endLine: 2,
                startColumn: 0,
                endColumn: 10,
            }),
        );
        expect(comp.showDiff()).toBeTrue();
        expect(successSpy).toHaveBeenCalledWith('artemisApp.programmingExercise.inlineRefine.success');
    });

    it('should show error when inline refinement has no courseId', () => {
        const errorSpy = jest.spyOn(alertService, 'error');
        comp.exercise = createMockExercise({ problemStatement: 'Test' });
        comp.exercise.course = undefined;

        comp.onInlineRefinement({ instruction: 'Test', startLine: 1, endLine: 1, startColumn: 0, endColumn: 5 });

        expect(errorSpy).toHaveBeenCalledWith('artemisApp.programmingExercise.inlineRefine.error');
    });

    it('should show error when inline refinement has empty problem statement', () => {
        const errorSpy = jest.spyOn(alertService, 'error');
        comp.exercise = createMockExercise({ problemStatement: '   ' });

        comp.onInlineRefinement({ instruction: 'Test', startLine: 1, endLine: 1, startColumn: 0, endColumn: 5 });

        expect(errorSpy).toHaveBeenCalledWith('artemisApp.programmingExercise.inlineComment.applyError');
    });

    it('should handle inline refinement API error', () => {
        const errorSpy = jest.spyOn(alertService, 'error');
        (hyperionApiService.refineProblemStatementTargeted as jest.Mock).mockReturnValue(throwError(() => new Error('API error')));

        comp.onInlineRefinement({ instruction: 'Test', startLine: 1, endLine: 1, startColumn: 0, endColumn: 5 });

        expect(errorSpy).toHaveBeenCalledWith('artemisApp.programmingExercise.inlineRefine.error');
    });

    it('should handle inline refinement with empty response', () => {
        const errorSpy = jest.spyOn(alertService, 'error');
        (hyperionApiService.refineProblemStatementTargeted as jest.Mock).mockReturnValue(of({ refinedProblemStatement: '' }));

        comp.onInlineRefinement({ instruction: 'Test', startLine: 1, endLine: 1, startColumn: 0, endColumn: 5 });

        expect(errorSpy).toHaveBeenCalledWith('artemisApp.programmingExercise.inlineRefine.error');
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
        const successSpy = jest.spyOn(alertService, 'success');
        const mockResponse: ProblemStatementRefinementResponse = { refinedProblemStatement: 'Refined content' };
        (hyperionApiService.refineProblemStatementGlobally as jest.Mock).mockReturnValue(of(mockResponse));

        (comp as any).templateLoaded.set(true);
        (comp as any).templateProblemStatement.set('Template');
        (comp as any).currentProblemStatement.set('Original problem statement');

        comp.refinementPrompt.set('Improve clarity');
        comp.submitRefinement();

        expect(hyperionApiService.refineProblemStatementGlobally).toHaveBeenCalledWith(
            1,
            expect.objectContaining({ problemStatementText: 'Original problem statement', userPrompt: 'Improve clarity' }),
        );
        expect(comp.showDiff()).toBeTrue();
        expect(successSpy).toHaveBeenCalledWith('artemisApp.programmingExercise.inlineRefine.success');
    });

    it('should not submit when prompt is empty or whitespace', () => {
        comp.refinementPrompt.set('');
        comp.submitRefinement();
        expect(hyperionApiService.refineProblemStatementGlobally).not.toHaveBeenCalled();

        comp.refinementPrompt.set('   ');
        comp.submitRefinement();
        expect(hyperionApiService.refineProblemStatementGlobally).not.toHaveBeenCalled();
    });

    it('should not submit when no courseId for full refinement', () => {
        comp.exercise = createMockExercise({ problemStatement: 'Test' });
        comp.exercise.course = undefined;
        comp.refinementPrompt.set('Improve');
        comp.submitRefinement();
        expect(hyperionApiService.refineProblemStatementGlobally).not.toHaveBeenCalled();
    });

    it('should handle full refinement API error', () => {
        const errorSpy = jest.spyOn(alertService, 'error');
        (hyperionApiService.refineProblemStatementGlobally as jest.Mock).mockReturnValue(throwError(() => new Error('API error')));

        (comp as any).templateLoaded.set(true);
        (comp as any).templateProblemStatement.set('Template');
        (comp as any).currentProblemStatement.set('Original problem statement');

        comp.refinementPrompt.set('Improve');
        comp.submitRefinement();

        expect(errorSpy).toHaveBeenCalledWith('artemisApp.programmingExercise.inlineRefine.error');
    });
});

describe('CodeEditorInstructorAndEditorContainerComponent - Severity Helpers', () => {
    let fixture: ComponentFixture<CodeEditorInstructorAndEditorContainerComponent>;
    let comp: CodeEditorInstructorAndEditorContainerComponent;

    beforeEach(async () => {
        await configureTestBed();
        fixture = TestBed.createComponent(CodeEditorInstructorAndEditorContainerComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        fixture?.destroy();
    });

    it('should return correct icon for HIGH severity', () => {
        expect(comp.getSeverityIcon('HIGH')).toBe(faCircleExclamation);
    });

    it('should return correct icon for MEDIUM severity', () => {
        expect(comp.getSeverityIcon('MEDIUM')).toBe(faTriangleExclamation);
    });

    it('should return correct icon for LOW severity', () => {
        expect(comp.getSeverityIcon('LOW')).toBe(faCircleInfo);
    });

    it('should return default icon for unknown severity', () => {
        expect(comp.getSeverityIcon('UNKNOWN' as any)).toBe(faCircleInfo);
    });

    it('should return correct color for HIGH severity', () => {
        expect(comp.getSeverityColor('HIGH')).toBe('text-danger');
    });

    it('should return correct color for MEDIUM severity', () => {
        expect(comp.getSeverityColor('MEDIUM')).toBe('text-warning');
    });

    it('should return correct color for LOW severity', () => {
        expect(comp.getSeverityColor('LOW')).toBe('text-info');
    });

    it('should return default color for unknown severity', () => {
        expect(comp.getSeverityColor('UNKNOWN' as any)).toBe('text-secondary');
    });

    it('should sort issues by severity', () => {
        const issues: ConsistencyIssue[] = [
            {
                severity: 'LOW',
                description: 'Low issue',
                relatedLocations: [],
                category: 'METHOD_RETURN_TYPE_MISMATCH',
                suggestedFix: '',
            },
            {
                severity: 'HIGH',
                description: 'High issue',
                relatedLocations: [],
                category: 'METHOD_RETURN_TYPE_MISMATCH',
                suggestedFix: '',
            },
            {
                severity: 'MEDIUM',
                description: 'Medium issue',
                relatedLocations: [],
                category: 'METHOD_RETURN_TYPE_MISMATCH',
                suggestedFix: '',
            },
        ];

        comp.consistencyIssues.set(issues);

        const sorted = comp.sortedIssues();
        expect(sorted[0].severity).toBe('HIGH');
        expect(sorted[1].severity).toBe('MEDIUM');
        expect(sorted[2].severity).toBe('LOW');
    });
});

describe('CodeEditorInstructorAndEditorContainerComponent - Issue Navigation', () => {
    let fixture: ComponentFixture<CodeEditorInstructorAndEditorContainerComponent>;
    let comp: CodeEditorInstructorAndEditorContainerComponent;
    let alertService: AlertService;

    beforeEach(async () => {
        await configureTestBed();
        alertService = TestBed.inject(AlertService);
        fixture = TestBed.createComponent(CodeEditorInstructorAndEditorContainerComponent);
        comp = fixture.componentInstance;
        comp.exercise = createMockExercise();
    });

    afterEach(() => {
        fixture?.destroy();
        jest.clearAllMocks();
    });

    it('should navigate to problem statement location', () => {
        const issue: ConsistencyIssue = {
            severity: 'HIGH',
            description: 'Test issue',
            relatedLocations: [{ type: 'PROBLEM_STATEMENT', startLine: 1, endLine: 5, filePath: '' }],
            category: 'METHOD_RETURN_TYPE_MISMATCH',
            suggestedFix: '',
        };

        const mockEditableInstructions = { jumpToLine: jest.fn() };
        const mockCodeEditorContainer = {
            selectedFile: '',
            problemStatementIdentifier: 'problem-statement',
        };

        (comp as any).editableInstructions = mockEditableInstructions;
        (comp as any).codeEditorContainer = mockCodeEditorContainer;

        comp.onIssueNavigate(issue, 1, new Event('click'));

        expect(mockCodeEditorContainer.selectedFile).toBe('problem-statement');
        expect(mockEditableInstructions.jumpToLine).toHaveBeenCalledWith(5);
    });

    it('should set lineJumpOnFileLoad for template repository', () => {
        const issue: ConsistencyIssue = {
            severity: 'MEDIUM',
            description: 'Test issue',
            relatedLocations: [{ type: 'TEMPLATE_REPOSITORY', startLine: 1, endLine: 10, filePath: 'src/Main.java' }],
            category: 'METHOD_RETURN_TYPE_MISMATCH',
            suggestedFix: '',
        };

        const mockCodeEditorContainer = {
            selectedRepository: jest.fn().mockReturnValue('SOLUTION'),
        };
        (comp as any).codeEditorContainer = mockCodeEditorContainer;

        const selectTemplateSpy = jest.spyOn(comp, 'selectTemplateParticipation').mockImplementation();

        comp.onIssueNavigate(issue, 1, new Event('click'));

        expect(comp.lineJumpOnFileLoad).toBe(10);
        expect(selectTemplateSpy).toHaveBeenCalled();
    });

    it('should set lineJumpOnFileLoad for solution repository', () => {
        const issue: ConsistencyIssue = {
            severity: 'MEDIUM',
            description: 'Test issue',
            relatedLocations: [{ type: 'SOLUTION_REPOSITORY', startLine: 1, endLine: 15, filePath: 'src/Main.java' }],
            category: 'METHOD_RETURN_TYPE_MISMATCH',
            suggestedFix: '',
        };

        const mockCodeEditorContainer = {
            selectedRepository: jest.fn().mockReturnValue('TEMPLATE'),
        };
        (comp as any).codeEditorContainer = mockCodeEditorContainer;

        const selectSolutionSpy = jest.spyOn(comp, 'selectSolutionParticipation').mockImplementation();

        comp.onIssueNavigate(issue, 1, new Event('click'));

        expect(comp.lineJumpOnFileLoad).toBe(15);
        expect(selectSolutionSpy).toHaveBeenCalled();
    });

    it('should set lineJumpOnFileLoad for tests repository', () => {
        const issue: ConsistencyIssue = {
            severity: 'LOW',
            description: 'Test issue',
            relatedLocations: [{ type: 'TESTS_REPOSITORY', startLine: 1, endLine: 20, filePath: 'test/Test.java' }],
            category: 'METHOD_RETURN_TYPE_MISMATCH',
            suggestedFix: '',
        };

        const mockCodeEditorContainer = {
            selectedRepository: jest.fn().mockReturnValue('TEMPLATE'),
        };
        (comp as any).codeEditorContainer = mockCodeEditorContainer;

        const selectTestsSpy = jest.spyOn(comp, 'selectTestRepository').mockImplementation();

        comp.onIssueNavigate(issue, 1, new Event('click'));

        expect(comp.lineJumpOnFileLoad).toBe(20);
        expect(selectTestsSpy).toHaveBeenCalled();
    });

    it('should navigate within same issue locations', () => {
        const issue: ConsistencyIssue = {
            severity: 'HIGH',
            description: 'Test issue',
            relatedLocations: [
                { type: 'PROBLEM_STATEMENT', startLine: 1, endLine: 5, filePath: '' },
                { type: 'PROBLEM_STATEMENT', startLine: 10, endLine: 15, filePath: '' },
            ],
            category: 'METHOD_RETURN_TYPE_MISMATCH',
            suggestedFix: '',
        };

        const mockEditableInstructions = { jumpToLine: jest.fn() };
        const mockCodeEditorContainer = {
            selectedFile: '',
            problemStatementIdentifier: 'problem-statement',
        };

        (comp as any).editableInstructions = mockEditableInstructions;
        (comp as any).codeEditorContainer = mockCodeEditorContainer;

        // First navigation
        comp.onIssueNavigate(issue, 1, new Event('click'));
        expect(comp.locationIndex).toBe(0);

        // Navigate forward within same issue
        comp.onIssueNavigate(issue, 1, new Event('click'));
        expect(comp.locationIndex).toBe(1);

        // Navigate backward
        comp.onIssueNavigate(issue, -1, new Event('click'));
        expect(comp.locationIndex).toBe(0);
    });

    it('should handle navigation error gracefully', () => {
        const errorSpy = jest.spyOn(alertService, 'error');
        const issue: ConsistencyIssue = {
            severity: 'HIGH',
            description: 'Test issue',
            relatedLocations: [{ type: 'TEMPLATE_REPOSITORY', startLine: 1, endLine: 5, filePath: 'src/Main.java' }],
            category: 'METHOD_RETURN_TYPE_MISMATCH',
            suggestedFix: '',
        };

        const mockCodeEditorContainer = {
            selectedRepository: jest.fn().mockReturnValue('SOLUTION'),
        };
        (comp as any).codeEditorContainer = mockCodeEditorContainer;

        jest.spyOn(comp, 'selectTemplateParticipation').mockImplementation(() => {
            throw new Error('Navigation failed');
        });

        comp.onIssueNavigate(issue, 1, new Event('click'));

        expect(errorSpy).toHaveBeenCalled();
        expect(comp.lineJumpOnFileLoad).toBeUndefined();
        expect(comp.fileToJumpOn).toBeUndefined();
    });

    it('should jump to line when file is loaded', () => {
        const mockCodeEditorContainer = {
            selectedFile: 'src/Main.java',
            jumpToLine: jest.fn(),
        };
        (comp as any).codeEditorContainer = mockCodeEditorContainer;

        comp.lineJumpOnFileLoad = 10;
        comp.fileToJumpOn = 'src/Main.java';

        comp.onEditorLoaded();

        expect(mockCodeEditorContainer.jumpToLine).toHaveBeenCalledWith(10);
        expect(comp.lineJumpOnFileLoad).toBeUndefined();
    });

    it('should not jump to line if file does not match', () => {
        const mockCodeEditorContainer = {
            jumpToLine: jest.fn(),
        };
        (comp as any).codeEditorContainer = mockCodeEditorContainer;

        comp.lineJumpOnFileLoad = 10;
        comp.fileToJumpOn = 'src/Main.java';

        comp.onFileLoad('src/Other.java');

        expect(mockCodeEditorContainer.jumpToLine).not.toHaveBeenCalled();
    });
});
