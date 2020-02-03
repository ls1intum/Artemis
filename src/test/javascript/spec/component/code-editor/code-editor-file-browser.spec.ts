import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockComponent } from 'ng-mocks';
import { CookieService } from 'ngx-cookie';
import { By } from '@angular/platform-browser';
import { TranslateModule } from '@ngx-translate/core';
import { WindowRef } from 'app/core/websocket/window.service';
import { DebugElement } from '@angular/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { AceEditorModule } from 'ng2-ace-editor';
import { TreeviewItem, TreeviewModule } from 'ngx-treeview';
import { SinonStub, spy, stub } from 'sinon';
import { Observable, Subject } from 'rxjs';
import {
    CodeEditorConflictStateService,
    CodeEditorFileBrowserComponent,
    CodeEditorFileBrowserCreateNodeComponent,
    CodeEditorFileBrowserFileComponent,
    CodeEditorFileBrowserFolderComponent,
    CodeEditorFileService,
    CodeEditorRepositoryFileService,
    CodeEditorRepositoryService,
    CodeEditorStatusComponent,
    CommitState,
    GitConflictState,
} from 'app/code-editor';
import { ArtemisTestModule } from '../../test.module';
import { MockCodeEditorConflictStateService, MockCodeEditorRepositoryFileService, MockCodeEditorRepositoryService, MockCookieService, MockSyncStorage } from '../../mocks';
import { FileType } from 'app/entities/ace-editor/file-change.model';
import { triggerChanges } from '../../utils/general.utils';
import { DeviceDetectorService } from 'ngx-device-detector';

chai.use(sinonChai);
const expect = chai.expect;

describe('CodeEditorFileBrowserComponent', () => {
    let comp: CodeEditorFileBrowserComponent;
    let fixture: ComponentFixture<CodeEditorFileBrowserComponent>;
    let debugElement: DebugElement;
    let codeEditorRepositoryFileService: CodeEditorRepositoryFileService;
    let codeEditorRepositoryService: CodeEditorRepositoryService;
    let conflictService: CodeEditorConflictStateService;
    let getRepositoryContentStub: SinonStub;
    let isCleanStub: SinonStub;
    let createFileStub: SinonStub;
    let createFolderStub: SinonStub;
    let renameFileStub: SinonStub;

    const createFileRoot = '#create_file_root';
    const createFolderRoot = '#create_folder_root';
    const compressTree = '#compress_tree';

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, AceEditorModule, TreeviewModule.forRoot()],
            declarations: [
                CodeEditorFileBrowserComponent,
                MockComponent(CodeEditorStatusComponent),
                CodeEditorFileBrowserFileComponent,
                CodeEditorFileBrowserFolderComponent,
                CodeEditorFileBrowserCreateNodeComponent,
            ],
            providers: [
                WindowRef,
                CodeEditorFileService,
                DeviceDetectorService,
                { provide: CodeEditorRepositoryService, useClass: MockCodeEditorRepositoryService },
                { provide: CodeEditorRepositoryFileService, useClass: MockCodeEditorRepositoryFileService },
                { provide: CodeEditorConflictStateService, useClass: MockCodeEditorConflictStateService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: CookieService, useClass: MockCookieService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CodeEditorFileBrowserComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                codeEditorRepositoryFileService = debugElement.injector.get(CodeEditorRepositoryFileService);
                codeEditorRepositoryService = debugElement.injector.get(CodeEditorRepositoryService);
                conflictService = debugElement.injector.get(CodeEditorConflictStateService);
                isCleanStub = stub(codeEditorRepositoryService, 'getStatus');
                getRepositoryContentStub = stub(codeEditorRepositoryFileService, 'getRepositoryContent');
                createFileStub = stub(codeEditorRepositoryFileService, 'createFile');
                createFolderStub = stub(codeEditorRepositoryFileService, 'createFolder');
                renameFileStub = stub(codeEditorRepositoryFileService, 'renameFile');
            });
    });

    afterEach(() => {
        isCleanStub.restore();
        getRepositoryContentStub.restore();
        createFileStub.restore();
        createFolderStub.restore();
    });

    it('should create no treeviewItems if getRepositoryContent returns an empty result', () => {
        const repositoryContent: { [fileName: string]: string } = {};
        const expectedFileTreeItems: TreeviewItem[] = [];
        getRepositoryContentStub.returns(Observable.of(repositoryContent));
        isCleanStub.returns(Observable.of({ repositoryStatus: CommitState.CLEAN }));
        comp.commitState = CommitState.UNDEFINED;

        triggerChanges(comp, { property: 'commitState', currentValue: CommitState.UNDEFINED });
        fixture.detectChanges();

        expect(comp.isLoadingFiles).to.equal(false);
        expect(comp.repositoryFiles).to.deep.equal(repositoryContent);
        expect(comp.filesTreeViewItem).to.deep.equal(expectedFileTreeItems);
        const renderedFolders = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        const renderedFiles = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(renderedFolders).to.have.lengthOf(0);
        expect(renderedFiles).to.have.lengthOf(0);
    });

    it('should create treeviewItems if getRepositoryContent returns files', () => {
        const repositoryContent: { [fileName: string]: string } = { file: 'FILE', folder: 'FOLDER' };
        getRepositoryContentStub.returns(Observable.of(repositoryContent));
        isCleanStub.returns(Observable.of({ repositoryStatus: CommitState.CLEAN }));
        comp.commitState = CommitState.UNDEFINED;

        triggerChanges(comp, { property: 'commitState', currentValue: CommitState.UNDEFINED });
        fixture.detectChanges();
        expect(comp.isLoadingFiles).to.equal(false);
        expect(comp.repositoryFiles).to.deep.equal(repositoryContent);
        const renderedFolders = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        const renderedFiles = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(renderedFolders).to.have.lengthOf(1);
        expect(renderedFiles).to.have.lengthOf(1);
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
        const renderedFolders = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        const renderedFiles = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(renderedFolders).to.have.lengthOf(3);
        expect(renderedFiles).to.have.lengthOf(3);
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
        isCleanStub.returns(Observable.of({ repositoryStatus: CommitState.CLEAN }));
        comp.commitState = CommitState.UNDEFINED;
        triggerChanges(comp, { property: 'commitState', currentValue: CommitState.UNDEFINED });
        fixture.detectChanges();
        expect(comp.isLoadingFiles).to.equal(false);
        expect(comp.repositoryFiles).to.deep.equal(allowedFiles);
        expect(comp.filesTreeViewItem.map(x => x.toString())).to.deep.equal(expectedFileTreeItems);
        const renderedFolders = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        const renderedFiles = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(renderedFolders).to.have.lengthOf(0);
        expect(renderedFiles).to.have.lengthOf(1);
    });

    it('should not load files if commitState could not be retrieved (possibly corrupt repository or server error)', () => {
        const isCleanSubject = new Subject<{ isClean: boolean }>();
        const onErrorSpy = spy(comp.onError, 'emit');
        const loadFilesSpy = spy(comp, 'loadFiles');
        isCleanStub.returns(isCleanSubject);
        comp.commitState = CommitState.UNDEFINED;
        triggerChanges(comp, { property: 'commitState', currentValue: CommitState.UNDEFINED });
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
        const renderedFolders = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        const renderedFiles = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(renderedFolders).to.have.lengthOf(0);
        expect(renderedFiles).to.have.lengthOf(0);
    });

    it('should set isLoading false and emit an error if loadFiles fails', () => {
        const isCleanSubject = new Subject<{ isClean: boolean }>();
        const getRepositoryContentSubject = new Subject<{ [fileName: string]: FileType }>();
        const onErrorSpy = spy(comp.onError, 'emit');
        isCleanStub.returns(isCleanSubject);
        getRepositoryContentStub.returns(getRepositoryContentSubject);
        comp.commitState = CommitState.UNDEFINED;
        triggerChanges(comp, { property: 'commitState', currentValue: CommitState.UNDEFINED });
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
        const renderedFolders = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        const renderedFiles = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(renderedFolders).to.have.lengthOf(0);
        expect(renderedFiles).to.have.lengthOf(0);
    });

    it('should set node to checked if its file gets selected and update ui', () => {
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
        triggerChanges(comp, { property: 'selectedFile', currentValue: 'folder/file2', firstChange: false });
        fixture.detectChanges();
        expect(comp.selectedFile).to.equal(selectedFile);
        const selectedTreeItem = comp.filesTreeViewItem.find(({ value }) => value === 'folder').children.find(({ value }) => value === selectedFile);
        expect(selectedTreeItem).to.exist;
        expect(selectedTreeItem.checked).to.be.true;
        const renderedFolders = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        const renderedFiles = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(renderedFolders).to.have.lengthOf(2);
        expect(renderedFiles).to.have.lengthOf(2);

        const selectedFileHtml = renderedFiles[0];
        const isSelected = !!selectedFileHtml.query(By.css('.node-selected'));
        expect(isSelected).to.be.true;
        const notSelectedFilesHtml = [renderedFiles[1], ...renderedFolders];
        const areUnSelected = !notSelectedFilesHtml.some(el => !!el.query(By.css('.node-selected')));
        expect(areUnSelected).to.be.true;
    });

    it('should add file to node tree if created', fakeAsync((done: any) => {
        const fileName = 'file2';
        const filePath = 'folder2/file2';
        const repositoryFiles = { file1: FileType.FILE, folder2: FileType.FOLDER };
        const treeItems = [
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
                text: 'folder2',
                value: 'folder2',
            } as any),
        ];
        const onFileChangeSpy = spy(comp.onFileChange, 'emit');
        const setupTreeviewStub = stub(comp, 'setupTreeview');
        createFileStub.returns(Observable.of(null));
        comp.repositoryFiles = repositoryFiles;
        comp.filesTreeViewItem = treeItems;
        comp.creatingFile = ['folder2', FileType.FILE];
        fixture.detectChanges();

        let creatingElement = debugElement.query(By.css('jhi-code-editor-file-browser-create-node'));
        expect(creatingElement).to.exist;
        const creatingInput = creatingElement.query(By.css('input'));
        expect(creatingInput).to.exist;

        tick();
        const focusedElement = debugElement.query(By.css(':focus')).nativeElement;
        expect(creatingInput.nativeElement).to.deep.equal(focusedElement);

        comp.onCreateFile(fileName);
        fixture.detectChanges();

        expect(createFileStub).to.have.been.calledOnceWithExactly(filePath);
        expect(comp.creatingFile).to.be.null;
        expect(setupTreeviewStub).to.have.been.calledOnceWithExactly();
        expect(onFileChangeSpy).to.have.been.calledOnce;
        expect(comp.repositoryFiles).to.deep.equal({ ...repositoryFiles, [filePath]: FileType.FILE });
        creatingElement = debugElement.query(By.css('jhi-code-editor-file-browser-create-node'));
        expect(creatingElement).not.to.exist;
    }));

    it('should add folder to node tree if created', fakeAsync(() => {
        const fileName = 'folder3';
        const filePath = 'folder2/folder3';
        const repositoryFiles = { file1: FileType.FILE, folder2: FileType.FOLDER };
        const treeItems = [
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
                text: 'folder2',
                value: 'folder2',
            } as any),
        ];
        const onFileChangeSpy = spy(comp.onFileChange, 'emit');
        const setupTreeviewStub = stub(comp, 'setupTreeview');
        createFolderStub.returns(Observable.of(null));
        comp.repositoryFiles = repositoryFiles;
        comp.filesTreeViewItem = treeItems;
        comp.creatingFile = ['folder2', FileType.FOLDER];
        fixture.detectChanges();

        let creatingElement = debugElement.query(By.css('jhi-code-editor-file-browser-create-node'));
        expect(creatingElement).to.exist;
        const creatingInput = creatingElement.query(By.css('input'));
        expect(creatingInput).to.exist;

        tick();
        const focusedElement = debugElement.query(By.css(':focus')).nativeElement;
        expect(creatingInput.nativeElement).to.deep.equal(focusedElement);

        comp.onCreateFile(fileName);

        fixture.detectChanges();
        expect(createFolderStub).to.have.been.calledOnceWithExactly(filePath);
        expect(comp.creatingFile).to.be.null;
        expect(setupTreeviewStub).to.have.been.calledOnceWithExactly();
        expect(onFileChangeSpy).to.have.been.calledOnce;
        expect(comp.repositoryFiles).to.deep.equal({ ...repositoryFiles, [filePath]: FileType.FOLDER });
        creatingElement = debugElement.query(By.css('jhi-code-editor-file-browser-create-node'));
        expect(creatingElement).not.to.exist;
    }));

    it('should not be able to create binary file', () => {
        const fileName = 'danger.bin';
        const onErrorSpy = spy(comp.onError, 'emit');
        comp.creatingFile = ['', FileType.FILE];
        comp.onCreateFile(fileName);
        fixture.detectChanges();
        expect(onErrorSpy).to.have.been.calledOnce;
        expect(createFileStub).not.to.have.been.called;
    });

    it('should not be able to create node that already exists', () => {
        const fileName = 'file1';
        const repositoryFiles = { 'folder2/file1': FileType.FILE, folder2: FileType.FOLDER };
        const onErrorSpy = spy(comp.onError, 'emit');
        comp.creatingFile = ['folder2', FileType.FILE];
        comp.repositoryFiles = repositoryFiles;
        comp.onCreateFile(fileName);
        fixture.detectChanges();
        expect(onErrorSpy).to.have.been.calledOnce;
        expect(createFileStub).not.to.have.been.called;
    });

    it('should update repository file entry on rename', fakeAsync(() => {
        const fileName = 'file1';
        const afterRename = 'newFileName';
        const treeItems = [
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
                text: 'folder2',
                value: 'folder2',
            } as any),
        ];
        const repositoryFiles = { file1: FileType.FILE, folder2: FileType.FOLDER };
        const onFileChangeSpy = spy(comp.onFileChange, 'emit');
        renameFileStub.returns(Observable.of(null));
        comp.repositoryFiles = repositoryFiles;
        comp.renamingFile = [fileName, fileName, FileType.FILE];
        comp.filesTreeViewItem = treeItems;
        fixture.detectChanges();

        let filesInTreeHtml = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(filesInTreeHtml).to.have.lengthOf(1);
        let foldersInTreeHtml = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        expect(foldersInTreeHtml).to.have.lengthOf(1);
        let renamingInput = filesInTreeHtml[0].query(By.css('input'));
        expect(renamingInput).to.exist;

        // Wait for focus of input element
        tick();
        const focusedElement = debugElement.query(By.css(':focus')).nativeElement;
        expect(renamingInput.nativeElement).to.deep.equal(focusedElement);

        renamingInput.nativeElement.value = afterRename;
        renamingInput.nativeElement.dispatchEvent(new Event('input'));
        renamingInput.nativeElement.dispatchEvent(new Event('focusout'));
        fixture.detectChanges();

        expect(renameFileStub).to.have.been.calledOnceWithExactly(fileName, afterRename);
        expect(comp.renamingFile).to.be.null;
        expect(onFileChangeSpy).to.have.been.calledOnce;
        expect(comp.repositoryFiles).to.deep.equal({ folder2: FileType.FOLDER, [afterRename]: FileType.FILE });

        filesInTreeHtml = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(filesInTreeHtml).to.have.lengthOf(1);
        foldersInTreeHtml = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        expect(foldersInTreeHtml).to.have.lengthOf(1);
        renamingInput = filesInTreeHtml[0].query(By.css('input'));
        expect(renamingInput).not.to.exist;
    }));

    it('should rename all paths concerned if a folder is renamed', fakeAsync(() => {
        const folderName = 'folder';
        const afterRename = 'newFolderName';
        const treeItems = [
            new TreeviewItem({
                internalDisabled: false,
                internalChecked: false,
                internalCollapsed: false,
                text: 'file1',
                value: 'folder/file1',
            } as any),
            new TreeviewItem({
                internalDisabled: false,
                internalChecked: false,
                internalCollapsed: false,
                text: 'file2',
                value: 'folder/file2',
            } as any),
            new TreeviewItem({
                internalDisabled: false,
                internalChecked: false,
                internalCollapsed: false,
                text: 'folder',
                value: 'folder',
            } as any),
            new TreeviewItem({
                internalDisabled: false,
                internalChecked: false,
                internalCollapsed: false,
                text: 'folder2',
                value: 'folder2',
            } as any),
        ];
        const repositoryFiles = { 'folder/file1': FileType.FILE, 'folder/file2': FileType.FILE, folder: FileType.FOLDER, folder2: FileType.FOLDER };
        const onFileChangeSpy = spy(comp.onFileChange, 'emit');
        renameFileStub.returns(Observable.of(null));
        comp.repositoryFiles = repositoryFiles;
        comp.renamingFile = [folderName, folderName, FileType.FILE];
        comp.filesTreeViewItem = treeItems;
        fixture.detectChanges();

        let filesInTreeHtml = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(filesInTreeHtml).to.have.lengthOf(2);
        let foldersInTreeHtml = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        expect(foldersInTreeHtml).to.have.lengthOf(2);
        let renamingInput = foldersInTreeHtml[0].query(By.css('input'));
        expect(renamingInput).to.exist;

        // Wait for focus of input element
        tick();
        const focusedElement = debugElement.query(By.css(':focus')).nativeElement;
        expect(renamingInput.nativeElement).to.deep.equal(focusedElement);

        renamingInput.nativeElement.value = afterRename;
        renamingInput.nativeElement.dispatchEvent(new Event('input'));
        renamingInput.nativeElement.dispatchEvent(new Event('focusout'));

        expect(renameFileStub).to.have.been.calledOnceWithExactly(folderName, afterRename);
        expect(comp.renamingFile).to.be.null;
        expect(onFileChangeSpy).to.have.been.calledOnce;
        expect(comp.repositoryFiles).to.deep.equal({
            [[afterRename, 'file1'].join('/')]: FileType.FILE,
            [[afterRename, 'file2'].join('/')]: FileType.FILE,
            [afterRename]: FileType.FOLDER,
            folder2: FileType.FOLDER,
        });

        filesInTreeHtml = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(filesInTreeHtml).to.have.lengthOf(2);
        foldersInTreeHtml = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        expect(foldersInTreeHtml).to.have.lengthOf(2);
        renamingInput = filesInTreeHtml[0].query(By.css('input'));
        expect(renamingInput).not.to.exist;
    }));

    it('should not rename a file if its new fileName already exists in the repository', fakeAsync(() => {
        const fileName = 'file1';
        const afterRename = 'newFileName';
        const repositoryFiles = { file1: FileType.FILE, newFileName: FileType.FILE };
        comp.repositoryFiles = repositoryFiles;
        comp.setupTreeview();
        fixture.detectChanges();
        comp.renamingFile = [fileName, fileName, FileType.FILE];
        fixture.detectChanges();

        let renamingInput = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'))[0].query(By.css('input'));
        expect(renamingInput).to.exist;

        // Wait for focus of input element
        tick();
        let focusedElement = debugElement.query(By.css(':focus')).nativeElement;
        expect(renamingInput.nativeElement).to.deep.equal(focusedElement);

        renamingInput.nativeElement.value = afterRename;
        renamingInput.nativeElement.dispatchEvent(new Event('input'));
        renamingInput.nativeElement.dispatchEvent(new Event('focusout'));
        fixture.detectChanges();

        expect(renameFileStub).not.to.have.been.called;
        expect(comp.repositoryFiles).to.deep.equal(repositoryFiles);

        // When renaming failed, the input should not be closed, because the user probably still wants to rename
        renamingInput = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'))[0].query(By.css('input'));
        expect(renamingInput).to.exist;
        focusedElement = debugElement.query(By.css(':focus')).nativeElement;
        expect(renamingInput.nativeElement).to.deep.equal(focusedElement);
    }));

    it('should not rename a file if its new fileName indicates a binary file', fakeAsync(() => {
        const fileName = 'file1';
        const afterRename = 'newFileName.bin';
        const repositoryFiles = { file1: FileType.FILE, newFileName: FileType.FILE };
        comp.repositoryFiles = repositoryFiles;
        comp.setupTreeview();
        fixture.detectChanges();
        comp.renamingFile = [fileName, fileName, FileType.FILE];
        fixture.detectChanges();

        let renamingInput = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'))[0].query(By.css('input'));
        expect(renamingInput).to.exist;

        // Wait for focus of input element
        tick();
        let focusedElement = debugElement.query(By.css(':focus')).nativeElement;
        expect(renamingInput.nativeElement).to.deep.equal(focusedElement);

        renamingInput.nativeElement.value = afterRename;
        renamingInput.nativeElement.dispatchEvent(new Event('input'));
        renamingInput.nativeElement.dispatchEvent(new Event('focusout'));
        fixture.detectChanges();

        expect(renameFileStub).not.to.have.been.called;
        expect(comp.repositoryFiles).to.deep.equal(repositoryFiles);

        // When renaming failed, the input should not be closed, because the user probably still wants to rename
        renamingInput = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'))[0].query(By.css('input'));
        expect(renamingInput).to.exist;
        focusedElement = debugElement.query(By.css(':focus')).nativeElement;
        expect(renamingInput.nativeElement).to.deep.equal(focusedElement);
    }));

    it('should leave rename state if renaming a file to the same file name', fakeAsync(() => {
        const fileName = 'file1';
        const repositoryFiles = { file1: FileType.FILE, newFileName: FileType.FILE };
        comp.repositoryFiles = repositoryFiles;
        comp.setupTreeview();
        fixture.detectChanges();
        comp.renamingFile = [fileName, fileName, FileType.FILE];
        fixture.detectChanges();

        let renamingInput = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'))[0].query(By.css('input'));
        expect(renamingInput).to.exist;

        // Wait for focus of input element
        tick();
        let focusedElement = debugElement.query(By.css(':focus')).nativeElement;
        expect(renamingInput.nativeElement).to.deep.equal(focusedElement);

        renamingInput.nativeElement.dispatchEvent(new Event('focusout'));
        fixture.detectChanges();

        expect(renameFileStub).not.to.have.been.called;
        expect(comp.repositoryFiles).to.deep.equal(repositoryFiles);

        // When renaming failed, the input should not be closed, because the user probably still wants to rename
        renamingInput = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'))[0].query(By.css('input'));
        expect(renamingInput).not.to.exist;
    }));

    it('should disable action buttons if there is a git conflict', () => {
        const repositoryContent: { [fileName: string]: string } = {};
        isCleanStub.returns(Observable.of({ repositoryStatus: CommitState.CONFLICT }));
        getRepositoryContentStub.returns(Observable.of(repositoryContent));
        comp.commitState = CommitState.UNDEFINED;

        triggerChanges(comp, { property: 'commitState', currentValue: CommitState.UNDEFINED });
        fixture.detectChanges();

        expect(comp.commitState).to.equal(CommitState.CONFLICT);

        expect(debugElement.query(By.css(createFileRoot)).nativeElement.disabled).to.be.true;
        expect(debugElement.query(By.css(createFolderRoot)).nativeElement.disabled).to.be.true;
        expect(debugElement.query(By.css(compressTree)).nativeElement.disabled).to.be.false;

        // Resolve conflict.
        conflictService.notifyConflictState(GitConflictState.OK);
        isCleanStub.returns(Observable.of({ repositoryStatus: CommitState.CLEAN }));

        comp.commitState = CommitState.UNDEFINED;

        triggerChanges(comp, { property: 'commitState', currentValue: CommitState.UNDEFINED });
        fixture.detectChanges();

        expect(comp.commitState).to.equal(CommitState.CLEAN);

        expect(debugElement.query(By.css(createFileRoot)).nativeElement.disabled).to.be.false;
        expect(debugElement.query(By.css(createFolderRoot)).nativeElement.disabled).to.be.false;
        expect(debugElement.query(By.css(compressTree)).nativeElement.disabled).to.be.false;

        expect(getRepositoryContentStub).to.have.been.calledOnce;
        expect(comp.selectedFile).to.be.undefined;
    });
});
