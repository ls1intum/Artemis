import { Component, EventEmitter, Input, OnInit, Output, ViewEncapsulation } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Layout } from '@swimlane/ngx-graph';
import * as shape from 'd3-shape';
import { Subject } from 'rxjs';
import { LearningPathService } from 'app/course/learning-paths/learning-path.service';
import { NgxLearningPathDTO, NgxLearningPathNode } from 'app/entities/competency/learning-path.model';
import { ExerciseEntry, LectureUnitEntry } from 'app/course/learning-paths/participate/learning-path-storage.service';
import { faEye } from '@fortawesome/free-solid-svg-icons';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { LearningPathProgressModalComponent } from 'app/course/learning-paths/progress-modal/learning-path-progress-modal.component';

export enum LearningPathViewMode {
    GRAPH = 'GRAPH',
    PATH = 'PATH',
}

@Component({
    selector: 'jhi-learning-path-graph',
    styleUrls: ['./learning-path-graph.component.scss'],
    templateUrl: './learning-path-graph.component.html',
    encapsulation: ViewEncapsulation.None,
})
export class LearningPathGraphComponent implements OnInit {
    isLoading = false;
    @Input() learningPathId: number;
    @Input() courseId: number;
    @Input() viewMode?: LearningPathViewMode;
    @Output() viewModeChange = new EventEmitter<LearningPathViewMode>();
    @Output() nodeClicked: EventEmitter<NgxLearningPathNode> = new EventEmitter();
    ngxLearningPath: NgxLearningPathDTO;
    ngxGraph?: NgxLearningPathDTO;
    ngxPath?: NgxLearningPathDTO;
    highlightedNode?: NgxLearningPathNode;

    layout: string | Layout = 'dagreCluster';
    curve = shape.curveBundle;

    private _draggingEnabled = false;
    private _panningEnabled = false;
    private _zoomEnabled = false;
    private _panOnZoom = false;

    update$: Subject<boolean> = new Subject<boolean>();
    center$: Subject<boolean> = new Subject<boolean>();
    zoomToFit$: Subject<boolean> = new Subject<boolean>();

    faEye = faEye;

    constructor(
        private activatedRoute: ActivatedRoute,
        private learningPathService: LearningPathService,
        private modalService: NgbModal,
    ) {}

    ngOnInit() {
        if (this.learningPathId) {
            this.loadDataIfNecessary();
        }
    }

    @Input() set draggingEnabled(value) {
        this._draggingEnabled = value || false;
    }

    get draggingEnabled() {
        return this._draggingEnabled;
    }

    @Input() set panningEnabled(value) {
        this._panningEnabled = value && this.viewMode !== LearningPathViewMode.PATH;
    }

    get panningEnabled() {
        return this._panningEnabled;
    }

    @Input() set zoomEnabled(value) {
        this._zoomEnabled = value && this.viewMode !== LearningPathViewMode.PATH;
    }

    get zoomEnabled() {
        return this._zoomEnabled;
    }

    @Input() set panOnZoom(value) {
        this._panOnZoom = value && this.viewMode !== LearningPathViewMode.PATH;
    }

    get panOnZoom() {
        return this._panOnZoom;
    }

    refreshData() {
        if (this.ngxGraph) {
            this.loadGraphRepresentation(this.viewMode === LearningPathViewMode.GRAPH);
        }
        if (this.ngxPath) {
            this.loadPathRepresentation(this.viewMode === LearningPathViewMode.PATH);
        }
    }

    loadDataIfNecessary() {
        if (this.viewMode === LearningPathViewMode.GRAPH) {
            if (!this.ngxGraph) {
                this.loadGraphRepresentation(true);
            } else {
                this.ngxLearningPath = this.ngxGraph;
                this.update$.next(true);
            }
        } else {
            if (!this.ngxPath) {
                this.loadPathRepresentation(true);
            } else {
                this.ngxLearningPath = this.ngxPath;
                this.update$.next(true);
            }
        }
    }

    loadGraphRepresentation(render: boolean) {
        this.isLoading = true;
        this.learningPathService.getLearningPathNgxGraph(this.learningPathId).subscribe((ngxLearningPathResponse) => {
            this.ngxGraph = ngxLearningPathResponse.body!;
            if (render) {
                this.ngxLearningPath = this.ngxGraph;
                this.update$.next(true);
            }
            this.isLoading = false;
        });
    }

    loadPathRepresentation(render: boolean) {
        this.isLoading = true;
        this.learningPathService.getLearningPathNgxPath(this.learningPathId).subscribe((ngxLearningPathResponse) => {
            this.ngxPath = ngxLearningPathResponse.body!;
            if (render) {
                this.ngxLearningPath = this.ngxPath;
                this.update$.next(true);
            }
            this.isLoading = false;
        });
    }

    onResize() {
        this.update$.next(true);
        this.center$.next(true);
        this.zoomToFit$.next(true);
    }

    onCenterView() {
        this.zoomToFit$.next(true);
        this.center$.next(true);
    }

    changeViewMode() {
        if (this.viewMode === LearningPathViewMode.GRAPH) {
            this.viewMode = LearningPathViewMode.PATH;
        } else {
            this.viewMode = LearningPathViewMode.GRAPH;
        }
        this.loadDataIfNecessary();
        this.update$.next(true);
    }

    highlightNode(learningObject: LectureUnitEntry | ExerciseEntry) {
        if (this.viewMode === LearningPathViewMode.GRAPH) {
            this.highlightedNode = this.findNode(learningObject, this.ngxGraph!);
        } else {
            this.highlightedNode = this.findNode(learningObject, this.ngxPath!);
        }
        this.update$.next(true);
    }

    clearHighlighting() {
        this.highlightedNode = undefined;
        this.update$.next(true);
    }

    private findNode(learningObject: LectureUnitEntry | ExerciseEntry, ngx: NgxLearningPathDTO) {
        if (learningObject instanceof LectureUnitEntry) {
            return ngx.nodes.find((node) => {
                return node.linkedResource === learningObject.lectureUnitId && node.linkedResourceParent === learningObject.lectureId;
            });
        } else {
            return ngx.nodes.find((node) => {
                return node.linkedResource === learningObject.exerciseId && !node.linkedResourceParent;
            });
        }
    }

    viewProgress() {
        this.learningPathService.getLearningPath(this.learningPathId).subscribe((learningPathResponse) => {
            const modalRef = this.modalService.open(LearningPathProgressModalComponent, {
                size: 'xl',
                backdrop: 'static',
                windowClass: 'learning-path-modal',
            });
            modalRef.componentInstance.courseId = this.courseId;
            modalRef.componentInstance.learningPath = learningPathResponse.body!;
        });
    }

    protected readonly LearningPathViewMode = LearningPathViewMode;
}
