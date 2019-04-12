import {
    Component,
    ElementRef,
    EventEmitter,
    Input,
    Output,
    OnDestroy,
    Renderer2,
    SimpleChanges,
    Injector,
    ComponentFactoryResolver,
    ApplicationRef,
    EmbeddedViewRef,
    OnChanges,
} from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import * as Remarkable from 'remarkable';
import { faCheckCircle, faTimesCircle } from '@fortawesome/free-regular-svg-icons';
import * as moment from 'moment';

import { CodeEditorService } from '../../code-editor/code-editor.service';
import { EditorInstructionsResultDetailComponent } from '../../code-editor/instructions/code-editor-instructions-result-detail';
import { Feedback } from '../feedback';
import { Result, ResultService } from '../result';
import { ProgrammingExercise } from './programming-exercise.model';
import { RepositoryFileService } from '../repository';
import { Participation } from '../participation';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { JhiWebsocketService, AccountService } from 'app/core';

type Step = {
    title: string;
    done: boolean;
};

@Component({
    selector: 'jhi-programming-exercise-instructions',
    templateUrl: './programming-exercise-instruction.component.html',
})
export class ProgrammingExerciseInstructionComponent implements OnChanges, OnDestroy {
    private markdown: Remarkable;

    @Input()
    public exercise: ProgrammingExercise;
    @Input()
    public participation: Participation;
    // If true, shows the participation of the exercise's template, instead of the assignment participation
    @Input()
    private showTemplatePartipation = false;
    // Emits an event, if this component loads a readme file from a student's git repository.
    // This is a workaround, see the comments on loadInstructions for more info.
    @Output()
    public onInstructionLoad = new EventEmitter();

    private websocketChannelResults: string;

    public isInitial = true;
    public isLoading = true;
    private latestResult: Result;
    private resultDetails: Feedback[];
    public steps: Array<Step> = [];
    public renderedMarkdown: string;
    // Can be used to remove the click listeners for result details
    private listenerRemoveFunctions: Function[] = [];

    constructor(
        private editorService: CodeEditorService,
        private translateService: TranslateService,
        private resultService: ResultService,
        private repositoryFileService: RepositoryFileService,
        private jhiWebsocketService: JhiWebsocketService,
        private accountService: AccountService,
        private renderer: Renderer2,
        private elementRef: ElementRef,
        private modalService: NgbModal,
        private componentFactoryResolver: ComponentFactoryResolver,
        private appRef: ApplicationRef,
        private injector: Injector,
    ) {
        this.markdown = new Remarkable();
        this.markdown.inline.ruler.before('text', 'testsStatus', this.remarkableTestsStatusParser.bind(this), {});
        this.markdown.block.ruler.before('paragraph', 'plantUml', this.remarkablePlantUmlParser.bind(this), {});
        this.markdown.renderer.rules['testsStatus'] = this.remarkableTestsStatusRenderer.bind(this);
        this.markdown.renderer.rules['plantUml'] = this.remarkablePlantUmlRenderer.bind(this);
    }

    public ngOnChanges(changes: SimpleChanges) {
        // If the participation changes, set component to initial as everything needs to be reloaded now
        if (this.participation && changes.participation && changes.participation.currentValue && this.participation.id !== changes.participation.currentValue.id) {
            this.isInitial = true;
        }
        // Only load instructions, details etc. if the participation and exercise are available
        if (this.participation && this.exercise && (changes.participation || (changes.exercise && changes.exercise.currentValue && changes.exercise.firstChange))) {
            this.loadInstructions()
                .catch(() => {
                    this.exercise.problemStatement = '';
                })
                .then(() => this.setupResultWebsocket())
                .then(() => this.isInitial && this.loadInitialResult());
        }
    }

    /**
     * Setup result websocket so that the instructions can use the latest result.
     * When a new result is received, the result details will be loaded and then the instructions will be rerendered.
     */
    setupResultWebsocket() {
        this.accountService.identity().then(() => {
            this.websocketChannelResults = `/topic/participation/${this.participation.id}/newResults`;
            this.jhiWebsocketService.subscribe(this.websocketChannelResults);
            this.jhiWebsocketService.receive(this.websocketChannelResults).subscribe((newResult: Result) => {
                // convert json string to moment
                console.log('Received new result ' + newResult.id + ': ' + newResult.resultString);
                newResult.completionDate = newResult.completionDate != null ? moment(newResult.completionDate) : null;
                this.handleNewResult(newResult);
            });
        });
    }

    /**
     * This method is used for initially loading the results so that the instructions can be rendered.
     */
    async loadInitialResult() {
        return new Promise(resolve => {
            if (this.participation && this.participation.results) {
                // Get the result with the highest id (most recent result)
                resolve(this.participation.results.reduce((acc, v) => (v.id > acc.id ? v : acc)));
            } else if (this.exercise && this.exercise.id) {
                // Only load results if the exercise already is in our database, otherwise there can be no build result anyway
                return this.loadLatestResult();
            }
        }).then((result: Result) => {
            this.isInitial = false;
            this.handleNewResult(result);
        });
    }

    /**
     * If a new result is received, this method is triggered.
     * It only reacts if this is the first result received or the result is new.
     * @param latestResult
     */
    handleNewResult(newResult: Result) {
        // If the same result comes again, don't handle it
        if (!this.latestResult || newResult.id !== this.latestResult.id) {
            this.latestResult = newResult;
            this.loadResultsDetails().then(() => this.updateMarkdown());
        }
    }

    /**
     * Reset and then render the markdown of the instruction file.
     */
    updateMarkdown() {
        this.steps = [];
        this.renderedMarkdown = this.markdown.render(this.exercise.problemStatement);
        // For whatever reason, we have to wait a tick here. The markdown parser should be synchronous...
        setTimeout(() => {
            this.setUpClickListeners();
            this.setUpTaskIcons();
        }, 500);
        this.isLoading = false;
    }

    /**
     * Retrieve latest result for the participation/exercise/course combination.
     */
    loadLatestResult(): Promise<Result> {
        return new Promise((resolve, reject) => {
            this.resultService.findResultsForParticipation(this.exercise.course.id, this.exercise.id, this.participation.id).subscribe(
                (latestResult: any) => {
                    if (latestResult.body.length) {
                        resolve(latestResult.body.reduce((acc: Result, v: Result) => (v.id > acc.id ? v : acc)));
                    } else {
                        reject();
                    }
                },
                err => {
                    console.log('Error while loading latest results!', err);
                    reject();
                },
            );
        });
    }

    /**
     * @function loadResultDetails
     * @desc Fetches details for the result (if we received one) => Input latestResult
     */
    loadResultsDetails(): Promise<void> {
        return new Promise((resolve, reject) =>
            this.resultService.getFeedbackDetailsForResult(this.latestResult.id).subscribe(
                resultDetails => {
                    this.resultDetails = resultDetails.body;
                    this.latestResult.feedbacks = this.resultDetails;
                    resolve();
                },
                err => {
                    console.log('Error while loading result details!', err);
                    reject();
                },
            ),
        );
    }

    /**
     * @function loadInstructions
     * @desc Loads the instructions for the programming exercise.
     * We added the problemStatement later, historically the instructions where a file in the student's repository
     * This is why we now prefer the problemStatement and if it doesn't exist try to load the readme.
     */
    loadInstructions(): Promise<void> {
        return new Promise((resolve, reject) => {
            // Historical fallback: Older exercises have an instruction file in the git repo
            if (this.exercise.problemStatement === undefined) {
                const participationId = this.showTemplatePartipation ? (this.exercise as ProgrammingExercise).templateParticipation.id : this.participation.id;
                this.repositoryFileService.get(participationId, 'README.md').subscribe(
                    fileObj => {
                        // Old readme files contain unescaped unicode, convert it to make it persistable by the database
                        this.exercise.problemStatement = fileObj.fileContent.replace(new RegExp(/✅/, 'g'), '[task]');
                        resolve();
                    },
                    err => {
                        console.log('Error while getting README.md file!', err);
                        reject();
                    },
                );
            } else {
                resolve();
            }
        });
    }

    /**
     * Remove existing listeners and then setup new listeners.
     */
    private setUpClickListeners() {
        // // Detach test status click listeners if already initialized; if not, set it empty
        if (this.listenerRemoveFunctions.length) {
            this.listenerRemoveFunctions.forEach(f => f());
            this.listenerRemoveFunctions = [];
        }
        // // Since our rendered markdown file gets inserted into the DOM after compile time, we need to register click events for test cases manually
        const testStatusDOMElements = this.elementRef.nativeElement.querySelectorAll('.test-status');

        testStatusDOMElements.forEach((element: any) => {
            const listenerRemoveFunction = this.renderer.listen(element, 'click', event => {
                event.stopPropagation();
                // Extract the data attribute for tests and open the details popup with it
                let tests = '';
                if (event.target.getAttribute('data-tests')) {
                    tests = event.target.getAttribute('data-tests');
                } else {
                    tests = event.target.parentElement.getAttribute('data-tests');
                }
                this.showDetailsForTests(this.latestResult, tests);
            });
            this.listenerRemoveFunctions.push(listenerRemoveFunction);
        });
    }

    /**
     * Add task icons (success or failed) to tasks in introduction file.
     * Existing icons will be removed.
     */
    private setUpTaskIcons() {
        this.steps.forEach(({ done }, i) => {
            const componentRef = this.componentFactoryResolver.resolveComponentFactory(FaIconComponent).create(this.injector);
            componentRef.instance.size = 'lg';
            componentRef.instance.iconProp = done ? faCheckCircle : faTimesCircle;
            componentRef.instance.classes = [done ? 'text-success' : 'text-danger'];
            componentRef.instance.ngOnChanges({});
            this.appRef.attachView(componentRef.hostView);
            const domElem = (componentRef.hostView as EmbeddedViewRef<any>).rootNodes[0] as HTMLElement;
            const iconContainer = document.getElementById(`step-icon-${i}`);
            iconContainer.innerHTML = '';
            iconContainer.append(domElem);
        });
    }

    /**
     * @function triggerTestStatusClick
     * @desc Clicks the corresponding testStatus DOM element to trigger the dialog
     * @param index {number} The index indicates which test status link should be clicked
     */
    public triggerTestStatusClick(index: number): void {
        const testStatusDOMElements = this.elementRef.nativeElement.querySelectorAll('.test-status');
        /** We analyze the tests up until our index to determine the number of green tests **/
        const testStatusCircleElements = this.elementRef.nativeElement.querySelectorAll('.stepwizard-circle');
        const testStatusCircleElementsUntilIndex = Array.from(testStatusCircleElements).slice(0, index + 1);
        const positiveTestsUntilIndex = testStatusCircleElementsUntilIndex.filter((testCircle: HTMLElement) => testCircle.children[0].classList.contains('text-success')).length;
        /** The click should only be executed if the clicked element is not a positive test **/
        if (testStatusDOMElements.length && !testStatusCircleElements[index].children[0].classList.contains('text-success')) {
            /** We subtract the number of positive tests from the index to match the correct test status link **/
            testStatusDOMElements[index - positiveTestsUntilIndex].click();
        }
    }

    /**
     * @function showDetailsForTests
     * @desc Opens the ResultDetailComponent as popup; displays test results
     * @param result {Result} Result object, mostly latestResult
     * @param tests {string} Identifies the testcase
     */
    showDetailsForTests(result: Result, tests: string) {
        if (!result) {
            return;
        }
        const modalRef = this.modalService.open(EditorInstructionsResultDetailComponent, { keyboard: true, size: 'lg' });
        modalRef.componentInstance.result = result;
        modalRef.componentInstance.tests = tests;
    }

    /**
     * @function remarkableTestsStatusParser
     * @desc Parser rule for Remarkable custom token TestStatus
     * @param state
     * @param silent
     */
    private remarkableTestsStatusParser(state: any, silent: boolean) {
        const regex = /^\[task\]\[([^\]]*)\]\s*\(([^)]+)\)/;

        // It is surely not our rule, so we can stop early
        if (state.src[state.pos] !== '[') {
            return false;
        }

        const match = regex.exec(state.src.slice(state.pos));
        if (match) {
            // In silent mode it shouldn't output any tokens or modify pending
            if (!silent) {
                // Insert the testsStatus token to our rendered tokens
                state.push({
                    type: 'testsStatus',
                    title: match[1],
                    tests: match[2].split(','),
                    level: state.level,
                });
            }

            // Every rule should set state.pos to a position after token's contents
            state.pos += match[0].length;

            return true;
        } else {
            return false;
        }
    }

    /**
     * @function remarkablePlantUmlParser
     * @desc Parser rule for Remarkable custom token PlantUml
     * @param state
     * @param startLine
     * @param endLine
     * @param silent
     */
    private remarkablePlantUmlParser = function(state: any, startLine: number, endLine: number, silent: boolean) {
        /**
         * Excerpt from the remarkable documentation regarding the stateBlock (param state):
         * src: the complete string the parser is currently working on
         * parser: The current block parser (here to make nested calls easier)
         * env: a namespaced data key-value store to allow core rules to exchange data
         * tokens: the tokens generated by the parser up to now, you will emit new tokens by calling push(newToken) on this
         * bMarks: a collection marking for each line the position of its start in src
         * eMarks: a collection marking for each line the position of its end in src
         * tShift: a collection marking for each line, how much spaces were used to indent it
         * level: the nested level for the current block
         */

        const shift = state.tShift[startLine];
        const max = state.eMarks[startLine];
        let pos = state.bMarks[startLine];
        pos += shift;

        if (shift > 3 || pos + 2 >= max) {
            return false;
        }
        if (state.src.charCodeAt(pos) !== 0x40 /* @ */) {
            return false;
        }

        const char = state.src.charCodeAt(pos + 1);

        // Is the current char a 's'?
        if (char === 0x73) {
            // Probably start or end of tag
            if (char === 0x73 /* \ */) {
                // opening tag
                const match = state.src.slice(pos, max).match(/^@startuml/);
                if (!match) {
                    return false;
                }
            }
            if (silent) {
                return true;
            }
        } else {
            return false;
        }

        // If we are here - we detected PlantUML block.
        // Let's roll down till empty line (block end).
        let nextLine = startLine + 1;
        while (nextLine < state.lineMax && !state.src.slice(state.bMarks[nextLine], state.bMarks[nextLine + 1]).match(/^@enduml/)) {
            nextLine++;
        }

        state.line = nextLine + 1;
        // Insert the plantUml token to our rendered tokens
        state.tokens.push({
            type: 'plantUml',
            level: state.level,
            lines: [startLine, state.line],
            content: state.getLines(startLine, state.line, 0, true),
        });

        return true;
    };

    /**
     * @function remarkableTestsStatusRenderer
     * @desc Renderer rule for Remarkable custom token TestStatus
     * Builds the raw html for test case status; also inserts the test details link
     * @param tokens
     * @param id
     * @param options
     * @param env
     */
    private remarkableTestsStatusRenderer(tokens: any[], id: number, options: any, env: any) {
        const tests = tokens[0].tests;
        const [done, label] = this.statusForTests(tests);

        let text = `<span class="bold"><span id=step-icon-${this.steps.length}></span>`;

        text += ' ' + tokens[0].title;
        text += '</span>: ';
        // If the test is not done, we set the 'data-tests' attribute to the a-element, which we later use for the details dialog
        if (done) {
            text += '<span class="text-success bold">' + label + '</span>';
        } else {
            // bugfix: do not let the user click on 'No Results'
            if (label === this.translateService.instant('arTeMiSApp.editor.testStatusLabels.noResult')) {
                text += '<span class="text-danger bold">' + label + '</span>'; // this should be bold
            } else {
                text += '<a data-tests="' + tests.toString() + '" class="test-status"><span class="text-danger result">' + label + '</span></a>';
            }
        }
        text += '<br>';

        this.steps.push({
            title: tokens[0].title,
            done,
        });

        return text;
    }

    /**
     * @function remarkablePlantUmlRenderer
     * @desc Renderer rule for Remarkable custom token PlantUml
     * @param tokens the tokens generated by the parser up to now
     * @param id
     */
    private remarkablePlantUmlRenderer(tokens: any[], id: number) {
        let plantUml = tokens[id].content;

        // tslint:disable-next-line:max-line-length
        plantUml = plantUml.replace(
            '@startuml',
            '@startuml\nskinparam shadowing false\nskinparam classBorderColor black\nskinparam classArrowColor black\nskinparam DefaultFontSize 14\nskinparam ClassFontStyle bold\nskinparam classAttributeIconSize 0\nhide empty members\n',
        );

        plantUml = plantUml.replace(/testsColor\(([^)]+)\)/g, (match: any, capture: string) => {
            const tests = capture.split(',');
            const [done] = this.statusForTests(tests);
            return done ? 'green' : 'red';
        });

        /**
         * Explanation: This call fetches the plantUml png as base64 string; the function returns and inserts an empty img tag with a placeholder
         * When the promise is fulfilled, the src-attribute of the img element is being set with the returned value
         */
        this.editorService.getPlantUmlImage(plantUml).subscribe(
            plantUmlSrcAttribute => {
                // Assign plantUmlSrcAttribute as src attribute to our img element if exists.
                if (document.getElementById('plantUml' + id)) {
                    document.getElementById('plantUml' + id).setAttribute('src', 'data:image/jpeg;base64,' + plantUmlSrcAttribute);
                }
            },
            err => {
                console.log('Error getting plantUmlImage', err);
            },
        );

        return "<img id='plantUml" + id + "' alt='plantUml'" + id + " '/>";
    }

    /**
     * @function statusForTests
     * @desc Callback function for renderers to set the appropiate test status
     * @param tests
     */
    private statusForTests(tests: string[]): [boolean, string] {
        const translationBasePath = 'arTeMiSApp.editor.testStatusLabels.';
        let done = false;
        let label = this.translateService.instant('arTeMiSApp.editor.testStatusLabels.noResult');
        const totalTests = tests.length;

        if (this.resultDetails && this.resultDetails.length > 0) {
            let failedTests = 0;
            for (const test of tests) {
                for (const result of this.resultDetails) {
                    if (result.text === test) {
                        failedTests++;
                    }
                }
            }

            // Exercise is done if it was completed successfully or no tests have failed
            done = (this.latestResult && this.latestResult.successful) || failedTests === 0;
            if (totalTests === 1) {
                if (done) {
                    label = this.translateService.instant(translationBasePath + 'testPassing');
                } else {
                    label = this.translateService.instant(translationBasePath + 'testFailing');
                }
            } else {
                if (done) {
                    label = this.translateService.instant(translationBasePath + 'totalTestsPassing', { totalTests });
                } else {
                    label = this.translateService.instant(translationBasePath + 'totalTestsFailing', { totalTests, failedTests });
                }
            }
        } else if (this.latestResult && this.latestResult.successful) {
            done = true;
            label = this.translateService.instant(translationBasePath + 'testPassing');
        }

        return [done, label];
    }

    ngOnDestroy() {
        this.listenerRemoveFunctions.forEach(f => f());
        this.listenerRemoveFunctions = [];
        this.steps = [];
        this.jhiWebsocketService.unsubscribe(this.websocketChannelResults);
    }
}
