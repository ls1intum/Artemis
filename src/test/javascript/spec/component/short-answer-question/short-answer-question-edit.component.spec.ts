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
import { ScoringType } from 'app/entities/quiz/quiz-question.model';
import { cloneDeep } from 'lodash';
import { stub } from 'sinon';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { ShortAnswerQuestionUtil } from 'app/exercises/quiz/shared/short-answer-question-util.service';

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

question.solutions = [shortAnswerSolution1, shortAnswerSolution2];

const spot1 = new ShortAnswerSpot();
spot1.spotNr = 0;

const spot2 = new ShortAnswerSpot();
spot2.spotNr = 1;

describe('ShortAnswerQuestionEditComponent', () => {
    let fixture: ComponentFixture<ShortAnswerQuestionEditComponent>;
    let component: ShortAnswerQuestionEditComponent;
    let artemisMarkdown: ArtemisMarkdownService;

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
        artemisMarkdown = TestBed.inject(ArtemisMarkdownService);
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

    it('should react to a solution being dropped on a spot', () => {
        const questionUpdatedSpy = sinon.spy(component.questionUpdated, 'emit');
        const solution = new ShortAnswerSolution();
        solution.id = 2;
        solution.text = 'solution text';
        const spot = new ShortAnswerSpot();
        spot.id = 2;
        spot.spotNr = 2;
        const spot3 = new ShortAnswerSpot();
        const mapping1 = new ShortAnswerMapping(spot2, shortAnswerSolution1);
        const mapping2 = new ShortAnswerMapping(spot3, shortAnswerSolution1);
        const alternativeMapping = new ShortAnswerMapping(new ShortAnswerSpot(), new ShortAnswerSolution());
        component.question.correctMappings = [mapping1, mapping2, alternativeMapping];
        const event = { dragData: shortAnswerSolution1 };

        fixture.detectChanges();
        component.onDragDrop(spot, event);

        const expectedMapping = new ShortAnswerMapping(spot, shortAnswerSolution1);
        expect(component.question.correctMappings.pop()).to.deep.equal(expectedMapping);
        expect(questionUpdatedSpy).to.have.been.calledOnce;
    });

    it('should setup question editor', () => {
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

    it('should add and delete text solution', () => {
        component.question.spots = [spot1, spot2];
        const mapping1 = new ShortAnswerMapping(spot1, shortAnswerSolution1);
        const mapping2 = new ShortAnswerMapping(spot2, shortAnswerSolution2);
        component.question.correctMappings = [mapping1, mapping2];

        component.addTextSolution();

        expect(component.question.solutions!.length).to.equal(3); // 2 -> 3

        component.deleteSolution(shortAnswerSolution2);

        expect(component.question.solutions!.length).to.equal(2); // 3 -> 2
    });

    it('should add spot at cursor visual mode', () => {
        const textParts = [['0'], ['0']];
        const shortAnswerQuestionUtil = TestBed.inject(ShortAnswerQuestionUtil);
        spyOn(shortAnswerQuestionUtil, 'divideQuestionTextIntoTextParts').and.returnValue(textParts);

        const node = {} as Node;

        const returnValue = ({
            // tslint:disable-next-line:no-unused-variable
            contains(_other: Node | null): boolean {
                return true;
            },
        } as unknown) as HTMLElement;
        stub(document, 'getElementById').returns(returnValue);

        const range = {
            cloneRange(): Range {
                return {
                    selectNodeContents(_node1: Node) {},
                    setEnd(_node2: Node, _offset: number) {},
                    cloneContents() {},
                } as Range;
            },
            endContainer: {} as Node,
            endOffset: 0,
        } as Range;

        const nodeValue = ({
            anchorNode: node,
            focusNode: {
                parentNode: {
                    parentElement: {
                        id: '0-0-0-0',
                        firstElementChild: {} as Element,
                    },
                },
            },
            getRangeAt(_index: number): Range {
                return range as Range;
            },
            toString() {
                return [];
            },
        } as unknown) as Selection;
        stub(window, 'getSelection').returns(nodeValue);

        const returnHTMLDivElement = ({
            appendChild(_param: DocumentFragment) {
                return {} as DocumentFragment;
            },
            innerHTML: 'innerHTML',
        } as unknown) as HTMLDivElement;
        stub(document, 'createElement').returns(returnHTMLDivElement);

        const markdownHelper = {
            length: 1,
            substring(_start: number, _end?: number): string {
                return '';
            },
        } as String;
        spyOn(artemisMarkdown, 'markdownForHtml').and.returnValue(markdownHelper);
        const questionUpdated = sinon.spy(component.questionUpdated, 'emit');

        component.question.spots = [spot1, spot2];
        component.question.correctMappings = [new ShortAnswerMapping(spot1, shortAnswerSolution1), new ShortAnswerMapping(spot2, shortAnswerSolution2)];
        fixture.detectChanges();

        component.addSpotAtCursorVisualMode();

        expect(component.numberOfSpot).to.equal(2);
        expect(component.firstPressed).to.equal(2);
        expect(questionUpdated).to.be.calledThrice;
    });

    it('should delete question', () => {
        const questionDeleted = sinon.spy(component.questionDeleted, 'emit');
        component.deleteQuestion();
        expect(questionDeleted).to.have.been.calledOnce;
    });

    it('should toggle preview', () => {
        component.question.text = 'This is the text of a question';
        component.showVisualMode = false;
        component.question.spots = [spot1, spot2];
        component.question.correctMappings = [];
        let mapping = new ShortAnswerMapping(spot1, shortAnswerSolution1);
        component.question.correctMappings.push(mapping);
        mapping = new ShortAnswerMapping(spot2, shortAnswerSolution2);
        component.question.correctMappings.push(mapping);

        component.togglePreview();
        expect(component.textParts.length).to.equal(1);
        const firstElement = component.textParts.pop();
        expect(firstElement!.length).to.equal(1);
        expect(firstElement).to.deep.equal(['<p>This is the text of a question</p>']);
    });

    it('should move the question in different ways', () => {
        const eventUpSpy = sinon.spy(component.questionMoveUp, 'emit');
        const eventDownSpy = sinon.spy(component.questionMoveDown, 'emit');

        component.moveUp();
        component.moveDown();

        expect(eventUpSpy).to.be.calledOnce;
        expect(eventDownSpy).to.be.calledOnce;
    });

    it('should reset the question', () => {
        const backup = new ShortAnswerQuestion();
        backup.title = 'backupQuestion';
        backup.invalid = false;
        backup.randomizeOrder = false;
        backup.scoringType = ScoringType.ALL_OR_NOTHING;
        backup.solutions = [];
        backup.correctMappings = [];
        backup.spots = [];
        backup.text = 'This is the text of a backup question';
        backup.explanation = 'I dont know';
        backup.hint = 'hinty';
        component.backupQuestion = backup;

        component.resetQuestion();

        expect(component.question.title).to.equal(backup.title);
        expect(component.question.text).to.equal(backup.text);
    });

    it('should reset spot', () => {
        component.backupQuestion.spots = [spot1, spot2];
        const modifiedSpot = cloneDeep(spot1);
        modifiedSpot.spotNr = 10; // initial spotNr was 0
        component.question.spots = [modifiedSpot, spot2];

        component.resetSpot(modifiedSpot);

        expect(component.question.spots[0].spotNr).to.equal(0);
    });

    it('should delete spot', () => {
        component.question.spots = [spot1, spot2];

        component.deleteSpot(spot1);

        expect(component.question.spots.length).to.equal(1);
        expect(component.question.spots[0]).to.deep.equal(spot2);
    });

    it('should set question text', () => {
        const text = 'This is a text for a test';
        const returnValue = ({ value: text } as unknown) as HTMLElement;
        const getNavigationStub = stub(document, 'getElementById').returns(returnValue);
        const array = ['0'];
        component.textParts = [array, array];
        fixture.detectChanges();

        component.setQuestionText('0-0-0-0');

        expect(getNavigationStub).to.have.been.calledOnce;
        const splitString = ['This', 'is', 'a', 'text', 'for', 'a', 'test'];
        expect(component.textParts.pop()).to.deep.equal(splitString);
    });

    it('should toggle exact match', () => {
        const questionUpdated = sinon.spy(component.questionUpdated, 'emit');

        component.toggleExactMatchCheckbox(true);

        expect(component.question.similarityValue).to.equal(100);
        expect(questionUpdated).to.be.calledOnce;
    });
});
