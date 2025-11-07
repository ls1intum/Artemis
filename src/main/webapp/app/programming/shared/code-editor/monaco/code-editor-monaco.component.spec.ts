import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';

import { Annotation, CodeEditorMonacoComponent } from 'app/programming/shared/code-editor/monaco/code-editor-monaco.component';
import { MockComponent } from 'ng-mocks';
import { CodeEditorTutorAssessmentInlineFeedbackComponent } from 'app/programming/manage/assess/code-editor-tutor-assessment-inline-feedback/code-editor-tutor-assessment-inline-feedback.component';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { MockResizeObserver } from 'test/helpers/mocks/service/mock-resize-observer';
import { CodeEditorFileService } from 'app/programming/shared/code-editor/services/code-editor-file.service';
import { CodeEditorRepositoryFileService, ConnectionError } from 'app/programming/shared/code-editor/services/code-editor-repository.service';
import { MockCodeEditorRepositoryFileService } from 'test/helpers/mocks/service/mock-code-editor-repository-file.service';
import { SimpleChange } from '@angular/core';
import { BehaviorSubject, Subject, of } from 'rxjs';
import { CodeEditorHeaderComponent } from 'app/programming/manage/code-editor/header/code-editor-header.component';
import { CommitState, CreateFileChange, DeleteFileChange, EditorState, FileType, RenameFileChange } from 'app/programming/shared/code-editor/model/code-editor.model';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { IKeyboardEvent } from 'monaco-editor';

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

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MonacoEditorComponent],
            declarations: [MockComponent(CodeEditorTutorAssessmentInlineFeedbackComponent), MockComponent(CodeEditorHeaderComponent)],
            providers: [
                CodeEditorFileService,
                { provide: CodeEditorRepositoryFileService, useClass: MockCodeEditorRepositoryFileService },

                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CodeEditorMonacoComponent);
        comp = fixture.componentInstance;
        codeEditorRepositoryFileService = TestBed.inject(CodeEditorRepositoryFileService);
        loadFileFromRepositoryStub = jest.spyOn(codeEditorRepositoryFileService, 'getFile');
        // Provide a safe default so accidental file loads during change detection don't throw
        loadFileFromRepositoryStub.mockReturnValue(of({ fileContent: '' }));
        getInlineFeedbackNodeStub = jest.spyOn(comp, 'getInlineFeedbackNode').mockReturnValue(document.createElement('div'));
        global.ResizeObserver = jest.fn().mockImplementation((callback: ResizeObserverCallback) => {
            return new MockResizeObserver(callback);
        });

        fixture.componentRef.setInput('sessionId', 'test');
        fixture.componentRef.setInput('commitState', CommitState.CLEAN);
        fixture.componentRef.setInput('editorState', EditorState.CLEAN);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should hide the editor if no file is selected', () => {
        fixture.detectChanges();
        const element = document.getElementById('monaco-editor-test');
        expect(element).not.toBeNull();
        expect(element!.hidden).toBeTrue();
    });

    it('should not try to load a file if none is selected', async () => {
        const editorChangeModelSpy = jest.spyOn(comp.editor(), 'changeModel');
        fixture.detectChanges();
        await comp.selectFileInEditor(undefined);
        expect(editorChangeModelSpy).not.toHaveBeenCalled();
        expect(loadFileFromRepositoryStub).not.toHaveBeenCalled();
    });

    it('should hide the editor if a file is being loaded', () => {
        fixture.componentRef.setInput('selectedFile', 'file');
        fixture.detectChanges();
        comp.loadingCount.set(1);
        fixture.detectChanges();
        const element = document.getElementById('monaco-editor-test');
        expect(element).not.toBeNull();
        expect(element!.hidden).toBeTrue();
    });

    it('should display the usable editor when a file is selected', () => {
        // Prevent loading the file
        jest.spyOn(comp, 'selectFileInEditor').mockImplementation().mockResolvedValue(undefined);
        fixture.componentRef.setInput('selectedFile', 'file');
        fixture.componentRef.setInput('isTutorAssessment', false);
        fixture.detectChanges();
        const element = document.getElementById('monaco-editor-test');
        expect(element).not.toBeNull();
        expect(element!.hidden).toBeFalse();
        expect(comp.editor().isReadOnly()).toBeFalse();
    });

    it.each([
        [() => {}, false],
        [() => fixture.componentRef.setInput('isTutorAssessment', true), true],
        [() => fixture.componentRef.setInput('disableActions', true), true],
        [() => fixture.componentRef.setInput('commitState', CommitState.CONFLICT), true],
        [() => fixture.componentRef.setInput('selectedFile', undefined), true],
        [() => comp.fileSession.set({ ['file']: { code: '', cursor: { lineNumber: 0, column: 0 }, loadingError: true, scrollTop: 0 } }), true], // TODO: convert to signal
    ])('should correctly lock the editor on changes', (setup: () => void, shouldLock: boolean) => {
        fixture.componentRef.setInput('selectedFile', 'file');
        comp.fileSession.set({
            [comp.selectedFile()!]: { code: 'some code', cursor: { lineNumber: 0, column: 0 }, loadingError: false, scrollTop: 0 },
        });
        fixture.detectChanges();
        setup();
        expect(comp.editorLocked()).toBe(shouldLock);
    });

    it('should update the file session and notify when the file content changes', () => {
        const selectedFile = 'file';
        const fileSession = {
            [selectedFile]: { code: 'some unchanged code', cursor: { lineNumber: 1, column: 1 }, loadingError: false, scrollTop: 0 },
        };
        const newCode = 'some new code';
        const valueCallbackStub = jest.fn();
        comp.onFileContentChange.subscribe(valueCallbackStub);
        fixture.detectChanges();
        comp.fileSession.set(fileSession);
        fixture.componentRef.setInput('selectedFile', selectedFile);
        comp.onFileTextChanged({ text: newCode, fileName: selectedFile });
        expect(valueCallbackStub).toHaveBeenCalledExactlyOnceWith({ fileName: selectedFile, text: newCode });
        expect(comp.fileSession()).toEqual({
            [selectedFile]: { ...fileSession[selectedFile], code: newCode, scrollTop: 0 },
        });
    });

    it('should load a selected file if it is not present yet', async () => {
        const fileToLoad = { fileName: 'file-to-load', fileContent: 'some code' };
        const loadedFileSubject = new BehaviorSubject(fileToLoad);
        loadFileFromRepositoryStub.mockReturnValue(loadedFileSubject);
        const setPositionStub = jest.spyOn(comp.editor(), 'setPosition').mockImplementation();
        const changeModelStub = jest.spyOn(comp.editor(), 'changeModel').mockImplementation();
        const presentFileName = 'present-file';
        const presentFileSession = {
            [presentFileName]: { code: 'code\ncode', cursor: { lineNumber: 1, column: 2 }, scrollTop: 0, loadingError: false },
        };
        fixture.detectChanges();
        comp.fileSession.set(presentFileSession);
        fixture.componentRef.setInput('selectedFile', fileToLoad.fileName);
        await comp.selectFileInEditor(fileToLoad.fileName);
        fixture.componentRef.setInput('selectedFile', presentFileName);
        await comp.selectFileInEditor(presentFileName);
        expect(loadFileFromRepositoryStub).toHaveBeenCalledExactlyOnceWith(fileToLoad.fileName);
        expect(comp.fileSession()).toEqual({
            ...presentFileSession,
            [fileToLoad.fileName]: { code: fileToLoad.fileContent, cursor: { column: 0, lineNumber: 0 }, scrollTop: 0, loadingError: false },
        });
        expect(setPositionStub).toHaveBeenCalledTimes(2);
        expect(changeModelStub).toHaveBeenCalledTimes(2);
    });

    it('should load a selected file after a loading error', async () => {
        const fileToLoad = { fileName: 'file-to-load', fileContent: 'some code' };
        // File session after loading fails
        const fileSession = { [fileToLoad.fileName]: { code: '', loadingError: true, cursor: { lineNumber: 0, column: 0 }, scrollTop: 0 } };
        const loadedFileSubject = new BehaviorSubject(fileToLoad);
        loadFileFromRepositoryStub.mockReturnValue(loadedFileSubject);
        comp.fileSession.set(fileSession);
        fixture.componentRef.setInput('selectedFile', fileToLoad.fileName);
        fixture.detectChanges();
        await new Promise(process.nextTick);
        expect(loadFileFromRepositoryStub).toHaveBeenCalledOnce();
        expect(comp.fileSession()).toEqual({
            [fileToLoad.fileName]: { code: fileToLoad.fileContent, loadingError: false, cursor: { lineNumber: 0, column: 0 }, scrollTop: 0 },
        });
    });

    it('should not load binaries into the editor', async () => {
        const changeModelSpy = jest.spyOn(comp.editor(), 'changeModel');
        const fileName = 'file-to-load';
        comp.fileSession.set({
            [fileName]: { code: '\0\0\0\0 (binary content)', loadingError: false, cursor: { lineNumber: 0, column: 0 }, scrollTop: 0 },
        });
        fixture.detectChanges();
        fixture.componentRef.setInput('selectedFile', fileName);
        await comp.selectFileInEditor(fileName);
        expect(changeModelSpy).not.toHaveBeenCalled();
        expect(comp.binaryFileSelected()).toBeTrue();
    });

    it.each([
        [new ConnectionError(), 'loadingFailedInternetDisconnected'],
        [new Error(), 'loadingFailed'],
    ])('should emit the correct error and update the file session when loading a file fails', async (error: Error, errorCode: string) => {
        const fileToLoad = { fileName: 'file-to-load', fileContent: 'some code that will not be loaded' };
        const errorCallbackStub = jest.fn();
        const loadFileSubject = new Subject<typeof fileToLoad>();
        loadFileFromRepositoryStub.mockReturnValue(loadFileSubject);
        comp.fileSession.set({});
        fixture.componentRef.setInput('selectedFile', fileToLoad.fileName);
        comp.onError.subscribe(errorCallbackStub);
        fixture.detectChanges();
        // Emit the error after the component has subscribed to the observable.
        // Otherwise, the error only surfaces on the next macrotask in the console.
        // This would break new tests, that await the next macrotask.
        loadFileSubject.error(error);
        await new Promise(process.nextTick);
        expect(loadFileFromRepositoryStub).toHaveBeenCalledOnce();
        expect(errorCallbackStub).toHaveBeenCalledExactlyOnceWith(errorCode);
        expect(comp.fileSession()).toEqual({
            [fileToLoad.fileName]: { code: '', loadingError: true, cursor: { lineNumber: 0, column: 0 }, scrollTop: 0 },
        });
    });

    it('should discard local changes when the editor is refreshed', async () => {
        const fileToReload = { fileName: 'file-to-reload', fileContent: 'some remote code' };
        const editorResetStub = jest.spyOn(comp.editor(), 'reset').mockImplementation();
        const reloadedFileSubject = new BehaviorSubject(fileToReload);
        loadFileFromRepositoryStub.mockReturnValue(reloadedFileSubject);
        fixture.componentRef.setInput('selectedFile', fileToReload.fileName);
        comp.fileSession.set({
            [fileToReload.fileName]: { code: 'some local undiscarded changes', cursor: { lineNumber: 0, column: 0 }, scrollTop: 0, loadingError: false },
        });
        fixture.componentRef.setInput('editorState', EditorState.CLEAN);
        // Simulate a refresh of the editor.
        await comp.ngOnChanges({ editorState: new SimpleChange(EditorState.REFRESHING, EditorState.CLEAN, false) });
        expect(comp.fileSession()).toEqual({
            [fileToReload.fileName]: { code: fileToReload.fileContent, cursor: { lineNumber: 0, column: 0 }, loadingError: false, scrollTop: 0 },
        });
        expect(editorResetStub).toHaveBeenCalledOnce();
    });

    it('should only load the currently selected file', async () => {
        const changeModelSpy = jest.spyOn(comp.editor(), 'changeModel');
        // Occurs when the first file load takes a while, but the user has already selected another file.
        comp.fileSession.set({ ['file2']: { code: 'code2', cursor: { lineNumber: 0, column: 0 }, loadingError: false, scrollTop: 0 } });
        fixture.detectChanges();
        fixture.componentRef.setInput('selectedFile', 'file1');
        const longLoadingFileSubject = new Subject();
        loadFileFromRepositoryStub.mockReturnValue(longLoadingFileSubject);
        // We do not await the promise here, as we want to simulate the user selecting another file while the first one is still loading.
        const firstFileChange = comp.selectFileInEditor('file1');
        fixture.componentRef.setInput('selectedFile', 'file2');
        await comp.selectFileInEditor('file2');
        longLoadingFileSubject.next({ fileName: 'file1', fileContent: 'some code that took a while to retrieve' });
        await firstFileChange;
        expect(changeModelSpy).toHaveBeenCalledExactlyOnceWith('file2', 'code2');
    });

    it('should use the code and cursor position of the selected file', async () => {
        const setPositionStub = jest.spyOn(comp.editor(), 'setPosition').mockImplementation();
        const changeModelStub = jest.spyOn(comp.editor(), 'changeModel').mockImplementation();
        fixture.detectChanges();
        const selectedFile = 'file1';
        const fileSession = {
            [selectedFile]: { code: 'code\ncode', cursor: { lineNumber: 1, column: 2 }, loadingError: false, scrollTop: 0 },
        };
        comp.fileSession.set(fileSession);
        fixture.componentRef.setInput('selectedFile', selectedFile);
        await comp.selectFileInEditor(selectedFile);
        expect(setPositionStub).toHaveBeenCalledExactlyOnceWith(fileSession[selectedFile].cursor);
        expect(changeModelStub).toHaveBeenCalledExactlyOnceWith(selectedFile, fileSession[selectedFile].code);
    });

    it('should display build annotations for the current file', async () => {
        const setAnnotationsStub = jest.spyOn(comp.editor(), 'setAnnotations').mockImplementation();
        const selectFileInEditorStub = jest.spyOn(comp, 'selectFileInEditor').mockResolvedValue(undefined);
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
        fixture.componentRef.setInput('buildAnnotations', buildAnnotations);
        fixture.componentRef.setInput('selectedFile', 'file1');
        fixture.detectChanges();
        await new Promise(process.nextTick);
        fixture.componentRef.setInput('selectedFile', 'file2');
        fixture.detectChanges();
        await new Promise(process.nextTick); // TODO: make this reusable
        // 3 calls: construction (effect), file1, file2
        expect(setAnnotationsStub).toHaveBeenCalledTimes(3);
        expect(selectFileInEditorStub).toHaveBeenCalledTimes(2);
        expect(setAnnotationsStub).toHaveBeenNthCalledWith(1, [buildAnnotations[0]], false);
        expect(setAnnotationsStub).toHaveBeenNthCalledWith(2, [buildAnnotations[0]], false);
        expect(setAnnotationsStub).toHaveBeenNthCalledWith(3, [buildAnnotations[1]], false);
    });

    it('should display feedback when viewing a tutor assessment', async () => {
        const consoleErrorSpy = jest.spyOn(console, 'error').mockImplementation(() => {});
        const addLineWidgetStub = jest.spyOn(comp.editor(), 'addLineWidget').mockImplementation();
        const selectFileInEditorStub = jest.spyOn(comp, 'selectFileInEditor').mockResolvedValue(undefined);
        loadFileFromRepositoryStub.mockReturnValue(of({ fileContent: 'loaded file content' }));
        fixture.componentRef.setInput('isTutorAssessment', true);
        fixture.componentRef.setInput('selectedFile', 'file1.java');
        fixture.componentRef.setInput('feedbacks', exampleFeedbacks);
        fixture.detectChanges();
        await comp.ngOnChanges({ selectedFile: new SimpleChange(undefined, 'file1', false) });
        await new Promise((r) => setTimeout(r, 0));

        expect(addLineWidgetStub).toHaveBeenCalledTimes(8);
        // 8=2x3+2 calls, as two feedbacks each trigger render three times in ngOnChanges
        // and the initial selectedFile=undefined and follow up selectedFile=file1.java
        // trigger the corresponding signal and effect twice.
        expect(addLineWidgetStub).toHaveBeenNthCalledWith(1, 2, `feedback-1-line-2`, document.createElement('div'));
        expect(addLineWidgetStub).toHaveBeenNthCalledWith(2, 3, `feedback-2-line-3`, document.createElement('div'));
        expect(getInlineFeedbackNodeStub).toHaveBeenCalledTimes(8);
        // The same explanation as above applies.
        expect(selectFileInEditorStub).toHaveBeenCalled();
        consoleErrorSpy.mockRestore();
    });

    it('should add a new feedback widget', fakeAsync(() => {
        // Feedback is stored as 0-based line numbers, but the editor requires 1-based line numbers.
        const feedbackLineOneBased = 3;
        const feedbackLineZeroBased = feedbackLineOneBased - 1;
        const addLineWidgetStub = jest.spyOn(comp.editor(), 'addLineWidget').mockImplementation();
        const element = document.createElement('div');
        getInlineFeedbackNodeStub.mockReturnValue(undefined);
        fixture.detectChanges();
        // Simulate adding the element
        comp.addNewFeedback(feedbackLineOneBased);
        getInlineFeedbackNodeStub.mockReturnValue(element);
        expect(comp.newFeedbackLines()).toEqual([feedbackLineZeroBased]);
        tick(1);
        expect(addLineWidgetStub).toHaveBeenCalledExactlyOnceWith(feedbackLineOneBased, `feedback-new-${feedbackLineZeroBased}`, element);
    }));

    const shouldOpenFeedbackUsingKeybindHelper = (isTutorAssessment: boolean, readOnlyManualFeedback: boolean): [any, any, any] => {
        const feedbackLineOneBased = 4;
        const addNewFeedbackSpy = jest.spyOn(comp, 'addNewFeedback');
        let capturedOnKeyDown: ((e: IKeyboardEvent) => void) | undefined;

        jest.spyOn(comp.editor(), 'onKeyDown').mockImplementation((kd) => {
            capturedOnKeyDown = kd;
            return { dispose: jest.fn() } as any;
        });
        jest.spyOn(comp.editor(), 'getPosition').mockReturnValue({ lineNumber: feedbackLineOneBased, column: 0 });

        fixture.componentRef.setInput('isTutorAssessment', isTutorAssessment);
        fixture.componentRef.setInput('readOnlyManualFeedback', readOnlyManualFeedback);
        fixture.detectChanges();

        (comp as any).setupAddFeedbackShortcut();

        const browserEvent = new KeyboardEvent('keydown', { key: '+', code: 'NumpadAdd' });
        const preventDefaultMock = jest.fn();
        expect(capturedOnKeyDown).toBeDefined();
        capturedOnKeyDown!({ browserEvent, preventDefault: preventDefaultMock } as unknown as IKeyboardEvent);

        (comp as any).disposeAddFeedbackShortcut();
        return [feedbackLineOneBased, preventDefaultMock, addNewFeedbackSpy];
    };

    it('should open feedback widget using keybind viewing a tutor assessment', () => {
        const [feedbackLineOneBased, preventDefaultMock, addNewFeedbackSpy] = shouldOpenFeedbackUsingKeybindHelper(true, false);
        expect(preventDefaultMock).toHaveBeenCalled();
        expect(addNewFeedbackSpy).toHaveBeenCalledExactlyOnceWith(feedbackLineOneBased);
    });

    it('should not open feedback widget using keybind because tutor assessment is disabled', () => {
        const [_, preventDefaultMock, addNewFeedbackSpy] = shouldOpenFeedbackUsingKeybindHelper(false, false);
        expect(preventDefaultMock).not.toHaveBeenCalled();
        expect(addNewFeedbackSpy).not.toHaveBeenCalled();
    });

    it('should not open feedback widget using keybind because read only manual feedback is disabled', () => {
        const [_, preventDefaultMock, addNewFeedbackSpy] = shouldOpenFeedbackUsingKeybindHelper(true, true);
        expect(preventDefaultMock).not.toHaveBeenCalled();
        expect(addNewFeedbackSpy).not.toHaveBeenCalled();
    });

    it('should delete feedbacks and notify', () => {
        const feedbackToDelete = exampleFeedbacks[0];
        const remainingFeedbacks = exampleFeedbacks.slice(1);
        const updateFeedbackCallbackStub = jest.fn();
        comp.onUpdateFeedback.subscribe(updateFeedbackCallbackStub);
        fixture.componentRef.setInput('feedbacks', [...exampleFeedbacks]);
        fixture.detectChanges();
        comp.deleteFeedback(feedbackToDelete);
        expect(comp.feedbackInternal()).toEqual(remainingFeedbacks);
        expect(updateFeedbackCallbackStub).toHaveBeenCalledExactlyOnceWith(remainingFeedbacks);
    });

    it('should delete unsaved feedback', () => {
        const feedbackLine = 1;
        comp.newFeedbackLines.set([feedbackLine, 2, 3]);
        fixture.detectChanges();
        comp.cancelFeedback(feedbackLine);
        expect(comp.newFeedbackLines()).toEqual([2, 3]);
    });

    it('should update existing feedback and notify', () => {
        const feedbackToUpdate: Feedback = { ...exampleFeedbacks[0] };
        const remainingFeedbacks = exampleFeedbacks.slice(1);
        const updateFeedbackCallbackStub = jest.fn();
        comp.onUpdateFeedback.subscribe(updateFeedbackCallbackStub);
        // Copy the original example feedback in to ensure changes here do not affect the component.
        fixture.componentRef.setInput('feedbacks', [...exampleFeedbacks]);
        fixture.detectChanges();
        feedbackToUpdate.text = 'some other text';
        comp.updateFeedback(feedbackToUpdate);
        const expectedFeedbacks = [feedbackToUpdate, ...remainingFeedbacks];
        expect(comp.feedbackInternal()).toEqual(expectedFeedbacks);
        expect(updateFeedbackCallbackStub).toHaveBeenCalledExactlyOnceWith(expectedFeedbacks);
    });

    it('should save new feedback and notify', () => {
        const feedbackToSave: Feedback = { ...exampleFeedbacks[0] };
        const remainingFeedbacks = exampleFeedbacks.slice(1);
        const newFeedbackLine = 1;
        const updateFeedbackCallbackStub = jest.fn();
        comp.onUpdateFeedback.subscribe(updateFeedbackCallbackStub);
        comp.newFeedbackLines.set([newFeedbackLine]);
        fixture.componentRef.setInput('feedbacks', [...remainingFeedbacks]);
        fixture.detectChanges();
        comp.updateFeedback(feedbackToSave);
        const expectedFeedbacks = [...remainingFeedbacks, feedbackToSave];
        expect(comp.feedbackInternal()).toEqual(expectedFeedbacks);
        // The feedback has been saved -> no longer new
        expect(comp.newFeedbackLines()).toHaveLength(0);
        expect(updateFeedbackCallbackStub).toHaveBeenCalledExactlyOnceWith(expectedFeedbacks);
    });

    it('should correctly accept a feedback suggestion and notify', () => {
        const updateFeedbackStub = jest.spyOn(comp, 'updateFeedback').mockImplementation();
        const acceptSuggestionCallbackStub = jest.fn();
        const suggestionToAccept: Feedback = exampleFeedbacks[0];
        fixture.componentRef.setInput('feedbackSuggestions', [suggestionToAccept]);
        comp.onAcceptSuggestion.subscribe(acceptSuggestionCallbackStub);
        fixture.detectChanges();
        comp.acceptSuggestion(suggestionToAccept);
        expect(comp.feedbackSuggestionsInternal()).toHaveLength(0);
        expect(updateFeedbackStub).toHaveBeenCalledExactlyOnceWith(suggestionToAccept);
        expect(acceptSuggestionCallbackStub).toHaveBeenCalledExactlyOnceWith(suggestionToAccept);
    });

    it('should correctly discard a suggestion and notify', () => {
        const discardSuggestionCallbackStub = jest.fn();
        const suggestionToDiscard = exampleFeedbacks[0];
        fixture.componentRef.setInput('feedbackSuggestions', [suggestionToDiscard]);
        comp.onDiscardSuggestion.subscribe(discardSuggestionCallbackStub);
        fixture.detectChanges();
        comp.discardSuggestion(suggestionToDiscard);
        expect(comp.feedbackSuggestionsInternal()).toHaveLength(0);
        expect(discardSuggestionCallbackStub).toHaveBeenCalledExactlyOnceWith(suggestionToDiscard);
    });

    it('should update file session when a file is renamed', async () => {
        const oldFileName = 'old-file-name';
        const newFileName = 'new-file-name';
        const otherFileName = 'other-file';
        const fileSession = {
            [oldFileName]: { code: 'renamed', cursor: { lineNumber: 0, column: 0 }, loadingError: false, scrollTop: 0 },
            [otherFileName]: { code: 'unrelated', cursor: { lineNumber: 0, column: 0 }, loadingError: false, scrollTop: 0 },
        };
        fixture.detectChanges();
        comp.fileSession.set({ ...fileSession });
        const renameFileChange = new RenameFileChange(FileType.FILE, oldFileName, newFileName);
        await comp.onFileChange(renameFileChange);
        expect(comp.fileSession()).toEqual({
            [newFileName]: fileSession[oldFileName],
            [otherFileName]: fileSession[otherFileName],
        });
    });

    it('should update file session when a file is deleted', async () => {
        const fileToDeleteName = 'file-to-delete';
        const otherFileName = 'other-file';
        const fileSession = {
            [fileToDeleteName]: { code: 'will be deleted', cursor: { lineNumber: 0, column: 0 }, loadingError: false, scrollTop: 0 },
            [otherFileName]: { code: 'unrelated', cursor: { lineNumber: 0, column: 0 }, loadingError: false, scrollTop: 0 },
        };
        fixture.detectChanges();
        comp.fileSession.set({ ...fileSession });
        const deleteFileChange = new DeleteFileChange(FileType.FILE, fileToDeleteName);
        await comp.onFileChange(deleteFileChange);
        expect(comp.fileSession()).toEqual({
            [otherFileName]: fileSession[otherFileName],
        });
    });

    it('should update file session when a file is created', async () => {
        const fileToCreateName = 'file-to-create';
        const otherFileName = 'other-file';
        const fileSession = {
            [otherFileName]: { code: 'unrelated', cursor: { lineNumber: 0, column: 0 }, loadingError: false, scrollTop: 0 },
        };
        fixture.detectChanges();
        comp.fileSession.set({ ...fileSession });
        const createFileChange = new CreateFileChange(FileType.FILE, fileToCreateName);
        await comp.onFileChange(createFileChange);
        expect(comp.fileSession()).toEqual({
            [otherFileName]: fileSession[otherFileName],
            [fileToCreateName]: { code: '', cursor: { lineNumber: 0, column: 0 }, loadingError: false, scrollTop: 0 },
        });
    });

    it('should use the correct class to highlight lines', () => {
        const highlightStub = jest.spyOn(comp.editor(), 'highlightLines').mockImplementation();
        fixture.detectChanges();
        comp.highlightLines(1, 2);
        expect(highlightStub).toHaveBeenCalledExactlyOnceWith(1, 2, CodeEditorMonacoComponent.CLASS_DIFF_LINE_HIGHLIGHT);
    });

    it('should remember scroll position', async () => {
        const setScrollTopStub = jest.spyOn(comp.editor(), 'setScrollTop');
        const scrolledFile = 'file';
        const scrollTop = 42;
        const fileSession = {
            [scrolledFile]: { code: 'unrelated', cursor: { lineNumber: 0, column: 0 }, loadingError: false, scrollTop: scrollTop },
        };
        fixture.detectChanges();
        comp.fileSession.set(fileSession);
        fixture.componentRef.setInput('selectedFile', scrolledFile);
        await comp.selectFileInEditor(scrolledFile);
        expect(setScrollTopStub).toHaveBeenCalledExactlyOnceWith(scrollTop);
    });
});
