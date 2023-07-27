import { Component, Input, ViewChild } from '@angular/core';
import { faCheckCircle, faCircle, faPlayCircle, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { NgxLearningPathNode, NodeType } from 'app/entities/competency/learning-path.model';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-learning-path-graph-node',
    templateUrl: './learning-path-graph-node.component.html',
})
export class LearningPathGraphNodeComponent {
    @Input() courseId: number;
    @Input() node: NgxLearningPathNode;

    //icons
    faCheckCircle = faCheckCircle;
    faPlayCircle = faPlayCircle;
    faQuestionCircle = faQuestionCircle;

    faCircle = faCircle;
    protected readonly NodeType = NodeType;

    @ViewChild('inspectPopover')
    private inspectPopover: NgbPopover;
}
