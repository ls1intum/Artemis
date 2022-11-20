import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FormsModule } from '@angular/forms';
import { NgbCollapse, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { ShortAnswerQuestionEditComponent } from 'app/exercises/quiz/manage/short-answer-question/short-answer-question-edit.component';
import { QuizScoringInfoModalComponent } from 'app/exercises/quiz/manage/quiz-scoring-info-modal/quiz-scoring-info-modal.component';
import { MatchPercentageInfoModalComponent } from 'app/exercises/quiz/manage/match-percentage-info-modal/match-percentage-info-modal.component';
import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { DragDropModule } from '@angular/cdk/drag-drop';
import { SimpleChange } from '@angular/core';
import { ShortAnswerSpot, SpotType } from 'app/entities/quiz/short-answer-spot.model';
import { ShortAnswerSolution } from 'app/entities/quiz/short-answer-solution.model';
import { ShortAnswerMapping } from 'app/entities/quiz/short-answer-mapping.model';
import { ScoringType } from 'app/entities/quiz/quiz-question.model';
import { cloneDeep } from 'lodash-es';
import { ShortAnswerQuestionUtil } from 'app/exercises/quiz/shared/short-answer-question-util.service';
import * as markdownConversionUtil from 'app/shared/util/markdown.conversion.util';

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
spot2.type = SpotType.NUMBER;
spot2.spotNr = 1;

describe('ShortAnswerQuestionEditComponent', () => {
    let fixture: ComponentFixture<ShortAnswerQuestionEditComponent>;
    let component: ShortAnswerQuestionEditComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(FormsModule), AceEditorModule, MockModule(DragDropModule)],
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
    });

    beforeEach(() => {
        component.shortAnswerQuestion = question;
        component.questionIndex = 0;
        component.reEvaluationInProgress = false;

        fixture.detectChanges();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize with different question texts', () => {
        // test spots concatenated to other words
        component.shortAnswerQuestion.text = 'This is a[-spot 12]regarding this question.\nAnother [-spot-number 8] is in the line above';
        component.ngOnInit();

        let expectedTextParts = [
            ['This', 'is', 'a', '[-spot 12]', 'regarding', 'this', 'question.'],
            ['Another', '[-spot-number 8]', 'is', 'in', 'the', 'line', 'above'],
        ];
        expect(component.textParts).toEqual(expectedTextParts);

        // test a long method with multiple indentations and concatenated words
        component.shortAnswerQuestion.text =
            'Enter your long question if needed\n\n' +
            'Select a part of the[-spot 6]and click on Add Spot to automatically [-spot 9]an input field and the corresponding[-spot 16]\n\n' +
            'You can define a input field like this: This [-spot 1] an [-spot 2] field.\n' +
            'Code snippets should be correctly indented:\n' +
            '[-spot-number 5] method testIndentation() {\n' +
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
            ['[-spot-number 5]', 'method', 'testIndentation()', '{'],
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
        expect(component.textParts).toEqual(expectedTextParts);

        // tests simple indentation
        component.shortAnswerQuestion.text =
            '[-spot-number 5]\n' + '    [-spot-number 6]\n' + '        [-spot 7]\n' + '            [-spot 8]\n' + '                [-spot 9]\n' + '                    [-spot 10]';

        component.ngOnInit();

        expectedTextParts = [
            ['[-spot-number 5]'],
            ['    [-spot-number 6]'],
            ['        [-spot 7]'],
            ['            [-spot 8]'],
            ['                [-spot 9]'],
            ['                    [-spot 10]'],
        ];
        expect(component.textParts).toEqual(expectedTextParts);

        // classic java main method test
        component.shortAnswerQuestion.text =
            '[-spot 1] class [-spot 2] {\n' +
            '    public static void main([-spot 3][] args){\n' +
            '        System.out.println("This is the [-spot-number 4] method");\n' +
            '    }\n' +
            '}';

        component.ngOnInit();

        expectedTextParts = [
            ['[-spot 1]', 'class', '[-spot 2]', '{'],
            ['    public', 'static', 'void', 'main(', '[-spot 3]', '[]', 'args){'],
            ['        System.out.println("This', 'is', 'the', '[-spot-number 4]', 'method");'],
            ['    }'],
            ['}'],
        ];
        expect(component.textParts).toEqual(expectedTextParts);

        // test multiple line parameter for method header
        component.shortAnswerQuestion.text =
            'private[-spot 1] methodCallWithMultipleLineParameter (\n' +
            '    int number,\n' +
            '    [-spot-number 2] secondNumber,\n' +
            '    [-spot 3] thirdString,\n' +
            '    boolean doesWork) {\n' +
            '        System.out.[-spot 4]("[-spot 5]");\n' +
            '}';

        component.ngOnInit();

        expectedTextParts = [
            ['private', '[-spot 1]', 'methodCallWithMultipleLineParameter', '('],
            ['    int', 'number,'],
            ['    [-spot-number 2]', 'secondNumber,'],
            ['    [-spot 3]', 'thirdString,'],
            ['    boolean', 'doesWork)', '{'],
            ['        System.out.', '[-spot 4]', '("', '[-spot 5]', '");'],
            ['}'],
        ];
        expect(component.textParts).toEqual(expectedTextParts);

        // test nested arrays
        component.shortAnswerQuestion.text =
            'const manyArrayFields = [\n' + "    ['test1'],\n" + "    ['test2'],\n" + "    ['[-spot 1]'],\n" + "    ['middleField'],\n" + "    ['[-spot-number 2]'],\n" + '];';

        component.ngOnInit();

        expectedTextParts = [
            ['const', 'manyArrayFields', '=', '['],
            ["    ['test1'],"],
            ["    ['test2'],"],
            ["    ['", '[-spot 1]', "'],"],
            ["    ['middleField'],"],
            ["    ['", '[-spot-number 2]', "'],"],
            ['];'],
        ];
        expect(component.textParts).toEqual(expectedTextParts);

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
        expect(component.textParts).toEqual(expectedTextParts);
    });

    it('should invoke ngOnChanges', () => {
        component.ngOnChanges({
            question: { currentValue: question2, previousValue: question } as SimpleChange,
        });
    });

    it('should react to a solution being dropped on a spot', () => {
        const questionUpdatedSpy = jest.spyOn(component.questionUpdated, 'emit');
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
        const event = {
            item: {
                data: shortAnswerSolution1,
            },
        };

        fixture.detectChanges();
        component.onDragDrop(spot, event);

        const expectedMapping = new ShortAnswerMapping(spot, shortAnswerSolution1);
        expect(component.shortAnswerQuestion.correctMappings.pop()).toEqual(expectedMapping);
        expect(questionUpdatedSpy).toHaveBeenCalledOnce();
    });

    it('should setup question editor', () => {
        component.shortAnswerQuestion.spots = [spot1, spot2];
        const mapping1 = new ShortAnswerMapping(spot1, shortAnswerSolution1);
        const mapping2 = new ShortAnswerMapping(spot2, shortAnswerSolution2);
        component.shortAnswerQuestion.correctMappings = [mapping1, mapping2];

        fixture.detectChanges();

        component.setupQuestionEditor();
        expect(component.numberOfSpot).toBe(3);
        expect(component.optionsWithID).toEqual(['[-option 0]', '[-option 1]']);
    });

    it('should open', () => {
        const content = {};
        const modalService = TestBed.inject(NgbModal);
        const modalSpy = jest.spyOn(modalService, 'open');
        component.open(content);
        expect(modalSpy).toHaveBeenCalledOnce();
    });

    it('should add spot to cursor', () => {
        component.numberOfSpot = 2;

        component.addSpotAtCursor(SpotType.TEXT);
        expect(component.numberOfSpot).toBe(3);
        expect(component.firstPressed).toBe(2);

        component.addSpotAtCursor(SpotType.NUMBER);
        expect(component.numberOfSpot).toBe(4);
        expect(component.firstPressed).toBe(3);
    });

    it('should add option', () => {
        component.addOption();

        expect(component.firstPressed).toBe(2);
    });

    it('should add and delete text solution', () => {
        component.shortAnswerQuestion.spots = [spot1, spot2];
        const mapping1 = new ShortAnswerMapping(spot1, shortAnswerSolution1);
        const mapping2 = new ShortAnswerMapping(spot2, shortAnswerSolution2);
        component.shortAnswerQuestion.correctMappings = [mapping1, mapping2];

        component.addTextSolution();

        expect(component.shortAnswerQuestion.solutions).toHaveLength(3); // 2 -> 3

        component.deleteSolution(shortAnswerSolution2);

        expect(component.shortAnswerQuestion.solutions).toHaveLength(2); // 3 -> 2
    });

    it('should add spot at cursor visual mode - text selected', () => {
        const textParts = [['0'], ['0']];
        const shortAnswerQuestionUtil = TestBed.inject(ShortAnswerQuestionUtil);
        jest.spyOn(shortAnswerQuestionUtil, 'divideQuestionTextIntoTextParts').mockReturnValue(textParts);

        const node = {} as Node;

        const returnValue = {
            // eslint-disable-next-line @typescript-eslint/no-unused-vars
            contains(other: Node | null): boolean {
                return true;
            },
        } as unknown as HTMLElement;
        jest.spyOn(document, 'getElementById').mockReturnValue(returnValue);

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

        let nodeValue = {
            anchorNode: node,
            focusNode: {
                parentNode: {
                    parentElement: {
                        id: '0-0-0-0',
                        firstElementChild: {} as Element,
                    },
                    tagName: 'P',
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
        jest.spyOn(window, 'getSelection').mockReturnValue(nodeValue);

        const returnHTMLDivElement = {
            // eslint-disable-next-line @typescript-eslint/no-unused-vars
            appendChild(param: DocumentFragment) {
                return {} as DocumentFragment;
            },
            innerHTML: 'innerHTML',
        } as unknown as HTMLDivElement;
        jest.spyOn(document, 'createElement').mockReturnValue(returnHTMLDivElement);

        let markdownHelper = {
            length: 1,
            // eslint-disable-next-line @typescript-eslint/no-unused-vars
            substring(start: number, end?: number): string {
                return '';
            },
            // eslint-disable-next-line @typescript-eslint/no-unused-vars
            replace(pattern: string, replacement: string): string {
                return '';
            },
        } as string;
        jest.spyOn(markdownConversionUtil, 'markdownForHtml').mockReturnValue(markdownHelper);
        const questionUpdated = jest.spyOn(component.questionUpdated, 'emit');

        component.shortAnswerQuestion.spots = [spot1, spot2];
        component.shortAnswerQuestion.correctMappings = [new ShortAnswerMapping(spot1, shortAnswerSolution1), new ShortAnswerMapping(spot2, shortAnswerSolution2)];
        fixture.detectChanges();

        component.addSpotAtCursorVisualMode(SpotType.TEXT);

        expect(component.numberOfSpot).toBe(2);
        expect(component.firstPressed).toBe(2);
        expect(questionUpdated).toHaveBeenCalledTimes(3);

        nodeValue = {
            ...nodeValue,
            toString() {
                return '1.2345';
            },
        } as unknown as Selection;
        jest.spyOn(window, 'getSelection').mockReturnValue(nodeValue);

        markdownHelper = {
            length: 1,
            // eslint-disable-next-line @typescript-eslint/no-unused-vars
            substring(start: number, end?: number): string {
                return '1.2345';
            },
            // eslint-disable-next-line @typescript-eslint/no-unused-vars
            replace(pattern: string, replacement: string): string {
                return '1.2345';
            },
        } as string;
        jest.spyOn(markdownConversionUtil, 'markdownForHtml').mockReturnValue(markdownHelper);
        component.addSpotAtCursorVisualMode(SpotType.NUMBER);
        expect(component.numberOfSpot).toBe(3);
        expect(component.firstPressed).toBe(3);
    });

    it('should add spot at cursor visual mode - div container selected', () => {
        const textParts = [['0'], ['0']];
        const shortAnswerQuestionUtil = TestBed.inject(ShortAnswerQuestionUtil);
        jest.spyOn(shortAnswerQuestionUtil, 'divideQuestionTextIntoTextParts').mockReturnValue(textParts);

        const node = {} as Node;

        const returnValue = {
            // eslint-disable-next-line @typescript-eslint/no-unused-vars
            contains(other: Node | null): boolean {
                return true;
            },
        } as unknown as HTMLElement;
        jest.spyOn(document, 'getElementById').mockReturnValue(returnValue);

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
            setStart: (_: Node, __: number) => {},
            setEnd: (_: Node, __: number) => {},
        } as Range;

        let parentElement = {
            id: '0-0-0-0',
            firstElementChild: {} as Element,
        };
        let nodeValue = {
            anchorNode: node,
            focusNode: {
                parentNode: {
                    parentElement,
                    tagName: 'div',
                    children: [
                        {
                            children: [
                                {
                                    parentElement,
                                    childNodes: [node],
                                },
                            ],
                        },
                    ],
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
        jest.spyOn(window, 'getSelection').mockReturnValue(nodeValue);

        const returnHTMLDivElement = {
            // eslint-disable-next-line @typescript-eslint/no-unused-vars
            appendChild(param: DocumentFragment) {
                return {} as DocumentFragment;
            },
            innerHTML: 'innerHTML',
        } as unknown as HTMLDivElement;
        jest.spyOn(document, 'createElement').mockReturnValue(returnHTMLDivElement);

        jest.spyOn(document, 'createRange').mockReturnValue(range);

        let markdownHelper = {
            length: 1,
            // eslint-disable-next-line @typescript-eslint/no-unused-vars
            substring(start: number, end?: number): string {
                return '';
            },
            // eslint-disable-next-line @typescript-eslint/no-unused-vars
            replace(pattern: string, replacement: string): string {
                return '';
            },
        } as string;
        jest.spyOn(markdownConversionUtil, 'markdownForHtml').mockReturnValue(markdownHelper);
        const questionUpdated = jest.spyOn(component.questionUpdated, 'emit');

        component.shortAnswerQuestion.spots = [spot1, spot2];
        component.shortAnswerQuestion.correctMappings = [new ShortAnswerMapping(spot1, shortAnswerSolution1), new ShortAnswerMapping(spot2, shortAnswerSolution2)];
        fixture.detectChanges();

        component.addSpotAtCursorVisualMode(SpotType.TEXT);

        expect(component.numberOfSpot).toBe(2);
        expect(component.firstPressed).toBe(2);
        expect(questionUpdated).toHaveBeenCalledTimes(3);

        nodeValue = {
            ...nodeValue,
            toString() {
                return '1.2345';
            },
        } as unknown as Selection;
        jest.spyOn(window, 'getSelection').mockReturnValue(nodeValue);

        markdownHelper = {
            length: 1,
            // eslint-disable-next-line @typescript-eslint/no-unused-vars
            substring(start: number, end?: number): string {
                return '1.2345';
            },
            // eslint-disable-next-line @typescript-eslint/no-unused-vars
            replace(pattern: string, replacement: string): string {
                return '1.2345';
            },
        } as string;
        jest.spyOn(markdownConversionUtil, 'markdownForHtml').mockReturnValue(markdownHelper);
        component.addSpotAtCursorVisualMode(SpotType.NUMBER);
        expect(component.numberOfSpot).toBe(3);
        expect(component.firstPressed).toBe(3);
    });

    it('should delete question', () => {
        const questionDeleted = jest.spyOn(component.questionDeleted, 'emit');
        component.deleteQuestion();
        expect(questionDeleted).toHaveBeenCalledOnce();
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
        expect(component.textParts).toHaveLength(1);
        const firstElement = component.textParts.pop();
        expect(firstElement).toHaveLength(1);
        expect(firstElement).toEqual(['<p>This is the text of a question</p>']);
    });

    it('should move the question in different ways', () => {
        const eventUpSpy = jest.spyOn(component.questionMoveUp, 'emit');
        const eventDownSpy = jest.spyOn(component.questionMoveDown, 'emit');

        component.moveUp();
        component.moveDown();

        expect(eventUpSpy).toHaveBeenCalledOnce();
        expect(eventDownSpy).toHaveBeenCalledOnce();
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
        backup.hint = 'hint';
        component.backupQuestion = backup;

        component.resetQuestion();

        expect(component.shortAnswerQuestion.title).toBe(backup.title);
        expect(component.shortAnswerQuestion.text).toBe(backup.text);
    });

    it('should reset spot', () => {
        component.backupQuestion.spots = [spot1, spot2];
        const modifiedSpot = cloneDeep(spot1);
        modifiedSpot.spotNr = 10; // initial spotNr was 0
        component.shortAnswerQuestion.spots = [modifiedSpot, spot2];

        component.resetSpot(modifiedSpot);

        expect(component.shortAnswerQuestion.spots[0].spotNr).toBe(0);
    });

    it('should delete spot', () => {
        component.shortAnswerQuestion.spots = [spot1, spot2];

        component.deleteSpot(spot1);

        expect(component.shortAnswerQuestion.spots).toHaveLength(1);
        expect(component.shortAnswerQuestion.spots[0]).toEqual(spot2);

        component.deleteSpot(spot2);

        expect(component.shortAnswerQuestion.spots).toHaveLength(0);
    });

    it('should set question text', () => {
        const text = 'This is a text for a test';
        const returnValue = { value: text } as unknown as HTMLElement;
        const getNavigationSpy = jest.spyOn(document, 'getElementById').mockReturnValue(returnValue);
        const array = ['0'];
        component.textParts = [array, array];
        fixture.detectChanges();

        component.setQuestionText('0-0-0-0');

        expect(getNavigationSpy).toHaveBeenCalledOnce();
        const splitString = ['This', 'is', 'a', 'text', 'for', 'a', 'test'];
        expect(component.textParts.pop()).toEqual(splitString);
    });

    it('should toggle exact match', () => {
        const questionUpdated = jest.spyOn(component.questionUpdated, 'emit');

        component.toggleExactMatchCheckbox(true);

        expect(component.shortAnswerQuestion.similarityValue).toBe(100);
        expect(questionUpdated).toHaveBeenCalledOnce();
    });
});
