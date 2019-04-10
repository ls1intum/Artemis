import { Component, Input } from '@angular/core';

@Component({
    selector: 'jhi-resizable-instructions',
    templateUrl: './resizable-instructions.component.html',
    styleUrls: ['./resizable-instructions.component.scss'],
})
export class ResizableInstructionsComponent {
    @Input() public formattedProblemStatement: string;
    @Input() public formattedSampleSolution: string;
    @Input() public formattedGradingInstructions: string;
    @Input() public toggleCollapse: ($event: any, type?: string) => void;
    @Input() public toggleCollapseId?: string;

    constructor() {}
}
