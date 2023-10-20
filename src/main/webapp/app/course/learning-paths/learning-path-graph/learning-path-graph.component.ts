import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Layout } from '@swimlane/ngx-graph';
import * as shape from 'd3-shape';
import { Subject } from 'rxjs';
import { LearningPathService } from 'app/course/learning-paths/learning-path.service';
import { NgxLearningPathDTO, NgxLearningPathNode } from 'app/entities/competency/learning-path.model';
import { faEye } from '@fortawesome/free-solid-svg-icons';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-learning-path-graph',
    styleUrls: ['./learning-path-graph.component.scss'],
    templateUrl: './learning-path-graph.component.html',
})
export class LearningPathGraphComponent implements OnInit {
    isLoading = false;
    @Input() learningPathId: number;
    @Input() courseId: number;
    @Output() nodeClicked: EventEmitter<NgxLearningPathNode> = new EventEmitter();
    ngxLearningPath: NgxLearningPathDTO;

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
            this.loadGraphRepresentation(true);
        }
    }

    @Input() set draggingEnabled(value) {
        this._draggingEnabled = value;
    }

    get draggingEnabled() {
        return this._draggingEnabled;
    }

    @Input() set panningEnabled(value) {
        this._panningEnabled = value;
    }

    get panningEnabled() {
        return this._panningEnabled;
    }

    @Input() set zoomEnabled(value) {
        this._zoomEnabled = value;
    }

    get zoomEnabled() {
        return this._zoomEnabled;
    }

    @Input() set panOnZoom(value) {
        this._panOnZoom = value;
    }

    get panOnZoom() {
        return this._panOnZoom;
    }

    refreshData() {
        this.loadGraphRepresentation(true);
    }

    loadGraphRepresentation(render: boolean) {
        this.isLoading = true;
        this.learningPathService.getLearningPathNgxGraph(this.learningPathId).subscribe((ngxLearningPathResponse) => {
            this.ngxLearningPath = ngxLearningPathResponse.body!;
            if (render) {
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
}
