import { Component, OnInit, ViewChild, Input } from '@angular/core';
import { Subscription } from 'rxjs/Subscription';
import { CreditsCommand, DomainCommand, FeedbackCommand, GradingCriteriaCommand, InstructionCommand, UsageCountCommand } from 'app/markdown-editor/domainCommands';
import { KatexCommand } from 'app/markdown-editor/commands';
import { MarkdownEditorComponent } from 'app/markdown-editor';
import { GradingCriterion } from 'app/structured-grading-criterion/grading-criterion.model';
import { Exercise, ExerciseService } from 'app/entities/exercise';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-edit-structured-grading-instruction',
    templateUrl: './edit-structured-grading-instruction.component.html',
    styleUrls: ['./edit-structured-grading-instruction.scss'],
})
export class EditStructuredGradingInstructionComponent implements OnInit {
    paramSub: Subscription;
    /** Ace Editor configuration constants **/
    questionEditorText = '';
    @ViewChild('markdownEditor', { static: false })
    private markdownEditor: MarkdownEditorComponent;
    private criteria: GradingCriterion[];
    @Input()
    exercise: Exercise;
    isLoading: boolean;
    hasLoadedPendingSubmissions = false;

    katexCommand = new KatexCommand();
    gradingCriteriaCommand = new GradingCriteriaCommand();
    creditsCommand = new CreditsCommand();
    instructionCommand = new InstructionCommand();
    feedbackCommand = new FeedbackCommand();
    usageCountCommand = new UsageCountCommand();

    domainCommands: DomainCommand[] = [this.katexCommand, this.creditsCommand, this.instructionCommand, this.feedbackCommand, this.usageCountCommand, this.gradingCriteriaCommand];
    constructor(private route: ActivatedRoute, private exerciseService: ExerciseService) {}

    ngOnInit() {
        //  this.questionEditorText = this.generateMarkdown();
    }
    credits = new Array();
    instructions = new Array();
    feedback = new Array();
    usageCount = new Array();
    /**
     * @function generateMarkdown
     * @desc Generate the markdown text for this grading instruction

    generateMarkdown(): string {
        const markdownText =
            CreditsCommand.identifier +
            ' ' +
            CreditsCommand.text +
            '\n' +
            InstructionCommand.identifier +
            ' ' +
            InstructionCommand.text +
            '\n' +
            FeedbackCommand.identifier +
            ' ' +
            FeedbackCommand.text +
            '\n' +
            UsageCountCommand.identifier +
            ' ' +
            UsageCountCommand.text;
        return markdownText;
    }
     */

    prepareForSave(): void {
        this.markdownEditor.parse();
    }
    /**
     * @function domainCommandsFound
     * @desc 1. Gets a tuple of text and domainCommandIdentifiers and assigns text values according to the domainCommandIdentifiers
     *       2. The tupple order is the same as the order of the commands in the markdown text inserted by the user
     * @param domainCommands containing tuples of [text, domainCommandIdentifiers]
     */
    domainCommandsFound(domainCommands: [string, DomainCommand][]): void {
        this.credits = [];
        this.instructions = [];
        this.feedback = [];
        this.usageCount = [];
        for (const [text, command] of domainCommands) {
            if (command instanceof CreditsCommand) {
                this.credits.push(text);
            } else if (command instanceof InstructionCommand) {
                this.instructions.push(text);
            } else if (command instanceof FeedbackCommand) {
                this.feedback.push(text);
            } else {
                this.usageCount.push(text);
            }
        }
    }
}
