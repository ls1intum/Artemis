import { Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges, ViewChild} from '@angular/core';
import { MultipleChoiceQuestion } from 'app/entities/multiple-choice-question';
import { AnswerOption } from 'app/entities/answer-option';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { MarkdownEditorComponent } from 'app/markdown-editor';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { HintCommand } from 'app/markdown-editor/specialCommands/hint.command';
import {
    CorrectOptionCommand,
    ExplanationCommand,
    IncorrectOptionCommand,
    SpecialCommand
} from 'app/markdown-editor/specialCommands';
import { EditQuizQuestion } from 'app/quiz/edit/edit-quiz-question.interface';

@Component({
    selector: 'jhi-edit-multiple-choice-question',
    templateUrl: './edit-multiple-choice-question.component.html',
    providers: [ArtemisMarkdown]
})
export class EditMultipleChoiceQuestionComponent implements OnInit, OnChanges, EditQuizQuestion {

    @ViewChild('markdownEditor')
    private markdownEditor: MarkdownEditorComponent;

    @Input()
    question: MultipleChoiceQuestion;
    @Input()
    questionIndex: number;

    @Output()
    questionUpdated = new EventEmitter();
    @Output()
    questionDeleted = new EventEmitter();

    /** Ace Editor configuration constants **/
    questionEditorText = '';

    /** Status boolean for collapse status **/
    isQuestionCollapsed: boolean;

    currentAnswerOption: AnswerOption;

    showPreview = false;

    hintCommand = new HintCommand();
    correctCommand = new CorrectOptionCommand();
    incorrectCommand = new IncorrectOptionCommand();
    explanationCommand = new ExplanationCommand();

    commandMultipleChoiceQuestions: SpecialCommand[] = [this.correctCommand, this.incorrectCommand, this.explanationCommand, this.hintCommand];

    constructor(private artemisMarkdown: ArtemisMarkdown, private modalService: NgbModal) {}

    ngOnInit(): void {
        this.questionEditorText = this.generateMarkdown();
    }

    /**
     * @function ngOnChanges
     * @desc Watch for any changes to the question model and notify listener
     * @param changes {SimpleChanges}
     */
    ngOnChanges(changes: SimpleChanges): void {
        /** Check if previousValue wasn't null to avoid firing at component initialization **/
        if (changes.question && changes.question.previousValue != null) {
            this.questionUpdated.emit();
        }
    }

    /**
     * @function generateMarkdown
     * @desc Generate the markdown text for this question
     * 1. First the question text, hint, and explanation are added using ArtemisMarkdown
     * 2. After an empty line, the answer options are added
     * 3. For each answer option: text, hint and explanation are added using ArtemisMarkdown
     */
    generateMarkdown(): string {
        const markdownText =
            this.artemisMarkdown.generateTextHintExplanation(this.question) +
            '\n\n' +
            this.question.answerOptions
                .map(
                    answerOption =>
                        (answerOption.isCorrect ? '[x]' : '[ ]') + ' ' + this.artemisMarkdown.generateTextHintExplanation(answerOption)
                )
                .join('\n');
        return markdownText;
    }

    /**
     * This function opens the modal for the help dialog.
     */
    open(content: any) {
        this.modalService.open(content, {size: 'lg'});
    }


    /**
     * @function togglePreview
     * @desc Toggles the preview in the template
     */
    togglePreview(showPreview: boolean): void {
        this.showPreview = showPreview;
        if (showPreview) {
            this.prepareForSave();
        }
    }

    prepareForSave(): void {
        this.cleanupQuestion();

        // Parse Markdown
        this.markdownEditor.parse();
        console.log('pare');
        this.questionUpdated.emit();
        console.log('emit');
    }

    private cleanupQuestion() {
        // Reset Question Object
        this.question.answerOptions = [];
        this.question.text = null;
        this.question.explanation = null;
        this.question.hint = null;
        this.question.hasCorrectOption = null;

        // Remove Current Answer Option
        this.currentAnswerOption = null;

    }

    changesInMarkdown(){
        console.log('ich werde aufgerufen');
        this.prepareForSave();
    }

    specialCommandFound(textLine: string, specialCommand: SpecialCommand) {

        if (specialCommand === null && textLine.length > 0) {
            this.question.text = textLine;
        }

        if (specialCommand instanceof CorrectOptionCommand || specialCommand instanceof IncorrectOptionCommand) {
            this.currentAnswerOption = new AnswerOption();
            if (specialCommand instanceof CorrectOptionCommand) {
                this.currentAnswerOption.isCorrect = true;
                this.question.hasCorrectOption = true;
            } else {
                this.currentAnswerOption.isCorrect = false;
            }
            this.currentAnswerOption.text = textLine;
            this.question.answerOptions.push(this.currentAnswerOption);
        } else if (specialCommand instanceof ExplanationCommand) {
            if (this.currentAnswerOption != null) {
                this.currentAnswerOption.explanation = textLine;
            } else {
                this.question.explanation = textLine;
            }
        } else if (specialCommand instanceof HintCommand) {
            if (this.currentAnswerOption != null) {
                this.currentAnswerOption.hint = textLine;
            } else {
                this.question.hint = textLine;
            }
        }

    }

    /**
     * @function deleteQuestion
     * @desc Delete this question from the quiz
     */
    deleteQuestion(): void {
        this.questionDeleted.emit();
    }
}
