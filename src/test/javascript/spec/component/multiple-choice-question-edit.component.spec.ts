import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { QuizScoringInfoModalComponent } from 'app/exercises/quiz/manage/quiz-scoring-info-modal/quiz-scoring-info-modal.component';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { DragAndDropQuestionComponent } from 'app/exercises/quiz/shared/questions/drag-and-drop-question/drag-and-drop-question.component';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { TranslatePipe } from '@ngx-translate/core';
import { FormsModule } from '@angular/forms';
import { NgbCollapse, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { DndModule } from 'ng2-dnd';
import { ExplanationCommand } from 'app/shared/markdown-editor/domainCommands/explanation.command';
import { HintCommand } from 'app/shared/markdown-editor/domainCommands/hint.command';
import { MultipleChoiceQuestionEditComponent } from 'app/exercises/quiz/manage/multiple-choice-question/multiple-choice-question-edit.component';
import { ArtemisTestModule } from '../test.module';
import { MockNgbModalService } from '../helpers/mocks/service/mock-ngb-modal.service';
import { MultipleChoiceQuestionComponent } from 'app/exercises/quiz/shared/questions/multiple-choice-question/multiple-choice-question.component';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { By } from '@angular/platform-browser';
import { TestCaseCommand } from 'app/shared/markdown-editor/domainCommands/programming-exercise/testCase.command';
import { CorrectOptionCommand } from 'app/shared/markdown-editor/domainCommands/correctOptionCommand';
import { IncorrectOptionCommand } from 'app/shared/markdown-editor/domainCommands/incorrectOptionCommand';

chai.use(sinonChai);
const expect = chai.expect;

describe('MultipleChoiceQuestionEditComponent', () => {
    let fixture: ComponentFixture<MultipleChoiceQuestionEditComponent>;
    let component: MultipleChoiceQuestionEditComponent;

    const question: MultipleChoiceQuestion = {
        id: 1,
        text: 'some-text',
        hint: 'some-hint',
        explanation: 'some-explanation',
        answerOptions: [{ id: 1, explanation: 'answer-explanation', hint: 'answer-hint', text: 'answer-text' }],
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule, DndModule.forRoot()],
            declarations: [
                MultipleChoiceQuestionEditComponent,
                MockPipe(TranslatePipe),
                MockComponent(QuizScoringInfoModalComponent),
                MockComponent(MarkdownEditorComponent),
                MockComponent(SecuredImageComponent),
                MockComponent(DragAndDropQuestionComponent),
                MockDirective(NgbCollapse),
                MockComponent(MultipleChoiceQuestionComponent),
            ],
            providers: [{ provide: NgbModal, useClass: MockNgbModalService }],
        }).compileComponents();
        fixture = TestBed.createComponent(MultipleChoiceQuestionEditComponent);
        component = fixture.componentInstance;
        component.question = question;
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize with question markdown text', () => {
        fixture.detectChanges();
        expect(component).to.be.ok;
        expect(component.questionEditorText).eq(
            'some-text\n' + '\t[hint] some-hint\n' + '\t[exp] some-explanation\n' + '\n' + '[wrong] answer-text\n' + '\t[hint] answer-hint\n' + '\t[exp] answer-explanation',
        );
    });

    it('should parse answer options but not question titles ', () => {
        component.domainCommandsFound([
            ['text1', new TestCaseCommand()],
            ['text2', new CorrectOptionCommand()],
            ['text3', new IncorrectOptionCommand()],
            ['text4', new ExplanationCommand()],
            ['text5', new HintCommand()],
        ]);

        const expected: MultipleChoiceQuestion = {
            id: question.id,
            text: undefined,
            explanation: undefined,
            hint: undefined,
            hasCorrectOption: undefined,
            answerOptions: [
                {
                    isCorrect: true,
                    invalid: false,
                    text: 'text2',
                },
                {
                    isCorrect: false,
                    invalid: false,
                    text: 'text3',
                    explanation: 'text4',
                    hint: 'text5',
                },
            ],
        };

        expect(component.question).to.deep.equal(expected);
        expect(component.showMultipleChoiceQuestionPreview).to.be.true;
    });

    it('should parse answer options with question titles ', () => {
        component.domainCommandsFound([
            ['text1', new ExplanationCommand()],
            ['text2', new HintCommand()],
            ['text3', new TestCaseCommand()],
            ['text4', new CorrectOptionCommand()],
            ['text5', new IncorrectOptionCommand()],
        ]);

        const expected: MultipleChoiceQuestion = {
            id: question.id,
            text: undefined,
            hint: 'text2',
            explanation: 'text1',
            hasCorrectOption: undefined,
            answerOptions: [
                {
                    isCorrect: true,
                    invalid: false,
                    text: 'text4',
                },
                {
                    isCorrect: false,
                    invalid: false,
                    text: 'text5',
                },
            ],
        };

        expect(component.question).to.deep.equal(expected);
        expect(component.showMultipleChoiceQuestionPreview).to.be.true;
    });

    it('should find no domain commands', () => {
        component.domainCommandsFound([]);

        expectCleanupQuestion();
        expect(component.showMultipleChoiceQuestionPreview).to.be.true;
    });

    it('should prepare for save', () => {
        const spy = sinon.spy(component.questionUpdated, 'emit');

        fixture.detectChanges();
        component.prepareForSave();

        expectCleanupQuestion();
        expect(spy).to.have.been.called;
    });

    function expectCleanupQuestion() {
        expect(component.question.answerOptions?.length).to.eq(0);
        expect(component.question.text).to.be.undefined;
        expect(component.question.explanation).to.be.undefined;
        expect(component.question.hint).to.be.undefined;
        expect(component.question.hasCorrectOption).to.be.undefined;
    }

    it('should trigger delete button', () => {
        const spy = sinon.spy(component, 'deleteQuestion');
        const deleteButton = fixture.debugElement.query(By.css(`.delete-button`));
        deleteButton.nativeElement.click();
        expect(spy).to.have.been.called;
    });
});
