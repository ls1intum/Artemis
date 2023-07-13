import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Layout } from '@swimlane/ngx-graph';
import * as shape from 'd3-shape';
import { Subject } from 'rxjs';
import { LearningPathService } from 'app/course/learning-paths/learning-path.service';
import { NgxLearningPathDTO } from 'app/entities/learning-path.model';

@Component({
    selector: 'jhi-learning-path-graph',
    styleUrls: ['./learning-path-graph.component.scss'],
    templateUrl: './learning-path-graph.component.html',
})
export class LearningPathGraphComponent implements OnInit {
    isLoading = false;
    courseId: number;
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

    constructor(private activatedRoute: ActivatedRoute, private learningPathService: LearningPathService) {}

    ngOnInit() {
        this.activatedRoute.parent!.parent!.params.subscribe((params) => {
            this.courseId = +params['courseId'];
            if (this.courseId) {
                this.loadData();
            }
        });
    }

    loadData() {
        this.isLoading = true;
        this.learningPathService.getNgxLearningPath(this.courseId).subscribe((ngxLearningPathResponse) => {
            this.ngxLearningPath = ngxLearningPathResponse.body!;
            console.log(this.ngxLearningPath);
            this.isLoading = false;
        });
    }

    onResize() {
        this.update$.next(true);
        this.center$.next(true);
        this.zoomToFit$.next(true);
    }
}
