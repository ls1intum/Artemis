import { Component, input } from '@angular/core';
import { Node } from '@swimlane/ngx-graph';

@Component({
    selector: 'jhi-course-competencies-relation-node',
    standalone: true,
    imports: [],
    templateUrl: './course-competencies-relation-node.component.html',
    styleUrl: './course-competencies-relation-node.component.scss',
})
export class CourseCompetenciesRelationNodeComponent {
    readonly courseCompetencyNode = input.required<Node>();
}
