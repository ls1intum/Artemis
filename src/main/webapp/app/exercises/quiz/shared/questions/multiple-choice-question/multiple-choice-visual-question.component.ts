import { Component, Input, ViewEncapsulation } from '@angular/core';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { RenderedQuizQuestionMarkDownElement } from 'app/entities/quiz/quiz-question.model';
import { faExclamationCircle, faExclamationTriangle, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { faCheckSquare, faCircle, faDotCircle, faSquare } from '@fortawesome/free-regular-svg-icons';

@Component({
    selector: 'jhi-multiple-choice-visual-question',
    templateUrl: './multiple-choice-visual-question.component.html',
    styleUrls: ['./multiple-choice-question.component.scss', '../../../participate/quiz-participation.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class MultipleChoiceVisualQuestionComponent {
    _question: MultipleChoiceQuestion;

    @Input()
    set question(question: MultipleChoiceQuestion) {
        this._question = question;
        this.watchCollection();
    }
    get question(): MultipleChoiceQuestion {
        return this._question;
    }

    renderedQuestion: RenderedQuizQuestionMarkDownElement;

    // Icons
    faQuestionCircle = faQuestionCircle;
    faExclamationTriangle = faExclamationTriangle;
    faExclamationCircle = faExclamationCircle;
    faSquare = faSquare;
    faCheckSquare = faCheckSquare;
    faCircle = faCircle;
    faDotCircle = faDotCircle;

    constructor(private artemisMarkdown: ArtemisMarkdownService) {}

    watchCollection(): void {
        this.renderedQuestion = new RenderedQuizQuestionMarkDownElement();
        // this.renderedQuestion.hint = this.artemisMarkdown.safeHtmlForMarkdown(this.question.hint);
        // this.renderedQuestion.explanation = this.artemisMarkdown.safeHtmlForMarkdown(this.question.explanation);
        // this.renderedQuestion.renderedSubElements = this.question.answerOptions!.map((answerOption) => {
        //     const renderedAnswerOption = new RenderedQuizQuestionMarkDownElement();
        //     renderedAnswerOption.text = this.artemisMarkdown.safeHtmlForMarkdown(answerOption.text);
        //     renderedAnswerOption.hint = this.artemisMarkdown.safeHtmlForMarkdown(answerOption.hint);
        //     renderedAnswerOption.explanation = this.artemisMarkdown.safeHtmlForMarkdown(answerOption.explanation);
        //     return renderedAnswerOption;
        // });
    }

    parseQuestion() {
        console.log(this.question.text);
    }
}
