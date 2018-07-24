import {ResultService} from '../../entities/result';
import {RepositoryFileService, RepositoryService} from '../../entities/repository/repository.service';
import {Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, SimpleChanges} from '@angular/core';
import {NgbModal} from '@ng-bootstrap/ng-bootstrap';
import {ExerciseParticipationService, Participation, ParticipationService} from '../../entities/participation';
import {WindowRef} from '../../shared/websocket/window.service';
import {JhiAlertService} from 'ng-jhipster';
import {CourseExerciseService} from '../../entities/course';
import {JhiWebsocketService} from '../../shared';
import {EditorComponent} from '../editor.component';
import { TreeModel, TreeModelSettings, Ng2TreeSettings } from 'ng2-tree';

@Component({
    selector: 'jhi-editor-file-browser',
    templateUrl: './editor-file-browser.component.html',
    providers: [
        JhiAlertService,
        WindowRef,
        ResultService,
        RepositoryService,
        CourseExerciseService,
        ParticipationService,
        ExerciseParticipationService,
        NgbModal,
        RepositoryFileService
    ]
})

export class EditorFileBrowserComponent implements OnInit, OnDestroy, OnChanges {

    public fileTree: TreeModel;
    public fileTreeSettings: Ng2TreeSettings = {
        rootIsVisible: false,
        showCheckboxes: false,
        enableCheckboxes: false
    };

    /**
     * bindings:
        participation: '<',
        file: '=',
        onCreatedFile: '&',
        onDeletedFile: '&',
        repositoryFiles: '<'
     */
    @Input() participation: Participation;
    @Input() repositoryFiles;
    @Input() fileName: string;
    @Output() createdFile = new EventEmitter<object>();
    @Output() deletedFile = new EventEmitter<object>();
    @Output() selectedFile = new EventEmitter<object>();

    constructor(private parent: EditorComponent,
                private jhiWebsocketService: JhiWebsocketService,
                private repositoryFileService: RepositoryFileService,
                public modalService: NgbModal) {
    }

    /**
     * @function ngOnInit
     * @desc Framework function which is executed when the component is instantiated.
     * Used to assign parameters which are used by the component
     */
    ngOnInit(): void {
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (this.participation) {
            this.getRepositoryFiles();
        }
    }

    onCreatedFile(statusChange: object) {
        console.log('EMITTING onCreatedFile');
        this.createdFile.emit(statusChange);
    }

    onDeletedFile(statusChange: object) {
        console.log('EMITTING onDeletedFile');
        this.deletedFile.emit(statusChange);
    }

    /**
     * Callback function for when a node in the file tree view has been selected
     * @param event: Corresponding event object, holds the node name and parent informations
     */
    handleNodeSelected(event) {

        const parentNodeValue = event.node.parent.node.value;
        /**
         * If the selected file is not in the root directory, we need to prepend its name with its parent node name
         * Otherwise we just emit the node name (value)
         */
        if(parentNodeValue != null && parentNodeValue != 'root') {
            this.selectedFile.emit({
                fileName: parentNodeValue + '/' + event.node.value
            });
        } else {
            this.selectedFile.emit({
                fileName: event.node.value
            });
        }
    }

    updateRepositoryCommitStatus(event) {
        console.log(event);
        if (this.onCreatedFile) {
            this.onCreatedFile({
                bIsSaved: false
            });
        } else if (this.onDeletedFile) {
            this.onDeletedFile({
                bIsSaved: false
            });
        }
    }

    initializeTreeViewer(fileTree): void {
        this.fileTree = {
            value: 'root',
            settings: this.getTreeViewSettings(),
            children: fileTree
        };
    }

    getRepositoryFiles() {
        if (!this.repositoryFiles) {
            /** Query the repositoryFileService for files in the repository */
            this.repositoryFileService.query(this.parent.participation.id).subscribe(files => {
                this.repositoryFiles = files;
                console.log(this.repositoryFiles);
                this.setupTreeview(this.repositoryFiles);
            }, err => {
                console.log('There was an error while getting files: ' + err.body.msg);
            });
        } else {
            this.setupTreeview(this.repositoryFiles);
        }
    }

    setupTreeview(files) {
        let tree = this.buildTree(files);
        tree = this.compressTree(tree);
        this.initializeTreeViewer(tree);
    }

    getTreeViewSettings(): TreeModelSettings {

        /**
         expandIcon: 'fa fa-folder',
         emptyIcon: 'fa fa-file',
         collapseIcon: 'fa fa-folder-open',
         *
         */

        return {
            'static': false,
            'isCollapsedOnInit': false,
            'cssClasses': {
                'expanded': 'fa fa-folder-open',
                'collapsed': 'fa fa-folder',
                'leaf': 'fa',
                'empty': 'fa fa-folder disabled'
            },
            'templates': {
                'leaf': '<i class="fa fa-file-o"></i>'
            }
        };
    }

    buildTree(files, tree?, folder?) {

        console.log('CALLING BUILDTREE WITH ARGUMENTS: ');
        console.log(arguments);

        /**
         * Extract exerciseName for further processing by reading from the participation
         */
        const repoUrlSplit = this.participation.repositoryUrl.split('/');
        const exerciseName = repoUrlSplit[repoUrlSplit.length - 1].slice(0, -4);

        /**
         * Initialize tree if empty
         */
        if (tree == null) {
            tree = [];
        }

        for (let file of files) {

            let fileSplit = file.split('\\');
            fileSplit = fileSplit.slice(fileSplit.indexOf(exerciseName) + 1);
            file = fileSplit.join('/');

            let node = tree.find(function(element) {
                return element.value === fileSplit[0];
            });

            if (node == null) {
                node = {
                    value: fileSplit[0]
                };
                tree.push(node);
            }

            fileSplit.shift();

            if (fileSplit.length > 0) {
                // directory node
                node.selectable = false;
                node.children = this.buildTree([fileSplit.join('/')], node.children, folder ? folder + '/' + node.value : node.value);
                node.folder = node.value;
            } else {
                // file node
                node.folder = folder;
                node.file = (folder ? folder  + '/' : '' ) + node.text;

                if (node.file === this.fileName) {
                    folder = node.folder;
                    node.state = {
                        selected: true
                    };
                }
            }
        }
        return tree;
    }

    // Compress tree to not contain nodes with only one directory child node
    compressTree(tree) {

        for (const node of tree) {
            if (node.children && node.children.length === 1 && node.children[0].children) {
                node.value = node.value + ' / ' + node.children[0].value;
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

    // TODO: create-Modal
    // TODO: delete-Modal

    /**
     * @function ngOnDestroy
     * @desc Framework function which is executed when the component is destroyed.
     * Used for component cleanup, close open sockets, connections, subscriptions...
     */
    ngOnDestroy(): void {}

}
