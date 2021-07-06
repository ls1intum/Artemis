import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
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
                MockPipe(ArtemisTranslatePipe),
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
        component.shortAnswerQuestion = question;
        component.questionIndex = 0;
        component.reEvaluationInProgress = false;

        fixture.detectChanges();
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize with different question texts', () => {
        // test spots concatenated to other words
        component.shortAnswerQuestion.text = 'This is a[-spot 12]regarding this question.\nAnother [-spot 8] is in the line above';
        component.ngOnInit();

        let expectedTextParts = [
            ['This', 'is', 'a', '[-spot 12]', 'regarding', 'this', 'question.'],
            ['Another', '[-spot 8]', 'is', 'in', 'the', 'line', 'above'],
        ];
        expect(component.textParts).to.deep.equal(expectedTextParts);

        // test a long method with multiple indentations and concatenated words
        component.shortAnswerQuestion.text =
            'Enter your long question if needed\n\n' +
            'Select a part of the[-spot 6]and click on Add Spot to automatically [-spot 9]an input field and the corresponding[-spot 16]\n\n' +
            'You can define a input field like this: This [-spot 1] an [-spot 2] field.\n' +
            'Code snippets should be correctly indented:\n' +
            '[-spot 5] method testIndentation() {\n' +
            "    System.out.[-spot 3]('Print this');\n" +
            '    const [-spot 4] Array = [\n' +
            '    first element = ({\n' +
            '        firstAttribute : (\n' +
            '            we need more attributes\n' +
            '            )\n' +
            '    });\n' +
            '    ]\n' +
            '}\n' +
            '\n' +
            'To define the solution for the input fields you need to create a mapping (multiple mapping also possible):';

        component.ngOnInit();

        expectedTextParts = [
            ['Enter', 'your', 'long', 'question', 'if', 'needed'],
            [
                'Select',
                'a',
                'part',
                'of',
                'the',
                '[-spot 6]',
                'and',
                'click',
                'on',
                'Add',
                'Spot',
                'to',
                'automatically',
                '[-spot 9]',
                'an',
                'input',
                'field',
                'and',
                'the',
                'corresponding',
                '[-spot 16]',
            ],
            ['You', 'can', 'define', 'a', 'input', 'field', 'like', 'this:', 'This', '[-spot 1]', 'an', '[-spot 2]', 'field.'],
            ['Code', 'snippets', 'should', 'be', 'correctly', 'indented:'],
            ['[-spot 5]', 'method', 'testIndentation()', '{'],
            ['    System.out.', '[-spot 3]', "('Print", "this');"],
            ['    const', '[-spot 4]', 'Array', '=', '['],
            ['    first', 'element', '=', '({'],
            ['        firstAttribute', ':', '('],
            ['            we', 'need', 'more', 'attributes'],
            ['            )'],
            ['    });'],
            ['    ]'],
            ['}'],
            ['To', 'define', 'the', 'solution', 'for', 'the', 'input', 'fields', 'you', 'need', 'to', 'create', 'a', 'mapping', '(multiple', 'mapping', 'also', 'possible):'],
        ];
        expect(component.textParts).to.deep.equal(expectedTextParts);

        // tests simple indentation
        component.shortAnswerQuestion.text =
            '[-spot 5]\n' + '    [-spot 6]\n' + '        [-spot 7]\n' + '            [-spot 8]\n' + '                [-spot 9]\n' + '                    [-spot 10]';

        component.ngOnInit();

        expectedTextParts = [['[-spot 5]'], ['    [-spot 6]'], ['        [-spot 7]'], ['            [-spot 8]'], ['                [-spot 9]'], ['                    [-spot 10]']];
        expect(component.textParts).to.deep.equal(expectedTextParts);

        // classic java main method test
        component.shortAnswerQuestion.text =
            '[-spot 1] class [-spot 2] {\n' +
            '    public static void main([-spot 3][] args){\n' +
            '        System.out.println("This is the [-spot 4] method");\n' +
            '    }\n' +
            '}';

        component.ngOnInit();

        expectedTextParts = [
            ['[-spot 1]', 'class', '[-spot 2]', '{'],
            ['    public', 'static', 'void', 'main(', '[-spot 3]', '[]', 'args){'],
            ['        System.out.println("This', 'is', 'the', '[-spot 4]', 'method");'],
            ['    }'],
            ['}'],
        ];
        expect(component.textParts).to.deep.equal(expectedTextParts);

        // test multiple line parameter for method header
        component.shortAnswerQuestion.text =
            'private[-spot 1] methodCallWithMultipleLineParameter (\n' +
            '    int number,\n' +
            '    [-spot 2] secondNumber,\n' +
            '    [-spot 3] thirdString,\n' +
            '    boolean doesWork) {\n' +
            '        System.out.[-spot 4]("[-spot 5]");\n' +
            '}';

        component.ngOnInit();

        expectedTextParts = [
            ['private', '[-spot 1]', 'methodCallWithMultipleLineParameter', '('],
            ['    int', 'number,'],
            ['    [-spot 2]', 'secondNumber,'],
            ['    [-spot 3]', 'thirdString,'],
            ['    boolean', 'doesWork)', '{'],
            ['        System.out.', '[-spot 4]', '("', '[-spot 5]', '");'],
            ['}'],
        ];
        expect(component.textParts).to.deep.equal(expectedTextParts);

        // test nested arrays
        component.shortAnswerQuestion.text =
            'const manyArrayFields = [\n' + "    ['test1'],\n" + "    ['test2'],\n" + "    ['[-spot 1]'],\n" + "    ['middleField'],\n" + "    ['[-spot 2]'],\n" + '];';

        component.ngOnInit();

        expectedTextParts = [
            ['const', 'manyArrayFields', '=', '['],
            ["    ['test1'],"],
            ["    ['test2'],"],
            ["    ['", '[-spot 1]', "'],"],
            ["    ['middleField'],"],
            ["    ['", '[-spot 2]', "'],"],
            ['];'],
        ];
        expect(component.textParts).to.deep.equal(expectedTextParts);

        // test textual enumeration
        component.shortAnswerQuestion.text =
            'If we want a enumeration, we can also [-spot 1] this:\n' +
            '- first major point\n' +
            '    - first not so major point\n' +
            '    - second not so major point\n' +
            '- second major point\n' +
            '- third major point\n' +
            '        - first very not major point, super indented';

        component.ngOnInit();

        expectedTextParts = [
            ['If', 'we', 'want', 'a', 'enumeration,', 'we', 'can', 'also', '[-spot 1]', 'this:'],
            ['-', 'first', 'major', 'point'],
            ['    -', 'first', 'not', 'so', 'major', 'point'],
            ['    -', 'second', 'not', 'so', 'major', 'point'],
            ['-', 'second', 'major', 'point'],
            ['-', 'third', 'major', 'point'],
            ['        -', 'first', 'very', 'not', 'major', 'point,', 'super', 'indented'],
        ];
        expect(component.textParts).to.deep.equal(expectedTextParts);
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
        component.shortAnswerQuestion.correctMappings = [mapping1, mapping2, alternativeMapping];
        const event = { dragData: shortAnswerSolution1 };

        fixture.detectChanges();
        component.onDragDrop(spot, event);

        const expectedMapping = new ShortAnswerMapping(spot, shortAnswerSolution1);
        expect(component.shortAnswerQuestion.correctMappings.pop()).to.deep.equal(expectedMapping);
        expect(questionUpdatedSpy).to.have.been.calledOnce;
    });

    it('should setup question editor', () => {
        component.shortAnswerQuestion.spots = [spot1, spot2];
        const mapping1 = new ShortAnswerMapping(spot1, shortAnswerSolution1);
        const mapping2 = new ShortAnswerMapping(spot2, shortAnswerSolution2);
        component.shortAnswerQuestion.correctMappings = [mapping1, mapping2];

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
        component.shortAnswerQuestion.spots = [spot1, spot2];
        const mapping1 = new ShortAnswerMapping(spot1, shortAnswerSolution1);
        const mapping2 = new ShortAnswerMapping(spot2, shortAnswerSolution2);
        component.shortAnswerQuestion.correctMappings = [mapping1, mapping2];

        component.addTextSolution();

        expect(component.shortAnswerQuestion.solutions!.length).to.equal(3); // 2 -> 3

        component.deleteSolution(shortAnswerSolution2);

        expect(component.shortAnswerQuestion.solutions!.length).to.equal(2); // 3 -> 2
    });

    it('should add spot at cursor visual mode', () => {
        const textParts = [['0'], ['0']];
        const shortAnswerQuestionUtil = TestBed.inject(ShortAnswerQuestionUtil);
        spyOn(shortAnswerQuestionUtil, 'divideQuestionTextIntoTextParts').and.returnValue(textParts);

        const node = {} as Node;

        const returnValue = {
            // eslint-disable-next-line @typescript-eslint/no-unused-vars
            contains(other: Node | null): boolean {
                return true;
            },
        } as unknown as HTMLElement;
        stub(document, 'getElementById').returns(returnValue);

        const range = {
            cloneRange(): Range {
                return {
                    // eslint-disable-next-line @typescript-eslint/no-unused-vars
                    selectNodeContents(node1: Node) {},
                    // eslint-disable-next-line @typescript-eslint/no-unused-vars
                    setEnd(node2: Node, offset: number) {},
                    cloneContents() {},
                } as Range;
            },
            endContainer: {} as Node,
            endOffset: 0,
        } as Range;

        const nodeValue = {
            anchorNode: node,
            focusNode: {
                parentNode: {
                    parentElement: {
                        id: '0-0-0-0',
                        firstElementChild: {} as Element,
                    },
                },
            },
            // eslint-disable-next-line @typescript-eslint/no-unused-vars
            getRangeAt(index: number): Range {
                return range as Range;
            },
            toString() {
                return [];
            },
        } as unknown as Selection;
        stub(window, 'getSelection').returns(nodeValue);

        const returnHTMLDivElement = {
            // eslint-disable-next-line @typescript-eslint/no-unused-vars
            appendChild(param: DocumentFragment) {
                return {} as DocumentFragment;
            },
            innerHTML: 'innerHTML',
        } as unknown as HTMLDivElement;
        stub(document, 'createElement').returns(returnHTMLDivElement);

        const markdownHelper = {
            length: 1,
            // eslint-disable-next-line @typescript-eslint/no-unused-vars
            substring(start: number, end?: number): string {
                return '';
            },
        } as String;
        spyOn(artemisMarkdown, 'markdownForHtml').and.returnValue(markdownHelper);
        const questionUpdated = sinon.spy(component.questionUpdated, 'emit');

        component.shortAnswerQuestion.spots = [spot1, spot2];
        component.shortAnswerQuestion.correctMappings = [new ShortAnswerMapping(spot1, shortAnswerSolution1), new ShortAnswerMapping(spot2, shortAnswerSolution2)];
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
        component.shortAnswerQuestion.text = 'This is the text of a question';
        component.showVisualMode = false;
        component.shortAnswerQuestion.spots = [spot1, spot2];
        component.shortAnswerQuestion.correctMappings = [];
        let mapping = new ShortAnswerMapping(spot1, shortAnswerSolution1);
        component.shortAnswerQuestion.correctMappings.push(mapping);
        mapping = new ShortAnswerMapping(spot2, shortAnswerSolution2);
        component.shortAnswerQuestion.correctMappings.push(mapping);

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

        expect(component.shortAnswerQuestion.title).to.equal(backup.title);
        expect(component.shortAnswerQuestion.text).to.equal(backup.text);
    });

    it('should reset spot', () => {
        component.backupQuestion.spots = [spot1, spot2];
        const modifiedSpot = cloneDeep(spot1);
        modifiedSpot.spotNr = 10; // initial spotNr was 0
        component.shortAnswerQuestion.spots = [modifiedSpot, spot2];

        component.resetSpot(modifiedSpot);

        expect(component.shortAnswerQuestion.spots[0].spotNr).to.equal(0);
    });

    it('should delete spot', () => {
        component.shortAnswerQuestion.spots = [spot1, spot2];

        component.deleteSpot(spot1);

        expect(component.shortAnswerQuestion.spots.length).to.equal(1);
        expect(component.shortAnswerQuestion.spots[0]).to.deep.equal(spot2);
    });

    it('should set question text', () => {
        const text = 'This is a text for a test';
        const returnValue = { value: text } as unknown as HTMLElement;
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

        expect(component.shortAnswerQuestion.similarityValue).to.equal(100);
        expect(questionUpdated).to.be.calledOnce;
    });
});
