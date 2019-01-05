import { Component, AfterViewInit, ViewChild, Input, Output, EventEmitter, SimpleChanges, OnChanges } from '@angular/core';
import { AceEditorComponent } from 'ng2-ace-editor';
import 'brace/theme/chrome';
import 'brace/mode/markdown';
import { Command } from 'app/markdown-editor/commands/command';
import { BoldCommand } from 'app/markdown-editor/commands/bold.command';
import { ItalicCommand } from 'app/markdown-editor/commands/italic.command';
import { UnderlineCommand } from 'app/markdown-editor/commands/underline.command';
import { AnswerOption } from 'app/entities/answer-option';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { MultipleChoiceQuestion } from 'app/entities/multiple-choice-question';

@Component({
    selector: 'jhi-markdown-editor',
    styleUrls: ['./markdown-editor.scss'],
    providers: [ArtemisMarkdown],
    templateUrl: './markdown-editor.component.html'
})
export class MarkdownEditorComponent implements AfterViewInit, OnChanges {
    @ViewChild('aceEditor')
    aceEditorContainer: AceEditorComponent;

    @Input() defaultText: string;
    @Input() question: MultipleChoiceQuestion;

    @Output() changedText = new EventEmitter();

    questionEditorText = '';
    questionEditorAutoUpdate = true;

    commands: Command[] = [new BoldCommand(), new ItalicCommand(), new UnderlineCommand()];

    constructor(private artemisMarkdown: ArtemisMarkdown) {}

    ngAfterViewInit(): void {
        requestAnimationFrame(this.setupQuestionMarkdownEditor.bind(this));
    }

    ngOnChanges(changes: SimpleChanges): void {
        /** Check if previousValue wasn't null to avoid firing at component initialization **/
        if (changes.question && changes.question.previousValue != null) {
            this.changedText.emit();
        }
    }

    /**
     * @function setupQuestionEditor
     * @desc Initializes the ace editor for the mc question
     */

    /** Currently responsible for making the editor appear nicely**/
    setupQuestionMarkdownEditor(): void {
        this.aceEditorContainer.setTheme('chrome');
        this.aceEditorContainer.getEditor().renderer.setShowGutter(false);
        this.aceEditorContainer.getEditor().renderer.setPadding(10);
        this.aceEditorContainer.getEditor().renderer.setScrollMargin(8, 8);
        this.aceEditorContainer.getEditor().setHighlightActiveLine(false);
        this.aceEditorContainer.getEditor().setShowPrintMargin(false);
        this.aceEditorContainer.getEditor().clearSelection();
        this.defaultText;
        this.aceEditorContainer.getEditor().on(
            'blur',
            () => {
                this.parseMarkdown(this.defaultText);
                this.changedText.emit();
                console.log(this.changedText);
                console.log(this.defaultText);
            },
            this
        );
    }

    parseMarkdown(text: string): void {
        // First split by [], [ ], [x] and [X]
        const questionParts = text.split(/\[\]|\[ \]|\[x\]|\[X\]/g);
        const questionText = questionParts[0];

        let endOfPreviousPart = text.indexOf(questionText) + questionText.length;
        /**
         * Work on answer options
         * We slice the first questionPart since that's our question text and no real answer option
         */
        for (const answerOptionText of questionParts.slice(1)) {
            // Find the box (text in-between the parts)
            const answerOption = new AnswerOption();
            const startOfThisPart = text.indexOf(answerOptionText, endOfPreviousPart);
            const box = text.substring(endOfPreviousPart, startOfThisPart);
            // Check if box says this answer option is correct or not
            answerOption.isCorrect = box === '[x]' || box === '[X]';
            // Update endOfPreviousPart for next loop
            endOfPreviousPart = startOfThisPart + answerOptionText.length;

            // Parse this answerOption
            this.artemisMarkdown.parseTextHintExplanation(answerOptionText, answerOption);
        }
    }
}
