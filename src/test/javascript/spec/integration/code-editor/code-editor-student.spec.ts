import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateModule } from '@ngx-translate/core';
import * as moment from 'moment';
import { WindowRef } from 'app/core/websocket/window.service';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { AccountService } from 'app/core/auth/account.service';
import { ChangeDetectorRef, DebugElement } from '@angular/core';
import { ActivatedRoute, Params } from '@angular/router';
import { SinonStub, stub } from 'sinon';
import { BehaviorSubject, Observable, of, Subject } from 'rxjs';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import {
    ArtemisCodeEditorModule,
    CodeEditorBuildLogService,
    CodeEditorConflictStateService,
    CodeEditorRepositoryFileService,
    CodeEditorRepositoryService,
    CodeEditorSessionService,
    CodeEditorStudentContainerComponent,
    CommitState,
    DomainService,
    DomainType,
    EditorState,
    GitConflictState,
} from 'app/code-editor';
import { ExerciseHintService, IExerciseHintService } from 'app/entities/exercise-hint';
import { ArtemisTestModule } from '../../test.module';
import {
    MockActivatedRoute,
    MockCodeEditorBuildLogService,
    MockCodeEditorRepositoryFileService,
    MockCodeEditorRepositoryService,
    MockCodeEditorSessionService,
    MockExerciseHintService,
    MockParticipationWebsocketService,
    MockResultService,
    MockSyncStorage,
} from '../../mocks';
import { Result, ResultService } from 'app/entities/result';
import { Participation, StudentParticipation } from 'app/entities/participation';
import { ParticipationWebsocketService } from 'app/entities/participation/participation-websocket.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise';
import { ProgrammingExerciseParticipationService } from 'app/entities/programming-exercise/services/programming-exercise-participation.service';
import { DeleteFileChange, FileType } from 'app/entities/ace-editor/file-change.model';
import { buildLogs, extractedBuildLogErrors } from '../../sample/build-logs';
import { problemStatement } from '../../sample/problemStatement.json';
import { Feedback } from 'app/entities/feedback';
import { BuildLogEntryArray } from 'app/entities/build-log';
import { MockAccountService } from '../../mocks/mock-account.service';
import { MockProgrammingExerciseParticipationService } from '../../mocks/mock-programming-exercise-participation.service';
import { ProgrammingSubmissionService, ProgrammingSubmissionState, ProgrammingSubmissionStateObj } from 'app/programming-submission/programming-submission.service';
import { MockProgrammingSubmissionService } from '../../mocks/mock-programming-submission.service';
import { ProgrammingSubmission } from 'app/entities/programming-submission';
import { ExerciseHint } from 'app/entities/exercise-hint/exercise-hint.model';
import { DeviceDetectorService } from 'ngx-device-detector';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { getElement } from '../../utils/general.utils';
import { GuidedTourMapping } from 'app/guided-tour/guided-tour-setting.model';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { MockWebsocketService } from '../../mocks/mock-websocket.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('CodeEditorStudentIntegration', () => {
    let container: CodeEditorStudentContainerComponent;
    let containerFixture: ComponentFixture<CodeEditorStudentContainerComponent>;
    let containerDebugElement: DebugElement;
    let codeEditorRepositoryFileService: CodeEditorRepositoryFileService;
    let codeEditorRepositoryService: CodeEditorRepositoryService;
    let participationWebsocketService: ParticipationWebsocketService;
    let resultService: ResultService;
    let buildLogService: CodeEditorBuildLogService;
    let programmingExerciseParticipationService: ProgrammingExerciseParticipationService;
    let conflictService: CodeEditorConflictStateService;
    let domainService: DomainService;
    let submissionService: ProgrammingSubmissionService;
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
    let getStudentParticipationWithLatestResultStub: SinonStub;
    let getLatestPendingSubmissionStub: SinonStub;
    let getHintsForExerciseStub: SinonStub;
    let guidedTourService: GuidedTourService;

    let subscribeForLatestResultOfParticipationSubject: BehaviorSubject<Result>;
    let routeSubject: Subject<Params>;
    let getLatestPendingSubmissionSubject = new Subject<ProgrammingSubmissionStateObj>();

    const result = { id: 3, successful: false, completionDate: moment().subtract(2, 'days') };
    const exerciseHints = [{ id: 1 }, { id: 2 }];

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisCodeEditorModule],
            declarations: [],
            providers: [
                JhiLanguageHelper,
                WindowRef,
                ChangeDetectorRef,
                DeviceDetectorService,
                { provide: AccountService, useClass: MockAccountService },
                { provide: ActivatedRoute, useClass: MockActivatedRoute },
                { provide: JhiWebsocketService, useClass: MockWebsocketService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                { provide: ProgrammingExerciseParticipationService, useClass: MockProgrammingExerciseParticipationService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: ResultService, useClass: MockResultService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: CodeEditorRepositoryService, useClass: MockCodeEditorRepositoryService },
                { provide: CodeEditorRepositoryFileService, useClass: MockCodeEditorRepositoryFileService },
                { provide: CodeEditorBuildLogService, useClass: MockCodeEditorBuildLogService },
                { provide: CodeEditorSessionService, useClass: MockCodeEditorSessionService },
                { provide: ResultService, useClass: MockResultService },
                { provide: ProgrammingSubmissionService, useClass: MockProgrammingSubmissionService },
                { provide: ExerciseHintService, useClass: MockExerciseHintService },
            ],
        })
            .compileComponents()
            .then(() => {
                containerFixture = TestBed.createComponent(CodeEditorStudentContainerComponent);
                container = containerFixture.componentInstance;
                containerDebugElement = containerFixture.debugElement;
                guidedTourService = TestBed.get(GuidedTourService);

                codeEditorRepositoryService = containerDebugElement.injector.get(CodeEditorRepositoryService);
                codeEditorRepositoryFileService = containerDebugElement.injector.get(CodeEditorRepositoryFileService);
                participationWebsocketService = containerDebugElement.injector.get(ParticipationWebsocketService);
                resultService = containerDebugElement.injector.get(ResultService);
                buildLogService = containerDebugElement.injector.get(CodeEditorBuildLogService);
                programmingExerciseParticipationService = containerDebugElement.injector.get(ProgrammingExerciseParticipationService);
                route = containerDebugElement.injector.get(ActivatedRoute);
                conflictService = containerDebugElement.injector.get(CodeEditorConflictStateService);
                domainService = containerDebugElement.injector.get(DomainService);
                submissionService = containerDebugElement.injector.get(ProgrammingSubmissionService);
                exerciseHintService = containerDebugElement.injector.get(ExerciseHintService);

                subscribeForLatestResultOfParticipationSubject = new BehaviorSubject<Result>(null);

                routeSubject = new Subject<Params>();
                // @ts-ignore
                (route as MockActivatedRoute).setSubject(routeSubject);

                getLatestPendingSubmissionSubject = new Subject<ProgrammingSubmissionStateObj>();

                checkIfRepositoryIsCleanStub = stub(codeEditorRepositoryService, 'getStatus');
                getRepositoryContentStub = stub(codeEditorRepositoryFileService, 'getRepositoryContent');
                subscribeForLatestResultOfParticipationStub = stub(participationWebsocketService, 'subscribeForLatestResultOfParticipation').returns(
                    subscribeForLatestResultOfParticipationSubject,
                );
                getFeedbackDetailsForResultStub = stub(resultService, 'getFeedbackDetailsForResult');
                getBuildLogsStub = stub(buildLogService, 'getBuildLogs');
                getFileStub = stub(codeEditorRepositoryFileService, 'getFile');
                saveFilesStub = stub(codeEditorRepositoryFileService, 'updateFiles');
                commitStub = stub(codeEditorRepositoryService, 'commit');
                getStudentParticipationWithLatestResultStub = stub(programmingExerciseParticipationService, 'getStudentParticipationWithLatestResult');
                getLatestPendingSubmissionStub = stub(submissionService, 'getLatestPendingSubmissionByParticipationId').returns(getLatestPendingSubmissionSubject);
                getHintsForExerciseStub = stub(exerciseHintService, 'findByExerciseId').returns(of({ body: exerciseHints }) as Observable<HttpResponse<ExerciseHint[]>>);
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
        getStudentParticipationWithLatestResultStub.restore();

        subscribeForLatestResultOfParticipationSubject = new BehaviorSubject<Result>(null);
        subscribeForLatestResultOfParticipationStub.returns(subscribeForLatestResultOfParticipationSubject);

        routeSubject = new Subject<Params>();
        // @ts-ignore
        (route as MockActivatedRoute).setSubject(routeSubject);

        getLatestPendingSubmissionSubject = new Subject<ProgrammingSubmissionStateObj>();
        getLatestPendingSubmissionStub.returns(getLatestPendingSubmissionSubject);
    });

    const cleanInitialize = () => {
        const exercise = { id: 1, problemStatement };
        const participation = { id: 2, exercise, student: { id: 99 }, results: [result] } as Participation;
        const commitState = CommitState.UNDEFINED;
        const isCleanSubject = new Subject();
        const getRepositoryContentSubject = new Subject();
        const getBuildLogsSubject = new Subject();
        checkIfRepositoryIsCleanStub.returns(isCleanSubject);
        getRepositoryContentStub.returns(getRepositoryContentSubject);
        getFeedbackDetailsForResultStub.returns(of([]));
        getBuildLogsStub.returns(getBuildLogsSubject);
        getLatestPendingSubmissionStub.returns(getLatestPendingSubmissionSubject);

        container.participation = participation as any;
        container.exercise = exercise as ProgrammingExercise;
        container.commitState = commitState;
        // TODO: This should be replaced by testing with route params.
        domainService.setDomain([DomainType.PARTICIPATION, participation]);
        containerFixture.detectChanges();

        isCleanSubject.next({ repositoryStatus: CommitState.CLEAN });
        getBuildLogsSubject.next(buildLogs);
        getRepositoryContentSubject.next({ file: FileType.FILE, folder: FileType.FOLDER, file2: FileType.FILE });
        getLatestPendingSubmissionSubject.next([ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, null]);

        containerFixture.detectChanges();

        // container
        expect(container.commitState).to.equal(CommitState.CLEAN);
        expect(container.editorState).to.equal(EditorState.CLEAN);
        expect(container.buildLogErrors).to.deep.equal(extractedBuildLogErrors);
        expect(container.buildOutput.isBuilding).to.be.false;
        expect(container.unsavedFiles).to.be.empty;

        // file browser
        expect(checkIfRepositoryIsCleanStub).to.have.been.calledOnce;
        expect(getRepositoryContentStub).to.have.been.calledOnce;
        expect(container.fileBrowser.errorFiles).to.deep.equal(Object.keys(extractedBuildLogErrors.errors));
        expect(container.fileBrowser.unsavedFiles).to.be.empty;

        // ace editor
        expect(container.aceEditor.isLoading).to.be.false;
        expect(container.aceEditor.buildLogErrors).to.deep.equal(extractedBuildLogErrors);
        expect(container.aceEditor.commitState).to.equal(CommitState.CLEAN);

        // actions
        expect(container.actions.commitState).to.equal(CommitState.CLEAN);
        expect(container.actions.editorState).to.equal(EditorState.CLEAN);
        expect(container.actions.isBuilding).to.be.false;

        // status
        expect(container.fileBrowser.status.commitState).to.equal(CommitState.CLEAN);
        expect(container.fileBrowser.status.editorState).to.equal(EditorState.CLEAN);

        // build output
        expect(getBuildLogsStub).to.have.been.calledOnce;
        expect(container.buildOutput.buildLogErrors).to.deep.equal(extractedBuildLogErrors);
        expect(container.buildOutput.isBuilding).to.be.false;

        // instructions
        expect(container.instructions.participation).to.deep.equal(participation);
        expect(container.instructions.readOnlyInstructions).to.exist;
        expect(container.instructions.editableInstructions).not.to.exist;
        expect(container.instructions.readOnlyInstructions.exerciseHints).to.deep.equal(exerciseHints);

        // called by build output & instructions
        expect(getFeedbackDetailsForResultStub).to.have.been.calledTwice;
        expect(getFeedbackDetailsForResultStub).to.have.been.calledWithExactly(participation.results[0].id);

        // called once each by instructions and exercise hint component in status bar.
        expect(getHintsForExerciseStub).to.have.been.calledTwice;
        expect(getHintsForExerciseStub).to.have.been.calledWithExactly(exercise.id);
    };

    const loadFile = (fileName: string, fileContent: string) => {
        getFileStub.returns(of({ fileContent }));
        container.fileBrowser.selectedFile = fileName;
    };

    it('should initialize all components correctly if all server calls are successful', done => {
        cleanInitialize();
        setTimeout(() => {
            expect(subscribeForLatestResultOfParticipationStub).to.have.been.calledThrice;
            done();
        }, 0);
    });

    it('should not load files and render other components correctly if the repository status cannot be retrieved', (done: any) => {
        const exercise = { id: 1, problemStatement, course: { id: 2 } };
        const participation = { id: 2, exercise, results: [result] } as StudentParticipation;
        const commitState = CommitState.UNDEFINED;
        const isCleanSubject = new Subject();
        const getBuildLogsSubject = new Subject();
        checkIfRepositoryIsCleanStub.returns(isCleanSubject);
        subscribeForLatestResultOfParticipationStub.returns(of(null));
        getFeedbackDetailsForResultStub.returns(of([]));
        getBuildLogsStub.returns(getBuildLogsSubject);

        container.participation = participation;
        container.exercise = exercise as ProgrammingExercise;
        container.commitState = commitState;
        // TODO: This should be replaced by testing with route params.
        domainService.setDomain([DomainType.PARTICIPATION, participation]);
        containerFixture.detectChanges();

        isCleanSubject.error('fatal error');
        getBuildLogsSubject.next(buildLogs);
        getLatestPendingSubmissionSubject.next([ProgrammingSubmissionState.HAS_FAILED_SUBMISSION, null]);

        containerFixture.detectChanges();

        // container
        expect(container.commitState).to.equal(CommitState.COULD_NOT_BE_RETRIEVED);
        expect(container.editorState).to.equal(EditorState.CLEAN);
        expect(container.buildLogErrors).to.deep.equal(extractedBuildLogErrors);
        expect(container.buildOutput.isBuilding).to.be.false;
        expect(container.unsavedFiles).to.be.empty;

        // file browser
        expect(checkIfRepositoryIsCleanStub).to.have.been.calledOnce;
        expect(getRepositoryContentStub).to.not.have.been.calledOnce;
        expect(container.fileBrowser.errorFiles).to.deep.equal(Object.keys(extractedBuildLogErrors.errors));
        expect(container.fileBrowser.unsavedFiles).to.be.empty;

        // ace editor
        expect(container.aceEditor.isLoading).to.be.false;
        expect(container.aceEditor.buildLogErrors).to.deep.equal(extractedBuildLogErrors);
        expect(container.aceEditor.commitState).to.equal(CommitState.COULD_NOT_BE_RETRIEVED);

        // actions
        expect(container.actions.commitState).to.equal(CommitState.COULD_NOT_BE_RETRIEVED);
        expect(container.actions.editorState).to.equal(EditorState.CLEAN);
        expect(container.actions.isBuilding).to.be.false;

        // status
        expect(container.fileBrowser.status.commitState).to.equal(CommitState.COULD_NOT_BE_RETRIEVED);
        expect(container.fileBrowser.status.editorState).to.equal(EditorState.CLEAN);

        // build output
        expect(getBuildLogsStub).to.have.been.calledOnce;
        expect(container.buildOutput.buildLogErrors).to.deep.equal(extractedBuildLogErrors);
        expect(container.buildOutput.isBuilding).to.be.false;

        // instructions
        expect(container.instructions.participation).to.deep.equal(participation);
        expect(container.instructions.readOnlyInstructions).to.exist;
        expect(container.instructions.editableInstructions).not.to.exist;

        // called by build output & instructions
        expect(getFeedbackDetailsForResultStub).to.have.been.calledTwice;
        expect(getFeedbackDetailsForResultStub).to.have.been.calledWithExactly(participation.results[0].id);

        setTimeout(() => {
            // called by build output, instructions & result
            expect(subscribeForLatestResultOfParticipationStub).to.have.been.calledThrice;
            done();
        }, 0);
    });

    it('should update the file browser and ace editor on file selection', () => {
        cleanInitialize();
        const selectedFile = Object.keys(container.fileBrowser.repositoryFiles)[0];
        const fileContent = 'lorem ipsum';
        loadFile(selectedFile, fileContent);

        containerFixture.detectChanges();

        expect(container.selectedFile).to.equal(selectedFile);
        expect(container.aceEditor.selectedFile).to.equal(selectedFile);
        expect(container.aceEditor.isLoading).to.be.false;
        expect(Object.keys(container.aceEditor.fileSession)).to.contain(selectedFile);
        expect(getFileStub).to.have.been.calledOnceWithExactly(selectedFile);

        containerFixture.detectChanges();
        expect(
            container.aceEditor.editor
                .getEditor()
                .getSession()
                .getValue(),
        ).to.equal(fileContent);
    });

    it('should mark file to have unsaved changes in file tree if the file was changed in editor', () => {
        cleanInitialize();
        const selectedFile = Object.keys(container.fileBrowser.repositoryFiles)[0];
        const fileContent = 'lorem ipsum';
        const newFileContent = 'new lorem ipsum';
        loadFile(selectedFile, fileContent);

        containerFixture.detectChanges();
        container.aceEditor.onFileTextChanged(newFileContent);
        containerFixture.detectChanges();

        expect(getFileStub).to.have.been.calledOnceWithExactly(selectedFile);
        expect(container.unsavedFiles).to.deep.equal({ [selectedFile]: newFileContent });
        expect(container.fileBrowser.unsavedFiles).to.deep.equal([selectedFile]);
        expect(container.editorState).to.equal(EditorState.UNSAVED_CHANGES);
        expect(container.actions.editorState).to.equal(EditorState.UNSAVED_CHANGES);
    });

    it('should save files and remove unsaved status of saved files afterwards', () => {
        // setup
        cleanInitialize();
        const selectedFile = Object.keys(container.fileBrowser.repositoryFiles)[0];
        const otherFileWithUnsavedChanges = Object.keys(container.fileBrowser.repositoryFiles)[2];
        const fileContent = 'lorem ipsum';
        const newFileContent = 'new lorem ipsum';
        const saveFilesSubject = new Subject();
        saveFilesStub.returns(saveFilesSubject);
        container.unsavedFiles = { [otherFileWithUnsavedChanges]: 'lorem ipsum dolet', [selectedFile]: newFileContent };
        loadFile(selectedFile, fileContent);
        containerFixture.detectChanges();

        // init saving
        container.actions.saveChangedFiles().subscribe();
        expect(container.commitState).to.equal(CommitState.CLEAN);
        expect(container.editorState).to.equal(EditorState.SAVING);

        // emit saving result
        saveFilesSubject.next({ [selectedFile]: null, [otherFileWithUnsavedChanges]: null });
        containerFixture.detectChanges();

        // check if saving result updates comps as expected
        expect(container.unsavedFiles).to.be.empty;
        expect(container.editorState).to.equal(EditorState.CLEAN);
        expect(container.commitState).to.equal(CommitState.UNCOMMITTED_CHANGES);
        expect(container.fileBrowser.unsavedFiles).to.be.empty;
        expect(container.actions.editorState).to.equal(EditorState.CLEAN);
    });

    it('should remove the unsaved changes flag in all components if the unsaved file is deleted', () => {
        cleanInitialize();
        const repositoryFiles = { file: FileType.FILE, file2: FileType.FILE, folder: FileType.FOLDER };
        const expectedFilesAfterDelete = { file2: FileType.FILE, folder: FileType.FOLDER };
        const unsavedChanges = { file: 'lorem ipsum' };
        container.fileBrowser.repositoryFiles = repositoryFiles;
        container.unsavedFiles = unsavedChanges;

        containerFixture.detectChanges();

        expect(container.fileBrowser.unsavedFiles).to.deep.equal(Object.keys(unsavedChanges));
        expect(container.actions.editorState).to.equal(EditorState.UNSAVED_CHANGES);

        container.fileBrowser.onFileDeleted(new DeleteFileChange(FileType.FILE, 'file'));
        containerFixture.detectChanges();

        expect(container.unsavedFiles).to.be.empty;
        expect(container.fileBrowser.repositoryFiles).to.deep.equal(expectedFilesAfterDelete);
        expect(container.actions.editorState).to.equal(EditorState.CLEAN);
    });

    it('should wait for build result after submission if no unsaved changes exist', () => {
        cleanInitialize();
        const result = { id: 4, successful: true, feedbacks: [] as Feedback[], participation: { id: 3 } } as Result;
        const expectedBuildLog = new BuildLogEntryArray();
        expect(container.unsavedFiles).to.be.empty;
        container.commitState = CommitState.UNCOMMITTED_CHANGES;
        containerFixture.detectChanges();

        // commit
        expect(container.actions.commitState).to.equal(CommitState.UNCOMMITTED_CHANGES);
        commitStub.returns(of(null));
        getLatestPendingSubmissionSubject.next({
            submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION,
            submission: {} as ProgrammingSubmission,
            participationId: result!.participation!.id,
        });
        container.actions.commit();
        containerFixture.detectChanges();

        // waiting for build result
        expect(container.commitState).to.equal(CommitState.CLEAN);
        expect(container.buildOutput.isBuilding).to.be.true;

        getLatestPendingSubmissionSubject.next({
            submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION,
            submission: null,
            participationId: result!.participation!.id,
        });
        subscribeForLatestResultOfParticipationSubject.next(result);
        containerFixture.detectChanges();

        expect(container.buildOutput.isBuilding).to.be.false;
        expect(container.buildOutput.rawBuildLogs).to.deep.equal(expectedBuildLog);
        expect(container.fileBrowser.errorFiles).to.be.empty;
    });

    it('should first save unsaved files before triggering commit', () => {
        cleanInitialize();
        const successfulResult = { id: 4, successful: true, feedbacks: [] as Feedback[], participation: { id: 3 } } as Result;
        const expectedBuildLog = new BuildLogEntryArray();
        const unsavedFile = Object.keys(container.fileBrowser.repositoryFiles)[0];
        const saveFilesSubject = new Subject();
        const commitSubject = new Subject();
        saveFilesStub.returns(saveFilesSubject);
        commitStub.returns(commitSubject);
        container.unsavedFiles = { [unsavedFile]: 'lorem ipsum' };
        container.editorState = EditorState.UNSAVED_CHANGES;
        container.commitState = CommitState.UNCOMMITTED_CHANGES;
        containerFixture.detectChanges();

        // trying to commit
        container.actions.commit();
        containerFixture.detectChanges();

        // saving before commit
        expect(saveFilesStub).to.have.been.calledOnceWithExactly([{ fileName: unsavedFile, fileContent: 'lorem ipsum' }]);
        expect(container.editorState).to.equal(EditorState.SAVING);
        expect(container.fileBrowser.status.editorState).to.equal(EditorState.SAVING);
        expect(container.commitState).to.equal(CommitState.UNCOMMITTED_CHANGES);
        expect(container.fileBrowser.status.commitState).to.equal(CommitState.UNCOMMITTED_CHANGES);
        saveFilesSubject.next({ [unsavedFile]: null });

        // committing
        expect(commitStub).to.have.been.calledOnce;
        expect(container.commitState).to.equal(CommitState.COMMITTING);
        expect(container.editorState).to.equal(EditorState.CLEAN);
        subscribeForLatestResultOfParticipationSubject.next(successfulResult);
        getLatestPendingSubmissionSubject.next({
            submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION,
            submission: {} as ProgrammingSubmission,
            participationId: successfulResult!.participation!.id,
        });
        commitSubject.next(null);
        containerFixture.detectChanges();

        // waiting for build result
        expect(container.commitState).to.equal(CommitState.CLEAN);
        expect(container.buildOutput.isBuilding).to.be.true;

        getLatestPendingSubmissionSubject.next({
            submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION,
            submission: null,
            participationId: successfulResult!.participation!.id,
        });
        containerFixture.detectChanges();

        expect(container.buildOutput.isBuilding).to.be.false;
        expect(container.buildOutput.rawBuildLogs).to.deep.equal(expectedBuildLog);
        expect(container.fileBrowser.errorFiles).to.be.empty;
    });

    it('should initialize correctly on route change if participation can be retrieved', () => {
        container.ngOnInit();
        const participation = { id: 1, results: [result], exercise: { id: 99 } } as Participation;
        const feedbacks = [{ id: 2 }] as Feedback[];
        const findWithLatestResultSubject = new Subject<Participation>();
        const getFeedbackDetailsForResultSubject = new Subject<{ body: Feedback[] }>();
        getStudentParticipationWithLatestResultStub.returns(findWithLatestResultSubject);
        getFeedbackDetailsForResultStub.returns(getFeedbackDetailsForResultSubject);

        routeSubject.next({ participationId: 1 });

        expect(container.loadingParticipation).to.be.true;

        findWithLatestResultSubject.next(participation);
        getFeedbackDetailsForResultSubject.next({ body: feedbacks });

        expect(getStudentParticipationWithLatestResultStub).to.have.been.calledOnceWithExactly(participation.id);
        expect(getFeedbackDetailsForResultStub).to.have.been.calledOnceWithExactly(result.id);
        expect(container.loadingParticipation).to.be.false;
        expect(container.participationCouldNotBeFetched).to.be.false;
        expect(container.participation).to.deep.equal({ ...participation, results: [{ ...result, feedbacks }] });
    });

    it('should show the repository locked badge and disable the editor actions if the exercises buildAndTestAfterDueDate is set and the due date has passed', () => {
        container.ngOnInit();
        const participation = {
            id: 1,
            results: [result],
            exercise: { id: 99, buildAndTestStudentSubmissionsAfterDueDate: moment().subtract(1, 'hours'), dueDate: moment().subtract(2, 'hours') } as ProgrammingExercise,
        } as any;
        const feedbacks = [{ id: 2 }] as Feedback[];
        const findWithLatestResultSubject = new Subject<Participation>();
        const getFeedbackDetailsForResultSubject = new Subject<{ body: Feedback[] }>();
        const isCleanSubject = new Subject();
        getStudentParticipationWithLatestResultStub.returns(findWithLatestResultSubject);
        getFeedbackDetailsForResultStub.returns(getFeedbackDetailsForResultSubject);
        checkIfRepositoryIsCleanStub.returns(isCleanSubject);

        routeSubject.next({ participationId: 1 });
        findWithLatestResultSubject.next(participation);
        getFeedbackDetailsForResultSubject.next({ body: feedbacks });

        containerFixture.detectChanges();
        isCleanSubject.next({ repositoryStatus: CommitState.CLEAN });

        // Repository should be locked, the student can't write into it anymore.
        expect(container.repositoryIsLocked).to.be.true;
        expect(getElement(containerDebugElement, '.locked-container')).to.exist;
        expect(container.fileBrowser.disableActions).to.be.true;
        expect(container.actions.disableActions).to.be.true;
    });

    it('should abort initialization and show error state if participation cannot be retrieved', () => {
        container.ngOnInit();
        const findWithLatestResultSubject = new Subject<{ body: Participation }>();
        getStudentParticipationWithLatestResultStub.returns(findWithLatestResultSubject);

        routeSubject.next({ participationId: 1 });

        expect(container.loadingParticipation).to.be.true;

        findWithLatestResultSubject.error('fatal error');

        expect(container.loadingParticipation).to.be.false;
        expect(container.participationCouldNotBeFetched).to.be.true;
        expect(getFeedbackDetailsForResultStub).to.not.have.been.called;
        expect(container.participation).to.be.undefined;
    });

    it('should enter conflict mode if a git conflict between local and remote arises', fakeAsync(() => {
        const guidedTourMapping = {} as GuidedTourMapping;
        spyOn<any>(guidedTourService, 'checkTourState').and.returnValue(true);
        guidedTourService.guidedTourMapping = guidedTourMapping;
        container.ngOnInit();
        const exercise = { id: 1, problemStatement };
        const result = { id: 3, successful: false };
        const participation = { id: 1, results: [result], exercise: { id: 99 } } as Participation;
        const feedbacks = [{ id: 2 }] as Feedback[];
        const findWithLatestResultSubject = new Subject<Participation>();
        const getFeedbackDetailsForResultSubject = new Subject<{ body: Feedback[] }>();
        const isCleanSubject = new Subject();
        getStudentParticipationWithLatestResultStub.returns(findWithLatestResultSubject);
        checkIfRepositoryIsCleanStub.returns(isCleanSubject);
        getFeedbackDetailsForResultStub.returns(getFeedbackDetailsForResultSubject);
        getRepositoryContentStub.returns(of([]));

        routeSubject.next({ participationId: 1 });

        containerFixture.detectChanges();

        findWithLatestResultSubject.next(participation);
        getFeedbackDetailsForResultSubject.next({ body: feedbacks });

        containerFixture.detectChanges();

        // Create conflict.
        isCleanSubject.next({ repositoryStatus: CommitState.CONFLICT });
        containerFixture.detectChanges();

        expect(container.commitState).to.equal(CommitState.CONFLICT);
        expect(getRepositoryContentStub).to.not.have.been.called;

        // Resolve conflict.
        conflictService.notifyConflictState(GitConflictState.OK);
        tick();
        containerFixture.detectChanges();
        isCleanSubject.next({ repositoryStatus: CommitState.CLEAN });
        containerFixture.detectChanges();

        expect(container.commitState).to.equal(CommitState.CLEAN);
        expect(getRepositoryContentStub).to.calledOnce;

        containerFixture.destroy();
        flush();
    }));
});
