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
import { SafeHtml } from '@angular/platform-browser';
import { HttpResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import * as Remarkable from 'remarkable';
import { ShowdownExtension } from 'showdown';
import { faCheckCircle, faQuestionCircle, faTimesCircle } from '@fortawesome/free-regular-svg-icons';
import { catchError, filter, flatMap, map, switchMap, tap } from 'rxjs/operators';
import { ProgrammingExercisePlantUmlService } from 'app/entities/programming-exercise/instructions/programming-exercise-plant-uml.service';
import { EditorInstructionsResultDetailComponent } from 'app/code-editor/instructions/code-editor-instructions-result-detail';
import { Feedback } from 'app/entities/feedback';
import { Result, ResultService } from 'app/entities/result';
import { ProgrammingExercise } from '../programming-exercise.model';
import { RepositoryFileService } from 'app/entities/repository';
import { hasParticipationChanged, Participation, ParticipationWebsocketService } from 'app/entities/participation';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { Observable, Subscription } from 'rxjs';
import { hasExerciseChanged, problemStatementHasChanged } from 'app/entities/exercise';
import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise/programming-exercise-test-case.model';
import { isLegacyResult } from 'app/entities/programming-exercise/utils/programming-exercise.utils';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { ProgrammingExerciseTaskExtensionFactory } from './extensions/programming-exercise-task.extension';
import { ProgrammingExercisePlantUmlExtensionFactory } from 'app/entities/programming-exercise/instructions/extensions/programming-exercise-plant-uml.extension';

export enum TestCaseState {
    NOT_EXECUTED = 'NOT_EXECUTED',
    SUCCESS = 'SUCCESS',
    FAIL = 'FAIL',
    NO_RESULT = 'NO_RESULT',
}

type Step = {
    title: string;
    done: TestCaseState;
};

@Component({
    selector: 'jhi-programming-exercise-instructions',
    templateUrl: './programming-exercise-instruction.component.html',
    styleUrls: ['./programming-exercise-instruction.scss'],
})
export class ProgrammingExerciseInstructionComponent implements OnChanges, OnDestroy {
    TestCaseState = TestCaseState;

    private markdown: Remarkable;

    @Input()
    public exercise: ProgrammingExercise;
    @Input()
    public participation: Participation;
    // If there are no instructions available (neither in the exercise problemStatement or the legacy README.md) emits an event
    @Output()
    public onNoInstructionsAvailable = new EventEmitter();
    @Output()
    public resultChange = new EventEmitter<Result>();

    public problemStatement: string;
    public participationSubscription: Subscription;

    public isInitial = true;
    public isLoading: boolean;
    public latestResult: Result | null;
    public steps: Array<Step> = [];
    public plantUMLs: { [id: string]: string } = {};
    public renderedMarkdown: SafeHtml;

    private markdownExtensions: ShowdownExtension[];

    constructor(
        private editorService: ProgrammingExercisePlantUmlService,
        private translateService: TranslateService,
        private resultService: ResultService,
        private repositoryFileService: RepositoryFileService,
        private participationWebsocketService: ParticipationWebsocketService,
        private markdownService: ArtemisMarkdown,
        private programmingExerciseTaskFactory: ProgrammingExerciseTaskExtensionFactory,
        private programmingExercisePlantUmlFactory: ProgrammingExercisePlantUmlExtensionFactory,
    ) {
        this.markdownExtensions = [this.programmingExerciseTaskFactory.getExtension(), this.programmingExercisePlantUmlFactory.getExtension()];
    }

    /**
     * If the participation changes, the participation's instructions need to be loaded and the
     * subscription for the participation's result needs to be set up.
     * @param changes
     */
    public ngOnChanges(changes: SimpleChanges) {
        const participationHasChanged = hasParticipationChanged(changes);
        // It is possible that the exercise does not have an id in case it is being created now.
        if (participationHasChanged) {
            this.isInitial = true;
            this.setupResultWebsocket();
        }
        // If the exercise is not loaded, the instructions can't be loaded and so there is no point in loading the results, etc, yet.
        if (!this.isLoading && this.exercise && this.participation && (this.isInitial || participationHasChanged)) {
            this.isLoading = true;
            this.loadInstructions()
                .pipe(
                    // If no instructions can be loaded, abort pipe and hide the instruction panel
                    tap(problemStatement => {
                        if (!problemStatement) {
                            this.onNoInstructionsAvailable.emit();
                            this.isLoading = false;
                            this.isInitial = false;
                            return Observable.of(null);
                        }
                    }),
                    filter(problemStatement => !!problemStatement),
                    tap(problemStatement => (this.problemStatement = problemStatement!)),
                    switchMap(() => this.loadInitialResult()),
                    tap(latestResult => {
                        this.latestResult = latestResult;
                        this.programmingExerciseTaskFactory.setLatestResult(this.latestResult);
                        this.programmingExercisePlantUmlFactory.setLatestResult(this.latestResult);
                    }),
                    tap(() => {
                        /*                        this.updateMarkdown();*/
                        this.renderedMarkdown = this.markdownService.htmlForMarkdown(this.problemStatement, this.markdownExtensions);
                        this.isInitial = false;
                        this.isLoading = false;
                    }),
                )
                .subscribe();
        } else if (this.exercise && problemStatementHasChanged(changes)) {
            // If the exercise's problemStatement is updated from the parent component, re-render the markdown.
            // This is e.g. the case if the parent component uses an editor to update the problemStatement.
            this.problemStatement = this.exercise.problemStatement!;
            this.renderedMarkdown = this.markdownService.htmlForMarkdown(this.problemStatement, this.markdownExtensions);
            /*            this.updateMarkdown();*/
        }
    }

    /**
     * Set up the websocket for retrieving build results.
     * Online updates the build logs if the result is new, otherwise doesn't react.
     */
    private setupResultWebsocket() {
        if (this.participationSubscription) {
            this.participationSubscription.unsubscribe();
        }
        this.participationSubscription = this.participationWebsocketService
            .subscribeForLatestResultOfParticipation(this.participation.id)
            .pipe(filter(participation => !!participation))
            .subscribe((result: Result) => {
                this.latestResult = result;
                this.programmingExerciseTaskFactory.setLatestResult(this.latestResult);
                this.programmingExercisePlantUmlFactory.setLatestResult(this.latestResult);
                this.renderedMarkdown = this.markdownService.htmlForMarkdown(this.problemStatement);
                /*                this.updateMarkdown();*/
            });
    }

    /**
     * This method is used for initially loading the results so that the instructions can be rendered.
     */
    loadInitialResult(): Observable<Result | null> {
        if (this.participation && this.participation.id && this.participation.results && this.participation.results.length) {
            // Get the result with the highest id (most recent result)
            const latestResult = this.participation.results.reduce((acc, v) => (v.id > acc.id ? v : acc));
            if (!latestResult) {
                return Observable.of(null);
            }
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
        this.renderedMarkdown = this.markdown.render(this.problemStatement);
        // Wait for re-render of component
        setTimeout(() => {
            this.loadAndInsertPlantUmls();
            /*            this.setUpClickListeners();*/
            /*            this.setUpTaskIcons();*/
        }, 0);
    }

    /**
     * Retrieve latest result for the participation/exercise/course combination.
     * If there is no result, return null.
     */
    loadLatestResult(): Observable<Result | null> {
        return this.resultService.findResultsForParticipation(this.exercise.course!.id, this.exercise.id, this.participation.id).pipe(
            catchError(() => Observable.of(null)),
            map((latestResult: HttpResponse<Result[]>) => {
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
            map(res => res && res.body),
            map((feedbacks: Feedback[]) => {
                result.feedbacks = feedbacks;
                return result;
            }),
            catchError(() => Observable.of(result)),
        );
    }

    /**
     * @function loadInstructions
     * @desc Loads the instructions for the programming exercise.
     * We added the problemStatement later, historically the instructions where a file in the student's repository
     * This is why we now prefer the problemStatement and if it doesn't exist try to load the readme.
     */
    loadInstructions(): Observable<string | null> {
        if (this.exercise.problemStatement !== null && this.exercise.problemStatement !== undefined) {
            return Observable.of(this.exercise.problemStatement);
        } else {
            if (!this.participation.id) {
                return Observable.of(null);
            }
            return this.repositoryFileService.get(this.participation.id, 'README.md').pipe(
                catchError(() => Observable.of(null)),
                // Old readme files contain chars instead of our domain command tags - replace them when loading the file
                map(fileObj => fileObj && fileObj.fileContent.replace(new RegExp(/✅/, 'g'), '[task]')),
            );
        }
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
                        document.getElementById('plantUml' + id)!.setAttribute('src', 'data:image/jpeg;base64,' + plantUmlSrcAttribute);
                    }
                },
                err => {
                    console.log('Error getting plantUmlImage', err);
                },
            ),
        );
    }

    // TODO: The information about the known test status should come from a service.
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
        const positiveTestsUntilIndex = testStatusCircleElementsUntilIndex.filter((testCircle: HTMLElement) => testCircle.classList.contains('stepwizard-step--success')).length;
        /** The click should only be executed if the clicked element is not a positive test **/
        if (testStatusDOMElements.length && !testStatusCircleElements[index].classList.contains('stepwizard-step--success')) {
            /** We subtract the number of positive tests from the index to match the correct test status link **/
            testStatusDOMElements[index - positiveTestsUntilIndex].click();
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
            return done === TestCaseState.SUCCESS ? 'green' : done === TestCaseState.FAIL ? 'red' : 'grey';
        });

        this.plantUMLs[id] = plantUml;

        return "<img id='plantUml" + id + "' alt='plantUml'" + id + " '/>";
    }

    /**
     * @function statusForTests
     * @desc Callback function for renderers to set the appropiate test status
     * @param tests
     */
    private statusForTests(tests: string[]): [TestCaseState, string] {
        const translationBasePath = 'artemisApp.editor.testStatusLabels.';
        const totalTests = tests.length;

        if (this.latestResult && this.latestResult.successful && (!this.latestResult.feedbacks || !this.latestResult.feedbacks.length)) {
            // Case 1: Submission fulfills all test cases and there are no feedbacks (legacy case), no further checking needed.
            const label = this.translateService.instant(translationBasePath + 'testPassing');
            return [TestCaseState.SUCCESS, label];
        } else if (this.latestResult && this.latestResult.feedbacks && this.latestResult.feedbacks.length) {
            // Case 2: At least one test case is not successful, tests need to checked to find out if they were not fulfilled
            const { failed, notExecuted, successful } = tests.reduce(
                (acc, testName) => {
                    const feedback = this.latestResult!.feedbacks.find(({ text }) => text === testName);
                    // This is a legacy check, results before the 24th May are considered legacy.
                    const resultIsLegacy = isLegacyResult(this.latestResult!);
                    // If there is no feedback item, we assume that the test was successful (legacy check).
                    if (resultIsLegacy) {
                        return {
                            failed: feedback ? [...acc.failed, testName] : acc.failed,
                            successful: feedback ? acc.successful : [...acc.successful, testName],
                            notExecuted: acc.notExecuted,
                        };
                    } else {
                        return {
                            failed: feedback && feedback.positive === false ? [...acc.failed, testName] : acc.failed,
                            successful: feedback && feedback.positive === true ? [...acc.successful, testName] : acc.successful,
                            notExecuted: !feedback || feedback.positive === undefined ? [...acc.notExecuted, testName] : acc.notExecuted,
                        };
                    }
                },
                { failed: [], successful: [], notExecuted: [] },
            );

            // Exercise is done if none of the tests failed
            const testCaseState = failed.length > 0 ? TestCaseState.FAIL : notExecuted.length > 0 ? TestCaseState.NOT_EXECUTED : TestCaseState.SUCCESS;
            const label = this.translateService.instant(translationBasePath + 'totalTestsPassing', { totalTests, passedTests: successful.length });
            return [testCaseState, label];
        } else {
            // Case 3: There are no results
            const label = this.translateService.instant(translationBasePath + 'noResult');
            return [TestCaseState.NO_RESULT, label];
        }
    }

    ngOnDestroy() {
        this.steps = [];
        if (this.participationSubscription) {
            this.participationSubscription.unsubscribe();
        }
    }
}
