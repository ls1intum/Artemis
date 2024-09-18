import { AfterViewInit, Component, ElementRef, computed, inject, input, output } from '@angular/core';
import { SizeUpdate } from 'app/course/learning-paths/components/competency-node/competency-node.component';
import { Node } from '@swimlane/ngx-graph';
import { CourseCompetencyType } from 'app/entities/competency.model';
import { NgClass } from '@angular/common';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@Component({
    selector: 'jhi-course-competency-relation-node',
    standalone: true,
    imports: [NgClass, TranslateDirective, NgbTooltipModule, ArtemisSharedModule],
    templateUrl: './course-competency-relation-node.component.html',
    styleUrl: './course-competency-relation-node.component.scss',
})
export class CourseCompetencyRelationNodeComponent implements AfterViewInit {
    protected readonly CourseCompetencyType = CourseCompetencyType;
    // height of node element in pixels
    private readonly nodeHeight = 45.59;

    private readonly element = inject(ElementRef);

    readonly courseCompetencyNode = input.required<Node>();
    readonly courseCompetencyType = computed(() => this.courseCompetencyNode().data.type!);

    readonly onSizeSet = output<SizeUpdate>();

    ngAfterViewInit(): void {
        this.setDimensions(this.element);
    }

    setDimensions(element: ElementRef): void {
        const width: number = element.nativeElement.offsetWidth;
        const height = this.nodeHeight;
        this.onSizeSet.emit({ id: `${this.courseCompetencyNode().id}`, dimension: { height, width } });
    }
}
