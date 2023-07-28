import { ComponentFixture, TestBed } from '@angular/core/testing';
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

describe('CodeEditorAceComponent', () => {
    let comp: CodeEditorAceComponent;
    let fixture: ComponentFixture<CodeEditorAceComponent>;
    let debugElement: DebugElement;
    let codeEditorRepositoryFileService: CodeEditorRepositoryFileService;
    let loadRepositoryFileStub: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, AceEditorModule],
            declarations: [
                CodeEditorAceComponent,
                TranslatePipeMock,
                MockComponent(CodeEditorTutorAssessmentInlineFeedbackComponent),
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
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should know the lines in which inline feedback is shown', () => {
        expect(comp.linesWithInlineFeedbackShown).toEqual([]);
        comp.linesWithNewFeedback = [15, 5];
        expect(comp.linesWithInlineFeedbackShown).toEqual([5, 15]);
        comp.fileFeedbackPerLine = { 50: {}, 1050: {} };
        expect(comp.linesWithInlineFeedbackShown).toEqual([5, 15, 50, 1050]);
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

    it('should correctly init editor after file change', () => {
        const selectedFile = 'dummy';
        const fileSession = {};
        const loadFileSubject = new Subject();
        const initEditorAfterFileChangeSpy = jest.spyOn(comp, 'initEditorAfterFileChange');
        loadRepositoryFileStub.mockReturnValue(loadFileSubject);
        comp.selectedFile = selectedFile;
        comp.fileSession = fileSession;

        triggerChanges(comp, { property: 'selectedFile', currentValue: selectedFile });
        fixture.detectChanges();

        expect(comp.isLoading).toBeTrue();
        expect(loadRepositoryFileStub).toHaveBeenCalledWith(selectedFile);
        expect(initEditorAfterFileChangeSpy).not.toHaveBeenCalled();
        loadFileSubject.next({ fileName: selectedFile, fileContent: 'lorem ipsum' });
        fixture.detectChanges();

        expect(comp.isLoading).toBeFalse();
        expect(comp.fileSession).toEqual({ dummy: { code: 'lorem ipsum', cursor: { column: 0, row: 0 }, loadingError: false } });
        expect(initEditorAfterFileChangeSpy).toHaveBeenCalledWith();
    });

    it.each([
        [new ConnectionError(), 'loadingFailedInternetDisconnected'],
        [new Error(), 'loadingFailed'],
    ])('should correctly init editor after file change in case of error', (error: Error, errorCode: string) => {
        const selectedFile = 'dummy';
        const fileSession = {};
        const loadFileSubject = new Subject();
        const initEditorAfterFileChangeSpy = jest.spyOn(comp, 'initEditorAfterFileChange');
        const onErrorSpy = jest.spyOn(comp.onError, 'emit');
        loadRepositoryFileStub.mockReturnValue(loadFileSubject);
        comp.selectedFile = selectedFile;
        comp.fileSession = fileSession;

        triggerChanges(comp, { property: 'selectedFile', currentValue: selectedFile });
        fixture.detectChanges();

        expect(comp.isLoading).toBeTrue();
        expect(loadRepositoryFileStub).toHaveBeenCalledWith(selectedFile);
        expect(initEditorAfterFileChangeSpy).not.toHaveBeenCalled();
        loadFileSubject.error(error);
        fixture.detectChanges();

        expect(comp.isLoading).toBeFalse();
        expect(comp.fileSession).toEqual({ dummy: { code: '', cursor: { column: 0, row: 0 }, loadingError: true } });
        expect(initEditorAfterFileChangeSpy).toHaveBeenCalledWith();
        expect(onErrorSpy).toHaveBeenCalledWith(errorCode);
    });

    it('should discard all new feedback after a re-init because of a file change', () => {
        const getInlineFeedbackNodeStub = jest.spyOn(comp, 'getInlineFeedbackNode');
        getInlineFeedbackNodeStub.mockReturnValue(undefined);
        comp.addLineWidgetWithFeedback(16);
        comp.initEditorAfterFileChange();
        expect(comp.linesWithNewFeedback).toEqual([]);
    });

    it('should not load the file from server on selected file change if the file is already in session', () => {
        const selectedFile = 'dummy';
        const fileSession = { [selectedFile]: { code: 'lorem ipsum', cursor: { column: 0, row: 0 }, loadingError: false } };
        const initEditorAfterFileChangeSpy = jest.spyOn(comp, 'initEditorAfterFileChange');
        const loadFileSpy = jest.spyOn(comp, 'loadFile');
        comp.selectedFile = selectedFile;
        comp.fileSession = fileSession;

        triggerChanges(comp, { property: 'selectedFile', currentValue: selectedFile });
        fixture.detectChanges();

        expect(initEditorAfterFileChangeSpy).toHaveBeenCalledWith();
        expect(loadFileSpy).not.toHaveBeenCalled();
    });

    it('should load the file from server on selected file change if the file is already in session but there was a loading error', () => {
        const selectedFile = 'dummy';
        const fileSession = { [selectedFile]: { code: 'lorem ipsum', cursor: { column: 0, row: 0 }, loadingError: true } };
        const initEditorAfterFileChangeSpy = jest.spyOn(comp, 'initEditorAfterFileChange');
        const loadFileSpy = jest.spyOn(comp, 'loadFile');
        comp.selectedFile = selectedFile;
        comp.fileSession = fileSession;

        triggerChanges(comp, { property: 'selectedFile', currentValue: selectedFile });
        fixture.detectChanges();

        expect(initEditorAfterFileChangeSpy).not.toHaveBeenCalled();
        expect(loadFileSpy).toHaveBeenCalledOnce();
    });

    it('should update file session references on file rename', () => {
        const selectedFile = 'file';
        const newFileName = 'newFilename';
        const fileChange = new RenameFileChange(FileType.FILE, selectedFile, newFileName);
        const fileSession = {
            [selectedFile]: { code: 'lorem ipsum', cursor: { column: 0, row: 0 }, loadingError: false },
            anotherFile: { code: 'lorem ipsum 2', cursor: { column: 0, row: 0 }, loadingError: false },
        };
        comp.selectedFile = newFileName;
        comp.fileSession = fileSession;

        comp.onFileChange(fileChange);

        expect(comp.fileSession).toEqual({ anotherFile: fileSession.anotherFile, [fileChange.newFileName]: fileSession[selectedFile] });
    });

    it('should init editor on newly created file if selected', () => {
        const selectedFile = 'file';
        const fileChange = new CreateFileChange(FileType.FILE, selectedFile);
        const fileSession = { anotherFile: { code: 'lorem ipsum 2', cursor: { column: 0, row: 0 }, loadingError: false } };
        const initEditorAfterFileChangeSpy = jest.spyOn(comp, 'initEditorAfterFileChange');
        comp.selectedFile = selectedFile;
        comp.fileSession = fileSession;

        comp.onFileChange(fileChange);

        expect(initEditorAfterFileChangeSpy).toHaveBeenCalledWith();
        expect(comp.fileSession).toEqual({ anotherFile: fileSession.anotherFile, [fileChange.fileName]: { code: '', cursor: { row: 0, column: 0 }, loadingError: false } });
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

    it('should update build log errors and emit a message on file text change', () => {
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
        comp.onFileTextChanged(newFileContent);

        expect(onFileContentChangeSpy).toHaveBeenCalledWith({ file: selectedFile, fileContent: newFileContent });
        const newAnnotations = [{ fileName: selectedFile, text: 'error', type: 'error', timestamp: 0, row: 4, column: 4 }];
        expect(comp.annotationsArray).toEqual(newAnnotations);
    });

    it('should be in readonly mode and display feedback when in tutor assessment', () => {
        comp.isTutorAssessment = true;
        comp.fileFeedbacks = [];
        const displayFeedbacksSpy = jest.spyOn(comp, 'displayFeedbacks');
        comp.onFileTextChanged('newFileContent');

        expect(comp.editor.getEditor().getReadOnly()).toBeTrue();
        expect(displayFeedbacksSpy).toHaveBeenCalledOnce();
    });

    it('should be in readonly mode when the file could not be loaded', () => {
        comp.selectedFile = 'asdf';
        comp.fileSession = { asdf: { code: '', cursor: { column: 0, row: 0 }, loadingError: true } };

        fixture.detectChanges();

        expect(comp.editor.getEditor().getReadOnly()).toBeTrue();
    });

    it('should setup inline comment buttons in gutter', () => {
        comp.isTutorAssessment = true;
        comp.readOnlyManualFeedback = false;
        comp.fileFeedbacks = [];
        const setupLineIconsSpy = jest.spyOn(comp, 'setupLineIcons');
        const observerDomSpy = jest.spyOn(comp, 'observerDom');
        comp.onFileTextChanged('newFileContent');

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

    it('should temporarily show new feedbacks which have not been updated yet', () => {
        const getInlineFeedbackNodeSpy = jest.spyOn(comp, 'getInlineFeedbackNode');
        getInlineFeedbackNodeSpy.mockReturnValue(undefined);
        comp.addLineWidgetWithFeedback(16);
        expect(comp.linesWithNewFeedback).toEqual([16]);
    });

    it('should not show an updated feedback as new', () => {
        comp.feedbacks = [];
        const getInlineFeedbackNodeSpy = jest.spyOn(comp, 'getInlineFeedbackNode');
        getInlineFeedbackNodeSpy.mockReturnValue(undefined);
        const adjustLineWidgetHeightStub = jest.spyOn(comp, 'adjustLineWidgetHeight');
        adjustLineWidgetHeightStub.mockImplementation(() => {});
        comp.addLineWidgetWithFeedback(16);
        comp.addLineWidgetWithFeedback(17);
        comp.updateFeedback({ reference: 'line:16' });
        expect(comp.linesWithNewFeedback).toEqual([17]);
    });

    it('should not show new feedback that is cancelled anymore', () => {
        const getInlineFeedbackNodeSpy = jest.spyOn(comp, 'getInlineFeedbackNode');
        getInlineFeedbackNodeSpy.mockReturnValue(undefined);
        comp.editorSession = { lineWidgets: [], widgetManager: { removeLineWidget: () => {} } } as any;
        const removeLineWidget = jest.spyOn(comp.editorSession.widgetManager, 'removeLineWidget');
        comp.addLineWidgetWithFeedback(1);
        comp.addLineWidgetWithFeedback(16);
        comp.cancelFeedback(16);
        expect(comp.linesWithNewFeedback).toEqual([1]);
        expect(removeLineWidget).toHaveBeenCalled();
    });

    it('should explicitly remove all ACE line widgets when being destroyed', () => {
        const getInlineFeedbackNodeStub = jest.spyOn(comp, 'getInlineFeedbackNode');
        getInlineFeedbackNodeStub.mockReturnValue(undefined);
        comp.editorSession = { lineWidgets: [], widgetManager: { removeLineWidget: () => {} } } as any;
        const removeLineWidget = jest.spyOn(comp.editorSession.widgetManager, 'removeLineWidget');
        comp.addLineWidgetWithFeedback(1);
        comp.addLineWidgetWithFeedback(16);
        comp.ngOnDestroy();
        expect(removeLineWidget).toHaveBeenCalledTimes(2);
    });
});
