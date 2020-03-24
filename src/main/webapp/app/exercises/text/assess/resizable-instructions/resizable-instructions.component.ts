import { Component, Input } from '@angular/core';
import { GradingCriterion } from 'app/exercises/shared/structured-grading-criterion/grading-criterion.model';

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
    test() {
        console.log(this.criteria);
    }
}
