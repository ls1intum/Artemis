import { Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { Layout, NgxGraphZoomOptions } from '@swimlane/ngx-graph';
import * as shape from 'd3-shape';
import { Subject } from 'rxjs';
import { LearningPathService } from 'app/course/learning-paths/learning-path.service';
import { CompetencyProgressForLearningPathDTO, NgxLearningPathDTO, NgxLearningPathNode, NodeType } from 'app/entities/competency/learning-path.model';

@Component({
    selector: 'jhi-learning-path-graph',
    styleUrls: ['./learning-path-graph.component.scss'],
    templateUrl: './learning-path-graph.component.html',
})
export class LearningPathGraphComponent implements OnInit {
    private learningPathService = inject(LearningPathService);

    isLoading = false;
    @Input() learningPathId: number;
    @Input() courseId: number;
    @Output() nodeClicked: EventEmitter<NgxLearningPathNode> = new EventEmitter();
    ngxLearningPath: NgxLearningPathDTO;
    nodeTypes: Set<NodeType> = new Set();
    competencyProgress: Map<number, CompetencyProgressForLearningPathDTO> = new Map();

    layout: string | Layout = 'dagreCluster';
    curve = shape.curveBundle;

    private _draggingEnabled = false;
    private _panningEnabled = false;
    private _zoomEnabled = false;
    private _panOnZoom = false;
    private _showMiniMap = false;

    update$: Subject<boolean> = new Subject<boolean>();
    center$: Subject<boolean> = new Subject<boolean>();
    zoomToFit$: Subject<NgxGraphZoomOptions> = new Subject<NgxGraphZoomOptions>();

    protected readonly NodeType = NodeType;

    ngOnInit() {
        if (this.learningPathId) {
            this.loadDataAndRender();
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

    @Input() set showMiniMap(value) {
        this._showMiniMap = value;
    }

    get showMiniMap() {
        return this._showMiniMap;
    }

    refreshData() {
        this.loadDataAndRender();
    }

    loadDataAndRender() {
        this.learningPathService.getCompetencyProgressForLearningPath(this.learningPathId).subscribe({
            next: (response) => {
                response.body!.forEach((progress) => {
                    this.competencyProgress.set(progress.competencyId!, progress);
                });
            },
            complete: () => {
                this.loadGraphRepresentation(true);
            },
        });
    }

    loadGraphRepresentation(render: boolean) {
        this.isLoading = true;
        this.learningPathService.getLearningPathNgxGraph(this.learningPathId).subscribe((ngxLearningPathResponse) => {
            ngxLearningPathResponse.body!.nodes.forEach((node) => {
                this.defineNodeDimensions(node);
            });
            this.ngxLearningPath = ngxLearningPathResponse.body!;

            // update contained node types
            this.nodeTypes = new Set();
            this.ngxLearningPath.nodes.forEach((node) => {
                this.nodeTypes.add(node.type!);
            });

            if (render) {
                this.update$.next(true);
            }
            this.isLoading = false;
        });
    }

    defineNodeDimensions(node: NgxLearningPathNode) {
        if (node.type === NodeType.COMPETENCY_START) {
            node.dimension = { width: 75, height: 75 };
        } else {
            node.dimension = { width: 50, height: 50 };
        }
    }

    onResize() {
        this.update$.next(true);
        this.center$.next(true);
        this.zoomToFit$.next({ autoCenter: true });
    }

    onCenterView() {
        this.zoomToFit$.next({ autoCenter: true });
        this.center$.next(true);
    }
}
