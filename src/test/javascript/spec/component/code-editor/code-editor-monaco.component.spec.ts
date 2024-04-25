import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';

import { CodeEditorMonacoComponent } from 'app/exercises/programming/shared/code-editor/monaco/code-editor-monaco.component';
import { MockComponent } from 'ng-mocks';
import { CodeEditorTutorAssessmentInlineFeedbackComponent } from 'app/exercises/programming/assess/code-editor-tutor-assessment-inline-feedback.component';
import { MonacoEditorModule } from 'app/shared/monaco-editor/monaco-editor.module';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { MockResizeObserver } from '../../helpers/mocks/service/mock-resize-observer';
import { CodeEditorFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-file.service';
import { CodeEditorRepositoryFileService, ConnectionError } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { MockCodeEditorRepositoryFileService } from '../../helpers/mocks/service/mock-code-editor-repository-file.service';
import { MockLocalStorageService } from '../../helpers/mocks/service/mock-local-storage.service';
import { LocalStorageService } from 'ngx-webstorage';
import { Annotation } from 'app/exercises/programming/shared/code-editor/ace/code-editor-ace.component';
import { SimpleChange } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { CodeEditorHeaderComponent } from 'app/exercises/programming/shared/code-editor/header/code-editor-header.component';
import { CommitState, CreateFileChange, DeleteFileChange, EditorState, FileType, RenameFileChange } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';

describe('CodeEditorMonacoComponent', () => {
    let comp: CodeEditorMonacoComponent;
    let fixture: ComponentFixture<CodeEditorMonacoComponent>;
    let getInlineFeedbackNodeStub: jest.SpyInstance;
    let codeEditorRepositoryFileService: CodeEditorRepositoryFileService;
    let loadFileFromRepositoryStub: jest.SpyInstance;

    const exampleFeedbacks = [
        {
            id: 1,
            reference: 'file:file1.java_line:1',
        },
        {
            id: 2,
            reference: 'file:file1.java_line:2',
        },
        {
            id: 3,
            reference: 'file:file2.java_line:9',
        },
    ];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MonacoEditorModule],
            declarations: [
                CodeEditorMonacoComponent,
                MockComponent(CodeEditorTutorAssessmentInlineFeedbackComponent),
                MockComponent(CodeEditorHeaderComponent),
                MonacoEditorComponent,
            ],
            providers: [
                CodeEditorFileService,
                { provide: CodeEditorRepositoryFileService, useClass: MockCodeEditorRepositoryFileService },
                { provide: LocalStorageService, useClass: MockLocalStorageService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CodeEditorMonacoComponent);
                comp = fixture.componentInstance;
                codeEditorRepositoryFileService = fixture.debugElement.injector.get(CodeEditorRepositoryFileService);
                loadFileFromRepositoryStub = jest.spyOn(codeEditorRepositoryFileService, 'getFile');
                getInlineFeedbackNodeStub = jest.spyOn(comp, 'getInlineFeedbackNode').mockReturnValue(document.createElement('div'));
                global.ResizeObserver = jest.fn().mockImplementation((callback: ResizeObserverCallback) => {
                    return new MockResizeObserver(callback);
                });
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should hide the editor if no file is selected', () => {
        comp.sessionId = 'test';
        fixture.detectChanges();
        const element = document.getElementById('monaco-editor-test');
        expect(element).not.toBeNull();
        expect(element!.hidden).toBeTrue();
    });

    it('should not try to load a file if none is selected', async () => {
        const editorChangeModelSpy = jest.spyOn(comp.editor, 'changeModel');
        fixture.detectChanges();
        await comp.selectFileInEditor(undefined);
        expect(editorChangeModelSpy).not.toHaveBeenCalled();
        expect(loadFileFromRepositoryStub).not.toHaveBeenCalled();
    });

    it('should hide the editor if a file is being loaded', () => {
        comp.sessionId = 'test';
        comp.selectedFile = 'file';
        fixture.detectChanges();
        comp.isLoading = true;
        fixture.detectChanges();
        const element = document.getElementById('monaco-editor-test');
        expect(element).not.toBeNull();
        expect(element!.hidden).toBeTrue();
    });

    it('should display the usable editor when a file is selected', () => {
        comp.sessionId = 'test';
        comp.selectedFile = 'file';
        comp.isLoading = false;
        comp.isTutorAssessment = false;
        fixture.detectChanges();
        const element = document.getElementById('monaco-editor-test');
        expect(element).not.toBeNull();
        expect(element!.hidden).toBeFalse();
        expect(comp.editor.isReadOnly()).toBeFalse();
    });

    it.each([
        [() => {}, false],
        [() => (comp.isTutorAssessment = true), true],
        [() => (comp.disableActions = true), true],
        [() => (comp.commitState = CommitState.CONFLICT), true],
        [() => (comp.selectedFile = undefined), true],
        [() => (comp.fileSession['file'].loadingError = true), true],
    ])('should correctly lock the editor on changes', (setup: () => void, shouldLock: boolean) => {
        comp.selectedFile = 'file';
        comp.fileSession = {
            [comp.selectedFile]: { code: 'some code', cursor: { row: 0, column: 0 }, loadingError: false },
        };
        fixture.detectChanges();
        setup();
        comp.ngOnChanges({});
        expect(comp.editorLocked).toBe(shouldLock);
    });

    it('should update the file session and notify when the file content changes', () => {
        const selectedFile = 'file';
        const fileSession = {
            [selectedFile]: { code: 'some unchanged code', cursor: { row: 0, column: 0 }, loadingError: false },
        };
        const newCode = 'some new code';
        const valueCallbackStub = jest.fn();
        comp.onFileContentChange.subscribe(valueCallbackStub);
        fixture.detectChanges();
        comp.fileSession = fileSession;
        comp.selectedFile = selectedFile;
        comp.onFileTextChanged(newCode);
        expect(valueCallbackStub).toHaveBeenCalledExactlyOnceWith({ file: selectedFile, fileContent: newCode });
        expect(comp.fileSession).toEqual({
            [selectedFile]: { ...fileSession[selectedFile], code: newCode },
        });
    });

    it('should load a selected file if it is not present yet', async () => {
        const fileToLoad = { fileName: 'file-to-load', fileContent: 'some code' };
        const loadedFileSubject = new BehaviorSubject(fileToLoad);
        loadFileFromRepositoryStub.mockReturnValue(loadedFileSubject);
        const setPositionStub = jest.spyOn(comp.editor, 'setPosition').mockImplementation();
        const changeModelStub = jest.spyOn(comp.editor, 'changeModel').mockImplementation();
        const presentFileName = 'present-file';
        const presentFileSession = {
            [presentFileName]: { code: 'code\ncode', cursor: { row: 1, column: 2 }, loadingError: false },
        };
        fixture.detectChanges();
        comp.fileSession = presentFileSession;
        await comp.selectFileInEditor(fileToLoad.fileName);
        await comp.selectFileInEditor(presentFileName);
        expect(loadFileFromRepositoryStub).toHaveBeenCalledExactlyOnceWith(fileToLoad.fileName);
        expect(comp.fileSession).toEqual({
            ...presentFileSession,
            [fileToLoad.fileName]: { code: fileToLoad.fileContent, cursor: { column: 0, row: 0 }, loadingError: false },
        });
        expect(setPositionStub).toHaveBeenCalledTimes(2);
        expect(changeModelStub).toHaveBeenCalledTimes(2);
    });

    it('should load a selected file after a loading error', async () => {
        const fileToLoad = { fileName: 'file-to-load', fileContent: 'some code' };
        // File session after loading fails
        const fileSession = { [fileToLoad.fileName]: { code: '', loadingError: true, cursor: { row: 0, column: 0 } } };
        const loadedFileSubject = new BehaviorSubject(fileToLoad);
        loadFileFromRepositoryStub.mockReturnValue(loadedFileSubject);
        comp.fileSession = fileSession;
        comp.selectedFile = fileToLoad.fileName;
        fixture.detectChanges();
        await comp.ngOnChanges({ selectedFile: new SimpleChange(undefined, fileToLoad, false) });
        expect(loadFileFromRepositoryStub).toHaveBeenCalledOnce();
        expect(comp.fileSession).toEqual({ [fileToLoad.fileName]: { code: fileToLoad.fileContent, loadingError: false, cursor: { row: 0, column: 0 } } });
    });

    it.each([
        [new ConnectionError(), 'loadingFailedInternetDisconnected'],
        [new Error(), 'loadingFailed'],
    ])('should emit the correct error and update the file session when loading a file fails', async (error: Error, errorCode: string) => {
        const fileToLoad = { fileName: 'file-to-load', fileContent: 'some code that will not be loaded' };
        const errorCallbackStub = jest.fn();
        const loadFileSubject = new BehaviorSubject(fileToLoad);
        loadFileFromRepositoryStub.mockReturnValue(loadFileSubject);
        loadFileSubject.error(error);
        comp.fileSession = {};
        comp.selectedFile = fileToLoad.fileName;
        comp.onError.subscribe(errorCallbackStub);
        fixture.detectChanges();
        await comp.ngOnChanges({ selectedFile: new SimpleChange(undefined, fileToLoad.fileName, false) });
        expect(loadFileFromRepositoryStub).toHaveBeenCalledOnce();
        expect(errorCallbackStub).toHaveBeenCalledExactlyOnceWith(errorCode);
        expect(comp.fileSession).toEqual({ [fileToLoad.fileName]: { code: '', loadingError: true, cursor: { row: 0, column: 0 } } });
    });

    it('should discard local changes when the editor is refreshed', async () => {
        const fileToReload = { fileName: 'file-to-reload', fileContent: 'some remote code' };
        const editorResetStub = jest.spyOn(comp.editor, 'reset').mockImplementation();
        const reloadedFileSubject = new BehaviorSubject(fileToReload);
        loadFileFromRepositoryStub.mockReturnValue(reloadedFileSubject);
        comp.selectedFile = fileToReload.fileName;
        comp.fileSession = {
            [fileToReload.fileName]: { code: 'some local undiscarded changes', cursor: { row: 0, column: 0 }, loadingError: false },
        };
        comp.editorState = EditorState.CLEAN;
        fixture.detectChanges();
        // Simulate a refresh of the editor.
        await comp.ngOnChanges({ editorState: new SimpleChange(EditorState.REFRESHING, EditorState.CLEAN, false) });
        expect(comp.fileSession).toEqual({
            [fileToReload.fileName]: { code: fileToReload.fileContent, cursor: { row: 0, column: 0 }, loadingError: false },
        });
        expect(editorResetStub).toHaveBeenCalledOnce();
    });

    it('should use the code and cursor position of the selected file', async () => {
        const setPositionStub = jest.spyOn(comp.editor, 'setPosition').mockImplementation();
        const changeModelStub = jest.spyOn(comp.editor, 'changeModel').mockImplementation();
        fixture.detectChanges();
        const selectedFile = 'file1';
        const fileSession = {
            [selectedFile]: { code: 'code\ncode', cursor: { row: 1, column: 2 }, loadingError: false },
        };
        comp.fileSession = fileSession;
        await comp.selectFileInEditor(selectedFile);
        expect(setPositionStub).toHaveBeenCalledExactlyOnceWith(fileSession[selectedFile].cursor);
        expect(changeModelStub).toHaveBeenCalledExactlyOnceWith(selectedFile, fileSession[selectedFile].code);
    });

    it('should display build annotations for the current file', async () => {
        const setAnnotationsStub = jest.spyOn(comp.editor, 'setAnnotations').mockImplementation();
        const selectFileInEditorStub = jest.spyOn(comp, 'selectFileInEditor').mockImplementation();
        const buildAnnotations: Annotation[] = [
            {
                fileName: 'file1',
                text: 'error',
                type: 'error',
                hash: 'file111error',
                timestamp: 0,
                row: 1,
                column: 1,
            },
            {
                fileName: 'file2',
                text: 'error',
                type: 'error',
                hash: 'file211error',
                timestamp: 0,
                row: 1,
                column: 1,
            },
        ];
        comp.annotationsArray = buildAnnotations;
        comp.selectedFile = 'file1';
        fixture.detectChanges();
        await comp.ngOnChanges({ selectedFile: new SimpleChange(undefined, 'file1', false) });
        comp.selectedFile = 'file2';
        fixture.detectChanges();
        await comp.ngOnChanges({ selectedFile: new SimpleChange('file1', 'file2', false) });
        expect(setAnnotationsStub).toHaveBeenCalledTimes(2);
        expect(selectFileInEditorStub).toHaveBeenCalledTimes(2);
        expect(setAnnotationsStub).toHaveBeenNthCalledWith(1, [buildAnnotations[0]], false);
        expect(setAnnotationsStub).toHaveBeenNthCalledWith(2, [buildAnnotations[1]], false);
    });

    it('should display feedback when viewing a tutor assessment', async () => {
        const addLineWidgetStub = jest.spyOn(comp.editor, 'addLineWidget').mockImplementation();
        const selectFileInEditorStub = jest.spyOn(comp, 'selectFileInEditor').mockImplementation();
        comp.isTutorAssessment = true;
        comp.selectedFile = 'file1.java';
        comp.feedbacks = exampleFeedbacks;
        fixture.detectChanges();
        await comp.ngOnChanges({ selectedFile: new SimpleChange(undefined, 'file1', false) });
        expect(addLineWidgetStub).toHaveBeenCalledTimes(2);
        expect(addLineWidgetStub).toHaveBeenNthCalledWith(1, 2, `feedback-1`, document.createElement('div'));
        expect(addLineWidgetStub).toHaveBeenNthCalledWith(2, 3, `feedback-2`, document.createElement('div'));
        expect(getInlineFeedbackNodeStub).toHaveBeenCalledTimes(2);
        expect(selectFileInEditorStub).toHaveBeenCalledOnce();
    });

    it('should update file session when a file is renamed', async () => {
        const oldFileName = 'old-file-name';
        const newFileName = 'new-file-name';
        const otherFileName = 'other-file';
        const fileSession = {
            [oldFileName]: { code: 'renamed', cursor: { row: 0, column: 0 }, loadingError: false },
            [otherFileName]: { code: 'unrelated', cursor: { row: 0, column: 0 }, loadingError: false },
        };
        fixture.detectChanges();
        comp.fileSession = { ...fileSession };
        const renameFileChange = new RenameFileChange(FileType.FILE, oldFileName, newFileName);
        await comp.onFileChange(renameFileChange);
        expect(comp.fileSession).toEqual({
            [newFileName]: fileSession[oldFileName],
            [otherFileName]: fileSession[otherFileName],
        });
    });

    it('should update file session when a file is deleted', async () => {
        const fileToDeleteName = 'file-to-delete';
        const otherFileName = 'other-file';
        const fileSession = {
            [fileToDeleteName]: { code: 'will be deleted', cursor: { row: 0, column: 0 }, loadingError: false },
            [otherFileName]: { code: 'unrelated', cursor: { row: 0, column: 0 }, loadingError: false },
        };
        fixture.detectChanges();
        comp.fileSession = { ...fileSession };
        const deleteFileChange = new DeleteFileChange(FileType.FILE, fileToDeleteName);
        await comp.onFileChange(deleteFileChange);
        expect(comp.fileSession).toEqual({
            [otherFileName]: fileSession[otherFileName],
        });
    });

    it('should update file session when a file is created', async () => {
        const fileToCreateName = 'file-to-create';
        const otherFileName = 'other-file';
        const fileSession = {
            [otherFileName]: { code: 'unrelated', cursor: { row: 0, column: 0 }, loadingError: false },
        };
        fixture.detectChanges();
        comp.fileSession = { ...fileSession };
        const createFileChange = new CreateFileChange(FileType.FILE, fileToCreateName);
        await comp.onFileChange(createFileChange);
        expect(comp.fileSession).toEqual({
            [otherFileName]: fileSession[otherFileName],
            [fileToCreateName]: { code: '', cursor: { row: 0, column: 0 }, loadingError: false },
        });
    });
});
