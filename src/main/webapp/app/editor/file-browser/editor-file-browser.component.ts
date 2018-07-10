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
import * as $ from 'jquery';

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

    // participation="participation" file="file" on-created-file="$ctrl.isCommitted = false" on-deleted-file="$ctrl.isCommitted = false" repository-files="$ctrl.repositoryFiles"
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
    ngOnInit(): void {}

    ngOnChanges(changes: SimpleChanges): void {
        if (this.participation && this.repositoryFiles) {
            this.getFiles();
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

    updateRepositoryCommitStatus(event) {
        console.log(event);
        if(this.onCreatedFile) {
            this.onCreatedFile({
                bIsSaved: false
            });
        } else if(this.onDeletedFile) {
            this.onDeletedFile({
                bIsSaved: false
            });
        }
    }

    getFiles() {
        if (!this.repositoryFiles) {
            /** Query the repositoryFileService for files in the repository */
            this.repositoryFileService.query(this.participation.id).subscribe(files => {
                this.repositoryFiles = files;
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
        $('#fileTree').treeview({
            data: tree,
            levels: 5,
            expandIcon: 'fa fa-folder',
            emptyIcon: 'fa fa-file',
            collapseIcon: 'fa fa-folder-open',
            showBorder: false
        }).on('nodeSelected', function (event, node) {
            this.fileName = node.file;
            this.folder = node.folder;
            /*$state.go('editor', {
                file: node.file
            }, {notify:false});*/
        });
    }

    buildTree(files, tree?, folder?) {
        if (tree == null) {
            tree = [];
        }

        for(let file of files) {

            // remove leading and trailing slash
            file = file.replace(/^\/|\/$/g, '');

            var fileSplit = file.split('/');

            var node = fileSplit.find(function(element) {
                return element.text === fileSplit[0];
            });
            if (node == null) {
                node = {
                    text: fileSplit[0]
                };
                tree.push(node);
            }

            fileSplit.shift();
            if (fileSplit.length > 0) {
                // directory node
                node.selectable = false;
                node.nodes = this.buildTree([fileSplit.join('/')], node.nodes, folder ? folder + '/' + node.text: node.text);
                node.folder = node.text;
            } else {
                // file node
                node.folder = folder;
                node.file = (folder ? folder  + '/' : '' )+ node.text;

                if(node.file == this.fileName) {
                    folder = node.folder;
                    node.state = {
                        selected: true
                    }
                }
            }
        }
        return tree;
    }

    // Compress tree to not contain nodes with only one directory child node
    compressTree(tree) {

        for(let node of tree) {
            if (node.nodes && node.nodes.length == 1 && node.nodes[0].nodes) {
                node.text = node.text + ' / ' + node.nodes[0].text;
                node.nodes = this.compressTree(node.nodes[0].nodes);
                if(node.nodes[0].nodes) {
                    return this.compressTree(tree);
                }
            } else if (node.nodes) {
                node.nodes = this.compressTree(node.nodes);
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
