import { Component, OnInit } from '@angular/core';
import { CreditsCommand, DomainCommand, FeedbackCommand, InstructionCommand, UsageCountCommand } from 'app/markdown-editor/domainCommands';
import { KatexCommand } from 'app/markdown-editor/commands';

@Component({
    selector: 'jhi-structured-grading-instructions',
    templateUrl: './structured-grading-instructions.component.html',
    styles: [],
})
export class StructuredGradingInstructionsComponent implements OnInit {
    katexCommand = new KatexCommand();
    creditsCommand = new CreditsCommand();
    instructionCommand = new InstructionCommand();
    feedbackCommand = new FeedbackCommand();
    usageCount = new UsageCountCommand();

    domainCommands: DomainCommand[] = [this.katexCommand, this.creditsCommand, this.instructionCommand, this.feedbackCommand, this.usageCount];
    constructor() {}

    ngOnInit() {}
    showStructuredGradingInstructionsPreview = true;
}
