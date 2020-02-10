import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateModule } from '@ngx-translate/core';
import { WindowRef } from 'app/core/websocket/window.service';
import { DebugElement, EventEmitter } from '@angular/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { AceEditorModule } from 'ng2-ace-editor';
import { TreeviewModule } from 'ngx-treeview';
import { SinonStub, spy, stub } from 'sinon';
import { Subject } from 'rxjs';
import { CodeEditorAceComponent, CodeEditorFileService, CodeEditorRepositoryFileService } from 'app/code-editor';
import { ArtemisTestModule } from '../../test.module';
import { MockCodeEditorRepositoryFileService } from '../../mocks';
import { CreateFileChange, FileType, RenameFileChange } from 'app/entities/ace-editor/file-change.model';
import { AnnotationArray } from 'app/entities/ace-editor';
import { triggerChanges } from '../../utils/general.utils';

chai.use(sinonChai);
const expect = chai.expect;

describe('CodeEditorAceComponent', () => {
    let comp: CodeEditorAceComponent;
    let fixture: ComponentFixture<CodeEditorAceComponent>;
    let debugElement: DebugElement;
    let codeEditorRepositoryFileService: CodeEditorRepositoryFileService;
    let loadRepositoryFileStub: SinonStub;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, AceEditorModule, TreeviewModule.forRoot()],
            declarations: [CodeEditorAceComponent],
            providers: [WindowRef, CodeEditorFileService, { provide: CodeEditorRepositoryFileService, useClass: MockCodeEditorRepositoryFileService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CodeEditorAceComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                codeEditorRepositoryFileService = debugElement.injector.get(CodeEditorRepositoryFileService);
                loadRepositoryFileStub = stub(codeEditorRepositoryFileService, 'getFile');
            });
    });

    afterEach(() => {
        loadRepositoryFileStub.restore();
    });

    it('without any inputs, should still render correctly without ace, showing a placeholder', () => {
        fixture.detectChanges();
        const placeholder = debugElement.query(By.css('#no-file-selected'));
        expect(placeholder).to.exist;
        const aceEditor = debugElement.query(By.css('#ace-code-editor'));
        expect(aceEditor.nativeElement.hasAttribute('hidden')).to.be.true;
    });

    it('if the component is loading a file from server, it should show the editor in a readonly state', () => {
        comp.selectedFile = 'dummy';
        comp.isLoading = true;
        fixture.detectChanges();
        const placeholder = debugElement.query(By.css('#no-file-selected'));
        expect(placeholder).not.to.exist;
        const aceEditor = debugElement.query(By.css('#ace-code-editor'));
        expect(aceEditor.nativeElement.hasAttribute('hidden')).to.be.true;
        expect(comp.editor.getEditor().getReadOnly()).to.be.true;

        comp.isLoading = false;
        fixture.detectChanges();
        expect(aceEditor.nativeElement.hasAttribute('hidden')).to.be.false;
        expect(comp.editor.getEditor().getReadOnly()).to.be.false;
    });

    it('if a file is selected and the component is not loading a file from server, the editor should be usable', () => {
        comp.selectedFile = 'dummy';
        comp.isLoading = false;
        fixture.detectChanges();
        const placeholder = debugElement.query(By.css('#no-file-selected'));
        expect(placeholder).not.to.exist;
        const aceEditor = debugElement.query(By.css('#ace-code-editor'));
        expect(aceEditor.nativeElement.hasAttribute('hidden')).to.be.false;
        expect(comp.editor.getEditor().getReadOnly()).to.be.false;
    });

    it('should not load the file from server on selected file change if the file is already in session', () => {
        const selectedFile = 'dummy';
        const fileSession = {};
        const loadFileSubject = new Subject();
        const initEditorAfterFileChangeSpy = spy(comp, 'initEditorAfterFileChange');
        loadRepositoryFileStub.returns(loadFileSubject);
        comp.selectedFile = selectedFile;
        comp.fileSession = fileSession;

        triggerChanges(comp, { property: 'selectedFile', currentValue: selectedFile });
        fixture.detectChanges();

        expect(comp.isLoading).to.be.true;
        expect(loadRepositoryFileStub).to.have.been.calledOnceWithExactly(selectedFile);
        expect(initEditorAfterFileChangeSpy).to.not.have.been.called;
        loadFileSubject.next({ fileName: selectedFile, fileContent: 'lorem ipsum' });
        fixture.detectChanges();

        expect(comp.isLoading).to.be.false;
        expect(initEditorAfterFileChangeSpy).to.have.been.calledOnceWithExactly();
    });

    it('should not load the file from server on selected file change if the file is already in session', () => {
        const selectedFile = 'dummy';
        const fileSession = { [selectedFile]: { code: 'lorem ipsum', cursor: { column: 0, row: 0 } } };
        const initEditorAfterFileChangeSpy = spy(comp, 'initEditorAfterFileChange');
        const loadFileSpy = spy(comp, 'loadFile');
        comp.selectedFile = selectedFile;
        comp.fileSession = fileSession;

        triggerChanges(comp, { property: 'selectedFile', currentValue: selectedFile });
        fixture.detectChanges();

        expect(initEditorAfterFileChangeSpy).to.have.been.calledOnceWithExactly();
        expect(loadFileSpy).not.to.have.been.called;
    });

    it('should update file session references on file rename', () => {
        const selectedFile = 'file';
        const newFileName = 'newFilename';
        const fileChange = new RenameFileChange(FileType.FILE, selectedFile, newFileName);
        const fileSession = { [selectedFile]: { code: 'lorem ipsum', cursor: { column: 0, row: 0 } }, anotherFile: { code: 'lorem ipsum 2', cursor: { column: 0, row: 0 } } };
        comp.selectedFile = newFileName;
        comp.fileSession = fileSession;
        comp.fileChange = fileChange;

        triggerChanges(comp, { property: 'fileChange', currentValue: fileChange, firstChange: false });
        fixture.detectChanges();

        expect(comp.fileSession).to.deep.equal({ anotherFile: fileSession.anotherFile, [fileChange.newFileName]: fileSession[selectedFile] });
    });

    it('should init editor on newly created file if selected', () => {
        const selectedFile = 'file';
        const fileChange = new CreateFileChange(FileType.FILE, selectedFile);
        const fileSession = { anotherFile: { code: 'lorem ipsum 2', cursor: { column: 0, row: 0 } } };
        const initEditorAfterFileChangeSpy = spy(comp, 'initEditorAfterFileChange');
        comp.selectedFile = selectedFile;
        comp.fileSession = fileSession;
        comp.fileChange = fileChange;

        triggerChanges(comp, { property: 'fileChange', currentValue: fileChange, firstChange: false });
        fixture.detectChanges();

        expect(initEditorAfterFileChangeSpy).to.have.been.calledOnceWithExactly();
        expect(comp.fileSession).to.deep.equal({ anotherFile: fileSession.anotherFile, [fileChange.fileName]: { code: '', cursor: { row: 0, column: 0 } } });
    });

    it('should not do anything on file content change if the code has not changed', () => {
        const textChangeEmitter = new EventEmitter<string>();
        comp.editor.textChanged = textChangeEmitter;
        const onFileContentChangeSpy = spy(comp.onFileContentChange, 'emit');

        const selectedFile = 'file';
        const fileSession = { [selectedFile]: { code: 'lorem ipsum', cursor: { column: 0, row: 0 } } };
        comp.selectedFile = selectedFile;
        comp.fileSession = fileSession;

        textChangeEmitter.emit('lorem ipsum');

        expect(onFileContentChangeSpy).to.not.have.been.called;
    });

    it('should update build log errors and emit a message on file text change', () => {
        const onFileContentChangeSpy = spy(comp.onFileContentChange, 'emit');

        const selectedFile = 'file';
        const newFileContent = 'lorem ipsum new';
        const fileSession = { [selectedFile]: { code: 'lorem ipsum', cursor: { column: 0, row: 0 } } };
        const buildLogErrors = { errors: { [selectedFile]: new AnnotationArray(...[{ row: 5, column: 4, text: 'error', type: 'error', ts: 0 }]) }, timestamp: 0 };
        const editorChangeLog = [{ start: { row: 1, column: 1 }, end: { row: 2, column: 1 }, action: 'remove' }];
        comp.selectedFile = selectedFile;
        comp.fileSession = fileSession;
        comp.buildLogErrors = buildLogErrors;
        comp.editorChangeLog = editorChangeLog;

        comp.onFileTextChanged(newFileContent);

        expect(onFileContentChangeSpy).to.have.been.calledOnceWithExactly({ file: selectedFile, fileContent: newFileContent });
        const newBuildLogErrors = { errors: { file: [{ text: 'error', type: 'error', ts: 0, row: 4, column: 4 }] }, timestamp: 0 };
        expect(comp.buildLogErrors).to.deep.equal(newBuildLogErrors);
    });
});
