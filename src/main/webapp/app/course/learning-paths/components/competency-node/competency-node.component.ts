import { CommonModule } from '@angular/common';
import { AfterViewInit, Component, ElementRef, computed, inject, input, output } from '@angular/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { NgbAccordionModule, NgbDropdownModule } from '@ng-bootstrap/ng-bootstrap';
import { NodeDimension } from '@swimlane/ngx-graph';
import { CompetencyGraphNodeDTO } from 'app/entities/competency/learning-path.model';

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
    // height of node element in pixels
    private readonly nodeHeight = 45.59;

    readonly competencyNode = input.required<CompetencyGraphNodeDTO>();
    readonly masteryProgress = computed(() => Math.floor(this.competencyNode().masteryProgress));
    private readonly element: ElementRef = inject(ElementRef);

    readonly onSizeSet = output<SizeUpdate>();

    ngAfterViewInit(): void {
        this.setDimensions();
    }

    isMastered(): boolean {
        return this.masteryProgress() === 100;
    }

    isStarted(): boolean {
        return this.masteryProgress() > 0 && this.masteryProgress() < 100;
    }

    isNotStarted(): boolean {
        return this.masteryProgress() === 0;
    }

    setDimensions() {
        const width: number = this.element.nativeElement.offsetWidth;
        const height = this.nodeHeight;
        this.onSizeSet.emit({ id: this.competencyNode().id, dimension: { height, width } });
    }
}
