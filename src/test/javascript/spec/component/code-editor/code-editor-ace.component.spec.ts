import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { DebugElement, EventEmitter } from '@angular/core';
import { NgModel } from '@angular/forms';
import { NgbDropdown } from '@ng-bootstrap/ng-bootstrap';
import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { Subject } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { CreateFileChange, FileType, RenameFileChange } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { triggerChanges } from '../../helpers/utils/general.utils';
import { CodeEditorRepositoryFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { CodeEditorFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-file.service';
import { CodeEditorAceComponent } from 'app/exercises/programming/shared/code-editor/ace/code-editor-ace.component';
import { MockCodeEditorRepositoryFileService } from '../../helpers/mocks/service/mock-code-editor-repository-file.service';
import { LocalStorageService } from 'ngx-webstorage';
import { MockLocalStorageService } from '../../helpers/mocks/service/mock-local-storage.service';
import { MockComponent, MockDirective } from 'ng-mocks';
import { CodeEditorTutorAssessmentInlineFeedbackComponent } from 'app/exercises/programming/assess/code-editor-tutor-assessment-inline-feedback.component';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { MAX_TAB_SIZE } from 'app/shared/markdown-editor/ace-editor/ace-editor.component';

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
                MockDirective(NgModel),
                MockDirective(NgbDropdown),
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

    it('without any inputs, should still render correctly without ace, showing a placeholder', () => {
        fixture.detectChanges();
        const placeholder = debugElement.query(By.css('#no-file-selected'));
        expect(placeholder).not.toBe(null);
        const aceEditor = debugElement.query(By.css('#ace-code-editor'));
        expect(aceEditor.nativeElement.hasAttribute('hidden')).toBeTrue();
    });

    it('if the component is loading a file from server, it should show the editor in a readonly state', () => {
        comp.selectedFile = 'dummy';
        comp.isLoading = true;
        fixture.detectChanges();
        const placeholder = debugElement.query(By.css('#no-file-selected'));
        expect(placeholder).toBe(null);
        const aceEditor = debugElement.query(By.css('#ace-code-editor'));
        expect(aceEditor.nativeElement.hasAttribute('hidden')).toBeTrue();
        expect(comp.editor.getEditor().getReadOnly()).toBeTrue();

        comp.isLoading = false;
        fixture.detectChanges();
        expect(aceEditor.nativeElement.hasAttribute('hidden')).toBeFalse();
        expect(comp.editor.getEditor().getReadOnly()).toBeFalse();
    });

    it('if a file is selected and the component is not loading a file from server, the editor should be usable', () => {
        comp.selectedFile = 'dummy';
        comp.isLoading = false;
        fixture.detectChanges();
        const placeholder = debugElement.query(By.css('#no-file-selected'));
        expect(placeholder).toBe(null);
        const aceEditor = debugElement.query(By.css('#ace-code-editor'));
        expect(aceEditor.nativeElement.hasAttribute('hidden')).toBeFalse();
        expect(comp.editor.getEditor().getReadOnly()).toBeFalse();
    });

    it('should not load the file from server on selected file change if the file is already in session', () => {
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
        expect(initEditorAfterFileChangeSpy).toHaveBeenCalledWith();
    });

    it('should not load the file from server on selected file change if the file is already in session', () => {
        const selectedFile = 'dummy';
        const fileSession = { [selectedFile]: { code: 'lorem ipsum', cursor: { column: 0, row: 0 } } };
        const initEditorAfterFileChangeSpy = jest.spyOn(comp, 'initEditorAfterFileChange');
        const loadFileSpy = jest.spyOn(comp, 'loadFile');
        comp.selectedFile = selectedFile;
        comp.fileSession = fileSession;

        triggerChanges(comp, { property: 'selectedFile', currentValue: selectedFile });
        fixture.detectChanges();

        expect(initEditorAfterFileChangeSpy).toHaveBeenCalledWith();
        expect(loadFileSpy).not.toHaveBeenCalled();
    });

    it('should update file session references on file rename', () => {
        const selectedFile = 'file';
        const newFileName = 'newFilename';
        const fileChange = new RenameFileChange(FileType.FILE, selectedFile, newFileName);
        const fileSession = { [selectedFile]: { code: 'lorem ipsum', cursor: { column: 0, row: 0 } }, anotherFile: { code: 'lorem ipsum 2', cursor: { column: 0, row: 0 } } };
        comp.selectedFile = newFileName;
        comp.fileSession = fileSession;

        comp.onFileChange(fileChange);

        expect(comp.fileSession).toEqual({ anotherFile: fileSession.anotherFile, [fileChange.newFileName]: fileSession[selectedFile] });
    });

    it('should init editor on newly created file if selected', () => {
        const selectedFile = 'file';
        const fileChange = new CreateFileChange(FileType.FILE, selectedFile);
        const fileSession = { anotherFile: { code: 'lorem ipsum 2', cursor: { column: 0, row: 0 } } };
        const initEditorAfterFileChangeSpy = jest.spyOn(comp, 'initEditorAfterFileChange');
        comp.selectedFile = selectedFile;
        comp.fileSession = fileSession;

        comp.onFileChange(fileChange);

        expect(initEditorAfterFileChangeSpy).toHaveBeenCalledWith();
        expect(comp.fileSession).toEqual({ anotherFile: fileSession.anotherFile, [fileChange.fileName]: { code: '', cursor: { row: 0, column: 0 } } });
    });

    it('should not do anything on file content change if the code has not changed', () => {
        const textChangeEmitter = new EventEmitter<string>();
        comp.editor.textChanged = textChangeEmitter;
        const onFileContentChangeSpy = jest.spyOn(comp.onFileContentChange, 'emit');

        const selectedFile = 'file';
        const fileSession = { [selectedFile]: { code: 'lorem ipsum', cursor: { column: 0, row: 0 } } };
        comp.selectedFile = selectedFile;
        comp.fileSession = fileSession;

        textChangeEmitter.emit('lorem ipsum');

        expect(onFileContentChangeSpy).not.toHaveBeenCalled();
    });

    it('should update build log errors and emit a message on file text change', () => {
        const onFileContentChangeSpy = jest.spyOn(comp.onFileContentChange, 'emit');

        const selectedFile = 'file';
        const newFileContent = 'lorem ipsum new';
        const fileSession = { [selectedFile]: { code: 'lorem ipsum', cursor: { column: 0, row: 0 } } };
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

    it('should setup inline comment buttons in gutter', () => {
        comp.isTutorAssessment = true;
        comp.readOnlyManualFeedback = false;
        comp.fileFeedbacks = [];
        const setupLineIconsSpy = jest.spyOn(comp, 'setupLineIcons');
        const observerDomSpy = jest.spyOn(comp, 'observerDom');
        comp.onFileTextChanged('newFileContent');

        expect(setupLineIconsSpy).toBeCalledTimes(1);
        expect(observerDomSpy).toBeCalledTimes(1);
    });

    it('should only allow tab sizes between 1 and the maximum size', () => {
        comp.tabSize = 4;
        comp.validateTabSize();
        expect(comp.tabSize).toBe(4);

        comp.tabSize = -1;
        comp.validateTabSize();
        expect(comp.tabSize).toBe(1);

        comp.tabSize = MAX_TAB_SIZE + 10;
        comp.validateTabSize();
        expect(comp.tabSize).toBe(MAX_TAB_SIZE);
    });

    it('should only change the displayed tab width if it is valid', () => {
        const editorTabSize = () => comp.editor.getEditor().getSession().getTabSize();

        comp.tabSize = 5;
        fixture.detectChanges();
        expect(editorTabSize()).toBe(5);

        // invalid values either too small or too big should be ignored
        comp.tabSize = -1;
        fixture.detectChanges();
        expect(editorTabSize()).toBe(5);
        comp.tabSize = MAX_TAB_SIZE + 10;
        fixture.detectChanges();
        expect(editorTabSize()).toBe(5);

        comp.tabSize = 4;
        fixture.detectChanges();
        expect(editorTabSize()).toBe(4);
    });
});
