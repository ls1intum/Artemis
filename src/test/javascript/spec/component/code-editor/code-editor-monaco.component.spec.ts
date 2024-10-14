import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';

import { Annotation, CodeEditorMonacoComponent } from 'app/exercises/programming/shared/code-editor/monaco/code-editor-monaco.component';
import { MockComponent } from 'ng-mocks';
import { CodeEditorTutorAssessmentInlineFeedbackComponent } from 'app/exercises/programming/assess/code-editor-tutor-assessment-inline-feedback.component';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { MockResizeObserver } from '../../helpers/mocks/service/mock-resize-observer';
import { CodeEditorFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-file.service';
import { CodeEditorRepositoryFileService, ConnectionError } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { MockCodeEditorRepositoryFileService } from '../../helpers/mocks/service/mock-code-editor-repository-file.service';
import { MockLocalStorageService } from '../../helpers/mocks/service/mock-local-storage.service';
import { LocalStorageService } from 'ngx-webstorage';
import { SimpleChange } from '@angular/core';
import { BehaviorSubject, Subject } from 'rxjs';
import { CodeEditorHeaderComponent } from 'app/exercises/programming/shared/code-editor/header/code-editor-header.component';
import { CommitState, CreateFileChange, DeleteFileChange, EditorState, FileType, RenameFileChange } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { Feedback } from 'app/entities/feedback.model';

describe('CodeEditorMonacoComponent', () => {
    let comp: CodeEditorMonacoComponent;
    let fixture: ComponentFixture<CodeEditorMonacoComponent>;
    let getInlineFeedbackNodeStub: jest.SpyInstance;
    let codeEditorRepositoryFileService: CodeEditorRepositoryFileService;
    let loadFileFromRepositoryStub: jest.SpyInstance;

    const exampleFeedbacks: Feedback[] = [
        {
            id: 1,
            reference: 'file:file1.java_line:1',
            text: 'comment on line 1',
            detailText: 'detailed text',
        },
        {
            id: 2,
            reference: 'file:file1.java_line:2',
            text: 'comment on line 2',
            detailText: 'more detailed text',
        },
        {
            id: 3,
            reference: 'file:file2.java_line:9',
            text: 'comment on line 9',
            detailText: 'the most detailed text',
        },
    ];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MonacoEditorComponent],
            declarations: [CodeEditorMonacoComponent, MockComponent(CodeEditorTutorAssessmentInlineFeedbackComponent), MockComponent(CodeEditorHeaderComponent)],
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
        comp.loadingCount = 1;
        fixture.detectChanges();
        const element = document.getElementById('monaco-editor-test');
        expect(element).not.toBeNull();
        expect(element!.hidden).toBeTrue();
    });

    it('should display the usable editor when a file is selected', () => {
        comp.sessionId = 'test';
        comp.selectedFile = 'file';
        comp.loadingCount = 0;
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
            [comp.selectedFile]: { code: 'some code', cursor: { lineNumber: 0, column: 0 }, loadingError: false },
        };
        fixture.detectChanges();
        setup();
        comp.ngOnChanges({});
        expect(comp.editorLocked).toBe(shouldLock);
    });

    it('should update the file session and notify when the file content changes', () => {
        const selectedFile = 'file';
        const fileSession = {
            [selectedFile]: { code: 'some unchanged code', cursor: { lineNumber: 0, column: 0 }, loadingError: false },
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
            [presentFileName]: { code: 'code\ncode', cursor: { lineNumber: 1, column: 2 }, loadingError: false },
        };
        fixture.detectChanges();
        comp.fileSession = presentFileSession;
        comp.selectedFile = fileToLoad.fileName;
        await comp.selectFileInEditor(fileToLoad.fileName);
        comp.selectedFile = presentFileName;
        await comp.selectFileInEditor(presentFileName);
        expect(loadFileFromRepositoryStub).toHaveBeenCalledExactlyOnceWith(fileToLoad.fileName);
        expect(comp.fileSession).toEqual({
            ...presentFileSession,
            [fileToLoad.fileName]: { code: fileToLoad.fileContent, cursor: { column: 0, lineNumber: 0 }, loadingError: false },
        });
        expect(setPositionStub).toHaveBeenCalledTimes(2);
        expect(changeModelStub).toHaveBeenCalledTimes(2);
    });

    it('should load a selected file after a loading error', async () => {
        const fileToLoad = { fileName: 'file-to-load', fileContent: 'some code' };
        // File session after loading fails
        const fileSession = { [fileToLoad.fileName]: { code: '', loadingError: true, cursor: { lineNumber: 0, column: 0 } } };
        const loadedFileSubject = new BehaviorSubject(fileToLoad);
        loadFileFromRepositoryStub.mockReturnValue(loadedFileSubject);
        comp.fileSession = fileSession;
        comp.selectedFile = fileToLoad.fileName;
        fixture.detectChanges();
        await comp.ngOnChanges({ selectedFile: new SimpleChange(undefined, fileToLoad, false) });
        expect(loadFileFromRepositoryStub).toHaveBeenCalledOnce();
        expect(comp.fileSession).toEqual({ [fileToLoad.fileName]: { code: fileToLoad.fileContent, loadingError: false, cursor: { lineNumber: 0, column: 0 } } });
    });

    it('should not load binaries into the editor', async () => {
        const changeModelSpy = jest.spyOn(comp.editor, 'changeModel');
        const fileName = 'file-to-load';
        comp.fileSession = {
            [fileName]: { code: '\0\0\0\0 (binary content)', loadingError: false, cursor: { lineNumber: 0, column: 0 } },
        };
        fixture.detectChanges();
        comp.selectedFile = fileName;
        await comp.selectFileInEditor(fileName);
        expect(changeModelSpy).not.toHaveBeenCalled();
        expect(comp.binaryFileSelected).toBeTrue();
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
        expect(comp.fileSession).toEqual({ [fileToLoad.fileName]: { code: '', loadingError: true, cursor: { lineNumber: 0, column: 0 } } });
    });

    it('should discard local changes when the editor is refreshed', async () => {
        const fileToReload = { fileName: 'file-to-reload', fileContent: 'some remote code' };
        const editorResetStub = jest.spyOn(comp.editor, 'reset').mockImplementation();
        const reloadedFileSubject = new BehaviorSubject(fileToReload);
        loadFileFromRepositoryStub.mockReturnValue(reloadedFileSubject);
        comp.selectedFile = fileToReload.fileName;
        comp.fileSession = {
            [fileToReload.fileName]: { code: 'some local undiscarded changes', cursor: { lineNumber: 0, column: 0 }, loadingError: false },
        };
        comp.editorState = EditorState.CLEAN;
        fixture.detectChanges();
        // Simulate a refresh of the editor.
        await comp.ngOnChanges({ editorState: new SimpleChange(EditorState.REFRESHING, EditorState.CLEAN, false) });
        expect(comp.fileSession).toEqual({
            [fileToReload.fileName]: { code: fileToReload.fileContent, cursor: { lineNumber: 0, column: 0 }, loadingError: false },
        });
        expect(editorResetStub).toHaveBeenCalledOnce();
    });

    it('should only load the currently selected file', async () => {
        const changeModelSpy = jest.spyOn(comp.editor, 'changeModel');
        // Occurs when the first file load takes a while, but the user has already selected another file.
        comp.fileSession = { ['file2']: { code: 'code2', cursor: { lineNumber: 0, column: 0 }, loadingError: false } };
        fixture.detectChanges();
        comp.selectedFile = 'file1';
        const longLoadingFileSubject = new Subject();
        loadFileFromRepositoryStub.mockReturnValue(longLoadingFileSubject);
        // We do not await the promise here, as we want to simulate the user selecting another file while the first one is still loading.
        const firstFileChange = comp.selectFileInEditor('file1');
        comp.selectedFile = 'file2';
        await comp.selectFileInEditor('file2');
        longLoadingFileSubject.next({ fileName: 'file1', fileContent: 'some code that took a while to retrieve' });
        await firstFileChange;
        expect(changeModelSpy).toHaveBeenCalledExactlyOnceWith('file2', 'code2');
    });

    it('should use the code and cursor position of the selected file', async () => {
        const setPositionStub = jest.spyOn(comp.editor, 'setPosition').mockImplementation();
        const changeModelStub = jest.spyOn(comp.editor, 'changeModel').mockImplementation();
        fixture.detectChanges();
        const selectedFile = 'file1';
        const fileSession = {
            [selectedFile]: { code: 'code\ncode', cursor: { lineNumber: 1, column: 2 }, loadingError: false },
        };
        comp.fileSession = fileSession;
        comp.selectedFile = selectedFile;
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

    it('should display feedback when viewing a tutor assessment', fakeAsync(() => {
        const addLineWidgetStub = jest.spyOn(comp.editor, 'addLineWidget').mockImplementation();
        const selectFileInEditorStub = jest.spyOn(comp, 'selectFileInEditor').mockImplementation();
        comp.isTutorAssessment = true;
        comp.selectedFile = 'file1.java';
        comp.feedbacks = exampleFeedbacks;
        fixture.detectChanges();
        // Use .then() here instead of await so fakeAsync does not break.
        comp.ngOnChanges({ selectedFile: new SimpleChange(undefined, 'file1', false) }).then(() => {
            // Rendering of the feedback items happens after one tick to allow the renderer to catch up with the DOM nodes.
            tick(1);
            expect(addLineWidgetStub).toHaveBeenCalledTimes(2);
            expect(addLineWidgetStub).toHaveBeenNthCalledWith(1, 2, `feedback-1`, document.createElement('div'));
            expect(addLineWidgetStub).toHaveBeenNthCalledWith(2, 3, `feedback-2`, document.createElement('div'));
            expect(getInlineFeedbackNodeStub).toHaveBeenCalledTimes(2);
            expect(selectFileInEditorStub).toHaveBeenCalledOnce();
        });
    }));

    it('should add a new feedback widget', fakeAsync(() => {
        // Feedback is stored as 0-based line numbers, but the editor requires 1-based line numbers.
        const feedbackLineOneBased = 3;
        const feedbackLineZeroBased = feedbackLineOneBased - 1;
        const addLineWidgetStub = jest.spyOn(comp.editor, 'addLineWidget').mockImplementation();
        const element = document.createElement('div');
        getInlineFeedbackNodeStub.mockReturnValue(undefined);
        fixture.detectChanges();
        // Simulate adding the element
        comp.addNewFeedback(feedbackLineOneBased);
        getInlineFeedbackNodeStub.mockReturnValue(element);
        expect(comp.newFeedbackLines).toEqual([feedbackLineZeroBased]);
        tick(1);
        expect(addLineWidgetStub).toHaveBeenCalledExactlyOnceWith(feedbackLineOneBased, `feedback-new-${feedbackLineZeroBased}`, element);
    }));

    it('should delete feedbacks and notify', () => {
        const feedbackToDelete = exampleFeedbacks[0];
        const remainingFeedbacks = exampleFeedbacks.slice(1);
        const updateFeedbackCallbackStub = jest.fn();
        comp.onUpdateFeedback.subscribe(updateFeedbackCallbackStub);
        comp.feedbacks = [...exampleFeedbacks];
        fixture.detectChanges();
        comp.deleteFeedback(feedbackToDelete);
        expect(comp.feedbacks).toEqual(remainingFeedbacks);
        expect(updateFeedbackCallbackStub).toHaveBeenCalledExactlyOnceWith(remainingFeedbacks);
    });

    it('should delete unsaved feedback', () => {
        const feedbackLine = 1;
        comp.newFeedbackLines = [feedbackLine, 2, 3];
        fixture.detectChanges();
        comp.cancelFeedback(feedbackLine);
        expect(comp.newFeedbackLines).toEqual([2, 3]);
    });

    it('should update existing feedback and notify', () => {
        const feedbackToUpdate: Feedback = { ...exampleFeedbacks[0] };
        const remainingFeedbacks = exampleFeedbacks.slice(1);
        const updateFeedbackCallbackStub = jest.fn();
        comp.onUpdateFeedback.subscribe(updateFeedbackCallbackStub);
        // Copy the original example feedback in to ensure changes here do not affect the component.
        comp.feedbacks = [...exampleFeedbacks];
        fixture.detectChanges();
        feedbackToUpdate.text = 'some other text';
        comp.updateFeedback(feedbackToUpdate);
        const expectedFeedbacks = [feedbackToUpdate, ...remainingFeedbacks];
        expect(comp.feedbacks).toEqual(expectedFeedbacks);
        expect(updateFeedbackCallbackStub).toHaveBeenCalledExactlyOnceWith(expectedFeedbacks);
    });

    it('should save new feedback and notify', () => {
        const feedbackToSave: Feedback = { ...exampleFeedbacks[0] };
        const remainingFeedbacks = exampleFeedbacks.slice(1);
        const newFeedbackLine = 1;
        const updateFeedbackCallbackStub = jest.fn();
        comp.onUpdateFeedback.subscribe(updateFeedbackCallbackStub);
        comp.newFeedbackLines = [newFeedbackLine];
        comp.feedbacks = [...remainingFeedbacks];
        fixture.detectChanges();
        comp.updateFeedback(feedbackToSave);
        const expectedFeedbacks = [...remainingFeedbacks, feedbackToSave];
        expect(comp.feedbacks).toEqual(expectedFeedbacks);
        // The feedback has been saved -> no longer new
        expect(comp.newFeedbackLines).toHaveLength(0);
        expect(updateFeedbackCallbackStub).toHaveBeenCalledExactlyOnceWith(expectedFeedbacks);
    });

    it('should correctly accept a feedback suggestion and notify', () => {
        const updateFeedbackStub = jest.spyOn(comp, 'updateFeedback').mockImplementation();
        const acceptSuggestionCallbackStub = jest.fn();
        const suggestionToAccept: Feedback = exampleFeedbacks[0];
        comp.feedbackSuggestions = [suggestionToAccept];
        comp.onAcceptSuggestion.subscribe(acceptSuggestionCallbackStub);
        fixture.detectChanges();
        comp.acceptSuggestion(suggestionToAccept);
        expect(comp.feedbackSuggestions).toHaveLength(0);
        expect(updateFeedbackStub).toHaveBeenCalledExactlyOnceWith(suggestionToAccept);
        expect(acceptSuggestionCallbackStub).toHaveBeenCalledExactlyOnceWith(suggestionToAccept);
    });

    it('should correctly discard a suggestion and notify', () => {
        const discardSuggestionCallbackStub = jest.fn();
        const suggestionToDiscard = exampleFeedbacks[0];
        comp.feedbackSuggestions = [suggestionToDiscard];
        comp.onDiscardSuggestion.subscribe(discardSuggestionCallbackStub);
        fixture.detectChanges();
        comp.discardSuggestion(suggestionToDiscard);
        expect(comp.feedbackSuggestions).toHaveLength(0);
        expect(discardSuggestionCallbackStub).toHaveBeenCalledExactlyOnceWith(suggestionToDiscard);
    });

    it('should update file session when a file is renamed', async () => {
        const oldFileName = 'old-file-name';
        const newFileName = 'new-file-name';
        const otherFileName = 'other-file';
        const fileSession = {
            [oldFileName]: { code: 'renamed', cursor: { lineNumber: 0, column: 0 }, loadingError: false },
            [otherFileName]: { code: 'unrelated', cursor: { lineNumber: 0, column: 0 }, loadingError: false },
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
            [fileToDeleteName]: { code: 'will be deleted', cursor: { lineNumber: 0, column: 0 }, loadingError: false },
            [otherFileName]: { code: 'unrelated', cursor: { lineNumber: 0, column: 0 }, loadingError: false },
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
            [otherFileName]: { code: 'unrelated', cursor: { lineNumber: 0, column: 0 }, loadingError: false },
        };
        fixture.detectChanges();
        comp.fileSession = { ...fileSession };
        const createFileChange = new CreateFileChange(FileType.FILE, fileToCreateName);
        await comp.onFileChange(createFileChange);
        expect(comp.fileSession).toEqual({
            [otherFileName]: fileSession[otherFileName],
            [fileToCreateName]: { code: '', cursor: { lineNumber: 0, column: 0 }, loadingError: false },
        });
    });

    it('should use the correct class to highlight lines', () => {
        const highlightStub = jest.spyOn(comp.editor, 'highlightLines').mockImplementation();
        fixture.detectChanges();
        comp.highlightLines(1, 2);
        expect(highlightStub).toHaveBeenCalledExactlyOnceWith(1, 2, CodeEditorMonacoComponent.CLASS_DIFF_LINE_HIGHLIGHT);
    });
});
