import { Component, EventEmitter, Input, OnInit, Output, ViewEncapsulation } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Layout } from '@swimlane/ngx-graph';
import * as shape from 'd3-shape';
import { Subject } from 'rxjs';
import { LearningPathService } from 'app/course/learning-paths/learning-path.service';
import { NgxLearningPathDTO, NgxLearningPathNode } from 'app/entities/competency/learning-path.model';

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

    layout: string | Layout = 'dagreCluster';
    curve = shape.curveBundle;

    draggingEnabled = false;
    panningEnabled = true;
    zoomEnabled = true;
    panOnZoom = true;

    update$: Subject<boolean> = new Subject<boolean>();
    center$: Subject<boolean> = new Subject<boolean>();
    zoomToFit$: Subject<boolean> = new Subject<boolean>();

    constructor(
        private activatedRoute: ActivatedRoute,
        private learningPathService: LearningPathService,
    ) {}

    ngOnInit() {
        if (this.learningPathId) {
            this.loadDataIfNecessary();
        }
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
}
