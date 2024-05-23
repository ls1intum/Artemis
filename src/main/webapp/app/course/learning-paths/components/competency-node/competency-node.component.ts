import { CommonModule } from '@angular/common';
import { Component, ElementRef, OnInit, afterNextRender, computed, inject, input, output } from '@angular/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { NgbAccordionModule, NgbDropdownModule } from '@ng-bootstrap/ng-bootstrap';
import { NodeDimension } from '@swimlane/ngx-graph';
import { CompetencyNode } from 'app/course/learning-paths/components/competency-graph/competency-graph.component';

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
export class CompetencyNodeComponent implements OnInit {
    private readonly nodeHeight = 45.59;

    readonly competencyNode = input.required<CompetencyNode>();
    readonly mastery = computed(() => this.competencyNode().mastery);
    private readonly element: ElementRef = inject(ElementRef);

    readonly onSizeSet = output<SizeUpdate>();

    constructor() {
        afterNextRender(() => {
            this.setDimensions();
        });
    }

    ngOnInit() {
        console.log('CompetencyNodeComponent initialized');
        console.log('Master:', this.mastery());
    }

    isMastered(): boolean {
        return this.mastery() >= 80;
    }

    isStarted(): boolean {
        return this.mastery() > 0 && this.mastery() < 80;
    }

    isNotStarted(): boolean {
        return this.mastery() === 0;
    }

    setDimensions() {
        const width: number = this.element.nativeElement.offsetWidth;
        const height = this.nodeHeight;
        this.onSizeSet.emit({ id: this.competencyNode().id, dimension: { height, width } });
    }
}
