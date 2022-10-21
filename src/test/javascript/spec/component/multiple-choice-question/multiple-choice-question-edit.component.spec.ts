import { DragDropModule } from '@angular/cdk/drag-drop';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { NgbCollapse, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { MultipleChoiceQuestionEditComponent } from 'app/exercises/quiz/manage/multiple-choice-question/multiple-choice-question-edit.component';
import { QuizScoringInfoModalComponent } from 'app/exercises/quiz/manage/quiz-scoring-info-modal/quiz-scoring-info-modal.component';
import { DragAndDropQuestionComponent } from 'app/exercises/quiz/shared/questions/drag-and-drop-question/drag-and-drop-question.component';
import { MultipleChoiceQuestionComponent } from 'app/exercises/quiz/shared/questions/multiple-choice-question/multiple-choice-question.component';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { CorrectOptionCommand } from 'app/shared/markdown-editor/domainCommands/correctOptionCommand';
import { ExplanationCommand } from 'app/shared/markdown-editor/domainCommands/explanation.command';
import { HintCommand } from 'app/shared/markdown-editor/domainCommands/hint.command';
import { IncorrectOptionCommand } from 'app/shared/markdown-editor/domainCommands/incorrectOptionCommand';
import { TestCaseCommand } from 'app/shared/markdown-editor/domainCommands/programming-exercise/testCase.command';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { ArtemisTestModule } from '../../test.module';

describe('MultipleChoiceQuestionEditComponent', () => {
    let fixture: ComponentFixture<MultipleChoiceQuestionEditComponent>;
    let component: MultipleChoiceQuestionEditComponent;

    const question: MultipleChoiceQuestion = {
        exportQuiz: false,
        randomizeOrder: true,
        id: 1,
        text: 'some-text',
        hint: 'some-hint',
        explanation: 'some-explanation',
        invalid: false,
        answerOptions: [{ id: 1, explanation: 'answer-explanation', hint: 'answer-hint', text: 'answer-text', invalid: false }],
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule, DragDropModule],
            declarations: [
                MultipleChoiceQuestionEditComponent,
                MockPipe(ArtemisTranslatePipe),
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

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize with question markdown text', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
        expect(component.questionEditorText).toEqual(
            'some-text\n' + '\t[hint] some-hint\n' + '\t[exp] some-explanation\n' + '\n' + '[wrong] answer-text\n' + '\t[hint] answer-hint\n' + '\t[exp] answer-explanation',
        );
    });

    it('should parse answer options but not question titles', () => {
        component.domainCommandsFound([
            ['text1', new TestCaseCommand()],
            ['text2', new CorrectOptionCommand()],
            ['text3', new IncorrectOptionCommand()],
            ['text4', new ExplanationCommand()],
            ['text5', new HintCommand()],
        ]);

        const expected: MultipleChoiceQuestion = {
            id: question.id,
            exportQuiz: false,
            randomizeOrder: true,
            invalid: false,
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

        expect(component.question).toEqual(expected);
        expect(component.showMultipleChoiceQuestionPreview).toBeTrue();
    });

    it('should parse answer options with question titles', () => {
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
            exportQuiz: false,
            randomizeOrder: true,
            invalid: false,
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

        expect(component.question).toEqual(expected);
        expect(component.showMultipleChoiceQuestionPreview).toBeTrue();
    });

    it('should find no domain commands', () => {
        component.domainCommandsFound([]);

        expectCleanupQuestion();
        expect(component.showMultipleChoiceQuestionPreview).toBeTrue();
    });

    it('should detect changes in markdown', () => {
        const spy = jest.spyOn(component.questionUpdated, 'emit');

        fixture.detectChanges();
        component.changesInMarkdown();

        expectCleanupQuestion();
        expect(spy).toHaveBeenCalledOnce();
    });

    function expectCleanupQuestion() {
        expect(component.question.answerOptions).toHaveLength(0);
        expect(component.question.text).toBeUndefined();
        expect(component.question.explanation).toBeUndefined();
        expect(component.question.hint).toBeUndefined();
        expect(component.question.hasCorrectOption).toBeUndefined();
    }

    it('should trigger delete button', () => {
        const spy = jest.spyOn(component, 'deleteQuestion');
        const deleteButton = fixture.debugElement.query(By.css(`.delete-button`));
        deleteButton.nativeElement.click();
        expect(spy).toHaveBeenCalledOnce();
    });
});
