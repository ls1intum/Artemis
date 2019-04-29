import { RepositoryFileService } from 'app/entities/repository';
import { AfterViewInit, Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, ViewChild, ElementRef } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable } from 'rxjs';
import { catchError, map as rxMap, tap } from 'rxjs/operators';
import { sortBy as _sortBy } from 'lodash';
import { compose, filter, fromPairs, map, toPairs } from 'lodash/fp';
import { Participation, hasParticipationChanged } from 'app/entities/participation';
import { WindowRef } from 'app/core';
import { CodeEditorComponent, CodeEditorFileBrowserDeleteComponent, CommitState, EditorState } from 'app/code-editor';
import { TreeviewComponent, TreeviewConfig, TreeviewHelper, TreeviewItem } from 'ngx-treeview';
import Interactable from '@interactjs/core/Interactable';
import interact from 'interactjs';
import { CreateFileChange, RenameFileChange, FileType, FileChange, DeleteFileChange } from 'app/entities/ace-editor/file-change.model';
import { textFileExtensions } from '../text-files.json';
import { HttpErrorResponse } from '@angular/common/http';

@Component({
    selector: 'jhi-code-editor-file-browser',
    templateUrl: './code-editor-file-browser.component.html',
    providers: [NgbModal, RepositoryFileService, WindowRef],
})
export class CodeEditorFileBrowserComponent implements OnChanges, AfterViewInit {
    public FileType = FileType;

    @Input()
    participation: Participation;
    @Input()
    unsavedFiles: string[];
    @Input()
    errorFiles: string[];
    @Input()
    editorState: EditorState;
    @Input()
    commitState: CommitState;
    @Input()
    isLoadingFiles: boolean;
    @Output()
    onFilesLoaded = new EventEmitter<string[]>();
    @Output()
    onFileChange = new EventEmitter<[string[], FileChange]>();
    @Output()
    selectedFileChange = new EventEmitter<string>();

    @ViewChild('treeview')
    treeview: TreeviewComponent;

    selectedFileValue: string;
    repositoryFiles: { [fileName: string]: FileType };
    folder: string;
    filesTreeViewItem: TreeviewItem[];
    compressFolders = true;
    compressedTreeItems: string[];

    @ViewChild('renamingInput') renamingInput: ElementRef;
    @ViewChild('creatingInput') creatingInput: ElementRef;

    // Triple: [filePath, fileName, fileType]
    renamingFile: [string, string, FileType] | null = null;
    creatingFile: [string, FileType] | null = null;

    isInitial = true;

    /** Provide basic configuration for the TreeView (ngx-treeview) **/
    treeviewConfig = TreeviewConfig.create({
        hasAllCheckBox: false,
        hasFilter: false,
        hasCollapseExpand: false,
        decoupleChildFromParent: false,
        // Default limit is 500, as our styling makes tree item relatively large, we need to increase it a lot
        maxHeight: 5000,
    });

    /** Resizable constants **/
    resizableMinWidth = 100;
    resizableMaxWidth = 800;
    interactResizable: Interactable;

    constructor(private parent: CodeEditorComponent, private $window: WindowRef, private repositoryFileService: RepositoryFileService, public modalService: NgbModal) {}

    /**
     * @function ngAfterViewInit
     * @desc After the view was initialized, we create an interact.js resizable object,
     *       designate the edges which can be used to resize the target element and set min and max values.
     *       The 'resizemove' callback function processes the event values and sets new width and height values for the element.
     */
    ngAfterViewInit(): void {
        this.resizableMinWidth = this.$window.nativeWindow.screen.width / 6;
        this.interactResizable = interact('.resizable-filebrowser')
            .resizable({
                // Enable resize from right edge; triggered by class rg-right
                edges: { left: false, right: '.rg-right', bottom: false, top: false },
                // Set min and max width
                restrictSize: {
                    min: { width: this.resizableMinWidth },
                    max: { width: this.resizableMaxWidth },
                },
                inertia: true,
            })
            .on('resizestart', function(event: any) {
                event.target.classList.add('card-resizable');
            })
            .on('resizeend', function(event: any) {
                event.target.classList.remove('card-resizable');
            })
            .on('resizemove', function(event) {
                const target = event.target;
                // Update element width
                target.style.width = event.rect.width + 'px';
            });
    }

    /**
     * @function ngOnInit
     * @desc Updates the file tree with the repositoryFiles
     * @param changes
     */
    ngOnChanges(changes: SimpleChanges): void {
        if (hasParticipationChanged(changes)) {
            this.isInitial = true;
        }
        // We need to make sure to not trigger multiple requests on the git repo at the same time.
        // This is why we first wait until the repository state was checked.
        if (this.commitState !== CommitState.UNDEFINED && this.isInitial) {
            this.isInitial = false;
            this.loadFiles().subscribe();
        } else if (changes.selectedFile && changes.selectedFile.currentValue) {
            this.renamingFile = null;
            this.setupTreeview();
        }
    }

    @Input()
    get selectedFile() {
        return this.selectedFileValue;
    }

    set selectedFile(file: string) {
        this.selectedFileValue = file;
        this.selectedFileChange.emit(this.selectedFile);
    }

    /**
     * Load files from the participants repository.
     * Files that are not relevant for the conduction of the exercise are removed from result.
     */
    private loadFiles() {
        this.isLoadingFiles = true;
        return this.repositoryFileService.query(this.participation.id).pipe(
            rxMap(files =>
                compose(
                    fromPairs,
                    // Filter root folder
                    filter(([value]) => value),
                    // Filter Readme file that was historically in the student's assignment repo
                    filter(([value]) => !value.includes('README.md')),
                    // Remove binary files as they can't be displayed in an editor
                    filter(([filename]) => {
                        const fileSplit = filename.split('.');
                        // Either the file has no ending or the file ending is allowed
                        return fileSplit.length === 1 || textFileExtensions.includes(fileSplit.pop());
                    }),
                    toPairs,
                )(files),
            ),
            catchError((error: HttpErrorResponse) => {
                console.log('There was an error while getting files: ' + error.message + ': ' + error.error);
                return Observable.of({});
            }),
            tap(files => {
                this.isLoadingFiles = false;
                this.repositoryFiles = files;
                this.setupTreeview();
                this.onFilesLoaded.emit(Object.keys(files));
            }),
        );
    }

    emitFileChange(fileChange: FileChange) {
        if (fileChange instanceof CreateFileChange) {
            this.repositoryFiles = { ...this.repositoryFiles, [fileChange.fileName]: fileChange.fileType };
        } else if (fileChange instanceof DeleteFileChange) {
            const fileRegex = new RegExp(`^${fileChange.fileName}`);
            // If the deleted item is a folder, also delete all sub files/folders
            this.repositoryFiles = compose(
                fromPairs,
                filter(([fileName]) => !fileRegex.test(fileName)),
                toPairs,
            )(this.repositoryFiles);
        } else if (fileChange instanceof RenameFileChange) {
            const fileRegex = new RegExp(`^${fileChange.oldFileName}`);
            // If the renamed item is a folder, also rename the path of all sub files/folders
            this.repositoryFiles = compose(
                fromPairs,
                map(([fileName, fileType]) => [fileName.replace(fileRegex, fileChange.newFileName), fileType]),
                toPairs,
            )(this.repositoryFiles);
        }
        this.setupTreeview();
        this.onFileChange.emit([Object.keys(this.repositoryFiles), fileChange]);
    }

    /**
     * @function onFileDeleted
     * @desc Emmiter function for when a file was deleted; notifies the parent component
     * @param statusChange
     */
    onFileDeleted(fileChange: FileChange) {
        this.emitFileChange(fileChange);
    }

    /**
     * @function handleNodeSelected
     * @desc Callback function for when a node in the file tree view has been selected
     * @param item: Corresponding event object, holds the selected TreeViewItem
     */
    handleNodeSelected(item: TreeviewItem) {
        if (item && item.value !== this.selectedFile) {
            item.checked = true;
            // If we had selected a file prior to this, we "uncheck" it
            if (this.selectedFile) {
                const priorFileSelection = TreeviewHelper.findItemInList(this.filesTreeViewItem, this.selectedFile);
                // Avoid issues after file deletion
                if (priorFileSelection) {
                    priorFileSelection.checked = false;
                }
            }

            // Inform parent editor component about the file selection change
            this.selectedFile = item.value;
        }
        /** Reset folder and search our parent with the TreeviewHelper and set the folder value accordingly **/
        this.folder = null;
        for (const treeviewItem of this.filesTreeViewItem) {
            const parent = TreeviewHelper.findParent(treeviewItem, item);
            // We found our parent => process the value and assign it
            if (parent) {
                this.folder = parent.text;
            }
        }
    }

    toggleTreeCompress($event: any) {
        this.compressFolders = !this.compressFolders;
        this.setupTreeview();
    }

    /**
     * @function setupTreeView
     * @desc Processes the file array, compresses it and then transforms it to a TreeViewItem
     * @param files: Provided repository files by parent editor component
     */
    setupTreeview() {
        let tree = this.buildTree(Object.keys(this.repositoryFiles).sort());
        if (this.compressFolders) {
            this.compressedTreeItems = [];
            tree = this.compressTree(tree);
        }
        this.filesTreeViewItem = this.transformTreeToTreeViewItem(tree);
    }

    /**
     * @function transformTreeToTreeViewItem
     * @desc Converts a parsed filetree to a TreeViewItem[] which will then be used by the Treeviewer (ngx-treeview)
     * @param tree: Filetree obtained by parsing the repository file list
     */
    transformTreeToTreeViewItem(tree: any): TreeviewItem[] {
        const treeViewItem = [];
        for (const node of tree) {
            treeViewItem.push(new TreeviewItem(node));
        }
        return treeViewItem;
    }

    /**
     * @function buildTree
     * @desc Parses the provided list of repository files
     * @param files {array of strings} Filepath strings to process
     * @param tree {array of objects} Current tree structure
     * @param folder {string} Folder name
     */
    buildTree(files: string[], tree?: any[], folder?: File) {
        /**
         * Initialize tree if empty
         */
        if (tree == null) {
            tree = [];
        }

        /**
         * Loop through our file array
         */
        for (let file of files) {
            // Remove leading and trailing spaces
            file = file.replace(/^\/|\/$/g, '');
            // Split file path by slashes
            const fileSplit = file.split('/');
            // Check if the first path part is already in our current tree
            let node = tree.find(element => element.text === fileSplit[0]);
            // Path part doesn't exist => add it to tree
            if (node == null) {
                node = {
                    text: fileSplit[0],
                };
                tree.push(node);
            }

            // Remove first path part from our file path
            fileSplit.shift();

            if (fileSplit.length > 0) {
                // Directory node
                node.checked = false;
                // Recursive function call to process children
                node.children = this.buildTree([fileSplit.join('/')], node.children, folder ? folder + '/' + node.text : node.text);
                node.folder = node.text;
                node.value = folder ? `${folder}/${node.folder}` : node.folder;
            } else {
                // File node
                node.folder = folder;
                node.file = (folder ? folder + '/' : '') + node.text;
                node.value = node.file;
                node.checked = false;

                // Currently processed node selected?
                if (node.file === this.selectedFile) {
                    folder = node.folder;
                    node.checked = true;
                }
            }
        }
        return tree;
    }

    /**
     * @function compressTree
     * @desc Compresses the tree obtained by buildTree() to not contain nodes with only one directory child node
     * @param tree {array of objects} Tree structure
     */
    compressTree(tree: any): any {
        for (const node of tree) {
            if (node.children && node.children.length === 1 && node.children[0].children) {
                node.text = node.text + '/' + node.children[0].text;
                node.value = node.text;
                node.children = this.compressTree(node.children[0].children);
                if (node.children[0].children) {
                    return this.compressTree(tree);
                } else {
                    this.compressedTreeItems.push(node.text);
                }
            } else if (node.children) {
                node.children = this.compressTree(node.children);
            }
        }
        return tree;
    }

    /**
     * @function toggleEditorCollapse
     * @desc Calls the parent (editorComponent) toggleCollapse method
     * @param $event
     * @param {boolean} horizontal
     */
    toggleEditorCollapse($event: any, horizontal: boolean) {
        this.parent.toggleCollapse($event, horizontal, this.interactResizable, this.resizableMinWidth);
    }

    /**
     * @function openDeleteFileModal
     * @desc Opens a popup to delete the selected repository file
     */
    openDeleteFileModal($event: any, fileName: string, fileType: FileType) {
        $event.stopPropagation();
        if (fileName) {
            const modalRef = this.modalService.open(CodeEditorFileBrowserDeleteComponent, { keyboard: true, size: 'lg' });
            modalRef.componentInstance.participation = this.participation;
            modalRef.componentInstance.parent = this;
            modalRef.componentInstance.fileNameToDelete = fileName;
            modalRef.componentInstance.fileType = fileType;
        }
    }

    /**
     * Rename the file (if new fileName is different than old fileName and new fileName is not empty)
     * and emit the changes to the parent.
     * After rename the rename state is exited.
     **/
    onRenameFile(event: any) {
        if (!event.target.value || !this.renamingFile) {
            return;
        } else if (event.target.value === this.renamingFile[1]) {
            this.renamingFile = null;
            return;
        }

        const [filePath, fileName, fileType] = this.renamingFile;
        let newFilePath: any = filePath.split('/');
        newFilePath[newFilePath.length - 1] = event.target.value;
        newFilePath = newFilePath.join('/');

        if (Object.keys(this.repositoryFiles).includes(newFilePath)) {
            this.parent.onError('fileExists');
            return;
        } else if (event.target.value.split('.').length > 1 && !textFileExtensions.includes(event.target.value.split('.').pop())) {
            this.parent.onError('unsupportedFile');
            return;
        }

        if (event.target.value !== fileName) {
            this.repositoryFileService.rename(this.participation.id, filePath, event.target.value).subscribe(
                () => {
                    this.emitFileChange(new RenameFileChange(fileType, filePath, newFilePath));
                    this.renamingFile = null;
                },
                () => this.parent.onError('fileOperationFailed'),
            );
        } else {
            this.renamingFile = null;
        }
    }

    /**
     * Enter rename file mode and focus the created input.
     **/
    setRenamingFile(event: any, filePath: string, fileName: string, fileType: FileType) {
        event.stopPropagation();
        this.renamingFile = [filePath, fileName, fileType];
        setTimeout(() => {
            if (this.renamingInput) {
                this.renamingInput.nativeElement.focus();
            }
        }, 0);
    }

    /**
     * Set renamingFile to null to make the input disappear.
     **/
    clearRenamingFile($event: any) {
        $event.stopPropagation();
        this.renamingFile = null;
    }

    /**
     * Create a file with the value of the creation input.
     **/
    onCreateFile(event: any) {
        if (!event.target.value || !this.creatingFile) {
            this.creatingFile = null;
            return;
        } else if (Object.keys(this.repositoryFiles).includes(event.target.value)) {
            this.parent.onError('fileExists');
            return;
        } else if (event.target.value.split('.').length > 1 && !textFileExtensions.includes(event.target.value.split('.').pop())) {
            this.parent.onError('unsupportedFile');
            return;
        }
        const [folderPath, fileType] = this.creatingFile;
        const file = folderPath ? `${folderPath}/${event.target.value}` : event.target.value;
        if (fileType === FileType.FILE) {
            this.repositoryFileService.createFile(this.participation.id, file).subscribe(
                () => {
                    this.emitFileChange(new CreateFileChange(FileType.FILE, file));
                    this.creatingFile = null;
                },
                () => this.parent.onError('fileOperationFailed'),
            );
        } else {
            this.repositoryFileService.createFolder(this.participation.id, file).subscribe(
                () => {
                    this.emitFileChange(new CreateFileChange(FileType.FOLDER, file));
                    this.creatingFile = null;
                },
                () => this.parent.onError('fileOperationFailed'),
            );
        }
    }

    /**
     * Enter rename file mode and focus the created input.
     **/
    setCreatingFile(event: any, folder: string, fileType: FileType) {
        event.stopPropagation();
        this.creatingFile = [folder, fileType];
        setTimeout(() => {
            if (this.creatingInput) {
                this.creatingInput.nativeElement.focus();
            }
        }, 0);
    }

    /**
     * Set creatingFile to null to make the input disappear.
     **/
    clearCreatingFile(event: any) {
        event.stopPropagation();
        this.creatingFile = null;
    }
}
