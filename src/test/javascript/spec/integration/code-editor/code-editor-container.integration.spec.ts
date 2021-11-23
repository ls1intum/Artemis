import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateModule } from '@ngx-translate/core';
import dayjs from 'dayjs';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { AccountService } from 'app/core/auth/account.service';
import { ChangeDetectorRef, DebugElement } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { BehaviorSubject, of, Subject } from 'rxjs';
import * as ace from 'brace';
import { ArtemisTestModule } from '../../test.module';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { CommitState, DeleteFileChange, DomainType, EditorState, FileType, GitConflictState } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { buildLogs, extractedBuildLogErrors, extractedErrorFiles } from '../../helpers/sample/build-logs';
import { problemStatement } from '../../helpers/sample/problemStatement.json';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { MockProgrammingExerciseParticipationService } from '../../helpers/mocks/service/mock-programming-exercise-participation.service';
import { ProgrammingSubmissionService, ProgrammingSubmissionState, ProgrammingSubmissionStateObj } from 'app/exercises/programming/participate/programming-submission.service';
import { MockProgrammingSubmissionService } from '../../helpers/mocks/service/mock-programming-submission.service';
import { DeviceDetectorService } from 'ngx-device-detector';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { GuidedTourMapping } from 'app/guided-tour/guided-tour-setting.model';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { MockWebsocketService } from '../../helpers/mocks/service/mock-websocket.service';
import { Participation } from 'app/entities/participation/participation.model';
import { BuildLogEntryArray } from 'app/entities/build-log.model';
import { CodeEditorConflictStateService } from 'app/exercises/programming/shared/code-editor/service/code-editor-conflict-state.service';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Result } from 'app/entities/result.model';
import {
    CodeEditorBuildLogService,
    CodeEditorRepositoryFileService,
    CodeEditorRepositoryService,
} from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { Feedback } from 'app/entities/feedback.model';
import { ExerciseHintService } from 'app/exercises/shared/exercise-hint/manage/exercise-hint.service';
import { DomainService } from 'app/exercises/programming/shared/code-editor/service/code-editor-domain.service';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { MockActivatedRouteWithSubjects } from '../../helpers/mocks/activated-route/mock-activated-route-with-subjects';
import { MockParticipationWebsocketService } from '../../helpers/mocks/service/mock-participation-websocket.service';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockResultService } from '../../helpers/mocks/service/mock-result.service';
import { MockCodeEditorRepositoryService } from '../../helpers/mocks/service/mock-code-editor-repository.service';
import { MockExerciseHintService } from '../../helpers/mocks/service/mock-exercise-hint.service';
import { MockCodeEditorRepositoryFileService } from '../../helpers/mocks/service/mock-code-editor-repository-file.service';
import { MockCodeEditorBuildLogService } from '../../helpers/mocks/service/mock-code-editor-build-log.service';
import { CodeEditorContainerComponent } from 'app/exercises/programming/shared/code-editor/container/code-editor-container.component';
import { omit } from 'lodash-es';
import { ProgrammingLanguage } from 'app/entities/programming-exercise.model';
import { CodeEditorGridComponent } from 'app/exercises/programming/shared/code-editor/layout/code-editor-grid.component';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { CodeEditorActionsComponent } from 'app/exercises/programming/shared/code-editor/actions/code-editor-actions.component';
import { CodeEditorFileBrowserComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser.component';
import { Annotation, CodeEditorAceComponent } from 'app/exercises/programming/shared/code-editor/ace/code-editor-ace.component';
import { CodeEditorInstructionsComponent } from 'app/exercises/programming/shared/code-editor/instructions/code-editor-instructions.component';
import { CodeEditorBuildOutputComponent } from 'app/exercises/programming/shared/code-editor/build-output/code-editor-build-output.component';
import { KeysPipe } from 'app/shared/pipes/keys.pipe';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { FeatureToggleLinkDirective } from 'app/shared/feature-toggle/feature-toggle-link.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CodeEditorFileBrowserCreateNodeComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser-create-node.component';
import { CodeEditorStatusComponent } from 'app/exercises/programming/shared/code-editor/status/code-editor-status.component';
import { CodeEditorFileBrowserFolderComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser-folder.component';
import { CodeEditorFileBrowserFileComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser-file.component';
import { TreeviewComponent } from 'ngx-treeview';
import { CodeEditorTutorAssessmentInlineFeedbackComponent } from 'app/exercises/programming/assess/code-editor-tutor-assessment-inline-feedback.component';
import { AceEditorModule } from 'ng2-ace-editor';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';

describe('CodeEditorContainerIntegration', () => {
    // needed to make sure ace is defined
    ace.acequire('ace/ext/modelist');
    let container: CodeEditorContainerComponent;
    let containerFixture: ComponentFixture<CodeEditorContainerComponent>;
    let containerDebugElement: DebugElement;
    let conflictService: CodeEditorConflictStateService;
    let domainService: DomainService;
    let checkIfRepositoryIsCleanStub: jest.SpyInstance;
    let getRepositoryContentStub: jest.SpyInstance;
    let subscribeForLatestResultOfParticipationStub: jest.SpyInstance;
    let getFeedbackDetailsForResultStub: jest.SpyInstance;
    let getBuildLogsStub: jest.SpyInstance;
    let getFileStub: jest.SpyInstance;
    let saveFilesStub: jest.SpyInstance;
    let commitStub: jest.SpyInstance;
    let getStudentParticipationWithLatestResultStub: jest.SpyInstance;
    let getLatestPendingSubmissionStub: jest.SpyInstance;
    let guidedTourService: GuidedTourService;
    let subscribeForLatestResultOfParticipationSubject: BehaviorSubject<Result | undefined>;
    let getLatestPendingSubmissionSubject = new Subject<ProgrammingSubmissionStateObj>();

    const result = { id: 3, successful: false, completionDate: dayjs().subtract(2, 'days') };

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, AceEditorModule],
            declarations: [
                CodeEditorContainerComponent,
                MockComponent(CodeEditorGridComponent),
                MockComponent(CodeEditorInstructionsComponent),
                KeysPipe,
                MockDirective(FeatureToggleDirective),
                MockDirective(FeatureToggleLinkDirective),
                MockPipe(ArtemisTranslatePipe),
                CodeEditorActionsComponent,
                CodeEditorFileBrowserComponent,
                CodeEditorBuildOutputComponent,
                CodeEditorAceComponent,
                MockComponent(CodeEditorFileBrowserCreateNodeComponent),
                MockComponent(CodeEditorFileBrowserFolderComponent),
                MockComponent(CodeEditorFileBrowserFileComponent),
                MockComponent(CodeEditorStatusComponent),
                MockComponent(TreeviewComponent),
                MockPipe(ArtemisDatePipe),
                MockComponent(CodeEditorTutorAssessmentInlineFeedbackComponent),
            ],
            providers: [
                JhiLanguageHelper,
                ChangeDetectorRef,
                DeviceDetectorService,
                CodeEditorConflictStateService,
                { provide: AccountService, useClass: MockAccountService },
                { provide: ActivatedRoute, useClass: MockActivatedRouteWithSubjects },
                { provide: JhiWebsocketService, useClass: MockWebsocketService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                { provide: ProgrammingExerciseParticipationService, useClass: MockProgrammingExerciseParticipationService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: ResultService, useClass: MockResultService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: CodeEditorRepositoryService, useClass: MockCodeEditorRepositoryService },
                { provide: CodeEditorRepositoryFileService, useClass: MockCodeEditorRepositoryFileService },
                { provide: CodeEditorBuildLogService, useClass: MockCodeEditorBuildLogService },
                { provide: ResultService, useClass: MockResultService },
                { provide: ProgrammingSubmissionService, useClass: MockProgrammingSubmissionService },
                { provide: ExerciseHintService, useClass: MockExerciseHintService },
            ],
        })
            .compileComponents()
            .then(() => {
                containerFixture = TestBed.createComponent(CodeEditorContainerComponent);
                container = containerFixture.componentInstance;
                containerDebugElement = containerFixture.debugElement;
                guidedTourService = TestBed.inject(GuidedTourService);

                const codeEditorRepositoryService = containerDebugElement.injector.get(CodeEditorRepositoryService);
                const codeEditorRepositoryFileService = containerDebugElement.injector.get(CodeEditorRepositoryFileService);
                const participationWebsocketService = containerDebugElement.injector.get(ParticipationWebsocketService);
                const resultService = containerDebugElement.injector.get(ResultService);
                const buildLogService = containerDebugElement.injector.get(CodeEditorBuildLogService);
                const programmingExerciseParticipationService = containerDebugElement.injector.get(ProgrammingExerciseParticipationService);
                conflictService = containerDebugElement.injector.get(CodeEditorConflictStateService);
                domainService = containerDebugElement.injector.get(DomainService);
                const submissionService = containerDebugElement.injector.get(ProgrammingSubmissionService);

                subscribeForLatestResultOfParticipationSubject = new BehaviorSubject<Result | undefined>(undefined);

                getLatestPendingSubmissionSubject = new Subject<ProgrammingSubmissionStateObj>();

                checkIfRepositoryIsCleanStub = jest.spyOn(codeEditorRepositoryService, 'getStatus');
                getRepositoryContentStub = jest.spyOn(codeEditorRepositoryFileService, 'getRepositoryContent');
                subscribeForLatestResultOfParticipationStub = jest
                    .spyOn(participationWebsocketService, 'subscribeForLatestResultOfParticipation')
                    .mockReturnValue(subscribeForLatestResultOfParticipationSubject);
                getFeedbackDetailsForResultStub = jest.spyOn(resultService, 'getFeedbackDetailsForResult');
                getBuildLogsStub = jest.spyOn(buildLogService, 'getBuildLogs');
                getFileStub = jest.spyOn(codeEditorRepositoryFileService, 'getFile');
                saveFilesStub = jest.spyOn(codeEditorRepositoryFileService, 'updateFiles');
                commitStub = jest.spyOn(codeEditorRepositoryService, 'commit');
                getStudentParticipationWithLatestResultStub = jest.spyOn(programmingExerciseParticipationService, 'getStudentParticipationWithLatestResult');
                getLatestPendingSubmissionStub = jest.spyOn(submissionService, 'getLatestPendingSubmissionByParticipationId').mockReturnValue(getLatestPendingSubmissionSubject);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
        //checkIfRepositoryIsCleanStub.mockRestore();
        //getRepositoryContentStub.mockRestore();
        //subscribeForLatestResultOfParticipationStub.mockRestore();
        //getFeedbackDetailsForResultStub.mockRestore();
        //getBuildLogsStub.mockRestore();
        //getFileStub.mockRestore();
        // saveFilesStub.mockRestore();
        //commitStub.mockRestore();
        //getStudentParticipationWithLatestResultStub.mockRestore();

        subscribeForLatestResultOfParticipationSubject = new BehaviorSubject<Result | undefined>(undefined);
        subscribeForLatestResultOfParticipationStub.mockReturnValue(subscribeForLatestResultOfParticipationSubject);

        getLatestPendingSubmissionSubject = new Subject<ProgrammingSubmissionStateObj>();
        getLatestPendingSubmissionStub.mockReturnValue(getLatestPendingSubmissionSubject);
    });

    const cleanInitialize = () => {
        const exercise = { id: 1, problemStatement };
        const participation = { id: 2, exercise, student: { id: 99 }, results: [result] } as StudentParticipation;
        const commitState = CommitState.UNDEFINED;
        const isCleanSubject = new Subject();
        const getRepositoryContentSubject = new Subject();
        const getBuildLogsSubject = new Subject();
        checkIfRepositoryIsCleanStub.mockReturnValue(isCleanSubject);
        getRepositoryContentStub.mockReturnValue(getRepositoryContentSubject);
        getFeedbackDetailsForResultStub.mockReturnValue(of([]));
        getBuildLogsStub.mockReturnValue(getBuildLogsSubject);
        getLatestPendingSubmissionStub.mockReturnValue(getLatestPendingSubmissionSubject);

        container.participation = participation as any;

        // TODO: This should be replaced by testing with route params.
        domainService.setDomain([DomainType.PARTICIPATION, participation]);
        containerFixture.detectChanges();

        container.commitState = commitState;

        isCleanSubject.next({ repositoryStatus: CommitState.CLEAN });
        getBuildLogsSubject.next(buildLogs);
        getRepositoryContentSubject.next({ file: FileType.FILE, folder: FileType.FOLDER, file2: FileType.FILE });
        getLatestPendingSubmissionSubject.next({ participationId: 1, submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined });

        containerFixture.detectChanges();

        // container
        expect(container.commitState).toEqual(CommitState.CLEAN);
        expect(container.editorState).toEqual(EditorState.CLEAN);
        expect(container.buildOutput.isBuilding).toBe(false);
        expect(container.unsavedFiles).toBeEmpty;

        // file browser
        expect(checkIfRepositoryIsCleanStub).toHaveBeenCalledTimes(1);
        expect(getRepositoryContentStub).toHaveBeenCalledTimes(1);
        expect(container.fileBrowser.errorFiles).toEqual(extractedErrorFiles);
        expect(container.fileBrowser.unsavedFiles).toBeEmpty();

        // ace editor
        expect(container.aceEditor.isLoading).toBe(false);
        expect(container.aceEditor.commitState).toEqual(CommitState.CLEAN);

        // actions
        expect(container.actions.commitState).toEqual(CommitState.CLEAN);
        expect(container.actions.editorState).toEqual(EditorState.CLEAN);
        expect(container.actions.isBuilding).toBe(false);

        // status
        expect(container.fileBrowser.status.commitState).toEqual(CommitState.CLEAN);
        expect(container.fileBrowser.status.editorState).toEqual(EditorState.CLEAN);

        // build output
        expect(getBuildLogsStub).toHaveBeenCalledTimes(1);
        expect(container.buildOutput.rawBuildLogs.extractErrors(ProgrammingLanguage.JAVA)).toEqual(extractedBuildLogErrors);
        expect(container.buildOutput.isBuilding).toBe(false);

        // instructions
        expect(container.instructions).not.toBe(null);

        // called by build output
        expect(getFeedbackDetailsForResultStub).toHaveBeenCalledTimes(1);
        expect(getFeedbackDetailsForResultStub).toHaveBeenCalledWith(participation.id!, participation.results![0].id);
    };

    const loadFile = (fileName: string, fileContent: string) => {
        getFileStub.mockReturnValue(of({ fileContent }));
        container.fileBrowser.selectedFile = fileName;
    };

    it('should initialize all components correctly if all server calls are successful', (done) => {
        cleanInitialize();
        setTimeout(() => {
            expect(subscribeForLatestResultOfParticipationStub).toHaveBeenCalledTimes(1);
            done();
        }, 0);
    });

    it('should not load files and render other components correctly if the repository status cannot be retrieved', (done: any) => {
        const exercise = { id: 1, problemStatement, course: { id: 2 } };
        const participation = { id: 2, exercise, results: [result] } as StudentParticipation;
        const commitState = CommitState.UNDEFINED;
        const isCleanSubject = new Subject();
        const getBuildLogsSubject = new Subject();
        checkIfRepositoryIsCleanStub.mockReturnValue(isCleanSubject);
        subscribeForLatestResultOfParticipationStub.mockReturnValue(of(undefined));
        getFeedbackDetailsForResultStub.mockReturnValue(of([]));
        getBuildLogsStub.mockReturnValue(getBuildLogsSubject);

        container.participation = participation;

        // TODO: This should be replaced by testing with route params.
        domainService.setDomain([DomainType.PARTICIPATION, participation]);
        containerFixture.detectChanges();

        container.commitState = commitState;

        isCleanSubject.error('fatal error');
        getBuildLogsSubject.next(buildLogs);
        getLatestPendingSubmissionSubject.next({ participationId: 1, submissionState: ProgrammingSubmissionState.HAS_FAILED_SUBMISSION, submission: undefined });

        containerFixture.detectChanges();

        // container
        expect(container.commitState).toEqual(CommitState.COULD_NOT_BE_RETRIEVED);
        expect(container.editorState).toEqual(EditorState.CLEAN);
        expect(container.buildOutput.isBuilding).toBe(false);
        expect(container.unsavedFiles).toBeEmpty();

        // file browser
        expect(checkIfRepositoryIsCleanStub).toHaveBeenCalledTimes(1);
        expect(getRepositoryContentStub).not.toHaveBeenCalled;
        expect(container.fileBrowser.errorFiles).toEqual(extractedErrorFiles);
        expect(container.fileBrowser.unsavedFiles).toBeEmpty();

        // ace editor
        expect(container.aceEditor.isLoading).toBe(false);
        expect(container.aceEditor.annotationsArray.map((a) => omit(a, 'hash'))).toEqual(extractedBuildLogErrors);
        expect(container.aceEditor.commitState).toEqual(CommitState.COULD_NOT_BE_RETRIEVED);

        // actions
        expect(container.actions.commitState).toEqual(CommitState.COULD_NOT_BE_RETRIEVED);
        expect(container.actions.editorState).toEqual(EditorState.CLEAN);
        expect(container.actions.isBuilding).toBe(false);

        // status
        expect(container.fileBrowser.status.commitState).toEqual(CommitState.COULD_NOT_BE_RETRIEVED);
        expect(container.fileBrowser.status.editorState).toEqual(EditorState.CLEAN);

        // build output
        expect(getBuildLogsStub).toHaveBeenCalledTimes(1);
        expect(container.buildOutput.rawBuildLogs.extractErrors(ProgrammingLanguage.JAVA)).toEqual(extractedBuildLogErrors);
        expect(container.buildOutput.isBuilding).toBe(false);

        // instructions
        expect(container.instructions).not.toBe(null);

        // called by build output & instructions
        expect(getFeedbackDetailsForResultStub).toHaveBeenCalledTimes(1);
        expect(getFeedbackDetailsForResultStub).toHaveBeenCalledWith(participation.id!, participation.results![0].id);

        setTimeout(() => {
            // called by build output
            expect(subscribeForLatestResultOfParticipationStub).toHaveBeenCalledTimes(1);
            done();
        }, 0);
    });

    it('should update the file browser and ace editor on file selection', () => {
        cleanInitialize();
        const selectedFile = Object.keys(container.fileBrowser.repositoryFiles)[0];
        const fileContent = 'lorem ipsum';
        loadFile(selectedFile, fileContent);

        containerFixture.detectChanges();

        expect(container.selectedFile).toEqual(selectedFile);
        expect(container.aceEditor.selectedFile).toEqual(selectedFile);
        expect(container.aceEditor.isLoading).toBe(false);
        expect(Object.keys(container.aceEditor.fileSession)).toContain(selectedFile);
        expect(getFileStub).toHaveBeenCalledTimes(1);
        expect(getFileStub).toHaveBeenCalledWith(selectedFile);

        containerFixture.detectChanges();
        expect(container.aceEditor.editor.getEditor().getSession().getValue()).toEqual(fileContent);
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

        expect(getFileStub).toHaveBeenCalledTimes(1);
        expect(getFileStub).toHaveBeenCalledWith(selectedFile);
        expect(container.unsavedFiles).toEqual({ [selectedFile]: newFileContent });
        expect(container.fileBrowser.unsavedFiles).toEqual([selectedFile]);
        expect(container.editorState).toEqual(EditorState.UNSAVED_CHANGES);
        expect(container.actions.editorState).toEqual(EditorState.UNSAVED_CHANGES);
    });

    it('should save files and remove unsaved status of saved files afterwards', () => {
        // setup
        cleanInitialize();
        const selectedFile = Object.keys(container.fileBrowser.repositoryFiles)[0];
        const otherFileWithUnsavedChanges = Object.keys(container.fileBrowser.repositoryFiles)[2];
        const fileContent = 'lorem ipsum';
        const newFileContent = 'new lorem ipsum';
        const saveFilesSubject = new Subject();
        saveFilesStub.mockReturnValue(saveFilesSubject);
        container.unsavedFiles = { [otherFileWithUnsavedChanges]: 'lorem ipsum dolet', [selectedFile]: newFileContent };
        loadFile(selectedFile, fileContent);
        containerFixture.detectChanges();

        // init saving
        container.actions.saveChangedFiles().subscribe();
        expect(container.commitState).toEqual(CommitState.UNCOMMITTED_CHANGES);
        expect(container.editorState).toEqual(EditorState.SAVING);

        // emit saving result
        saveFilesSubject.next({ [selectedFile]: undefined, [otherFileWithUnsavedChanges]: undefined });
        containerFixture.detectChanges();

        // check if saving result updates comps as expected
        expect(container.unsavedFiles).toBeEmpty();
        expect(container.editorState).toEqual(EditorState.CLEAN);
        expect(container.commitState).toEqual(CommitState.UNCOMMITTED_CHANGES);
        expect(container.fileBrowser.unsavedFiles).toBeEmpty();
        expect(container.actions.editorState).toEqual(EditorState.CLEAN);
    });

    it('should remove the unsaved changes flag in all components if the unsaved file is deleted', () => {
        cleanInitialize();
        const repositoryFiles = { file: FileType.FILE, file2: FileType.FILE, folder: FileType.FOLDER };
        const expectedFilesAfterDelete = { file2: FileType.FILE, folder: FileType.FOLDER };
        const unsavedChanges = { file: 'lorem ipsum' };
        container.fileBrowser.repositoryFiles = repositoryFiles;
        container.unsavedFiles = unsavedChanges;

        containerFixture.detectChanges();

        expect(container.fileBrowser.unsavedFiles).toEqual(Object.keys(unsavedChanges));
        expect(container.actions.editorState).toEqual(EditorState.UNSAVED_CHANGES);

        container.fileBrowser.onFileDeleted(new DeleteFileChange(FileType.FILE, 'file'));
        containerFixture.detectChanges();

        expect(container.unsavedFiles).toBeEmpty();
        expect(container.fileBrowser.repositoryFiles).toEqual(expectedFilesAfterDelete);
        expect(container.actions.editorState).toEqual(EditorState.CLEAN);
    });

    it('should wait for build result after submission if no unsaved changes exist', () => {
        cleanInitialize();
        const successfulSubmission = { id: 1, buildFailed: false } as ProgrammingSubmission;
        const successfulResult = { id: 4, successful: true, feedbacks: [] as Feedback[], participation: { id: 3 } } as Result;
        successfulResult.submission = successfulSubmission;
        const expectedBuildLog = new BuildLogEntryArray();
        expect(container.unsavedFiles).toBeEmpty();
        container.commitState = CommitState.UNCOMMITTED_CHANGES;
        containerFixture.detectChanges();

        // commit
        expect(container.actions.commitState).toEqual(CommitState.UNCOMMITTED_CHANGES);
        commitStub.mockReturnValue(of(undefined));
        getLatestPendingSubmissionSubject.next({
            submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION,
            submission: {} as ProgrammingSubmission,
            participationId: successfulResult!.participation!.id!,
        });
        container.actions.commit();
        containerFixture.detectChanges();

        // waiting for build successfulResult
        expect(container.commitState).toEqual(CommitState.CLEAN);
        expect(container.buildOutput.isBuilding).toBe(true);

        getLatestPendingSubmissionSubject.next({
            submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION,
            submission: undefined,
            participationId: successfulResult!.participation!.id!,
        });
        subscribeForLatestResultOfParticipationSubject.next(successfulResult);
        containerFixture.detectChanges();

        expect(container.buildOutput.isBuilding).toBe(false);
        expect(container.buildOutput.rawBuildLogs).toEqual(expectedBuildLog);
        expect(container.fileBrowser.errorFiles).toBeEmpty();
    });

    it('should first save unsaved files before triggering commit', fakeAsync(() => {
        cleanInitialize();
        const successfulSubmission = { id: 1, buildFailed: false } as ProgrammingSubmission;
        const successfulResult = { id: 4, successful: true, feedbacks: [] as Feedback[], participation: { id: 3 } } as Result;
        successfulResult.submission = successfulSubmission;
        const expectedBuildLog = new BuildLogEntryArray();
        const unsavedFile = Object.keys(container.fileBrowser.repositoryFiles)[0];
        const saveFilesSubject = new Subject();
        saveFilesStub.mockReturnValue(saveFilesSubject);
        container.unsavedFiles = { [unsavedFile]: 'lorem ipsum' };
        container.editorState = EditorState.UNSAVED_CHANGES;
        container.commitState = CommitState.UNCOMMITTED_CHANGES;
        containerFixture.detectChanges();

        // trying to commit
        container.actions.commit();
        containerFixture.detectChanges();

        // saving before commit
        expect(saveFilesStub).toHaveBeenCalledTimes(1);
        expect(saveFilesStub).toHaveBeenCalledWith([{ fileName: unsavedFile, fileContent: 'lorem ipsum' }], true);
        expect(container.editorState).toEqual(EditorState.SAVING);
        expect(container.fileBrowser.status.editorState).toEqual(EditorState.SAVING);
        // committing
        expect(commitStub).not.toHaveBeenCalled;
        expect(container.commitState).toEqual(CommitState.COMMITTING);
        expect(container.fileBrowser.status.commitState).toEqual(CommitState.COMMITTING);
        saveFilesSubject.next({ [unsavedFile]: undefined });

        expect(container.editorState).toEqual(EditorState.CLEAN);
        subscribeForLatestResultOfParticipationSubject.next(successfulResult);
        getLatestPendingSubmissionSubject.next({
            submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION,
            submission: {} as ProgrammingSubmission,
            participationId: successfulResult!.participation!.id!,
        });
        containerFixture.detectChanges();
        tick();

        // waiting for build result
        expect(container.commitState).toEqual(CommitState.CLEAN);
        expect(container.buildOutput.isBuilding).toBe(true);

        getLatestPendingSubmissionSubject.next({
            submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION,
            submission: undefined,
            participationId: successfulResult!.participation!.id!,
        });
        containerFixture.detectChanges();

        expect(container.buildOutput.isBuilding).toBe(false);
        expect(container.buildOutput.rawBuildLogs).toEqual(expectedBuildLog);
        expect(container.fileBrowser.errorFiles).toBeEmpty();

        containerFixture.destroy();
        flush();
    }));

    it('should enter conflict mode if a git conflict between local and remote arises', fakeAsync(() => {
        const guidedTourMapping = {} as GuidedTourMapping;
        jest.spyOn<any, any>(guidedTourService, 'checkTourState').mockReturnValue(true);
        guidedTourService.guidedTourMapping = guidedTourMapping;

        const successfulResult = { id: 3, successful: false };
        const participation = { id: 1, results: [successfulResult], exercise: { id: 99 } } as StudentParticipation;
        const feedbacks = [{ id: 2 }] as Feedback[];
        const findWithLatestResultSubject = new Subject<Participation>();
        const isCleanSubject = new Subject();
        getStudentParticipationWithLatestResultStub.mockReturnValue(findWithLatestResultSubject);
        checkIfRepositoryIsCleanStub.mockReturnValue(isCleanSubject);
        getFeedbackDetailsForResultStub.mockReturnValue(of(feedbacks));
        getRepositoryContentStub.mockReturnValue(of([]));

        container.participation = participation;
        domainService.setDomain([DomainType.PARTICIPATION, participation]);

        containerFixture.detectChanges();

        findWithLatestResultSubject.next(participation);

        containerFixture.detectChanges();

        // Create conflict.
        isCleanSubject.next({ repositoryStatus: CommitState.CONFLICT });
        containerFixture.detectChanges();

        expect(container.commitState).toEqual(CommitState.CONFLICT);
        expect(getRepositoryContentStub).not.toHaveBeenCalled;

        // Resolve conflict.
        conflictService.notifyConflictState(GitConflictState.OK);
        tick();
        containerFixture.detectChanges();
        isCleanSubject.next({ repositoryStatus: CommitState.CLEAN });
        containerFixture.detectChanges();

        expect(container.commitState).toEqual(CommitState.CLEAN);
        expect(getRepositoryContentStub).toHaveBeenCalledTimes(1);

        containerFixture.destroy();
        flush();
    }));
});
