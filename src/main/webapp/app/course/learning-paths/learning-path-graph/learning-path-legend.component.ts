import { Component, Input } from '@angular/core';
import { NgxLearningPathNode, NodeType, getIcon } from 'app/entities/competency/learning-path.model';

@Component({
    selector: 'jhi-learning-path-legend',
    styleUrls: ['./learning-path-graph.component.scss'],
    templateUrl: './learning-path-legend.component.html',
})
export class LearningPathLegendComponent {
    @Input() nodeTypes: Set<NodeType>;

    protected readonly getIcon = getIcon;
    protected readonly competencyEnd = { id: '', type: NodeType.COMPETENCY_END } as NgxLearningPathNode;
    protected readonly matchStart = { id: '', type: NodeType.MATCH_START } as NgxLearningPathNode;
    protected readonly matchEnd = { id: '', type: NodeType.MATCH_END } as NgxLearningPathNode;
    protected readonly learningObject = { id: '', type: NodeType.LECTURE_UNIT } as NgxLearningPathNode;
    protected readonly completedLearningObject = { id: '', type: NodeType.LECTURE_UNIT, completed: true } as NgxLearningPathNode;
    protected readonly NodeType = NodeType;
}
