import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { MockComponent } from 'ng-mocks';
import { TranslateModule } from '@ngx-translate/core';
import { WindowRef } from 'app/core';
import { DebugElement, SimpleChanges, SimpleChange } from '@angular/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { AceEditorModule } from 'ng2-ace-editor';
import { TreeviewItem, TreeviewModule } from 'ngx-treeview';
import { spy, stub, SinonStub } from 'sinon';
import { Observable, Subject } from 'rxjs';
import { CodeEditorFileBrowserComponent, CodeEditorRepositoryFileService, CodeEditorRepositoryService, CodeEditorStatusComponent, CommitState } from 'app/code-editor';
import { ArTEMiSTestModule } from '../../test.module';
import { MockCodeEditorRepositoryFileService, MockCodeEditorRepositoryService } from '../../mocks';
import { FileType } from 'app/entities/ace-editor/file-change.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('CodeEditorFileBrowserComponent', () => {
    let comp: CodeEditorFileBrowserComponent;
    let fixture: ComponentFixture<CodeEditorFileBrowserComponent>;
    let debugElement: DebugElement;
    let codeEditorRepositoryFileService: CodeEditorRepositoryFileService;
    let codeEditorRepositoryService: CodeEditorRepositoryService;
    let getRepositoryContentStub: SinonStub;
    let isCleanStub: SinonStub;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArTEMiSTestModule, AceEditorModule, TreeviewModule.forRoot()],
            declarations: [CodeEditorFileBrowserComponent, MockComponent(CodeEditorStatusComponent)],
            providers: [
                WindowRef,
                { provide: CodeEditorRepositoryService, useClass: MockCodeEditorRepositoryService },
                { provide: CodeEditorRepositoryFileService, useClass: MockCodeEditorRepositoryFileService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CodeEditorFileBrowserComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                codeEditorRepositoryFileService = debugElement.injector.get(CodeEditorRepositoryFileService);
                codeEditorRepositoryService = debugElement.injector.get(CodeEditorRepositoryService);
                isCleanStub = stub(codeEditorRepositoryService, 'isClean');
                getRepositoryContentStub = stub(codeEditorRepositoryFileService, 'getRepositoryContent');
            });
    });

    afterEach(() => {
        isCleanStub.restore();
        getRepositoryContentStub.restore();
    });

    it('should create no treeviewItems if getRepositoryContent returns an empty result', () => {
        const repositoryContent: { [fileName: string]: string } = {};
        const expectedFileTreeItems: TreeviewItem[] = [];
        getRepositoryContentStub.returns(Observable.of(repositoryContent));
        isCleanStub.returns(Observable.of({ isClean: true }));
        comp.commitState = CommitState.UNDEFINED;
        const changes: SimpleChanges = {
            commitState: new SimpleChange(undefined, CommitState.UNDEFINED, true),
        };
        comp.ngOnChanges(changes);
        fixture.detectChanges();

        expect(comp.isLoadingFiles).to.equal(false);
        expect(comp.repositoryFiles).to.deep.equal(repositoryContent);
        expect(comp.filesTreeViewItem).to.deep.equal(expectedFileTreeItems);
    });

    it('should create treeviewItems if getRepositoryContent returns files', () => {
        const repositoryContent: { [fileName: string]: string } = { file: 'FILE', folder: 'FOLDER' };
        const expectedFileTreeItems = [
            new TreeviewItem({
                internalDisabled: false,
                internalChecked: false,
                internalCollapsed: false,
                text: 'file1',
                value: 'file1',
            } as any),
            new TreeviewItem({
                internalDisabled: false,
                internalChecked: false,
                internalCollapsed: false,
                text: 'file2',
                value: 'file2',
            } as any),
        ].map(x => x.toString());
        getRepositoryContentStub.returns(Observable.of(repositoryContent));
        isCleanStub.returns(Observable.of({ isClean: true }));
        comp.commitState = CommitState.UNDEFINED;
        const changes: SimpleChanges = {
            commitState: new SimpleChange(undefined, CommitState.UNDEFINED, true),
        };
        comp.ngOnChanges(changes);
        fixture.detectChanges();
        expect(comp.isLoadingFiles).to.equal(false);
        expect(comp.repositoryFiles).to.deep.equal(repositoryContent);
        expect(comp.filesTreeViewItem.map(x => x.toString())).to.deep.equal(expectedFileTreeItems);
    });

    it('should create treeviewItems with nested folder structure', () => {
        const repositoryFiles = {
            folder: FileType.FOLDER,
            'folder/file1': FileType.FILE,
            folder2: FileType.FOLDER,
            'folder2/file2': FileType.FILE,
            'folder2/folder3': FileType.FOLDER,
            'folder2/folder3/file3': FileType.FILE,
        };
        const expectedTreeviewItems = [
            new TreeviewItem({
                internalDisabled: false,
                internalChecked: false,
                internalCollapsed: false,
                text: 'folder',
                value: 'folder',
                internalChildren: [
                    new TreeviewItem({
                        internalDisabled: false,
                        internalChecked: false,
                        internalCollapsed: false,
                        text: 'file1',
                        value: 'folder/file1',
                    } as any),
                ],
            } as any),
            new TreeviewItem({
                internalDisabled: false,
                internalChecked: false,
                internalCollapsed: false,
                text: 'folder2',
                value: 'folder2',
                internalChildren: [
                    new TreeviewItem({
                        internalDisabled: false,
                        internalChecked: false,
                        internalCollapsed: false,
                        text: 'file2',
                        value: 'folder2/file2',
                    } as any),
                    new TreeviewItem({
                        internalDisabled: false,
                        internalChecked: false,
                        internalCollapsed: false,
                        text: 'folder3',
                        value: 'folder2/folder3',
                        internalChildren: [
                            new TreeviewItem({
                                internalDisabled: false,
                                internalChecked: false,
                                internalCollapsed: false,
                                text: 'file3',
                                value: 'folder2/file3',
                            } as any),
                        ],
                    } as any),
                ],
            } as any),
        ];
        comp.repositoryFiles = repositoryFiles;
        comp.setupTreeview();
        fixture.detectChanges();
        const folder = comp.filesTreeViewItem.find(({ value }) => value === 'folder');
        expect(folder).to.exist;
        expect(folder.children).to.have.lengthOf(1);
        const file1 = folder.children.find(({ value }) => value === 'folder/file1');
        expect(file1).to.exist;
        expect(file1.children).to.be.undefined;
        const folder2 = comp.filesTreeViewItem.find(({ value }) => value === 'folder2');
        expect(folder2).to.exist;
        expect(folder2.children).to.have.lengthOf(2);
        const file2 = folder2.children.find(({ value }) => value === 'folder2/file2');
        expect(file2).to.exist;
        expect(file2.children).to.be.undefined;
        const folder3 = folder2.children.find(({ value }) => value === 'folder2/folder3');
        expect(folder3).to.exist;
        expect(folder3.children).to.have.lengthOf(1);
        const file3 = folder3.children.find(({ value }) => value === 'folder2/folder3/file3');
        expect(file3).to.exist;
        expect(file3.children).to.be.undefined;
    });

    it('should filter forbidden files from getRepositoryContent response', () => {
        const allowedFiles = {
            'allowedFile.java': FileType.FILE,
        };
        const forbiddenFiles = {
            'danger.bin': FileType.FOLDER,
            'README.md': FileType.FILE,
            '.hidden': FileType.FILE,
            '.': FileType.FOLDER,
        };
        const repositoryContent = {
            ...allowedFiles,
            ...forbiddenFiles,
        };
        const expectedFileTreeItems = [
            new TreeviewItem({
                internalDisabled: false,
                internalChecked: false,
                internalCollapsed: false,
                text: 'file1',
                value: 'file1',
            } as any),
        ].map(x => x.toString());
        getRepositoryContentStub.returns(Observable.of(repositoryContent));
        isCleanStub.returns(Observable.of({ isClean: true }));
        comp.commitState = CommitState.UNDEFINED;
        const changes: SimpleChanges = {
            commitState: new SimpleChange(undefined, CommitState.UNDEFINED, true),
        };
        comp.ngOnChanges(changes);
        fixture.detectChanges();
        expect(comp.isLoadingFiles).to.equal(false);
        expect(comp.repositoryFiles).to.deep.equal(allowedFiles);
        expect(comp.filesTreeViewItem.map(x => x.toString())).to.deep.equal(expectedFileTreeItems);
    });

    it('should not load files if commitState could not be retrieved (possibly corrupt repository or server error)', () => {
        const isCleanSubject = new Subject<{ isClean: boolean }>();
        const onErrorSpy = spy(comp.onError, 'emit');
        const loadFilesSpy = spy(comp, 'loadFiles');
        isCleanStub.returns(isCleanSubject);
        comp.commitState = CommitState.UNDEFINED;
        const changes: SimpleChanges = {
            commitState: new SimpleChange(undefined, CommitState.UNDEFINED, true),
        };
        comp.ngOnChanges(changes);
        fixture.detectChanges();
        expect(comp.isLoadingFiles).to.equal(true);
        expect(comp.commitState).to.equal(CommitState.UNDEFINED);
        isCleanSubject.error('fatal error');

        fixture.detectChanges();
        expect(comp.commitState).to.equal(CommitState.COULD_NOT_BE_RETRIEVED);
        expect(comp.isLoadingFiles).to.equal(false);
        expect(comp.repositoryFiles).to.be.undefined;
        expect(comp.filesTreeViewItem).to.be.undefined;
        expect(onErrorSpy).to.have.been.calledOnce;
        expect(loadFilesSpy).to.not.have.been.called;
    });

    it('should set isLoading false and emit an error if loadFiles fails', () => {
        const isCleanSubject = new Subject<{ isClean: boolean }>();
        const getRepositoryContentSubject = new Subject<{ [fileName: string]: FileType }>();
        const onErrorSpy = spy(comp.onError, 'emit');
        isCleanStub.returns(isCleanSubject);
        getRepositoryContentStub.returns(getRepositoryContentSubject);
        comp.commitState = CommitState.UNDEFINED;
        const changes: SimpleChanges = {
            commitState: new SimpleChange(undefined, CommitState.UNDEFINED, true),
        };
        comp.ngOnChanges(changes);
        fixture.detectChanges();
        expect(comp.isLoadingFiles).to.equal(true);
        expect(comp.commitState).to.equal(CommitState.UNDEFINED);
        isCleanSubject.next({ isClean: true });
        getRepositoryContentSubject.error('fatal error');

        fixture.detectChanges();
        expect(comp.isLoadingFiles).to.equal(false);
        expect(comp.repositoryFiles).to.be.undefined;
        expect(comp.filesTreeViewItem).to.be.undefined;
        expect(onErrorSpy).to.have.been.calledOnce;
    });

    it('should set node to checked if its file gets selected', () => {
        const selectedFile = 'folder/file1';
        const repositoryFiles = {
            folder: FileType.FOLDER,
            'folder/file1': FileType.FILE,
            folder2: FileType.FOLDER,
            'folder/file2': FileType.FILE,
        };
        const treeItems = [
            new TreeviewItem({
                internalDisabled: false,
                internalChecked: false,
                internalCollapsed: false,
                text: selectedFile,
                value: 'file1',
            } as any),
            new TreeviewItem({
                internalDisabled: false,
                internalChecked: false,
                internalCollapsed: false,
                text: 'folder2/file2',
                value: 'file2',
            } as any),
        ];
        comp.filesTreeViewItem = treeItems;
        comp.repositoryFiles = repositoryFiles;
        comp.selectedFile = selectedFile;
        const changes: SimpleChanges = {
            selectedFile: new SimpleChange(undefined, 'folder/file2', false),
        };
        comp.ngOnChanges(changes);
        fixture.detectChanges();
        expect(comp.selectedFile).to.equal(selectedFile);
        const selectedTreeItem = comp.filesTreeViewItem.find(({ value }) => value === 'folder').children.find(({ value }) => value === selectedFile);
        expect(selectedTreeItem).to.exist;
        expect(selectedTreeItem.checked).to.be.true;
    });
});
