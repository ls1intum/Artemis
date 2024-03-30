import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { DebugElement, EventEmitter } from '@angular/core';
import { NgModel } from '@angular/forms';
import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { Subject } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { CreateFileChange, FileType, RenameFileChange } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { triggerChanges } from '../../helpers/utils/general.utils';
import { CodeEditorRepositoryFileService, ConnectionError } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { CodeEditorFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-file.service';
import { CodeEditorAceComponent } from 'app/exercises/programming/shared/code-editor/ace/code-editor-ace.component';
import { MockCodeEditorRepositoryFileService } from '../../helpers/mocks/service/mock-code-editor-repository-file.service';
import { LocalStorageService } from 'ngx-webstorage';
import { MockLocalStorageService } from '../../helpers/mocks/service/mock-local-storage.service';
import { MockComponent, MockDirective } from 'ng-mocks';
import { CodeEditorTutorAssessmentInlineFeedbackComponent } from 'app/exercises/programming/assess/code-editor-tutor-assessment-inline-feedback.component';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { CodeEditorHeaderComponent } from 'app/exercises/programming/shared/code-editor/header/code-editor-header.component';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { MockResizeObserver } from '../../helpers/mocks/service/mock-resize-observer';
import { CodeEditorTutorAssessmentInlineFeedbackSuggestionComponent } from 'app/exercises/programming/assess/code-editor-tutor-assessment-inline-feedback-suggestion.component';

describe('CodeEditorAceComponent', () => {
    let comp: CodeEditorAceComponent;
    let fixture: ComponentFixture<CodeEditorAceComponent>;
    let debugElement: DebugElement;
    let codeEditorRepositoryFileService: CodeEditorRepositoryFileService;
    let loadRepositoryFileStub: jest.SpyInstance;
    let getInlineFeedbackNodeStub: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, AceEditorModule],
            declarations: [
                CodeEditorAceComponent,
                TranslatePipeMock,
                MockComponent(CodeEditorTutorAssessmentInlineFeedbackComponent),
                MockComponent(CodeEditorTutorAssessmentInlineFeedbackSuggestionComponent),
                MockComponent(CodeEditorHeaderComponent),
                MockDirective(NgModel),
            ],
            providers: [
                CodeEditorFileService,
                { provide: CodeEditorRepositoryFileService, useClass: MockCodeEditorRepositoryFileService },
                { provide: LocalStorageService, useClass: MockLocalStorageService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CodeEditorAceComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                codeEditorRepositoryFileService = debugElement.injector.get(CodeEditorRepositoryFileService);
                loadRepositoryFileStub = jest.spyOn(codeEditorRepositoryFileService, 'getFile');
                getInlineFeedbackNodeStub = jest.spyOn(comp, 'getInlineFeedbackNode');
                getInlineFeedbackNodeStub.mockReturnValue(document.createElement('div'));
                // Mock the ResizeObserver, which is not available in the test environment
                global.ResizeObserver = jest.fn().mockImplementation((callback: ResizeObserverCallback) => {
                    return new MockResizeObserver(callback);
                });
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('without any inputs, should still render correctly without ace, showing a placeholder', () => {
        fixture.detectChanges();
        const placeholder = debugElement.query(By.css('#no-file-selected'));
        expect(placeholder).not.toBeNull();
        const aceEditor = debugElement.query(By.css('#ace-code-editor'));
        expect(aceEditor.nativeElement.hasAttribute('hidden')).toBeTrue();
    });

    it('if the component is loading a file from server, it should show the editor in a readonly state', () => {
        comp.selectedFile = 'dummy';
        comp.isLoading = true;
        fixture.detectChanges();
        const placeholder = debugElement.query(By.css('#no-file-selected'));
        expect(placeholder).toBeNull();
        const aceEditor = debugElement.query(By.css('#ace-code-editor'));
        expect(aceEditor.nativeElement.hasAttribute('hidden')).toBeTrue();
        expect(comp.editor.getEditor().getReadOnly()).toBeTrue();

        comp.isLoading = false;
        fixture.detectChanges();
        expect(aceEditor.nativeElement.hasAttribute('hidden')).toBeFalse();
        expect(comp.editor.getEditor().getReadOnly()).toBeFalse();
    });

    it('if actions are disabled, it should show the editor in a readonly state', () => {
        comp.selectedFile = 'dummy';
        comp.disableActions = true;
        fixture.detectChanges();
        const aceEditor = debugElement.query(By.css('#ace-code-editor'));
        expect(aceEditor.nativeElement.hasAttribute('hidden')).toBeFalse();
        expect(comp.editor.getEditor().getReadOnly()).toBeTrue();
    });

    it('if a file is selected and the component is not loading a file from server, the editor should be usable', () => {
        comp.selectedFile = 'dummy';
        comp.isLoading = false;
        fixture.detectChanges();
        const placeholder = debugElement.query(By.css('#no-file-selected'));
        expect(placeholder).toBeNull();
        const aceEditor = debugElement.query(By.css('#ace-code-editor'));
        expect(aceEditor.nativeElement.hasAttribute('hidden')).toBeFalse();
        expect(comp.editor.getEditor().getReadOnly()).toBeFalse();
    });

    it('should correctly init editor after file change', waitForAsync(async () => {
        const selectedFile = 'dummy';
        const fileSession = {};
        const loadFileSubject = new Subject();
        const initEditorSpy = jest.spyOn(comp, 'initEditor');
        loadRepositoryFileStub.mockReturnValue(loadFileSubject);
        comp.selectedFile = selectedFile;
        comp.fileSession = fileSession;

        triggerChanges(comp, { property: 'selectedFile', currentValue: selectedFile });
        fixture.detectChanges();
        await fixture.whenStable();

        expect(comp.isLoading).toBeTrue();
        expect(loadRepositoryFileStub).toHaveBeenCalledWith(selectedFile);
        expect(initEditorSpy).not.toHaveBeenCalled();
        loadFileSubject.next({ fileName: selectedFile, fileContent: 'lorem ipsum' });
        fixture.detectChanges();
        await fixture.whenStable();

        expect(comp.isLoading).toBeFalse();
        expect(comp.fileSession).toEqual({
            dummy: {
                code: 'lorem ipsum',
                cursor: { column: 0, row: 0 },
                loadingError: false,
            },
        });
        expect(initEditorSpy).toHaveBeenCalledWith();
    }));

    it.each([
        [new ConnectionError(), 'loadingFailedInternetDisconnected'],
        [new Error(), 'loadingFailed'],
    ])(
        'should correctly init editor after file change in case of error',
        waitForAsync(async (error: Error, errorCode: string) => {
            const selectedFile = 'dummy';
            const fileSession = {};
            const loadFileSubject = new Subject();
            const initEditorSpy = jest.spyOn(comp, 'initEditor');
            const onErrorSpy = jest.spyOn(comp.onError, 'emit');
            loadRepositoryFileStub.mockReturnValue(loadFileSubject);
            comp.selectedFile = selectedFile;
            comp.fileSession = fileSession;

            triggerChanges(comp, { property: 'selectedFile', currentValue: selectedFile });
            fixture.detectChanges();
            await fixture.whenStable();

            expect(comp.isLoading).toBeTrue();
            expect(loadRepositoryFileStub).toHaveBeenCalledWith(selectedFile);
            expect(initEditorSpy).not.toHaveBeenCalled();
            loadFileSubject.error(error);
            fixture.detectChanges();
            await fixture.whenStable();

            expect(comp.isLoading).toBeFalse();
            expect(comp.fileSession).toEqual({ dummy: { code: '', cursor: { column: 0, row: 0 }, loadingError: true } });
            expect(initEditorSpy).toHaveBeenCalledWith();
            expect(onErrorSpy).toHaveBeenCalledWith(errorCode);
        }),
    );

    it('should discard all new feedback after a re-init because of a file change', async () => {
        comp.newFeedbackLines = [1, 2, 3];
        await comp.initEditor();
        expect(comp.newFeedbackLines).toEqual([]);
    });

    it('should not load the file from server on selected file change if the file is already in session', () => {
        const selectedFile = 'dummy';
        const fileSession = { [selectedFile]: { code: 'lorem ipsum', cursor: { column: 0, row: 0 }, loadingError: false } };
        const initEditorSpy = jest.spyOn(comp, 'initEditor');
        const loadFileSpy = jest.spyOn(comp, 'fetchFileContent');
        comp.selectedFile = selectedFile;
        comp.fileSession = fileSession;

        triggerChanges(comp, { property: 'selectedFile', currentValue: selectedFile });
        fixture.detectChanges();

        expect(initEditorSpy).toHaveBeenCalledWith();
        expect(loadFileSpy).not.toHaveBeenCalled();
    });

    it('should load the file from server on selected file change if the file is already in session but there was a loading error', waitForAsync(async () => {
        const selectedFile = 'dummy';
        const fileSession = { [selectedFile]: { code: 'lorem ipsum', cursor: { column: 0, row: 0 }, loadingError: true } };
        const initEditorSpy = jest.spyOn(comp, 'initEditor');
        const loadFileSpy = jest.spyOn(comp, 'fetchFileContent');
        const loadFileSubject = new Subject<{ fileName: 'dummy'; fileContent: 'lorem ipsum' }>();
        loadRepositoryFileStub.mockReturnValue(loadFileSubject);
        comp.selectedFile = selectedFile;
        comp.fileSession = fileSession;

        triggerChanges(comp, { property: 'selectedFile', currentValue: selectedFile });
        fixture.detectChanges();
        await fixture.whenStable();

        expect(initEditorSpy).not.toHaveBeenCalled();
        expect(loadFileSpy).toHaveBeenCalledOnce();
    }));

    it('should update file session references on file rename', async () => {
        const selectedFile = 'file';
        const newFileName = 'newFilename';
        const fileChange = new RenameFileChange(FileType.FILE, selectedFile, newFileName);
        const fileSession = {
            [selectedFile]: { code: 'lorem ipsum', cursor: { column: 0, row: 0 }, loadingError: false },
            anotherFile: { code: 'lorem ipsum 2', cursor: { column: 0, row: 0 }, loadingError: false },
        };
        comp.selectedFile = newFileName;
        comp.fileSession = fileSession;

        await comp.onFileChange(fileChange);

        expect(comp.fileSession).toEqual({
            anotherFile: fileSession.anotherFile,
            [fileChange.newFileName]: fileSession[selectedFile],
        });
    });

    it('should init editor on newly created file if selected', async () => {
        const selectedFile = 'file';
        const fileChange = new CreateFileChange(FileType.FILE, selectedFile);
        const fileSession = { anotherFile: { code: 'lorem ipsum 2', cursor: { column: 0, row: 0 }, loadingError: false } };
        const initEditorSpy = jest.spyOn(comp, 'initEditor');
        comp.selectedFile = selectedFile;
        comp.fileSession = fileSession;

        await comp.onFileChange(fileChange);

        expect(initEditorSpy).toHaveBeenCalledWith();
        expect(comp.fileSession).toEqual({
            anotherFile: fileSession.anotherFile,
            [fileChange.fileName]: { code: '', cursor: { row: 0, column: 0 }, loadingError: false },
        });
    });

    it('should not do anything on file content change if the code has not changed', () => {
        const textChangeEmitter = new EventEmitter<string>();
        comp.editor.textChanged = textChangeEmitter;
        const onFileContentChangeSpy = jest.spyOn(comp.onFileContentChange, 'emit');

        const selectedFile = 'file';
        const fileSession = { [selectedFile]: { code: 'lorem ipsum', cursor: { column: 0, row: 0 }, loadingError: false } };
        comp.selectedFile = selectedFile;
        comp.fileSession = fileSession;

        textChangeEmitter.emit('lorem ipsum');

        expect(onFileContentChangeSpy).not.toHaveBeenCalled();
    });

    it('should update build log errors and emit a message on file text change', async () => {
        const onFileContentChangeSpy = jest.spyOn(comp.onFileContentChange, 'emit');

        const selectedFile = 'file';
        const newFileContent = 'lorem ipsum new';
        const fileSession = { [selectedFile]: { code: 'lorem ipsum', cursor: { column: 0, row: 0 }, loadingError: false } };
        const annotations = [{ fileName: selectedFile, row: 5, column: 4, text: 'error', type: 'error', timestamp: 0 }];
        const editorChange = { start: { row: 1, column: 1 }, end: { row: 2, column: 1 }, action: 'remove' };

        comp.selectedFile = selectedFile;
        comp.fileSession = fileSession;
        comp.annotationsArray = annotations;

        comp.updateAnnotations(editorChange);
        await comp.onFileTextChanged(newFileContent);

        expect(onFileContentChangeSpy).toHaveBeenCalledWith({ file: selectedFile, fileContent: newFileContent });
        const newAnnotations = [
            {
                fileName: selectedFile,
                text: 'error',
                type: 'error',
                timestamp: 0,
                row: 4,
                column: 4,
            },
        ];
        expect(comp.annotationsArray).toEqual(newAnnotations);
    });

    it('should be in readonly mode and display feedback when in tutor assessment', async () => {
        await comp.initEditor();
        comp.isTutorAssessment = true;
        const updateLineWidgetsSpy = jest.spyOn(comp, 'updateLineWidgets');
        await comp.onFileTextChanged('newFileContent');

        expect(comp.editor.getEditor().getReadOnly()).toBeTrue();
        expect(updateLineWidgetsSpy).toHaveBeenCalledOnce();
    });

    it('should be in readonly mode when the file could not be loaded', () => {
        comp.selectedFile = 'asdf';
        comp.fileSession = { asdf: { code: '', cursor: { column: 0, row: 0 }, loadingError: true } };

        fixture.detectChanges();

        expect(comp.editor.getEditor().getReadOnly()).toBeTrue();
    });

    it('should setup inline comment buttons in gutter', async () => {
        await comp.initEditor();
        comp.isTutorAssessment = true;
        comp.readOnlyManualFeedback = false;
        const setupLineIconsSpy = jest.spyOn(comp, 'setupLineIcons');
        const observerDomSpy = jest.spyOn(comp, 'observerDom');
        await comp.onFileTextChanged('newFileContent');

        expect(setupLineIconsSpy).toHaveBeenCalledOnce();
        expect(observerDomSpy).toHaveBeenCalledOnce();
    });

    it('should change the displayed tab width', () => {
        const editorTabSize = () => comp.editor.getEditor().getSession().getTabSize();

        comp.updateTabSize(5);
        fixture.detectChanges();
        expect(editorTabSize()).toBe(5);

        comp.tabSize = 4;
        fixture.detectChanges();
        expect(editorTabSize()).toBe(4);
    });

    it('should temporarily show new feedbacks which have not been updated yet', async () => {
        await comp.initEditor();
        comp.newFeedbackLines = [16];
        await comp.updateLineWidgets();
        expect(comp.editorSession.lineWidgets[16]).toBeDefined();
    });

    it('should not show an updated feedback as new', async () => {
        await comp.initEditor();
        comp.feedbacks = [];
        const adjustLineWidgetHeightStub = jest.spyOn(comp, 'adjustLineWidgetHeight');
        adjustLineWidgetHeightStub.mockImplementation(() => {});
        comp.newFeedbackLines = [16, 17];
        await comp.updateLineWidgets();
        await comp.updateFeedback({ reference: 'file:src/Test.java_line:16' });
        expect(comp.newFeedbackLines).toEqual([17]);
    });

    it('should not show new feedback that is cancelled anymore', async () => {
        await comp.initEditor();
        const removeLineWidget = jest.spyOn(comp.editorSession.widgetManager, 'removeLineWidget');
        comp.newFeedbackLines = [1, 16];
        await comp.updateLineWidgets();
        await comp.cancelFeedback(16);
        expect(comp.newFeedbackLines).toEqual([1]);
        expect(removeLineWidget).toHaveBeenCalled();
    });

    it('should explicitly remove all ACE line widgets when being destroyed', async () => {
        await comp.initEditor();
        comp.newFeedbackLines = [1, 16];
        await comp.updateLineWidgets();
        const removeLineWidget = jest.spyOn(comp.editorSession.widgetManager, 'removeLineWidget');
        comp.ngOnDestroy();
        expect(removeLineWidget).toHaveBeenCalledTimes(2);
    });

    it('should only show referenced feedback that is for the current file', () => {
        comp.selectedFile = 'src/Test.java';
        const feedbacks = [{ reference: 'file:src/Test.java_line:16' }, { reference: 'file:src/Another.java_line:16' }, { reference: undefined }];
        expect(comp.filterFeedbackForFile(feedbacks)).toEqual([{ reference: 'file:src/Test.java_line:16' }]);
    });

    it('should convert an accepted feedback suggestion to a marked manual feedback', async () => {
        await comp.initEditor();
        const suggestion = {
            text: 'FeedbackSuggestion:',
            detailText: 'test',
            reference: 'file:src/Test.java_line:16',
            type: FeedbackType.MANUAL,
        };
        comp.feedbackSuggestions = [suggestion];
        await comp.acceptSuggestion(suggestion);
        expect(comp.feedbackSuggestions).toBeEmpty();
        expect(comp.feedbacks).toEqual([
            {
                text: 'FeedbackSuggestion:accepted:',
                detailText: 'test',
                reference: 'file:src/Test.java_line:16',
                type: FeedbackType.MANUAL,
            },
        ]);
    });

    it('should remove discarded suggestions', async () => {
        await comp.initEditor();
        const suggestion = {
            text: 'FeedbackSuggestion:',
            detailText: 'test',
            reference: 'file:src/Test.java_line:16',
            type: FeedbackType.MANUAL,
        };
        comp.feedbackSuggestions = [suggestion];
        comp.discardSuggestion(suggestion);
        expect(comp.feedbackSuggestions).toBeEmpty();
    });

    it('should update the line widget heights when feedbacks or suggestions change', async () => {
        const updateLineWidgetHeightSpy = jest.spyOn(comp, 'updateLineWidgets');

        await comp.initEditor();

        // Change of feedbacks from the outside
        await comp.ngOnChanges({
            feedbacks: {
                previousValue: [],
                currentValue: [new Feedback()],
                firstChange: true,
                isFirstChange: () => true,
            },
        });
        expect(updateLineWidgetHeightSpy).toHaveBeenCalled();

        // Change of feedback suggestions from the outside
        await comp.ngOnChanges({
            feedbackSuggestions: {
                previousValue: [],
                currentValue: [new Feedback()],
                firstChange: true,
                isFirstChange: () => true,
            },
        });
        expect(updateLineWidgetHeightSpy).toHaveBeenCalled();
    });

    it('renders line widgets for feedback suggestions', async () => {
        await comp.initEditor();
        const addLineWidgetWithFeedbackSpy = jest.spyOn(comp, 'addLineWidgetWithFeedback');

        comp.feedbackSuggestions = [
            {
                text: 'FeedbackSuggestion:',
                detailText: 'test',
                reference: 'file:src/Test.java_line:16',
                type: FeedbackType.MANUAL,
            },
        ];
        comp.selectedFile = 'src/Test.java';
        await comp.updateLineWidgets();

        expect(addLineWidgetWithFeedbackSpy).toHaveBeenCalled();
    });

    describe('feedback deletion', () => {
        let f1: Feedback;
        let f2: Feedback;
        beforeEach(() => {
            f1 = new Feedback();
            f1.text = 'File src/abc/BubbleSort.java at line 6';
            f1.detailText = 'a';
            f1.reference = 'file:src/abc/BubbleSort.java_line:5';

            f2 = new Feedback();
            f2.text = 'File src/abc/BubbleSort.java at line 7';
            f2.detailText = 'b';
            f2.reference = 'file:src/abc/BubbleSort.java_line:6';
        });

        it('should delete correct inline feedback before saving for the first time', async () => {
            await comp.initEditor();
            await comp.updateFeedback(f1);
            await comp.updateFeedback(f2);

            comp.deleteFeedback(f1);

            expect(comp.feedbacks).not.toContain(f1);
            expect(comp.feedbacks).toContain(f2);
        });

        it('should delete correct inline feedback after saving', async () => {
            await comp.initEditor();
            await comp.updateFeedback(f1);
            await comp.updateFeedback(f2);

            // saving is simulated here by giving the feedbacks an id, which is the only remaining untested factor for
            // feedback deletion
            f1.id = 1;
            f2.id = 2;

            comp.deleteFeedback(f1);

            expect(comp.feedbacks).not.toContain(f1);
            expect(comp.feedbacks).toContain(f2);
        });
    });
});
