import { AfterViewInit, Component, ElementRef, computed, inject, input, output } from '@angular/core';
import { SizeUpdate } from 'app/atlas/manage/competency-node/competency-node.component';
import { Node } from '@swimlane/ngx-graph';
import { CourseCompetencyType } from 'app/entities/competency.model';
import { NgClass } from '@angular/common';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-course-competency-relation-node',
    imports: [NgClass, TranslateDirective, NgbTooltipModule, ArtemisTranslatePipe],
    templateUrl: './course-competency-relation-node.component.html',
    styleUrl: './course-competency-relation-node.component.scss',
})
export class CourseCompetencyRelationNodeComponent implements AfterViewInit {
    protected readonly CourseCompetencyType = CourseCompetencyType;
    // height of node element in pixels
    private readonly nodeHeight = 45.59;

    private readonly element = inject(ElementRef);

    courseCompetencyNode = input.required<Node>();
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
