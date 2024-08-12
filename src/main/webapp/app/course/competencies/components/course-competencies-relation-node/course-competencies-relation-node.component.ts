import { Component, ElementRef, afterNextRender, computed, inject, input, output } from '@angular/core';
import { SizeUpdate } from 'app/course/learning-paths/components/competency-node/competency-node.component';
import { Node } from '@swimlane/ngx-graph';
import { CourseCompetencyType } from 'app/entities/competency.model';
import { NgClass } from '@angular/common';

@Component({
    selector: 'jhi-course-competencies-relation-node',
    standalone: true,
    imports: [NgClass],
    templateUrl: './course-competencies-relation-node.component.html',
    styleUrl: './course-competencies-relation-node.component.scss',
})
export class CourseCompetenciesRelationNodeComponent {
    // height of node element in pixels
    private readonly nodeHeight = 45.59;

    private readonly element = inject(ElementRef);

    readonly courseCompetencyNode = input.required<Node>();
    readonly courseCompetencyType = computed(() => this.courseCompetencyNode().data.type!);

    readonly onSizeSet = output<SizeUpdate>();

    constructor() {
        afterNextRender(() => this.setDimensions(this.element));
    }

    setDimensions(element: ElementRef): void {
        const width: number = element.nativeElement.offsetWidth;
        const height = this.nodeHeight;
        this.onSizeSet.emit({ id: `${this.courseCompetencyNode().id}`, dimension: { height, width } });
    }

    protected readonly CourseCompetencyType = CourseCompetencyType;
}
