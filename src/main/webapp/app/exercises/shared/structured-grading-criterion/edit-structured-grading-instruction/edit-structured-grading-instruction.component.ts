import { Component, OnInit, ViewChild, Input } from '@angular/core';
import { Subscription } from 'rxjs/Subscription';
import { GradingCriterion } from 'app/exercises/shared/structured-grading-criterion/grading-criterion.model';
import { ActivatedRoute } from '@angular/router';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { Exercise } from 'app/entities/exercise.model';
import { KatexCommand } from 'app/shared/markdown-editor/commands/katex.command';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { GradingCriteriaCommand } from 'app/shared/markdown-editor/domainCommands/gradingCriteria.command';
import { InstructionCommand } from 'app/shared/markdown-editor/domainCommands/instruction.command';
import { UsageCountCommand } from 'app/shared/markdown-editor/domainCommands/usageCount.command';
import { CreditsCommand } from 'app/shared/markdown-editor/domainCommands/credits.command';
import { FeedbackCommand } from 'app/shared/markdown-editor/domainCommands/feedback.command';
import { DomainCommand } from 'app/shared/markdown-editor/domainCommands/domainCommand';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-instruction/grading-instruction.model';

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
    private instruction: GradingInstruction;
    @Input()
    exercise: Exercise;
    entity: GradingCriterion[];
    isLoading: boolean;

    katexCommand = new KatexCommand();
    gradingCriteriaCommand = new GradingCriteriaCommand();
    creditsCommand = new CreditsCommand();
    instructionCommand = new InstructionCommand();
    feedbackCommand = new FeedbackCommand();
    usageCountCommand = new UsageCountCommand();

    domainCommands: DomainCommand[] = [this.katexCommand, this.creditsCommand, this.instructionCommand, this.feedbackCommand, this.usageCountCommand, this.gradingCriteriaCommand];

    constructor(private route: ActivatedRoute, private exerciseService: ExerciseService) {}

    ngOnInit() {
        this.criteria = this.exercise.gradingCriteria;
        this.init();
    }

    private newEntity: GradingCriterion;

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
        for (const [text, command] of domainCommands) {
            if (command instanceof CreditsCommand) {
                this.instruction.credit = parseFloat(text);
            } else if (command instanceof InstructionCommand) {
                this.instruction.instructionDescription = text;
            } else if (command instanceof FeedbackCommand) {
                this.instruction.feedback = text;
            } else {
                this.instruction.usageCount = parseInt(text, 10);
            }
        }
    }
    /**
     * @function init
     * @desc Initializes local constants and prepares the QuizExercise entity
     */
    init(): void {
        if (this.criteria) {
            this.entity = this.criteria;
        } else {
            this.newEntity = new GradingCriterion();
            this.newEntity.title = 'test';
            this.entity.push(this.newEntity);
            this.exercise.gradingCriteria = this.entity;
        }
    }
    /**
     * @function addGradingCriteria
     * @desc Add an empty grading criteria to the exercise
     */
    addGradingCriteria() {
        if (typeof this.criteria === 'undefined') {
            this.criteria = this.entity;
        }
        this.newEntity = new GradingCriterion();
        this.newEntity.title = '';
        this.entity.push(this.newEntity);
        this.criteria = this.entity;
        this.exercise.gradingCriteria = this.criteria;
    }
    /**
     * @function deleteCriterion
     * @desc Remove criterion from the exercise grading instructions
     * @param criterionToDelete {GradingCriterion} the criterion to remove
     */
    deleteCriterion(criterionToDelete: GradingCriterion): void {
        this.exercise.gradingCriteria = this.exercise.gradingCriteria.filter(criterion => criterion !== criterionToDelete);
    }
}
