import { CommonModule } from '@angular/common';
import { AfterViewInit, Component, ElementRef, computed, inject, input, output } from '@angular/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { NgbAccordionModule, NgbDropdownModule } from '@ng-bootstrap/ng-bootstrap';
import { NodeDimension } from '@swimlane/ngx-graph';
import { CompetencyGraphNodeDTO, CompetencyGraphNodeValueType } from 'app/entities/competency/learning-path.model';

export interface SizeUpdate {
    id: string;
    dimension: NodeDimension;
}

@Component({
    selector: 'jhi-learning-path-competency-node',
    standalone: true,
    imports: [NgbDropdownModule, FontAwesomeModule, NgbAccordionModule, CommonModule],
    templateUrl: './competency-node.component.html',
    styleUrl: './competency-node.component.scss',
})
export class CompetencyNodeComponent implements AfterViewInit {
    protected readonly CompetencyGraphNodeValueType = CompetencyGraphNodeValueType;
    // height of node element in pixels
    private readonly nodeHeight = 45.59;

    readonly competencyNode = input.required<CompetencyGraphNodeDTO>();
    readonly valueType = computed(() => this.competencyNode().valueType);

    readonly value = computed(() => this.competencyNode().value);

    private readonly element = inject(ElementRef);
    readonly onSizeSet = output<SizeUpdate>();

    ngAfterViewInit(): void {
        this.setDimensions();
    }

    isGreen(): boolean {
        switch (this.valueType()) {
            case CompetencyGraphNodeValueType.MASTERY_PROGRESS:
                return this.value() >= 100;
            default:
                return false;
        }
    }

    isYellow(): boolean {
        switch (this.valueType()) {
            case CompetencyGraphNodeValueType.MASTERY_PROGRESS:
            case CompetencyGraphNodeValueType.AVERAGE_MASTERY_PROGRESS:
                return this.value() > 0 && this.value() < 100;
            default:
                return false;
        }
    }

    isGray(): boolean {
        switch (this.valueType()) {
            case CompetencyGraphNodeValueType.MASTERY_PROGRESS:
            case CompetencyGraphNodeValueType.AVERAGE_MASTERY_PROGRESS:
                return this.value() === 0;
            default:
                return false;
        }
    }

    setDimensions(): void {
        const width: number = this.element.nativeElement.offsetWidth;
        const height = this.nodeHeight;
        this.onSizeSet.emit({ id: this.competencyNode().id, dimension: { height, width } });
    }
}
