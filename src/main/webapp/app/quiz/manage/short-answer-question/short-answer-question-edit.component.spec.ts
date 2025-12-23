import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FormsModule } from '@angular/forms';
import { ShortAnswerQuestion } from 'app/quiz/shared/entities/short-answer-question.model';
import { ShortAnswerQuestionEditComponent } from 'app/quiz/manage/short-answer-question/short-answer-question-edit.component';
import { QuizScoringInfoModalComponent } from 'app/quiz/manage/quiz-scoring-info-modal/quiz-scoring-info-modal.component';
import { MatchPercentageInfoModalComponent } from 'app/quiz/manage/match-percentage-info-modal/match-percentage-info-modal.component';
import { DragDropModule } from '@angular/cdk/drag-drop';
import { ShortAnswerSpot } from 'app/quiz/shared/entities/short-answer-spot.model';
import { ShortAnswerSolution } from 'app/quiz/shared/entities/short-answer-solution.model';
import { ShortAnswerMapping } from 'app/quiz/shared/entities/short-answer-mapping.model';
import { ScoringType } from 'app/quiz/shared/entities/quiz-question.model';
import { cloneDeep } from 'lodash-es';
import { ShortAnswerQuestionUtil } from 'app/quiz/shared/service/short-answer-question-util.service';
import * as markdownConversionUtil from 'app/shared/util/markdown.conversion.util';
import { NgbCollapse, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MockResizeObserver } from 'src/test/javascript/spec/helpers/mocks/service/mock-resize-observer';
import { MockTranslateService } from 'src/test/javascript/spec/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ThemeService } from 'app/core/theme/shared/theme.service';
import { MockThemeService } from 'src/test/javascript/spec/helpers/mocks/service/mock-theme.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

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

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockModule(FormsModule), MockModule(DragDropModule), MockDirective(NgbCollapse), FaIconComponent],
            declarations: [
                ShortAnswerQuestionEditComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(QuizScoringInfoModalComponent),
                MockComponent(MatchPercentageInfoModalComponent),
            ],
            providers: [
                MockProvider(NgbModal),
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: ThemeService, useClass: MockThemeService },
            ],
        })
            .overrideComponent(ShortAnswerQuestionEditComponent, {
                set: {
                    providers: [MockProvider(NgbModal)],
                },
            })
            .compileComponents();
        global.ResizeObserver = jest.fn().mockImplementation((callback: ResizeObserverCallback) => {
            return new MockResizeObserver(callback);
        });
        fixture = TestBed.createComponent(ShortAnswerQuestionEditComponent);
        component = fixture.componentInstance;
    });

    beforeEach(() => {
        component.shortAnswerQuestion = question;
        fixture.componentRef.setInput('questionIndex', 0);
        fixture.componentRef.setInput('reEvaluationInProgress', false);
        fixture.detectChanges();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize with different question texts', () => {
        // test spots concatenated to other words
        const newQuestion1 = new ShortAnswerQuestion();
        newQuestion1.text = 'This is a[-spot 12]regarding this question.\nAnother [-spot 8] is in the line above';
        fixture.componentRef.setInput('question', newQuestion1);
        fixture.detectChanges();

        let expectedTextParts = [
            ['This', 'is', 'a', '[-spot 12]', 'regarding', 'this', 'question.'],
            ['Another', '[-spot 8]', 'is', 'in', 'the', 'line', 'above'],
        ];
        expect(component.textParts).toEqual(expectedTextParts);

        // test a long method with multiple indentations and concatenated words
        const newQuestion2 = cloneDeep(component.question() as ShortAnswerQuestion);
        newQuestion2.text =
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
        fixture.componentRef.setInput('question', newQuestion2);
        fixture.detectChanges();

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
        expect(component.textParts).toEqual(expectedTextParts);

        // tests simple indentation
        const newQuestion3 = cloneDeep(component.question() as ShortAnswerQuestion);
        newQuestion3.text =
            '[-spot 5]\n' + '    [-spot 6]\n' + '        [-spot 7]\n' + '            [-spot 8]\n' + '                [-spot 9]\n' + '                    [-spot 10]';
        fixture.componentRef.setInput('question', newQuestion3);
        fixture.detectChanges();

        expectedTextParts = [['[-spot 5]'], ['    [-spot 6]'], ['        [-spot 7]'], ['            [-spot 8]'], ['                [-spot 9]'], ['                    [-spot 10]']];
        expect(component.textParts).toEqual(expectedTextParts);

        // classic java main method test
        const newQuestion4 = cloneDeep(component.question() as ShortAnswerQuestion);
        newQuestion4.text =
            '[-spot 1] class [-spot 2] {\n' +
            '    public static void main([-spot 3][] args){\n' +
            '        System.out.println("This is the [-spot 4] method");\n' +
            '    }\n' +
            '}';
        fixture.componentRef.setInput('question', newQuestion4);
        fixture.detectChanges();

        expectedTextParts = [
            ['[-spot 1]', 'class', '[-spot 2]', '{'],
            ['    public', 'static', 'void', 'main(', '[-spot 3]', '[]', 'args){'],
            ['        System.out.println("This', 'is', 'the', '[-spot 4]', 'method");'],
            ['    }'],
            ['}'],
        ];
        expect(component.textParts).toEqual(expectedTextParts);

        // test multiple line parameter for method header
        const newQuestion5 = cloneDeep(component.question() as ShortAnswerQuestion);
        newQuestion5.text =
            'private[-spot 1] methodCallWithMultipleLineParameter (\n' +
            '    int number,\n' +
            '    [-spot 2] secondNumber,\n' +
            '    [-spot 3] thirdString,\n' +
            '    boolean doesWork) {\n' +
            '        System.out.[-spot 4]("[-spot 5]");\n' +
            '}';
        fixture.componentRef.setInput('question', newQuestion5);
        fixture.detectChanges();

        expectedTextParts = [
            ['private', '[-spot 1]', 'methodCallWithMultipleLineParameter', '('],
            ['    int', 'number,'],
            ['    [-spot 2]', 'secondNumber,'],
            ['    [-spot 3]', 'thirdString,'],
            ['    boolean', 'doesWork)', '{'],
            ['        System.out.', '[-spot 4]', '("', '[-spot 5]', '");'],
            ['}'],
        ];
        expect(component.textParts).toEqual(expectedTextParts);

        // test nested arrays
        const newQuestion6 = cloneDeep(component.question() as ShortAnswerQuestion);
        newQuestion6.text =
            'const manyArrayFields = [\n' + "    ['test1'],\n" + "    ['test2'],\n" + "    ['[-spot 1]'],\n" + "    ['middleField'],\n" + "    ['[-spot 2]'],\n" + '];';
        fixture.componentRef.setInput('question', newQuestion6);
        fixture.detectChanges();

        expectedTextParts = [
            ['const', 'manyArrayFields', '=', '['],
            ["    ['test1'],"],
            ["    ['test2'],"],
            ["    ['", '[-spot 1]', "'],"],
            ["    ['middleField'],"],
            ["    ['", '[-spot 2]', "'],"],
            ['];'],
        ];
        expect(component.textParts).toEqual(expectedTextParts);

        // test textual enumeration
        const newQuestion7 = cloneDeep(component.question() as ShortAnswerQuestion);
        newQuestion7.text =
            'If we want a enumeration, we can also [-spot 1] this:\n' +
            '- first major point\n' +
            '    - first not so major point\n' +
            '    - second not so major point\n' +
            '- second major point\n' +
            '- third major point\n' +
            '        - first very not major point, super indented';
        fixture.componentRef.setInput('question', newQuestion7);
        fixture.detectChanges();

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

    it('should update shortAnswerQuestion and emit questionUpdated on question input change', () => {
        const emitSpy = jest.spyOn(component.questionUpdated, 'emit');

        expect(component.shortAnswerQuestion).toEqual(question);

        fixture.componentRef.setInput('question', question2);

        fixture.detectChanges();

        expect(component.shortAnswerQuestion).toEqual(question2);

        expect(emitSpy).toHaveBeenCalledTimes(0);

        fixture.componentRef.setInput('question', question);

        fixture.detectChanges();

        expect(component.shortAnswerQuestion).toEqual(question);

        expect(emitSpy).toHaveBeenCalledOnce();
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
        component.setQuestionEditorValue('[-spot 1] [-spot 2]');
        expect(component.numberOfSpot).toBe(3);
        expect(component.optionsWithID).toEqual(['[-option 0]', '[-option 1]']);
    });

    it('should open', () => {
        const content = {};
        const modalService = fixture.debugElement.injector.get(NgbModal);
        const modalSpy = jest.spyOn(modalService, 'open');
        component.open(content);
        expect(modalSpy).toHaveBeenCalledOnce();
    });

    it('should add spot to cursor and increase the spot number', () => {
        const questionUpdatedSpy = jest.spyOn(component.questionUpdated, 'emit');
        // Mock console methods to prevent test failures
        jest.spyOn(console, 'error').mockImplementation();
        jest.spyOn(console, 'warn').mockImplementation();

        component.addSpotAtCursor();

        expect(questionUpdatedSpy).toHaveBeenCalled();
        const text: string = component.questionEditorText;
        const firstLine = text.split('\n')[0];
        expect(firstLine).toInclude('[-spot 1]');
        expect(component.numberOfSpot).toBe(2);
    });

    it('should add option', () => {
        const questionUpdatedSpy = jest.spyOn(component.questionUpdated, 'emit');
        // Mock console methods to prevent test failures
        jest.spyOn(console, 'error').mockImplementation();
        jest.spyOn(console, 'warn').mockImplementation();

        component.addOption();

        expect(questionUpdatedSpy).toHaveBeenCalled();
        const text: string = component.questionEditorText;
        const lastLine = text.split('\n').last();
        expect(lastLine).toInclude(lastLine!);
    });

    it('should add text solution', () => {
        // Setup text
        component.questionEditorText = '';
        component.addOptionToSpot(1, shortAnswerSolution1.text!);
        component.addOptionToSpot(2, shortAnswerSolution2.text!);

        expect(component.shortAnswerQuestion.solutions).toHaveLength(2);
        component.shortAnswerQuestion.spots = [spot1, spot2];
        const mapping1 = new ShortAnswerMapping(spot1, shortAnswerSolution1);
        const mapping2 = new ShortAnswerMapping(spot2, shortAnswerSolution2);
        component.shortAnswerQuestion.correctMappings = [mapping1, mapping2];

        component.addTextSolution();
        expect(component.shortAnswerQuestion.solutions).toHaveLength(3);
    });

    it('should delete text solution', () => {
        component.shortAnswerQuestion.solutions = [shortAnswerSolution1, shortAnswerSolution2];
        component.deleteSolution(shortAnswerSolution1);
        expect(component.shortAnswerQuestion.solutions).toEqual([shortAnswerSolution2]);
    });

    it('should add spot at cursor visual mode', () => {
        const textParts = [['0'], ['0']];
        const shortAnswerQuestionUtil = TestBed.inject(ShortAnswerQuestionUtil);
        jest.spyOn(shortAnswerQuestionUtil, 'divideQuestionTextIntoTextParts').mockReturnValue(textParts);

        const node = {} as Node;

        const questionElement = {
            contains(other: Node | null): boolean {
                return true;
            },
        } as unknown as HTMLElement;
        const questionElementMock = { nativeElement: questionElement };
        jest.spyOn(component, 'questionElement').mockReturnValue(questionElementMock);

        const range = {
            cloneRange(): Range {
                return {
                    selectNodeContents(node1: Node) {},

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

            getRangeAt(index: number): Range {
                return range as Range;
            },
            toString() {
                return [];
            },
        } as unknown as Selection;
        jest.spyOn(window, 'getSelection').mockReturnValue(nodeValue);

        const returnHTMLDivElement = {
            appendChild(param: DocumentFragment) {
                return {} as DocumentFragment;
            },
            innerHTML: 'innerHTML',
        } as unknown as HTMLDivElement;
        jest.spyOn(document, 'createElement').mockReturnValue(returnHTMLDivElement);

        const markdownHelper = {
            length: 1,

            substring(start: number, end?: number): string {
                return '';
            },
        } as string;
        jest.spyOn(markdownConversionUtil, 'markdownForHtml').mockReturnValue(markdownHelper);
        const questionUpdated = jest.spyOn(component.questionUpdated, 'emit');

        component.shortAnswerQuestion.spots = [spot1, spot2];
        component.shortAnswerQuestion.correctMappings = [new ShortAnswerMapping(spot1, shortAnswerSolution1), new ShortAnswerMapping(spot2, shortAnswerSolution2)];
        fixture.componentRef.setInput('question', component.shortAnswerQuestion);
        fixture.changeDetectorRef.detectChanges();

        component.addSpotAtCursorVisualMode();

        expect(component.numberOfSpot).toBe(2);
        expect(questionUpdated).toHaveBeenCalledTimes(3);
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
        const backup = new ShortAnswerQuestion();
        component.backupQuestion = backup;
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
    });

    it('should set question text', () => {
        const text = 'This is a text for a test';
        const returnValue = { value: text } as unknown as HTMLElement;
        const getNavigationSpy = jest.spyOn(document, 'getElementById').mockReturnValue(returnValue);
        const array = ['0'];
        component.textParts = [array, array];
        fixture.changeDetectorRef.detectChanges();

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

    it('should return highest spot number', () => {
        expect(component.getHighestSpotNumbers(`[-spot 1]`)).toBe(1);
        expect(component.getHighestSpotNumbers(`hello [-spot 1] [-spot 3] [-spot 2]`)).toBe(3);
        expect(
            component.getHighestSpotNumbers(`hello
        [-spot 1] [-spot 3]
        [-spot 2]`),
        ).toBe(3);
        expect(component.getHighestSpotNumbers(`hello`)).toBe(0);
        expect(component.getHighestSpotNumbers(`[-spot ]]`)).toBe(0);
        expect(component.getHighestSpotNumbers(`[-spot x]]`)).toBe(0);
    });
});
