import { Component, EventEmitter, Input, OnInit, Output, ViewEncapsulation } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Layout } from '@swimlane/ngx-graph';
import * as shape from 'd3-shape';
import { Subject } from 'rxjs';
import { LearningPathService } from 'app/course/learning-paths/learning-path.service';
import { NgxLearningPathDTO, NgxLearningPathNode } from 'app/entities/competency/learning-path.model';

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
    @Output() nodeClicked: EventEmitter<NgxLearningPathNode> = new EventEmitter();
    ngxLearningPath: NgxLearningPathDTO;

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
            this.loadData();
        }
    }

    loadData() {
        this.isLoading = true;
        this.learningPathService.getLearningPathNgxGraph(this.learningPathId).subscribe((ngxLearningPathResponse) => {
            this.ngxLearningPath = ngxLearningPathResponse.body!;
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
