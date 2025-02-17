import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateModule } from '@ngx-translate/core';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { AccountService } from 'app/core/auth/account.service';
import { DebugElement } from '@angular/core';
import { ActivatedRoute, Params, Router } from '@angular/router';
import { BehaviorSubject, Subject, of, throwError } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { DomainType, FileType, RepositoryType } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { problemStatement } from '../../helpers/sample/problemStatement.json';
import { MockProgrammingExerciseParticipationService } from '../../helpers/mocks/service/mock-programming-exercise-participation.service';
import { CodeEditorInstructorAndEditorContainerComponent } from 'app/exercises/programming/manage/code-editor/code-editor-instructor-and-editor-container.component';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { MockCourseExerciseService } from '../../helpers/mocks/service/mock-course-exercise.service';
import {
    CodeEditorBuildLogService,
    CodeEditorRepositoryFileService,
    CodeEditorRepositoryService,
} from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { DomainService } from 'app/exercises/programming/shared/code-editor/service/code-editor-domain.service';
import { TemplateProgrammingExerciseParticipation } from 'app/entities/participation/template-programming-exercise-participation.model';
import { Result } from 'app/entities/result.model';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { SolutionProgrammingExerciseParticipation } from 'app/entities/participation/solution-programming-exercise-participation.model';
import { MockActivatedRouteWithSubjects } from '../../helpers/mocks/activated-route/mock-activated-route-with-subjects';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockResultService } from '../../helpers/mocks/service/mock-result.service';
import { MockCodeEditorRepositoryService } from '../../helpers/mocks/service/mock-code-editor-repository.service';
import { MockCodeEditorBuildLogService } from '../../helpers/mocks/service/mock-code-editor-build-log.service';
import { MockCodeEditorRepositoryFileService } from '../../helpers/mocks/service/mock-code-editor-repository-file.service';
import { MockParticipationWebsocketService } from '../../helpers/mocks/service/mock-participation-websocket.service';
import { MockParticipationService } from '../../helpers/mocks/service/mock-participation.service';
import { MockProgrammingExerciseService } from '../../helpers/mocks/service/mock-programming-exercise.service';
import { WebsocketService } from 'app/core/websocket/websocket.service';
import { MockWebsocketService } from '../../helpers/mocks/service/mock-websocket.service';
import { MockComponent, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { CodeEditorContainerComponent } from 'app/exercises/programming/shared/code-editor/container/code-editor-container.component';
import { IncludedInScoreBadgeComponent } from 'app/exercises/shared/exercise-headers/included-in-score-badge.component';
import { ProgrammingExerciseInstructorExerciseStatusComponent } from 'app/exercises/programming/manage/status/programming-exercise-instructor-exercise-status.component';
import { UpdatingResultComponent } from 'app/exercises/shared/result/updating-result.component';
import { ProgrammingExerciseStudentTriggerBuildButtonComponent } from 'app/exercises/programming/shared/actions/programming-exercise-student-trigger-build-button.component';
import { ProgrammingExerciseEditableInstructionComponent } from 'app/exercises/programming/manage/instructions-editor/programming-exercise-editable-instruction.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CodeEditorGridComponent } from 'app/exercises/programming/shared/code-editor/layout/code-editor-grid.component';
import { CodeEditorActionsComponent } from 'app/exercises/programming/shared/code-editor/actions/code-editor-actions.component';
import { CodeEditorFileBrowserComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser.component';
import { CodeEditorBuildOutputComponent } from 'app/exercises/programming/shared/code-editor/build-output/code-editor-build-output.component';
import { KeysPipe } from 'app/shared/pipes/keys.pipe';
import { CodeEditorInstructionsComponent } from 'app/exercises/programming/shared/code-editor/instructions/code-editor-instructions.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ProgrammingExerciseInstructionComponent } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instruction.component';
import { ProgrammingExerciseInstructionAnalysisComponent } from 'app/exercises/programming/manage/instructions-editor/analysis/programming-exercise-instruction-analysis.component';
import { ResultComponent } from 'app/exercises/shared/result/result.component';
import { ProgrammingExerciseInstructionStepWizardComponent } from 'app/exercises/programming/shared/instructions-render/step-wizard/programming-exercise-instruction-step-wizard.component';
import { ProgrammingExerciseInstructionTaskStatusComponent } from 'app/exercises/programming/shared/instructions-render/task/programming-exercise-instruction-task-status.component';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { CodeEditorMonacoComponent } from 'app/exercises/programming/shared/code-editor/monaco/code-editor-monaco.component';
import { MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { mockCodeEditorMonacoViewChildren } from '../../helpers/mocks/mock-instance.helper';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';

describe('CodeEditorInstructorIntegration', () => {
    let comp: CodeEditorInstructorAndEditorContainerComponent;
    let containerFixture: ComponentFixture<CodeEditorInstructorAndEditorContainerComponent>;
    let containerDebugElement: DebugElement;
    let domainService: DomainService;
    let route: ActivatedRoute;

    let checkIfRepositoryIsCleanStub: jest.SpyInstance;
    let getRepositoryContentStub: jest.SpyInstance;
    let subscribeForLatestResultOfParticipationStub: jest.SpyInstance;
    let getFeedbackDetailsForResultStub: jest.SpyInstance;
    let getBuildLogsStub: jest.SpyInstance;
    let findWithParticipationsStub: jest.SpyInstance;
    let getLatestResultWithFeedbacksStub: jest.SpyInstance;
    let navigateSpy: jest.SpyInstance;

    let checkIfRepositoryIsCleanSubject: Subject<{ isClean: boolean }>;
    let getRepositoryContentSubject: Subject<{ [fileName: string]: FileType }>;
    let subscribeForLatestResultOfParticipationSubject: BehaviorSubject<Result | null>;
    let findWithParticipationsSubject: Subject<{ body: ProgrammingExercise }>;
    let routeSubject: Subject<Params>;

    const mockProfileInfo = { activeProfiles: ['iris'] } as ProfileInfo;

    // Workaround for an error with MockComponent(). You can remove this once https://github.com/help-me-mom/ng-mocks/issues/8634 is resolved.
    mockCodeEditorMonacoViewChildren();

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, MockModule(NgbTooltipModule)],
            declarations: [
                CodeEditorInstructorAndEditorContainerComponent,
                CodeEditorContainerComponent,
                KeysPipe,
                CodeEditorInstructionsComponent,
                MockComponent(CodeEditorGridComponent),
                MockComponent(CodeEditorActionsComponent),
                MockComponent(CodeEditorFileBrowserComponent),
                MockComponent(CodeEditorMonacoComponent),
                CodeEditorBuildOutputComponent,
                MockPipe(ArtemisDatePipe),
                MockComponent(IncludedInScoreBadgeComponent),
                ProgrammingExerciseInstructorExerciseStatusComponent,
                UpdatingResultComponent,
                MockComponent(ProgrammingExerciseStudentTriggerBuildButtonComponent),
                ProgrammingExerciseEditableInstructionComponent,
                MockComponent(MarkdownEditorMonacoComponent),
                ProgrammingExerciseInstructionComponent,
                MockComponent(ProgrammingExerciseInstructionAnalysisComponent),
                MockPipe(ArtemisTranslatePipe),
                MockComponent(ResultComponent),
                MockComponent(ProgrammingExerciseInstructionStepWizardComponent),
                MockComponent(ProgrammingExerciseInstructionTaskStatusComponent),
            ],
            providers: [
                JhiLanguageHelper,
                { provide: Router, useClass: MockRouter },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ActivatedRoute, useClass: MockActivatedRouteWithSubjects },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: ResultService, useClass: MockResultService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: CourseExerciseService, useClass: MockCourseExerciseService },
                { provide: CodeEditorRepositoryService, useClass: MockCodeEditorRepositoryService },
                { provide: CodeEditorRepositoryFileService, useClass: MockCodeEditorRepositoryFileService },
                { provide: CodeEditorBuildLogService, useClass: MockCodeEditorBuildLogService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                { provide: ResultService, useClass: MockResultService },
                { provide: ParticipationService, useClass: MockParticipationService },
                { provide: ProgrammingExerciseParticipationService, useClass: MockProgrammingExerciseParticipationService },
                { provide: ProgrammingExerciseService, useClass: MockProgrammingExerciseService },
                { provide: WebsocketService, useClass: MockWebsocketService },
                MockProvider(ProfileService, {
                    getProfileInfo: () => of(mockProfileInfo),
                }),
            ],
        })
            .compileComponents()
            .then(() => {
                containerFixture = TestBed.createComponent(CodeEditorInstructorAndEditorContainerComponent);
                comp = containerFixture.componentInstance;
                containerDebugElement = containerFixture.debugElement;

                const codeEditorRepositoryService = containerDebugElement.injector.get(CodeEditorRepositoryService);
                const codeEditorRepositoryFileService = containerDebugElement.injector.get(CodeEditorRepositoryFileService);
                const participationWebsocketService = containerDebugElement.injector.get(ParticipationWebsocketService);
                const resultService = containerDebugElement.injector.get(ResultService);
                const buildLogService = containerDebugElement.injector.get(CodeEditorBuildLogService);
                const programmingExerciseParticipationService = containerDebugElement.injector.get(ProgrammingExerciseParticipationService);
                const programmingExerciseService = containerDebugElement.injector.get(ProgrammingExerciseService);
                domainService = containerDebugElement.injector.get(DomainService);
                route = containerDebugElement.injector.get(ActivatedRoute);
                containerDebugElement.injector.get(Router);
                checkIfRepositoryIsCleanSubject = new Subject<{ isClean: boolean }>();
                getRepositoryContentSubject = new Subject<{ [fileName: string]: FileType }>();
                subscribeForLatestResultOfParticipationSubject = new BehaviorSubject<Result | null>(null);
                findWithParticipationsSubject = new Subject<{ body: ProgrammingExercise }>();

                routeSubject = new Subject<Params>();
                // @ts-ignore
                (route as MockActivatedRouteWithSubjects).setSubject(routeSubject);

                checkIfRepositoryIsCleanStub = jest.spyOn(codeEditorRepositoryService, 'getStatus');
                getRepositoryContentStub = jest.spyOn(codeEditorRepositoryFileService, 'getRepositoryContent');
                subscribeForLatestResultOfParticipationStub = jest.spyOn(participationWebsocketService, 'subscribeForLatestResultOfParticipation');
                getFeedbackDetailsForResultStub = jest.spyOn(resultService, 'getFeedbackDetailsForResult');
                getLatestResultWithFeedbacksStub = jest
                    .spyOn(programmingExerciseParticipationService, 'getLatestResultWithFeedback')
                    .mockReturnValue(throwError(() => new Error('no result')));
                getBuildLogsStub = jest.spyOn(buildLogService, 'getBuildLogs');
                navigateSpy = jest.spyOn(TestBed.inject(Router), 'navigate');

                findWithParticipationsStub = jest.spyOn(programmingExerciseService, 'findWithTemplateAndSolutionParticipationAndResults');
                findWithParticipationsStub.mockReturnValue(findWithParticipationsSubject);

                subscribeForLatestResultOfParticipationStub.mockReturnValue(subscribeForLatestResultOfParticipationSubject);
                getRepositoryContentStub.mockReturnValue(getRepositoryContentSubject);
                checkIfRepositoryIsCleanStub.mockReturnValue(checkIfRepositoryIsCleanSubject);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();

        subscribeForLatestResultOfParticipationSubject = new BehaviorSubject<Result | null>(null);
        subscribeForLatestResultOfParticipationStub.mockReturnValue(subscribeForLatestResultOfParticipationSubject);

        routeSubject = new Subject<Params>();
        // @ts-ignore
        (route as MockActivatedRouteWithSubjects).setSubject(routeSubject);

        findWithParticipationsSubject = new Subject<{ body: ProgrammingExercise }>();
        findWithParticipationsStub.mockReturnValue(findWithParticipationsSubject);

        checkIfRepositoryIsCleanSubject = new Subject<{ isClean: boolean }>();
        checkIfRepositoryIsCleanStub.mockReturnValue(checkIfRepositoryIsCleanSubject);

        getRepositoryContentSubject = new Subject<{ [p: string]: FileType }>();
        getRepositoryContentStub.mockReturnValue(getRepositoryContentSubject);
    });

    const initContainer = (exercise: ProgrammingExercise, routeParams?: any) => {
        comp.ngOnInit();
        routeSubject.next({ exerciseId: 1, ...routeParams });
        expect(comp.codeEditorContainer).toBeUndefined(); // Have to use this as it's a component
        expect(findWithParticipationsStub).toHaveBeenCalledOnce();
        expect(findWithParticipationsStub).toHaveBeenCalledWith(exercise.id);
        expect(comp.loadingState).toBe(comp.LOADING_STATE.INITIALIZING);
    };

    it('should load the exercise and select the template participation if no participation id is provided', () => {
        jest.resetModules();
        // @ts-ignore
        const exercise = {
            id: 1,
            problemStatement,
            studentParticipations: [{ id: 2, repositoryUri: 'test' }],
            templateParticipation: { id: 3, repositoryUri: 'test2', results: [{ id: 9, submission: { id: 1, buildFailed: false } }] },
            solutionParticipation: { id: 4, repositoryUri: 'test3' },
            course: { id: 1 },
        } as ProgrammingExercise;
        exercise.studentParticipations = exercise.studentParticipations?.map((p) => {
            p.exercise = exercise;
            return p;
        });
        exercise.templateParticipation = { ...exercise.templateParticipation, programmingExercise: exercise };
        exercise.solutionParticipation = { ...exercise.solutionParticipation, programmingExercise: exercise };

        getFeedbackDetailsForResultStub.mockReturnValue(of([]));
        const setDomainSpy = jest.spyOn(domainService, 'setDomain');
        // @ts-ignore
        (comp.router as MockRouter).setUrl('code-editor-instructor/1');
        initContainer(exercise);

        findWithParticipationsSubject.next({ body: exercise });

        expect(getLatestResultWithFeedbacksStub).not.toHaveBeenCalled();
        expect(setDomainSpy).toHaveBeenCalledOnce();
        expect(setDomainSpy).toHaveBeenCalledWith([DomainType.PARTICIPATION, exercise.templateParticipation]);
        expect(comp.exercise).toEqual(exercise);
        expect(comp.selectedRepository).toBe(RepositoryType.TEMPLATE);
        expect(comp.selectedParticipation).toEqual(comp.selectedParticipation);
        expect(comp.loadingState).toBe(comp.LOADING_STATE.CLEAR);
        expect(comp.domainChangeSubscription).toBeDefined(); // External complex object

        containerFixture.detectChanges();
        expect(comp.codeEditorContainer.grid).toBeDefined(); // Have to use this as it's a component

        checkIfRepositoryIsCleanSubject.next({ isClean: true });
        getRepositoryContentSubject.next({ file: FileType.FILE, folder: FileType.FOLDER });
        containerFixture.detectChanges();

        // Submission could be built
        expect(getBuildLogsStub).not.toHaveBeenCalled();
        // Once called by each build-output & instructions
        expect(getFeedbackDetailsForResultStub).toHaveBeenCalledTimes(2);

        expect(comp.codeEditorContainer.grid).toBeDefined(); // Have to use this as it's a component
        expect(comp.codeEditorContainer.fileBrowser).toBeDefined(); // Have to use this as it's a component
        expect(comp.codeEditorContainer.actions).toBeDefined(); // Have to use this as it's a component
        expect(comp.editableInstructions).toBeDefined(); // Have to use this as it's a component
        expect(comp.editableInstructions.participation).toEqual(exercise.templateParticipation);
        expect(comp.resultComp).toBeDefined(); // Have to use this as it's a component
        expect(comp.codeEditorContainer.buildOutput).toBeDefined(); // Have to use this as it's a component

        // Called once by each build-output, instructions, result and twice by instructor-exercise-status (=templateParticipation,solutionParticipation) &
        expect(subscribeForLatestResultOfParticipationStub).toHaveBeenCalledTimes(5);
    });

    it('should go into error state when loading the exercise failed', () => {
        const exercise = { id: 1, studentParticipations: [{ id: 2 }], templateParticipation: { id: 3 }, solutionParticipation: { id: 4 } } as ProgrammingExercise;
        const setDomainSpy = jest.spyOn(domainService, 'setDomain');
        initContainer(exercise);

        findWithParticipationsSubject.error('fatal error');

        expect(setDomainSpy).not.toHaveBeenCalled();
        expect(comp.loadingState).toBe(comp.LOADING_STATE.FETCHING_FAILED);
        expect(comp.selectedRepository).toBeUndefined();

        containerFixture.detectChanges();
        expect(comp.codeEditorContainer).toBeUndefined();
    });

    it('should load test repository if specified in url', () => {
        const exercise = {
            id: 1,
            problemStatement,
            studentParticipations: [{ id: 2 }],
            templateParticipation: { id: 3 },
            solutionParticipation: { id: 4 },
            course: { id: 1 },
        } as ProgrammingExercise;
        const setDomainSpy = jest.spyOn(domainService, 'setDomain');
        // @ts-ignore
        (comp.router as MockRouter).setUrl(`code-editor/TESTS`);
        comp.ngOnDestroy();
        initContainer(exercise, { repositoryType: 'TESTS' });

        findWithParticipationsSubject.next({ body: exercise });

        expect(setDomainSpy).toHaveBeenCalledOnce();
        expect(setDomainSpy).toHaveBeenCalledWith([DomainType.TEST_REPOSITORY, exercise]);
        expect(comp.selectedParticipation).toEqual(exercise.templateParticipation);
        expect(comp.selectedRepository).toBe(RepositoryType.TESTS);
        expect(getBuildLogsStub).not.toHaveBeenCalled();
        expect(getFeedbackDetailsForResultStub).not.toHaveBeenCalled();

        containerFixture.detectChanges();

        expect(comp.codeEditorContainer).toBeDefined(); // Have to use this as it's a component
        expect(comp.editableInstructions).toBeDefined(); // Have to use this as it's a component
        expect(comp.editableInstructions.participation).toEqual(exercise.templateParticipation);
        expect(comp.resultComp).toBeUndefined();
        expect(comp.codeEditorContainer.buildOutput).toBeUndefined();
    });

    const checkSolutionRepository = (exercise: ProgrammingExercise) => {
        expect(comp.selectedRepository).toBe(RepositoryType.SOLUTION);
        expect(comp.selectedParticipation).toEqual(exercise.solutionParticipation);
        expect(comp.codeEditorContainer).toBeDefined(); // Have to use this as it's a component
        expect(comp.editableInstructions).toBeDefined(); // Have to use this as it's a component
        expect(comp.resultComp).toBeDefined(); // Have to use this as it's a component
        expect(comp.codeEditorContainer.buildOutput).toBeDefined(); // Have to use this as it's a component
        expect(comp.codeEditorContainer.buildOutput.participation).toEqual(exercise.solutionParticipation);
        expect(comp.editableInstructions.participation).toEqual(exercise.solutionParticipation);
    };

    it('should be able to switch between the repos and update the child components accordingly', () => {
        // @ts-ignore
        const exercise = {
            id: 1,
            course: { id: 1 },
            problemStatement,
        } as ProgrammingExercise;
        exercise.templateParticipation = { id: 3, repositoryUri: 'test2', programmingExercise: exercise } as TemplateProgrammingExerciseParticipation;
        exercise.solutionParticipation = { id: 4, repositoryUri: 'test3', programmingExercise: exercise } as SolutionProgrammingExerciseParticipation;
        // @ts-ignore
        exercise.studentParticipations = [{ id: 2, repositoryUri: 'test', exercise } as ProgrammingExerciseStudentParticipation];

        const setDomainSpy = jest.spyOn(domainService, 'setDomain');

        // Start with assignment repository
        // @ts-ignore
        (comp.router as MockRouter).setUrl(`code-editor/USER/2`);

        comp.ngOnInit();
        routeSubject.next({ exerciseId: 1, repositoryId: 2, repositoryType: 'USER' });
        findWithParticipationsSubject.next({ body: exercise });

        containerFixture.detectChanges();

        expect(comp.selectedRepository).toBe(RepositoryType.ASSIGNMENT);
        expect(comp.selectedParticipation).toEqual(exercise.studentParticipations[0]);
        expect(comp.codeEditorContainer).toBeDefined(); // Have to use this as it's a component
        expect(comp.editableInstructions).toBeDefined(); // Have to use this as it's a component
        expect(comp.resultComp).toBeDefined(); // Have to use this as it's a component
        expect(comp.codeEditorContainer.buildOutput).toBeDefined(); // Have to use this as it's a component
        expect(comp.codeEditorContainer.buildOutput.participation).toEqual(exercise.studentParticipations[0]);
        expect(comp.editableInstructions.participation).toEqual(exercise.studentParticipations[0]);

        // New select solution repository
        // @ts-ignore
        (comp.router as MockRouter).setUrl('code-editor/SOLUTION/4');
        routeSubject.next({ exerciseId: 1, repositoryId: 4 });

        containerFixture.detectChanges();

        checkSolutionRepository(exercise);

        expect(findWithParticipationsStub).toHaveBeenCalledOnce();
        expect(findWithParticipationsStub).toHaveBeenCalledWith(exercise.id);
        expect(setDomainSpy).toHaveBeenCalledTimes(2);
        expect(setDomainSpy).toHaveBeenNthCalledWith(1, [DomainType.PARTICIPATION, exercise.studentParticipations[0]]);
        expect(setDomainSpy).toHaveBeenNthCalledWith(2, [DomainType.PARTICIPATION, exercise.solutionParticipation]);
    });

    it('should not be able to select a repository without repositoryUri', () => {
        // @ts-ignore
        const exercise = {
            id: 1,
            course: { id: 1 },
            problemStatement,
        } as ProgrammingExercise;
        // @ts-ignore
        exercise.studentParticipations = [{ id: 2, repositoryUri: 'test', exercise } as ProgrammingExerciseStudentParticipation];
        exercise.templateParticipation = { id: 3, programmingExercise: exercise } as TemplateProgrammingExerciseParticipation;
        exercise.solutionParticipation = { id: 4, repositoryUri: 'test3', programmingExercise: exercise } as SolutionProgrammingExerciseParticipation;

        const setDomainSpy = jest.spyOn(domainService, 'setDomain');

        // Start with assignment repository
        // @ts-ignore
        (comp.router as MockRouter).setUrl('code-editor-instructor/1/3');
        comp.ngOnInit();
        routeSubject.next({ exerciseId: 1, participationId: 3 });
        findWithParticipationsSubject.next({ body: exercise });

        containerFixture.detectChanges();

        expect(setDomainSpy).toHaveBeenCalledOnce();
        expect(setDomainSpy).toHaveBeenCalledWith([DomainType.PARTICIPATION, exercise.solutionParticipation]);
        checkSolutionRepository(exercise);
    });

    describe('Repository Navigation', () => {
        const exercise = {
            id: 1,
            problemStatement,
            studentParticipations: [{ id: 2 }],
            templateParticipation: { id: 3 },
            solutionParticipation: { id: 4 },
            course: { id: 1 },
        } as ProgrammingExercise;

        beforeEach(() => {
            comp.exercise = exercise;
        });

        it('should navigate to template participation repository from auxiliary repository', () => {
            comp.selectedRepository = RepositoryType.AUXILIARY;
            comp.selectTemplateParticipation();
            expect(navigateSpy).toHaveBeenCalledWith(['../..', RepositoryType.TEMPLATE, exercise.templateParticipation!.id], expect.any(Object));
        });

        it('should navigate to template participation repository from test repository', () => {
            comp.selectedRepository = RepositoryType.TESTS;
            comp.selectTemplateParticipation();
            expect(navigateSpy).toHaveBeenCalledWith(['../..', RepositoryType.TEMPLATE, exercise.templateParticipation!.id], expect.any(Object));
        });

        it('should navigate to solution participation repository from auxiliary repository', () => {
            comp.selectedRepository = RepositoryType.AUXILIARY;
            comp.selectSolutionParticipation();
            expect(navigateSpy).toHaveBeenCalledWith(['../..', RepositoryType.SOLUTION, exercise.solutionParticipation!.id], expect.any(Object));
        });

        it('should navigate to solution participation repository from test repository', () => {
            comp.selectedRepository = RepositoryType.TESTS;
            comp.selectSolutionParticipation();
            expect(navigateSpy).toHaveBeenCalledWith(['../..', RepositoryType.SOLUTION, exercise.solutionParticipation!.id], expect.any(Object));
        });

        it('should navigate to assignment participation repository from auxiliary repository', () => {
            comp.selectedRepository = RepositoryType.AUXILIARY;
            comp.selectAssignmentParticipation();
            expect(navigateSpy).toHaveBeenCalledWith(['../..', RepositoryType.USER, exercise.studentParticipations![0].id], expect.any(Object));
        });

        it('should navigate to assignment participation repository from test repository', () => {
            comp.selectedRepository = RepositoryType.TESTS;
            comp.selectAssignmentParticipation();
            expect(navigateSpy).toHaveBeenCalledWith(['../..', RepositoryType.USER, exercise.studentParticipations![0].id], expect.any(Object));
        });

        it('should navigate to test repository from auxiliary repository', () => {
            comp.selectedRepository = RepositoryType.AUXILIARY;
            comp.selectTestRepository();
            expect(navigateSpy).toHaveBeenCalledWith(['../..', RepositoryType.TESTS, 'test'], expect.any(Object));
        });

        it('should navigate to test repository from test repository', () => {
            comp.selectedRepository = RepositoryType.TESTS;
            comp.selectTestRepository();
            expect(navigateSpy).toHaveBeenCalledWith(['../..', RepositoryType.TESTS, 'test'], expect.any(Object));
        });

        it('should navigate to auxiliary repository with provided repositoryId', () => {
            const repositoryId = 4;
            comp.selectedRepository = RepositoryType.AUXILIARY;
            comp.selectAuxiliaryRepository(repositoryId);
            expect(navigateSpy).toHaveBeenCalledWith(['../..', RepositoryType.AUXILIARY, repositoryId], expect.any(Object));
        });

        it('should navigate to auxiliary repository from test repository', () => {
            const repositoryId = 4;
            comp.selectedRepository = RepositoryType.TESTS;
            comp.selectAuxiliaryRepository(repositoryId);
            expect(navigateSpy).toHaveBeenCalledWith(['../..', RepositoryType.AUXILIARY, repositoryId], expect.any(Object));
        });
    });
});
