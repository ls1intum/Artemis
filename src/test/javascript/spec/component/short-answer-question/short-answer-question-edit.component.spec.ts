import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TranslatePipe } from '@ngx-translate/core';
import { FormsModule } from '@angular/forms';
import { NgbCollapse, NgbModal, NgbPopoverModule } from '@ng-bootstrap/ng-bootstrap';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { ShortAnswerQuestionEditComponent } from 'app/exercises/quiz/manage/short-answer-question/short-answer-question-edit.component';
import { QuizScoringInfoModalComponent } from 'app/exercises/quiz/manage/quiz-scoring-info-modal/quiz-scoring-info-modal.component';
import { MatchPercentageInfoModalComponent } from 'app/exercises/quiz/manage/match-percentage-info-modal/match-percentage-info-modal.component';
import { AceEditorModule } from 'ng2-ace-editor';
import { DndModule } from 'ng2-dnd';
import { SimpleChange } from '@angular/core';
import { ShortAnswerSpot } from 'app/entities/quiz/short-answer-spot.model';
import { ShortAnswerSolution } from 'app/entities/quiz/short-answer-solution.model';
import { ShortAnswerMapping } from 'app/entities/quiz/short-answer-mapping.model';

chai.use(sinonChai);
const expect = chai.expect;

const question = new ShortAnswerQuestion();
question.id = 1;
question.text = 'This is a text regarding this question';

const question2 = new ShortAnswerQuestion();
question2.id = 2;
question2.text = 'This is a second text regarding a question';

const shortAnswerSolution1 = new ShortAnswerSolution();
shortAnswerSolution1.id = 0;
shortAnswerSolution1.text = 'solution 1';
const shortAnswerSolution2 = new ShortAnswerSolution();
shortAnswerSolution2.id = 1;
shortAnswerSolution2.text = 'solution 2';

describe('ShortAnswerQuestionEditComponent', () => {
    let fixture: ComponentFixture<ShortAnswerQuestionEditComponent>;
    let component: ShortAnswerQuestionEditComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule, AceEditorModule, DndModule, NgbPopoverModule],
            declarations: [
                ShortAnswerQuestionEditComponent,
                MockPipe(TranslatePipe),
                MockComponent(QuizScoringInfoModalComponent),
                MockComponent(MatchPercentageInfoModalComponent),
                MockDirective(NgbCollapse),
            ],
            providers: [MockProvider(NgbModal)],
        }).compileComponents();
        fixture = TestBed.createComponent(ShortAnswerQuestionEditComponent);
        component = fixture.componentInstance;
    });

    beforeEach(() => {
        component.question = question;
        component.questionIndex = 0;
        component.reEvaluationInProgress = false;

        fixture.detectChanges();
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize', () => {
        expect(component).to.be.ok;
    });

    it('should invoke ngOnChanges', () => {
        component.ngOnChanges({
            question: { currentValue: question2, previousValue: question } as SimpleChange,
        });
    });

    it('should setup question editor', () => {
        const spot1 = new ShortAnswerSpot();
        spot1.spotNr = 0;
        const spot2 = new ShortAnswerSpot();
        spot2.spotNr = 1;
        component.question.solutions = [shortAnswerSolution1, shortAnswerSolution2];
        component.question.spots = [spot1, spot2];
        const mapping1 = new ShortAnswerMapping(spot1, shortAnswerSolution1);
        const mapping2 = new ShortAnswerMapping(spot2, shortAnswerSolution2);
        component.question.correctMappings = [mapping1, mapping2];

        fixture.detectChanges();

        component.setupQuestionEditor();
        expect(component.numberOfSpot).to.equal(3);
        expect(component.optionsWithID).to.deep.equal(['[-option 0]', '[-option 1]']);
    });

    it('should open', () => {
        const content = {};
        const modalService = TestBed.inject(NgbModal);
        const modalSpy = sinon.spy(modalService, 'open');
        component.open(content);
        expect(modalSpy).to.have.been.calledOnce;
    });

    it('should add spot to cursor', () => {
        component.numberOfSpot = 2;

        component.addSpotAtCursor();
        expect(component.numberOfSpot).to.equal(3);
        expect(component.firstPressed).to.equal(2);
    });

    it('should add option', () => {
        component.addOption();

        expect(component.firstPressed).to.equal(2);
    });

    it('should add spot at cursor visual mode', () => {
        const element = document.createElement('test');
        spyOn(document, 'getElementById').and.returnValue(element);

        component.addSpotAtCursorVisualMode();
        // TODO
    });

    it('should add and delete text solution', () => {
        const spot1 = new ShortAnswerSpot();
        spot1.spotNr = 0;
        const spot2 = new ShortAnswerSpot();
        spot2.spotNr = 1;
        component.question.solutions = [shortAnswerSolution1, shortAnswerSolution2];
        component.question.spots = [spot1, spot2];
        const mapping1 = new ShortAnswerMapping(spot1, shortAnswerSolution1);
        const mapping2 = new ShortAnswerMapping(spot2, shortAnswerSolution2);
        component.question.correctMappings = [mapping1, mapping2];

        component.addTextSolution();

        expect(component.question.solutions.length).to.equal(3);

        component.deleteSolution(shortAnswerSolution2);

        expect(component.question.solutions.length).to.equal(2);
    });

    it('should react to a solution being dropped on a spot', () => {
        const questionUpdatedSpy = sinon.spy(component.questionUpdated, 'emit');
        const solution = new ShortAnswerSolution();
        solution.id = 2;
        solution.text = 'solution text';
        const spot = new ShortAnswerSpot();
        spot.id = 2;
        spot.spotNr = 2;
        component.question.solutions = [solution];
        const alternativeMapping = new ShortAnswerMapping(new ShortAnswerSpot(), new ShortAnswerSolution());
        component.question.correctMappings = [alternativeMapping];
        const event = { dragData: solution };

        fixture.detectChanges();
        // component.onDragDrop(spot, event);

        const mapping = new ShortAnswerMapping(spot, solution);
        // expect(component.question.correctMappings.pop()).to.deep.equal(mapping);
        // expect(questionUpdatedSpy).to.have.been.calledOnce;
    });
});
