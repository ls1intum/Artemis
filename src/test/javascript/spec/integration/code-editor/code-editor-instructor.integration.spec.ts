import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateModule } from '@ngx-translate/core';
import { WindowRef } from 'app/core/websocket/window.service';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { AccountService } from 'app/core/auth/account.service';
import { ChangeDetectorRef, DebugElement } from '@angular/core';
import { ActivatedRoute, Params, Router } from '@angular/router';
import { SinonStub, spy, stub } from 'sinon';
import { BehaviorSubject, Observable, of, Subject, throwError } from 'rxjs';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as ace from 'brace';
import { ArtemisTestModule } from '../../test.module';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { DomainType, FileType } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { MockRouter } from '../../helpers/mocks/service/mock-router.service';
import { problemStatement } from '../../helpers/sample/problemStatement.json';
import { MockProgrammingExerciseParticipationService } from '../../helpers/mocks/service/mock-programming-exercise-participation.service';
import { ExerciseHint } from 'app/entities/exercise-hint.model';
import { DeviceDetectorService } from 'ngx-device-detector';
import { CodeEditorInstructorContainerComponent } from 'app/exercises/programming/manage/code-editor/code-editor-instructor-container.component';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { MockCourseExerciseService } from '../../helpers/mocks/service/mock-course-exercise.service';
import { ExerciseHintService, IExerciseHintService } from 'app/exercises/shared/exercise-hint/manage/exercise-hint.service';
import {
    CodeEditorBuildLogService,
    CodeEditorRepositoryFileService,
    CodeEditorRepositoryService,
} from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { CodeEditorSessionService } from 'app/exercises/programming/shared/code-editor/service/code-editor-session.service';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { DomainService } from 'app/exercises/programming/shared/code-editor/service/code-editor-domain.service';
import { TemplateProgrammingExerciseParticipation } from 'app/entities/participation/template-programming-exercise-participation.model';
import { Result } from 'app/entities/result.model';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { SolutionProgrammingExerciseParticipation } from 'app/entities/participation/solution-programming-exercise-participation.model';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated.route';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockResultService } from '../../helpers/mocks/service/mock-result.service';
import { MockCodeEditorRepositoryService } from '../../helpers/mocks/service/mock-code-editor-repository.service';
import { MockCodeEditorBuildLogService } from '../../helpers/mocks/service/mock-code-editor-build-log.service';
import { MockCodeEditorRepositoryFileService } from '../../helpers/mocks/service/mock-code-editor-repository-file.service';
import { MockCodeEditorSessionService } from '../../helpers/mocks/service/mock-code-editor-session.service';
import { MockParticipationWebsocketService } from '../../helpers/mocks/service/mock-participation-websocket.service';
import { MockParticipationService } from '../../helpers/mocks/service/mock-participation.service';
import { MockProgrammingExerciseService } from '../../helpers/mocks/service/mock-programming-exercise.service';
import { MockExerciseHintService } from '../../helpers/mocks/service/mock-exercise-hint.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { MockWebsocketService } from '../../helpers/mocks/service/mock-websocket.service';
import { ArtemisCodeEditorManagementModule } from 'app/exercises/programming/manage/code-editor/code-editor-management.module';
import { CourseExerciseService } from 'app/course/manage/course-management.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('CodeEditorInstructorIntegration', () => {
    // needed to make sure ace is defined
    ace.acequire('ace/ext/modelist');
    let container: CodeEditorInstructorContainerComponent;
    let containerFixture: ComponentFixture<CodeEditorInstructorContainerComponent>;
    let containerDebugElement: DebugElement;
    let codeEditorRepositoryFileService: CodeEditorRepositoryFileService;
    let codeEditorRepositoryService: CodeEditorRepositoryService;
    let participationWebsocketService: ParticipationWebsocketService;
    let resultService: ResultService;
    let programmingExerciseParticipationService: ProgrammingExerciseParticipationService;
    let buildLogService: CodeEditorBuildLogService;
    let participationService: ParticipationService;
    let programmingExerciseService: ProgrammingExerciseService;
    let domainService: DomainService;
    let exerciseHintService: IExerciseHintService;
    let route: ActivatedRoute;

    let checkIfRepositoryIsCleanStub: SinonStub;
    let getRepositoryContentStub: SinonStub;
    let subscribeForLatestResultOfParticipationStub: SinonStub;
    let getFeedbackDetailsForResultStub: SinonStub;
    let getBuildLogsStub: SinonStub;
    let getFileStub: SinonStub;
    let saveFilesStub: SinonStub;
    let commitStub: SinonStub;
    let findWithLatestResultStub: SinonStub;
    let findWithParticipationsStub: SinonStub;
    let getLatestResultWithFeedbacksStub: SinonStub;
    let getHintsForExerciseStub: SinonStub;

    let checkIfRepositoryIsCleanSubject: Subject<{ isClean: boolean }>;
    let getRepositoryContentSubject: Subject<{ [fileName: string]: FileType }>;
    let subscribeForLatestResultOfParticipationSubject: BehaviorSubject<Result | null>;
    let findWithParticipationsSubject: Subject<{ body: ProgrammingExercise }>;
    let routeSubject: Subject<Params>;

    const exerciseHints = [{ id: 1 }, { id: 2 }];

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisCodeEditorManagementModule],
            declarations: [],
            providers: [
                JhiLanguageHelper,
                WindowRef,
                ChangeDetectorRef,
                DeviceDetectorService,
                { provide: Router, useClass: MockRouter },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ActivatedRoute, useClass: MockActivatedRoute },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: ResultService, useClass: MockResultService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: CourseExerciseService, useClass: MockCourseExerciseService },
                { provide: CodeEditorRepositoryService, useClass: MockCodeEditorRepositoryService },
                { provide: CodeEditorRepositoryFileService, useClass: MockCodeEditorRepositoryFileService },
                { provide: CodeEditorBuildLogService, useClass: MockCodeEditorBuildLogService },
                { provide: CodeEditorSessionService, useClass: MockCodeEditorSessionService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                { provide: ResultService, useClass: MockResultService },
                { provide: ParticipationService, useClass: MockParticipationService },
                { provide: ProgrammingExerciseParticipationService, useClass: MockProgrammingExerciseParticipationService },
                { provide: ProgrammingExerciseService, useClass: MockProgrammingExerciseService },
                { provide: ExerciseHintService, useClass: MockExerciseHintService },
                { provide: JhiWebsocketService, useClass: MockWebsocketService },
            ],
        })
            .compileComponents()
            .then(() => {
                containerFixture = TestBed.createComponent(CodeEditorInstructorContainerComponent);
                container = containerFixture.componentInstance;
                containerDebugElement = containerFixture.debugElement;

                codeEditorRepositoryService = containerDebugElement.injector.get(CodeEditorRepositoryService);
                codeEditorRepositoryFileService = containerDebugElement.injector.get(CodeEditorRepositoryFileService);
                participationWebsocketService = containerDebugElement.injector.get(ParticipationWebsocketService);
                resultService = containerDebugElement.injector.get(ResultService);
                buildLogService = containerDebugElement.injector.get(CodeEditorBuildLogService);
                participationService = containerDebugElement.injector.get(ParticipationService);
                programmingExerciseParticipationService = containerDebugElement.injector.get(ProgrammingExerciseParticipationService);
                programmingExerciseService = containerDebugElement.injector.get(ProgrammingExerciseService);
                domainService = containerDebugElement.injector.get(DomainService);
                exerciseHintService = containerDebugElement.injector.get(ExerciseHintService);
                route = containerDebugElement.injector.get(ActivatedRoute);
                containerDebugElement.injector.get(Router);

                checkIfRepositoryIsCleanSubject = new Subject<{ isClean: boolean }>();
                getRepositoryContentSubject = new Subject<{ [fileName: string]: FileType }>();
                subscribeForLatestResultOfParticipationSubject = new BehaviorSubject<Result | null>(null);
                findWithParticipationsSubject = new Subject<{ body: ProgrammingExercise }>();

                routeSubject = new Subject<Params>();
                // @ts-ignore
                (route as MockActivatedRoute).setSubject(routeSubject);

                checkIfRepositoryIsCleanStub = stub(codeEditorRepositoryService, 'getStatus');
                getRepositoryContentStub = stub(codeEditorRepositoryFileService, 'getRepositoryContent');
                subscribeForLatestResultOfParticipationStub = stub(participationWebsocketService, 'subscribeForLatestResultOfParticipation');
                getFeedbackDetailsForResultStub = stub(resultService, 'getFeedbackDetailsForResult');
                getLatestResultWithFeedbacksStub = stub(programmingExerciseParticipationService, 'getLatestResultWithFeedback').returns(throwError('no result'));
                getBuildLogsStub = stub(buildLogService, 'getBuildLogs');
                getFileStub = stub(codeEditorRepositoryFileService, 'getFile');
                saveFilesStub = stub(codeEditorRepositoryFileService, 'updateFiles');
                commitStub = stub(codeEditorRepositoryService, 'commit');
                findWithLatestResultStub = stub(participationService, 'findWithLatestResult');
                getHintsForExerciseStub = stub(exerciseHintService, 'findByExerciseId').returns(of({ body: exerciseHints }) as Observable<HttpResponse<ExerciseHint[]>>);

                findWithParticipationsStub = stub(programmingExerciseService, 'findWithTemplateAndSolutionParticipation');
                findWithParticipationsStub.returns(findWithParticipationsSubject);

                subscribeForLatestResultOfParticipationStub.returns(subscribeForLatestResultOfParticipationSubject);
                getRepositoryContentStub.returns(getRepositoryContentSubject);
                checkIfRepositoryIsCleanStub.returns(checkIfRepositoryIsCleanSubject);
            });
    });

    afterEach(() => {
        checkIfRepositoryIsCleanStub.restore();
        getRepositoryContentStub.restore();
        subscribeForLatestResultOfParticipationStub.restore();
        getFeedbackDetailsForResultStub.restore();
        getBuildLogsStub.restore();
        getFileStub.restore();
        saveFilesStub.restore();
        commitStub.restore();
        findWithLatestResultStub.restore();
        findWithParticipationsStub.restore();
        getLatestResultWithFeedbacksStub.restore();

        subscribeForLatestResultOfParticipationSubject = new BehaviorSubject<Result | null>(null);
        subscribeForLatestResultOfParticipationStub.returns(subscribeForLatestResultOfParticipationSubject);

        routeSubject = new Subject<Params>();
        // @ts-ignore
        (route as MockActivatedRoute).setSubject(routeSubject);

        findWithParticipationsSubject = new Subject<{ body: ProgrammingExercise }>();
        findWithParticipationsStub.returns(findWithParticipationsSubject);

        checkIfRepositoryIsCleanSubject = new Subject<{ isClean: boolean }>();
        checkIfRepositoryIsCleanStub.returns(checkIfRepositoryIsCleanSubject);

        getRepositoryContentSubject = new Subject<{ [p: string]: FileType }>();
        getRepositoryContentStub.returns(getRepositoryContentSubject);
    });

    it('should load the exercise and select the template participation if no participation id is provided', () => {
        jest.resetModules();
        // @ts-ignore
        const exercise = {
            id: 1,
            problemStatement,
            studentParticipations: [{ id: 2, repositoryUrl: 'test' }],
            templateParticipation: { id: 3, repositoryUrl: 'test2', results: [{ id: 9, successful: true }] },
            solutionParticipation: { id: 4, repositoryUrl: 'test3' },
        } as ProgrammingExercise;
        exercise.studentParticipations = exercise.studentParticipations.map((p) => {
            p.exercise = exercise;
            return p;
        });
        exercise.templateParticipation = { ...exercise.templateParticipation, programmingExercise: exercise };
        exercise.solutionParticipation = { ...exercise.solutionParticipation, programmingExercise: exercise };

        getFeedbackDetailsForResultStub.returns(of([]));
        const setDomainSpy = spy(domainService, 'setDomain');
        // @ts-ignore
        (container.router as MockRouter).setUrl('code-editor-instructor/1');
        container.ngOnInit();
        routeSubject.next({ exerciseId: 1 });

        expect(container.grid).not.to.exist;
        expect(findWithParticipationsStub).to.have.been.calledOnceWithExactly(exercise.id);
        expect(container.loadingState).to.equal(container.LOADING_STATE.INITIALIZING);

        findWithParticipationsSubject.next({ body: exercise });

        expect(getLatestResultWithFeedbacksStub).not.to.have.been.called;
        expect(setDomainSpy).to.have.been.calledOnce;
        expect(setDomainSpy).to.have.been.calledOnceWithExactly([DomainType.PARTICIPATION, exercise.templateParticipation]);
        expect(container.exercise).to.deep.equal(exercise);
        expect(container.selectedRepository).to.equal(container.REPOSITORY.TEMPLATE);
        expect(container.selectedParticipation).to.deep.equal(container.selectedParticipation);
        expect(container.loadingState).to.equal(container.LOADING_STATE.CLEAR);
        expect(container.domainChangeSubscription).to.exist;

        containerFixture.detectChanges();
        expect(container.grid).to.exist;

        checkIfRepositoryIsCleanSubject.next({ isClean: true });
        getRepositoryContentSubject.next({ file: FileType.FILE, folder: FileType.FOLDER });
        containerFixture.detectChanges();

        // Result is successful, build logs should not be retrieved
        expect(getBuildLogsStub).to.not.have.been.called;
        // Once called by each build-output & instructions
        expect(getFeedbackDetailsForResultStub).to.have.been.calledTwice;

        expect(container.grid).to.exist;
        expect(container.fileBrowser).to.exist;
        expect(container.actions).to.exist;
        expect(container.instructions).to.exist;
        expect(container.instructions.participation.id).to.deep.equal(exercise.templateParticipation.id);
        expect(container.resultComp).to.exist;
        expect(container.buildOutput).to.exist;
        expect(container.instructions.editableInstructions.exerciseHints).to.deep.equal(exerciseHints);

        // Called once by each build-output, instructions, result and twice by instructor-exercise-status (=templateParticipation,solutionParticipation) &
        expect(subscribeForLatestResultOfParticipationStub.callCount).to.equal(5);

        // called once by instructions (hints are only visible if assignment repo is selected).
        expect(getHintsForExerciseStub).to.have.been.calledOnce;
        expect(getHintsForExerciseStub).to.have.been.calledWithExactly(exercise.id);
    });

    it('should go into error state when loading the exercise failed', () => {
        const exercise = { id: 1, studentParticipations: [{ id: 2 }], templateParticipation: { id: 3 }, solutionParticipation: { id: 4 } } as ProgrammingExercise;
        const setDomainSpy = spy(domainService, 'setDomain');
        container.ngOnInit();
        routeSubject.next({ exerciseId: 1 });

        expect(container.grid).not.to.exist;
        expect(findWithParticipationsStub).to.have.been.calledOnceWithExactly(exercise.id);
        expect(container.loadingState).to.equal(container.LOADING_STATE.INITIALIZING);

        findWithParticipationsSubject.error('fatal error');

        expect(setDomainSpy).to.not.have.been.called;
        expect(container.loadingState).to.equal(container.LOADING_STATE.FETCHING_FAILED);
        expect(container.selectedRepository).to.be.undefined;

        containerFixture.detectChanges();
        expect(container.grid).not.to.exist;
    });

    it('should load test repository if specified in url', () => {
        const exercise = {
            id: 1,
            problemStatement,
            studentParticipations: [{ id: 2 }],
            templateParticipation: { id: 3 },
            solutionParticipation: { id: 4 },
        } as ProgrammingExercise;
        const setDomainSpy = spy(domainService, 'setDomain');
        // @ts-ignore
        (container.router as MockRouter).setUrl('code-editor-instructor/1/test');
        container.ngOnDestroy();
        container.ngOnInit();
        routeSubject.next({ exerciseId: 1 });

        expect(container.grid).not.to.exist;
        expect(findWithParticipationsStub).to.have.been.calledOnceWithExactly(exercise.id);
        expect(container.loadingState).to.equal(container.LOADING_STATE.INITIALIZING);

        findWithParticipationsSubject.next({ body: exercise });

        expect(setDomainSpy).to.have.been.calledOnceWithExactly([DomainType.TEST_REPOSITORY, exercise]);
        expect(container.selectedParticipation).not.to.exist;
        expect(container.selectedRepository).to.equal(container.REPOSITORY.TEST);
        expect(getBuildLogsStub).not.to.have.been.called;
        expect(getFeedbackDetailsForResultStub).not.to.have.been.called;

        containerFixture.detectChanges();

        expect(container.grid).to.exist;
        expect(container.fileBrowser).to.exist;
        expect(container.actions).to.exist;
        expect(container.instructions).to.exist;
        expect(container.instructions.participation).to.deep.equal(exercise.templateParticipation);
        expect(container.resultComp).not.to.exist;
        expect(container.buildOutput).not.to.exist;
    });

    it('should be able to switch between the repos and update the child components accordingly', () => {
        // @ts-ignore
        const exercise = {
            id: 1,
            problemStatement,
        } as ProgrammingExercise;
        exercise.templateParticipation = { id: 3, repositoryUrl: 'test2', programmingExercise: exercise } as TemplateProgrammingExerciseParticipation;
        exercise.solutionParticipation = { id: 4, repositoryUrl: 'test3', programmingExercise: exercise } as SolutionProgrammingExerciseParticipation;
        // @ts-ignore
        exercise.studentParticipations = [{ id: 2, repositoryUrl: 'test', exercise } as ProgrammingExerciseStudentParticipation];

        const setDomainSpy = spy(domainService, 'setDomain');

        // Start with assignment repository
        // @ts-ignore
        (container.router as MockRouter).setUrl('code-editor-instructor/1/2');
        container.ngOnInit();
        routeSubject.next({ exerciseId: 1, participationId: 2 });
        findWithParticipationsSubject.next({ body: exercise });

        containerFixture.detectChanges();

        expect(container.selectedRepository).to.equal(container.REPOSITORY.ASSIGNMENT);
        expect(container.selectedParticipation).to.deep.equal(exercise.studentParticipations[0]);
        expect(container.grid).to.exist;
        expect(container.fileBrowser).to.exist;
        expect(container.actions).to.exist;
        expect(container.instructions).to.exist;
        expect(container.resultComp).to.exist;
        expect(container.buildOutput).to.exist;
        expect(container.buildOutput.participation).to.deep.equal(exercise.studentParticipations[0]);
        expect(container.instructions.participation).to.deep.equal(exercise.studentParticipations[0]);

        // New select solution repository
        // @ts-ignore
        (container.router as MockRouter).setUrl('code-editor-instructor/1/4');
        routeSubject.next({ exerciseId: 1, participationId: 4 });

        containerFixture.detectChanges();

        expect(container.selectedRepository).to.equal(container.REPOSITORY.SOLUTION);
        expect(container.selectedParticipation).to.deep.equal(exercise.solutionParticipation);
        expect(container.grid).to.exist;
        expect(container.fileBrowser).to.exist;
        expect(container.actions).to.exist;
        expect(container.instructions).to.exist;
        expect(container.resultComp).to.exist;
        expect(container.buildOutput).to.exist;
        expect(container.buildOutput.participation).to.deep.equal(exercise.solutionParticipation);
        expect(container.instructions.participation).to.deep.equal(exercise.solutionParticipation);

        expect(findWithParticipationsStub).to.have.been.calledOnceWithExactly(exercise.id);
        expect(setDomainSpy).to.have.been.calledTwice;
        expect(setDomainSpy).to.have.been.calledWith([DomainType.PARTICIPATION, exercise.studentParticipations[0]]);
        expect(setDomainSpy).to.have.been.calledWith([DomainType.PARTICIPATION, exercise.solutionParticipation]);
    });

    it('should not be able to select a repository without repositoryUrl', () => {
        // @ts-ignore
        const exercise = {
            id: 1,
            problemStatement,
        } as ProgrammingExercise;
        // @ts-ignore
        exercise.studentParticipations = [{ id: 2, repositoryUrl: 'test', exercise } as ProgrammingExerciseStudentParticipation];
        exercise.templateParticipation = { id: 3, programmingExercise: exercise } as TemplateProgrammingExerciseParticipation;
        exercise.solutionParticipation = { id: 4, repositoryUrl: 'test3', programmingExercise: exercise } as SolutionProgrammingExerciseParticipation;

        const setDomainSpy = spy(domainService, 'setDomain');

        // Start with assignment repository
        // @ts-ignore
        (container.router as MockRouter).setUrl('code-editor-instructor/1/3');
        container.ngOnInit();
        routeSubject.next({ exerciseId: 1, participationId: 3 });
        findWithParticipationsSubject.next({ body: exercise });

        containerFixture.detectChanges();

        expect(setDomainSpy).to.have.been.calledOnce;
        expect(setDomainSpy).to.have.been.calledOnceWithExactly([DomainType.PARTICIPATION, exercise.solutionParticipation]);
        expect(container.selectedRepository).to.equal(container.REPOSITORY.SOLUTION);
        expect(container.selectedParticipation).to.deep.equal(exercise.solutionParticipation);
        expect(container.grid).to.exist;
        expect(container.fileBrowser).to.exist;
        expect(container.actions).to.exist;
        expect(container.instructions).to.exist;
        expect(container.resultComp).to.exist;
        expect(container.buildOutput).to.exist;
        expect(container.buildOutput.participation).to.deep.equal(exercise.solutionParticipation);
        expect(container.instructions.participation).to.deep.equal(exercise.solutionParticipation);
    });
});
