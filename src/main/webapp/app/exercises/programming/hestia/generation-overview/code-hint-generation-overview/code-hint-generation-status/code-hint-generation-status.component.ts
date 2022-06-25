import { Component, EventEmitter, Input, Output } from '@angular/core';

// using this duplicated enumeration is a workaround, because importing the enum from the exporting component
// causes the problem that the whole template is not rendered
enum Copy {
    GIT_DIFF,
    COVERAGE,
    SOLUTION_ENTRIES,
    CODE_HINTS,
}
@Component({
    selector: 'jhi-code-hint-generation-status',
    templateUrl: './code-hint-generation-status.component.html',
    styleUrls: ['./code-hint-generation-status.component.scss'],
})
export class CodeHintGenerationStatusComponent {
    @Input()
    currentStep: Copy;

    @Input()
    isPerformedByStep: Map<Copy, boolean>;

    @Output()
    onStepChange = new EventEmitter<Copy>();

    readonly GenerationStep = Copy;

    constructor() {}

    onSelectStep(step: Copy) {
        this.onStepChange.emit(step);
    }
}
