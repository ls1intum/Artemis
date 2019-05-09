import {
    ApplicationRef,
    Component,
    ComponentFactoryResolver,
    ElementRef,
    EmbeddedViewRef,
    EventEmitter,
    Injector,
    Input,
    OnChanges,
    OnDestroy,
    Output,
    Renderer2,
    SimpleChanges,
} from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import * as Remarkable from 'remarkable';
import { faCheckCircle, faTimesCircle } from '@fortawesome/free-regular-svg-icons';
import { catchError, filter, flatMap, map, switchMap, tap } from 'rxjs/operators';

import { CodeEditorService } from '../../code-editor/code-editor.service';
import { EditorInstructionsResultDetailComponent } from '../../code-editor/instructions/code-editor-instructions-result-detail';
import { Feedback } from '../feedback';
import { Result, ResultService } from '../result';
import { ProgrammingExercise } from './programming-exercise.model';
import { RepositoryFileService } from '../repository';
import { hasParticipationChanged, Participation, ParticipationWebsocketService } from '../participation';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { Observable, Subscription } from 'rxjs';
import { hasExerciseChanged, problemStatementHasChanged } from '../exercise';

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
    // If there are no instructions available (neither in the exercise problemStatement or the legacy README.md) emits an event
    @Output()
    public onNoInstructionsAvailable = new EventEmitter();

    private resultSubscription: Subscription;

    public isInitial = true;
    public isLoading: boolean;
    public latestResult: Result | null;
    public steps: Array<Step> = [];
    public plantUMLs: { [id: string]: string } = {};
    public renderedMarkdown: string;
    // Can be used to remove the click listeners for result details
    private listenerRemoveFunctions: Function[] = [];

    constructor(
        private editorService: CodeEditorService,
        private translateService: TranslateService,
        private resultService: ResultService,
        private repositoryFileService: RepositoryFileService,
        private participationWebsocketService: ParticipationWebsocketService,
        private renderer: Renderer2,
        private elementRef: ElementRef,
        private modalService: NgbModal,
        private componentFactoryResolver: ComponentFactoryResolver,
        private appRef: ApplicationRef,
        private injector: Injector,
    ) {
        // Enabled for color picker of markdown editor that inserts spans into the markdown
        this.markdown = new Remarkable({ html: true });
        this.markdown.inline.ruler.before('text', 'testsStatus', this.remarkableTestsStatusParser.bind(this), {});
        this.markdown.block.ruler.before('paragraph', 'plantUml', this.remarkablePlantUmlParser.bind(this), {});
        this.markdown.renderer.rules['testsStatus'] = this.remarkableTestsStatusRenderer.bind(this);
        this.markdown.renderer.rules['plantUml'] = this.remarkablePlantUmlRenderer.bind(this);
    }

    /**
     * If the participation changes, the participation's instructions need to be loaded and the
     * subscription for the participation's result needs to be set up.
     * @param changes
     */
    public ngOnChanges(changes: SimpleChanges) {
        const participationHasChanged = hasParticipationChanged(changes);
        const exerciseHasChanged = hasExerciseChanged(changes);
        if (participationHasChanged) {
            this.isInitial = true;
            this.setupResultWebsocket();
        }
        // If the exercise is not loaded, the instructions can't be loaded and so there is no point in loading the results, etc, yet.
        if (!this.isLoading && this.exercise && (this.isInitial || participationHasChanged || exerciseHasChanged)) {
            this.isLoading = true;
            this.loadInstructions()
                .pipe(
                    // If no instructions can be loaded, abort pipe and hide the instruction panel
                    tap(problemStatement => {
                        if (!problemStatement) {
                            this.onNoInstructionsAvailable.emit();
                            this.isLoading = false;
                            return Observable.of(null);
                        }
                    }),
                    filter(problemStatement => !!problemStatement),
                    tap(problemStatement => (this.exercise.problemStatement = problemStatement)),
                    switchMap(() => (this.isInitial && this.exercise.id ? this.loadInitialResult() : Observable.of(null))),
                    map(latestResult => (this.latestResult = latestResult)),
                    tap(() => {
                        this.updateMarkdown();
                        this.isInitial = false;
                        this.isLoading = false;
                    }),
                )
                .subscribe();
        } else if (problemStatementHasChanged(changes)) {
            // If the exercise's problemStatement is updated from the parent component, re-render the markdown.
            // This is e.g. the case if the parent component uses an editor to update the problemStatement.
            this.updateMarkdown();
        }
    }

    /**
     * Set up the websocket for retrieving build results.
     * Online updates the build logs if the result is new, otherwise doesn't react.
     */
    private async setupResultWebsocket() {
        if (this.resultSubscription) {
            this.resultSubscription.unsubscribe();
        }
        return this.participationWebsocketService.subscribeForLatestResultOfParticipation(this.participation.id).subscribe(result => {
            this.latestResult = result;
            this.updateMarkdown();
        });
    }

    /**
     * This method is used for initially loading the results so that the instructions can be rendered.
     */
    loadInitialResult(): Observable<Result> {
        if (this.participation && this.participation.id && this.participation.results && this.participation.results.length) {
            // Get the result with the highest id (most recent result)
            const latestResult = this.participation.results.reduce((acc, v) => (v.id > acc.id ? v : acc));
            return latestResult.feedbacks ? Observable.of(latestResult) : this.loadAndAttachResultDetails(latestResult);
        } else if (this.participation && this.participation.id) {
            // Only load results if the exercise already is in our database, otherwise there can be no build result anyway
            return this.loadLatestResult();
        } else {
            return Observable.of(null);
        }
    }

    /**
     * Reset and then render the markdown of the instruction file.
     */
    updateMarkdown() {
        this.steps = [];
        this.plantUMLs = {};
        this.renderedMarkdown = this.markdown.render(this.exercise.problemStatement);
        // For whatever reason, we have to wait a tick here. The markdown parser should be synchronous...
        setTimeout(() => {
            this.loadAndInsertPlantUmls();
            this.setUpClickListeners();
            this.setUpTaskIcons();
        }, 100);
    }

    /**
     * Retrieve latest result for the participation/exercise/course combination.
     * If there is no result, return null.
     */
    loadLatestResult(): Observable<Result | null> {
        return this.resultService.findResultsForParticipation(this.exercise.course.id, this.exercise.id, this.participation.id).pipe(
            catchError(() => Observable.of(null)),
            map((latestResult: { body: Result[] }) => {
                if (latestResult && latestResult.body && latestResult.body.length) {
                    return latestResult.body.reduce((acc: Result, v: Result) => (v.id > acc.id ? v : acc));
                } else {
                    return null;
                }
            }),
            flatMap((latestResult: Result) => (latestResult ? this.loadAndAttachResultDetails(latestResult) : Observable.of(null))),
        );
    }

    /**
     * @function loadResultDetails
     * @desc Fetches details for the result (if we received one) and attach them to the result.
     * Mutates the input parameter result.
     */
    loadAndAttachResultDetails(result: Result): Observable<Result> {
        return this.resultService.getFeedbackDetailsForResult(result.id).pipe(
            catchError(() => Observable.of(null)),
            map(res => res && res.body),
            map((feedbacks: Feedback[]) => {
                result.feedbacks = feedbacks;
                return result;
            }),
        );
    }

    /**
     * @function loadInstructions
     * @desc Loads the instructions for the programming exercise.
     * We added the problemStatement later, historically the instructions where a file in the student's repository
     * This is why we now prefer the problemStatement and if it doesn't exist try to load the readme.
     */
    loadInstructions(): Observable<string> {
        if (this.exercise.problemStatement) {
            return Observable.of(this.exercise.problemStatement);
        } else {
            let participationId: number;
            if (this.showTemplatePartipation && this.exercise.templateParticipation) {
                participationId = this.exercise.templateParticipation.id;
            } else if (this.participation) {
                participationId = this.participation.id;
            } else {
                // in this case, no participation is available
                return Observable.of(null);
            }
            return this.repositoryFileService.get(participationId, 'README.md').pipe(
                catchError(() => Observable.of(null)),
                // Old readme files contain chars instead of our domain command tags - replace them when loading the file
                map(fileObj => fileObj && fileObj.fileContent.replace(new RegExp(/✅/, 'g'), '[task]')),
            );
        }
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
     * PlantUMLs are rendered on the server, we provide their structure as a string.
     * When parsing the file for plantUMLs we store their ids (= position in HTML) and structure in a dictionary, so that we can load them after the initial render.
     */
    public loadAndInsertPlantUmls() {
        Object.entries(this.plantUMLs).forEach(([id, plantUml]) =>
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
            ),
        );
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

        this.plantUMLs[id] = plantUml;

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

        if (this.latestResult && this.latestResult.feedbacks && this.latestResult.feedbacks.length > 0) {
            let failedTests = 0;
            for (const test of tests) {
                for (const result of this.latestResult.feedbacks) {
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
        if (this.resultSubscription) {
            this.resultSubscription.unsubscribe();
        }
    }
}
