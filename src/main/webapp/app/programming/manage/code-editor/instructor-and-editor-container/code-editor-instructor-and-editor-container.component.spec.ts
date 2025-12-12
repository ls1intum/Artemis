import 'zone.js';
import 'zone.js/testing';
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
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
import { ConsistencyCheckError, ErrorType } from 'app/programming/shared/entities/consistency-check-result.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { HyperionCodeGenerationApiService } from 'app/openapi/api/hyperionCodeGenerationApi.service';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

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
        await TestBed.configureTestingModule({
            imports: [CodeEditorInstructorAndEditorContainerComponent],
            providers: [
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
                { provide: CodeEditorRepositoryFileService, useValue: { getRepositoryContent: jest.fn(() => of({} as any)) } },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ConsistencyCheckService, useValue: { checkConsistencyForProgrammingExercise: jest.fn() } },
                { provide: ArtemisIntelligenceService, useValue: { consistencyCheck: jest.fn(), isLoading: () => false } },
            ],
        })
            // Avoid rendering heavy template dependencies for these tests
            .overrideComponent(CodeEditorInstructorAndEditorContainerComponent, {
                set: { template: '', imports: [] as any },
            })
            .compileComponents();

        alertService = TestBed.inject(AlertService);
        codeGenerationApi = TestBed.inject(HyperionCodeGenerationApiService) as any;
        ws = TestBed.inject(HyperionWebsocketService) as any;
        profileService = TestBed.inject(ProfileService);
        repoService = TestBed.inject(CodeEditorRepositoryService) as any;
        repoFileService = TestBed.inject(CodeEditorRepositoryFileService) as any;

        // Enable Hyperion by default so property initialization is deterministic
        jest.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);

        fixture = TestBed.createComponent(CodeEditorInstructorAndEditorContainerComponent);
        comp = fixture.componentInstance;

        // Minimal exercise setup used by generateCode
        comp.exercise = { id: 42 } as any;
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
        const addAlertSpy = jest.spyOn(alertService, 'addAlert');
        comp.selectedRepository = RepositoryType.SOLUTION;
        (codeGenerationApi.generateCode as jest.Mock).mockReturnValue(of({ jobId: 'job-7' }));

        // Intercept setTimeout to capture the scheduled callback and invoke it immediately
        const originalSetTimeout = window.setTimeout;
        let timeoutCallback: (() => void) | undefined;
        // @ts-ignore
        window.setTimeout = ((fn: () => void, _delay?: number) => {
            timeoutCallback = fn;
            return 1 as any;
        }) as any;

        try {
            (ws.subscribeToJob as jest.Mock).mockReturnValue(new Subject<any>().asObservable());
            comp.generateCode();
            await Promise.resolve();
            expect(comp.isGeneratingCode()).toBeTrue();

            // Simulate timeout
            if (timeoutCallback) {
                timeoutCallback();
            }

            expect(comp.isGeneratingCode()).toBeFalse();
            expect(ws.unsubscribeFromJob).toHaveBeenCalledWith('job-7');
            expect(addAlertSpy).toHaveBeenCalledWith(expect.objectContaining({ type: AlertType.WARNING, translationKey: 'artemisApp.programmingExercise.codeGeneration.timeout' }));
        } finally {
            window.setTimeout = originalSetTimeout;
        }
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
