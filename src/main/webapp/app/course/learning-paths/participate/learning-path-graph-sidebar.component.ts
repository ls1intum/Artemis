import { Component, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { LearningPathGraphComponent, LearningPathViewMode } from 'app/course/learning-paths/learning-path-graph/learning-path-graph.component';
import { NgxLearningPathNode } from 'app/entities/competency/learning-path.model';

@Component({
    selector: 'jhi-learning-path-graph-sidebar',
    styleUrls: ['./learning-path-graph-sidebar.component.scss'],
    templateUrl: './learning-path-graph-sidebar.component.html',
})
export class LearningPathGraphSidebarComponent {
    @Input() courseId: number;
    @Input() learningPathId: number;

    @ViewChild(`learningPathGraphComponent`, { static: false })
    learningPathGraphComponent: LearningPathGraphComponent;

    @Output() nodeClicked: EventEmitter<NgxLearningPathNode> = new EventEmitter();

    protected readonly PATH = LearningPathViewMode.PATH;
}
