import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CodeHintGenerationStep } from 'app/entities/hestia/code-hint-model';

@Component({
    selector: 'jhi-code-hint-generation-status',
    templateUrl: './code-hint-generation-status.component.html',
    styleUrls: ['./code-hint-generation-status.component.scss'],
})
export class CodeHintGenerationStatusComponent {
    @Input()
    currentStep: CodeHintGenerationStep;

    @Input()
    isPerformedByStep: Map<CodeHintGenerationStep, boolean>;

    @Output()
    onStepChange = new EventEmitter<CodeHintGenerationStep>();

    readonly GenerationStep = CodeHintGenerationStep;

    constructor() {}

    onSelectStep(step: CodeHintGenerationStep) {
        this.onStepChange.emit(step);
    }
}
