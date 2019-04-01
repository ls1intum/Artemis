import { Component, ElementRef, Input, OnChanges, OnDestroy, Renderer2, SimpleChanges } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import * as Remarkable from 'remarkable';

import { CodeEditorService } from '../../code-editor/code-editor.service';
import { EditorInstructionsResultDetailComponent } from '../../code-editor/instructions/code-editor-instructions-result-detail';
import { Feedback } from '../feedback';
import { Result, ResultService } from '../result';
import { ProgrammingExercise } from './programming-exercise.model';
import { FileService } from 'app/shared/http/file.service';
import { RepositoryFileService } from '../repository';
import { Participation } from '../participation';

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
    public participation: Participation;

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
        private fileService: FileService,
        private repositoryFileService: RepositoryFileService,
        private renderer: Renderer2,
        private elementRef: ElementRef,
        private modalService: NgbModal,
    ) {
        this.markdown = new Remarkable();
        this.markdown.inline.ruler.before('text', 'testsStatus', this.remarkableTestsStatusParser.bind(this), {});
        this.markdown.block.ruler.before('paragraph', 'plantUml', this.remarkablePlantUmlParser.bind(this), {});
        this.markdown.renderer.rules['testsStatus'] = this.remarkableTestsStatusRenderer.bind(this);
        this.markdown.renderer.rules['plantUml'] = this.remarkablePlantUmlRenderer.bind(this);
    }

    public ngOnChanges(changes: SimpleChanges) {
        if (changes.participation.currentValue) {
            this.steps = [];
            this.loadInstructions()
                .then(() => {
                    if (this.participation.results) {
                        this.latestResult = this.participation.results[0];
                        Promise.resolve();
                    } else {
                        return this.loadLatestResult();
                    }
                })
                .then(() => {
                    if (this.latestResult) {
                        return this.loadResultsDetails();
                    } else {
                        Promise.resolve();
                    }
                })
                .then(() => {
                    this.renderedMarkdown = this.markdown.render(this.participation.exercise.problemStatement);
                    // For whatever reason, we have to wait a tick here. The markdown parser should be synchronous...
                    setTimeout(() => this.setUpClickListeners(), 100);
                })
                .finally(() => {
                    this.isLoading = false;
                });
        }
    }

    loadLatestResult() {
        return new Promise((resolve, reject) => {
            const { exercise } = this.participation;
            this.resultService
                .findResultsForParticipation(exercise.course.id, exercise.id, this.participation.id, {
                    showAllResults: true,
                })
                .subscribe(
                    (latestResult: any) => {
                        this.latestResult = latestResult.body.length && latestResult.body[0];
                        resolve();
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
    loadResultsDetails() {
        return new Promise((resolve, reject) =>
            this.resultService.getFeedbackDetailsForResult(this.latestResult.id).subscribe(
                resultDetails => {
                    this.resultDetails = resultDetails.body;
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
    loadInstructions() {
        return new Promise((resolve, reject) => {
            if (this.participation.exercise.id === undefined) {
                this.fileService.getTemplateFile('programming-exercise-instructions').subscribe(file => {
                    this.participation.exercise.problemStatement = file;
                    resolve();
                });
                // Historical fallback: Older exercises have an instruction file in the git repo
            } else if (this.participation.exercise.problemStatement === undefined) {
                this.repositoryFileService.get((this.participation.exercise as ProgrammingExercise).templateParticipation.id, 'README.md').subscribe(
                    fileObj => {
                        this.participation.exercise.problemStatement = fileObj.fileContent;
                        resolve();
                    },
                    err => {
                        // TODO: handle the case that there is no README.md file
                        console.log('Error while getting README.md file!', err);
                        reject();
                    },
                );
            } else {
                resolve();
            }
        });
    }

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
     * For historical reasons, two patterns have to be parsed:
     * 1) Old pattern, used for storing the checkmark character in the readme file in the assignment git repo
     * 2) New pattern, used for storing the unicode representation in the database
     * @param state
     * @param silent
     */
    private remarkableTestsStatusParser(state: any, silent: boolean) {
        const regexOld = /^✅\[([^\]]*)\]\s*\(([^)]+)\)/;
        const regexNew = /^&#x2705;\[([^\]]*)\]\s*\(([^)]+)\)/;

        // It is surely not our rule, so we can stop early
        if (state.src[state.pos] !== '✅' && state.src[state.pos] !== '&') {
            return false;
        }

        const match = regexNew.exec(state.src.slice(state.pos)) || regexOld.exec(state.src.slice(state.pos));
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

        let text = '<span class="bold">';

        text += done
            ? `<fa-icon size="lg" [icon]="['far', 'check-circle']" class="text-success" style="font-size: 1.7em;"></fa-icon>`
            : `<fa-icon size="lg" [icon]="['far', 'times-circle']" class="text-danger" style="font-size: 1.7em;"></fa-icon>`;
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
    }
}
