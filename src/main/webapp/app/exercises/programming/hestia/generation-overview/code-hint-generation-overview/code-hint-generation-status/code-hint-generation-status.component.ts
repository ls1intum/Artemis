import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { faCheck, faDotCircle } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-code-hint-generation-status',
    templateUrl: './code-hint-generation-status.component.html',
    styleUrls: ['./code-hint-generation-status.component.scss'],
})
export class CodeHintGenerationStatusComponent implements OnInit {
    @Input() currentStep: number;

    @Input() performedDiffStep: boolean;
    @Input() performedCoverageStep: boolean;
    @Input() performedSolutionEntryStep: boolean;
    @Input() performedCodeHintsStep: boolean;

    @Output() onStepChange = new EventEmitter<number>();

    faCheck = faCheck;
    faDotCircle = faDotCircle;

    constructor() {}

    ngOnInit(): void {}

    onSelectStep(index: number) {
        if (index === this.currentStep) {
            return;
        }

        let allowStepSelection;
        switch (index) {
            case 0:
                allowStepSelection = true;
                break;
            case 1:
                allowStepSelection = this.performedDiffStep;
                break;
            case 2:
                allowStepSelection = this.performedDiffStep && this.performedCoverageStep;
                break;
            case 3:
                allowStepSelection = this.performedDiffStep && this.performedCoverageStep && this.performedSolutionEntryStep;
                break;
            default:
                allowStepSelection = false;
                break;
        }
        if (allowStepSelection) {
            this.onStepChange.emit(index);
        }
    }
}
