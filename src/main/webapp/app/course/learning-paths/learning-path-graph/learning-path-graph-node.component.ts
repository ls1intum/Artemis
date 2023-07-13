import { Component, Input } from '@angular/core';
import { faCheckCircle, faCircle, faInfoCircle } from '@fortawesome/free-solid-svg-icons';

export enum NodeType {
    COMPETENCY_START,
    COMPETENCY_END,
    MATCH_START,
    MATCH_END,
    COMPLETED,
}
@Component({
    selector: 'jhi-learning-path-graph-node',
    templateUrl: './learning-path-graph-node.component.html',
})
export class LearningPathGraphNodeComponent {
    @Input()
    type: NodeType;

    //icons
    faCheckCircle = faCheckCircle;
    faInfoCircle = faInfoCircle;
    faCircle = faCircle;
    protected readonly NodeType = NodeType;
}
