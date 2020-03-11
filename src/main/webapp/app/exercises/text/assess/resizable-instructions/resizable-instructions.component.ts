import { Component, Input } from '@angular/core';
import { GradingCriterion } from 'app/exercises/shared/structured-grading-criterion/grading-criterion.model';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';

@Component({
    selector: 'jhi-resizable-instructions',
    templateUrl: './resizable-instructions.component.html',
    styleUrls: ['./resizable-instructions.component.scss'],
})
export class ResizableInstructionsComponent {
    @Input() public criteria: GradingCriterion[];
    @Input() public problemStatement: string | null;
    @Input() public sampleSolution: string | null;
    @Input() public gradingInstructions: string | null;
    @Input() public toggleCollapse: ($event: any, type?: string) => void;
    @Input() public toggleCollapseId?: string;

    constructor() {}
    drag(ev: any, instruction: GradingInstruction) {
        ev.dataTransfer.setData('text', JSON.stringify(instruction));
    }
}
