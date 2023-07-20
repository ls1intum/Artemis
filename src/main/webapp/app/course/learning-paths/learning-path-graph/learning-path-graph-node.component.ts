import { Component, Input } from '@angular/core';
import { faCheckCircle, faCircle, faPlayCircle, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { NgxLearningPathNode, NodeType } from 'app/entities/learning-path.model';

@Component({
    selector: 'jhi-learning-path-graph-node',
    templateUrl: './learning-path-graph-node.component.html',
})
export class LearningPathGraphNodeComponent {
    @Input() node: NgxLearningPathNode;

    //icons
    faCheckCircle = faCheckCircle;
    faPlayCircle = faPlayCircle;
    faQuestionCircle = faQuestionCircle;

    faCircle = faCircle;
    protected readonly NodeType = NodeType;
}
