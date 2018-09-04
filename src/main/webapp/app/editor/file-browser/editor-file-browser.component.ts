import { RepositoryFileService } from '../../entities/repository/repository.service';
import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, ViewChild } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Participation } from '../../entities/participation';
import { JhiWebsocketService } from '../../shared';
import { EditorComponent } from '../editor.component';
import { EditorFileBrowserCreateComponent } from './editor-file-browser-create';
import { EditorFileBrowserDeleteComponent } from './editor-file-browser-delete';
import { TreeviewComponent, TreeviewConfig, TreeviewHelper, TreeviewItem } from 'ngx-treeview';

@Component({
    selector: 'jhi-editor-file-browser',
    templateUrl: './editor-file-browser.component.html',
    providers: [
        NgbModal,
        RepositoryFileService
    ]
})

export class EditorFileBrowserComponent implements OnChanges {

    @Input() participation: Participation;
    @Input() repositoryFiles: string[];
    @Input() fileName: string;
    @Output() createdFile = new EventEmitter<object>();
    @Output() deletedFile = new EventEmitter<object>();
    @Output() selectedFile = new EventEmitter<object>();

    @ViewChild('treeview') treeview: TreeviewComponent;

    folder: string;
    filesTreeViewItem: TreeviewItem[];

    /** Provide basic configuration for the TreeView (ngx-treeview) **/
    treeviewConfig = TreeviewConfig.create({
        hasAllCheckBox: false,
        hasFilter: false,
        hasCollapseExpand: false,
        decoupleChildFromParent: false,
        // Make sure the treeview div has enough height to expand
        maxHeight: 380
    });

    constructor(private parent: EditorComponent,
                private jhiWebsocketService: JhiWebsocketService,
                private repositoryFileService: RepositoryFileService,
                public modalService: NgbModal) {}

    /**
     * @function ngOnInit
     * @desc Tracks changes to the provided participation and repositoryFiles
     * @param changes
     */
    ngOnChanges(changes: SimpleChanges): void {
        /**
         * Initialize treeview
         */
        if (this.participation) {
            this.getRepositoryFiles();
        }
        /**
         * Update the treeview when files have been added or removed
         */
        if (this.repositoryFiles) {
            this.setupTreeview(this.repositoryFiles);
        }
    }

    /**
     * @function onCreatedFile
     * @desc Emmiter function for when a new file was created; notifies the parent component
     * @param statusChange
     */
    onCreatedFile(statusChange: object) {
        this.createdFile.emit(statusChange);
    }

    /**
     * @function onDeletedFile
     * @desc Emmiter function for when a file was deleted; notifies the parent component
     * @param statusChange
     */
    onDeletedFile(statusChange: object) {
        this.deletedFile.emit(statusChange);
    }

    /**
     * @function handleNodeSelected
     * @desc Callback function for when a node in the file tree view has been selected
     * @param item: Corresponding event object, holds the selected TreeViewItem
     */
    handleNodeSelected(item: TreeviewItem) {
        if (item && item.value !== this.fileName) {
            item.checked = true;
            // If we had selected a file prior to this, we "uncheck" it
            if (this.fileName) {
                const priorFileSelection = TreeviewHelper.findItemInList(this.filesTreeViewItem, this.fileName);
                // Avoid issues after file deletion
                if (priorFileSelection) {
                    priorFileSelection.checked = false;
                }
            }

            // Inform parent editor component about the file selection change
            this.selectedFile.emit({
               fileName: item.value
            });
        }
        /** Reset folder and search our parent with the TreeviewHelper and set the folder value accordingly **/
        this.folder = null;
        for (const treeviewItem of this.filesTreeViewItem) {
            const parent = TreeviewHelper.findParent(treeviewItem, item);
            // We found our parent => process the value and assign it
            if (parent) {
                this.folder = parent.text.split('/').map(str => str.trim()).join('/');
            }
        }
    }

    /**
     * @function getRepositoryFiles
     * @desc Checks if the repository files have been requested already
     * Also initiates the building of a filetree for the filetree viewer
     */
    getRepositoryFiles() {
        if (!this.repositoryFiles) {
            /** Query the repositoryFileService for files in the repository */
            this.repositoryFileService.query(this.parent.participation.id).subscribe(files => {
                this.repositoryFiles = files;
                this.setupTreeview(this.repositoryFiles);
            }, err => {
                console.log('There was an error while getting files: ' + err.body.msg);
            });
        } else {
            this.setupTreeview(this.repositoryFiles);
        }
    }

    /**
     * @function setupTreeView
     * @desc Processes the file array, compresses it and then transforms it to a TreeViewItem
     * @param files: Provided repository files by parent editor component
     */
    setupTreeview(files: string[]) {
        let tree = this.buildTree(files);
        tree = this.compressTree(tree);
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
            let node = tree.find( element => element.text === fileSplit[0]);
            // Path part doesn't exist => add it to tree
            if (node == null) {
                node = {
                    text: fileSplit[0]
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
                node.value = node.folder;
            } else {
                // File node
                node.folder = folder;
                node.file = (folder ? folder  + '/' : '' ) + node.text;
                node.value = node.file;
                node.checked = false;

                // Currently processed node selected?
                if (node.file === this.fileName) {
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
                node.text = node.text + ' / ' + node.children[0].text;
                node.value = node.text;
                node.children = this.compressTree(node.children[0].children);
                if (node.children[0].children) {
                    return this.compressTree(tree);
                }
            } else if (node.children) {
                node.children = this.compressTree(node.children);
            }
        }
        return tree;
    }

    /**
     * @function openCreateFileModal
     * @desc Opens a popup to create a new repository file
     */
    openCreateFileModal() {
        const modalRef = this.modalService.open(EditorFileBrowserCreateComponent, {keyboard: true, size: 'lg'});
        modalRef.componentInstance.participation = this.participation;
        modalRef.componentInstance.parent = this;
        if (this.folder) {
            modalRef.componentInstance.folder = this.folder;
        }
    }

    /**
     * @function openDeleteFileModal
     * @desc Opens a popup to delete the selected repository file
     */
    openDeleteFileModal() {
        /**
         * We only open the modal if the user has a file selected
         */
        if (this.fileName) {
            const modalRef = this.modalService.open(EditorFileBrowserDeleteComponent, {keyboard: true, size: 'lg'});
            modalRef.componentInstance.participation = this.participation;
            modalRef.componentInstance.parent = this;
            modalRef.componentInstance.fileNameToDelete = this.fileName;
        }
    }
}
