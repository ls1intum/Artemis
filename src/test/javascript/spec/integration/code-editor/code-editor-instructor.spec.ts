import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockComponent } from 'ng-mocks';
import { TranslateModule } from '@ngx-translate/core';
import { AccountService, JhiLanguageHelper, WindowRef } from 'app/core';
import { ChangeDetectorRef, DebugElement } from '@angular/core';
import { ActivatedRoute, Params, Router } from '@angular/router';
import { SinonStub, spy, stub } from 'sinon';
import { BehaviorSubject, of, Subject } from 'rxjs';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { AceEditorModule } from 'ng2-ace-editor';
import { TreeviewModule } from 'ngx-treeview';
import {
    ArTEMiSCodeEditorModule,
    CodeEditorBuildLogService,
    CodeEditorFileService,
    CodeEditorInstructorContainerComponent,
    CodeEditorRepositoryFileService,
    CodeEditorRepositoryService,
    CodeEditorSessionService,
    DomainService,
    DomainType,
} from 'app/code-editor';
import { ArTEMiSTestModule } from '../../test.module';
import {
    MockCodeEditorBuildLogService,
    MockCodeEditorRepositoryFileService,
    MockCodeEditorRepositoryService,
    MockCodeEditorSessionService,
    MockParticipationService,
    MockParticipationWebsocketService,
    MockProgrammingExerciseService,
    MockResultService,
    MockSyncStorage,
} from '../../mocks';
import { ArTEMiSResultModule, Result, ResultService, UpdatingResultComponent } from 'app/entities/result';
import { ArTEMiSSharedModule } from 'app/shared';
import { ArTEMiSProgrammingExerciseModule } from 'app/entities/programming-exercise/programming-exercise.module';
import { ParticipationService, ParticipationWebsocketService } from 'app/entities/participation';
import { ProgrammingExercise, ProgrammingExerciseService } from 'app/entities/programming-exercise';
import { FileType } from 'app/entities/ace-editor/file-change.model';
import { MockActivatedRoute } from '../../mocks/mock-activated.route';
import { MockAccountService } from '../../mocks/mock-account.service';
import { MockRouter } from '../../mocks/mock-router.service';
import { BuildLogEntryArray } from 'app/entities/build-log';
import { problemStatement } from '../../sample/problemStatement.json';

chai.use(sinonChai);
const expect = chai.expect;

describe('CodeEditorInstructorIntegration', () => {
    let container: CodeEditorInstructorContainerComponent;
    let containerFixture: ComponentFixture<CodeEditorInstructorContainerComponent>;
    let containerDebugElement: DebugElement;
    let codeEditorRepositoryFileService: CodeEditorRepositoryFileService;
    let codeEditorRepositoryService: CodeEditorRepositoryService;
    let participationWebsocketService: ParticipationWebsocketService;
    let resultService: ResultService;
    let buildLogService: CodeEditorBuildLogService;
    let participationService: ParticipationService;
    let programmingExerciseService: ProgrammingExerciseService;
    let domainService: DomainService;
    let route: ActivatedRoute;
    let router: Router;

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

    let checkIfRepositoryIsCleanSubject: Subject<{ isClean: boolean }>;
    let getRepositoryContentSubject: Subject<{ [fileName: string]: FileType }>;
    let subscribeForLatestResultOfParticipationSubject: BehaviorSubject<Result>;
    let findWithParticipationsSubject: Subject<{ body: ProgrammingExercise }>;
    let routeSubject: Subject<Params>;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [
                TranslateModule.forRoot(),
                ArTEMiSTestModule,
                AceEditorModule,
                TreeviewModule.forRoot(),
                ArTEMiSSharedModule,
                ArTEMiSProgrammingExerciseModule,
                ArTEMiSResultModule,
                ArTEMiSCodeEditorModule,
            ],
            declarations: [MockComponent(UpdatingResultComponent)],
            providers: [
                JhiLanguageHelper,
                WindowRef,
                CodeEditorFileService,
                ChangeDetectorRef,
                { provide: Router, useClass: MockRouter },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ActivatedRoute, useClass: MockActivatedRoute },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: ResultService, useClass: MockResultService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: CodeEditorRepositoryService, useClass: MockCodeEditorRepositoryService },
                { provide: CodeEditorRepositoryFileService, useClass: MockCodeEditorRepositoryFileService },
                { provide: CodeEditorBuildLogService, useClass: MockCodeEditorBuildLogService },
                { provide: CodeEditorSessionService, useClass: MockCodeEditorSessionService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                { provide: ResultService, useClass: MockResultService },
                { provide: ParticipationService, useClass: MockParticipationService },
                { provide: ProgrammingExerciseService, useClass: MockProgrammingExerciseService },
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
                programmingExerciseService = containerDebugElement.injector.get(ProgrammingExerciseService);
                domainService = containerDebugElement.injector.get(DomainService);
                route = containerDebugElement.injector.get(ActivatedRoute);
                router = containerDebugElement.injector.get(Router);

                checkIfRepositoryIsCleanSubject = new Subject<{ isClean: boolean }>();
                getRepositoryContentSubject = new Subject<{ [fileName: string]: FileType }>();
                subscribeForLatestResultOfParticipationSubject = new BehaviorSubject<Result>(null);
                findWithParticipationsSubject = new Subject<{ body: ProgrammingExercise }>();

                routeSubject = new Subject<Params>();
                // @ts-ignore
                (route as MockActivatedRoute).setSubject(routeSubject);

                checkIfRepositoryIsCleanStub = stub(codeEditorRepositoryService, 'isClean');
                getRepositoryContentStub = stub(codeEditorRepositoryFileService, 'getRepositoryContent');
                subscribeForLatestResultOfParticipationStub = stub(participationWebsocketService, 'subscribeForLatestResultOfParticipation').returns(
                    subscribeForLatestResultOfParticipationSubject,
                );
                getFeedbackDetailsForResultStub = stub(resultService, 'getFeedbackDetailsForResult');
                getBuildLogsStub = stub(buildLogService, 'getBuildLogs');
                getFileStub = stub(codeEditorRepositoryFileService, 'getFile');
                saveFilesStub = stub(codeEditorRepositoryFileService, 'updateFiles');
                commitStub = stub(codeEditorRepositoryService, 'commit');
                findWithLatestResultStub = stub(participationService, 'findWithLatestResult');

                findWithParticipationsStub = stub(programmingExerciseService, 'findWithParticipations');
                findWithParticipationsStub.returns(findWithParticipationsSubject);

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

        subscribeForLatestResultOfParticipationSubject = new BehaviorSubject<Result>(null);
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
        const exercise = {
            id: 1,
            problemStatement,
            participations: [{ id: 2 }],
            templateParticipation: { id: 3, results: [{ id: 9, successful: true }] },
            solutionParticipation: { id: 4 },
        } as ProgrammingExercise;
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
        // Once called by each build-output, instructions & result
        expect(subscribeForLatestResultOfParticipationStub).to.have.been.calledThrice;
    });

    it('should go into error state when loading the exercise failed', () => {
        const exercise = { id: 1, participations: [{ id: 2 }], templateParticipation: { id: 3 }, solutionParticipation: { id: 4 } } as ProgrammingExercise;
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
});
