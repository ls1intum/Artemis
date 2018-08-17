import { Participation } from '../../entities/participation';
import { JhiAlertService } from 'ng-jhipster';
import {
    AfterViewInit,
    Component,
    Input,
    OnChanges,
    OnInit,
    SimpleChanges,
    ElementRef,
    Renderer2
} from '@angular/core';
import { WindowRef } from '../../shared/websocket/window.service';
import { RepositoryFileService, RepositoryService} from '../../entities/repository/repository.service';
import { EditorComponent } from '../editor.component';
import { EditorService } from '../editor.service';
import { JhiWebsocketService } from '../../shared';
import { Result, ResultService, ParticipationResultService } from '../../entities/result';
import { Feedback } from '../../entities/feedback';
import { EditorInstructionsResultDetailComponent } from './editor-instructions-result-detail';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import * as interact from 'interactjs';
import * as Remarkable from 'remarkable';
import {HttpParams} from '@angular/common/http';

@Component({
    selector: 'jhi-editor-instructions',
    templateUrl: './editor-instructions.component.html',
    providers: [
        JhiAlertService,
        WindowRef,
        RepositoryService,
        ResultService,
        ParticipationResultService,
        EditorService
    ]
})

export class EditorInstructionsComponent implements OnInit, AfterViewInit, OnChanges {

    isLoading = false;
    loadedDetails = false;
    initialInstructionsWidth: number;
    markDown: Remarkable;
    readMeFileContent: string;
    readMeFileRendered: string;
    resultDetails: Feedback[];
    steps = [];

    @Input() participation: Participation;
    @Input() latestResult: Result;

    constructor(private parent: EditorComponent,
                private $window: WindowRef,
                private jhiWebsocketService: JhiWebsocketService,
                private repositoryService: RepositoryService,
                private repositoryFileService: RepositoryFileService,
                private resultService: ResultService,
                private editorService: EditorService,
                private modalService: NgbModal,
                private elRef: ElementRef,
                private renderer: Renderer2) {}

    /**
     * @function ngOnInit
     * @desc Initializes the Remarkable object and loads the repository README.md file
     */
    ngOnInit(): void {
        this.setupMarkDown();
        this.loadReadme();
    }

    /**
     * @function ngAfterViewInit
     * @desc Used to enable resizing for the instructions component
     */
    ngAfterViewInit(): void {
        this.initialInstructionsWidth = this.$window.nativeWindow.screen.width - 300 / 2;
        interact('.resizable-instructions')
            .resizable({
                // Enable resize from left edge; triggered by class rg-left
                edges: { left: '.rg-left', right: false, bottom: false, top: false },
                // Set maximum width
                restrictSize: {
                    max: { width: this.initialInstructionsWidth }
                },
                inertia: true,
            }).on('resizemove', function(event) {
            const target = event.target;
            // Update element size
            target.style.width  = event.rect.width + 'px';
            target.style.height = event.rect.height + 'px';
        });
    }

    /**
     * @function ngOnChanges
     * @desc New participation received => initialize new Remarkable object and load new README.md file
     *       New latestResult received => load details for result
     * @param {SimpleChanges} changes
     */
    ngOnChanges(changes: SimpleChanges): void {
        if (changes.participation && this.participation) {
            this.setupMarkDown();
            this.loadReadme();
        }

        if (changes.latestResult && changes.latestResult.currentValue && !this.isLoading) {
            // New result available
            this.loadResultsDetails();
        }
    }

    /**
     * @function loadReadme
     * @desc Gets the README.md file from our repository and starts the rendering process
     */
    loadReadme() {
        this.repositoryFileService.get(this.participation.id, 'README.md').subscribe( fileObj => {
           this.readMeFileContent = fileObj.fileContent;
           this.renderReadme();
        }, err => {
            console.log('Error while getting README.md file!', err);
        });
    }

    /**
     * @function loadResultDetails
     * @desc Fetches details for the result (if we received one) => Input latestResult
     */
    loadResultsDetails() {
        if (!this.latestResult) {
            return;
        }
        this.isLoading = true;

        this.resultService.details(this.latestResult.id).subscribe( resultDetails => {
            this.resultDetails = resultDetails.body;
            this.loadedDetails = true;
            this.renderReadme();
            this.isLoading = false;
        }, err => {
            console.log('Error while loading result details!', err);
        });
    }

    /**
     * @function setupMarkDown
     * @desc Initializes the Remarkable object and registers our custom parsing and rendering rules
     */
    setupMarkDown() {
        this.markDown = new Remarkable();
        // TODO: check if bind required
        this.markDown.inline.ruler.before('text', 'testsStatus', this.remarkableTestsStatusParser.bind(this), {});
        this.markDown.block.ruler.before('paragraph', 'plantUml', this.remarkablePlantUmlParser.bind(this), {});
        this.markDown.renderer.rules['testsStatus'] = this.remarkableTestsStatusRenderer.bind(this);
        this.markDown.renderer.rules['plantUml'] = this.remarkablePlantUmlRenderer.bind(this);
    }

    /**
     * @function renderReadme
     * @desc Prepares and starts the rendering process of the README.md file
     */
    renderReadme() {
        this.isLoading = true;
        // Reset steps array
        this.steps = [];
        // Render README.md file via Remarkable
        this.readMeFileRendered = this.markDown.render(this.readMeFileContent);
        this.isLoading = false;

        // Since our rendered markdown file gets inserted into the DOM after compile time, we need to register click events for test cases manually
        const testStatusDOMElements = this.elRef.nativeElement.querySelectorAll('.test-status');
        testStatusDOMElements.forEach( element => {
            this.renderer.listen(element, 'click', event => {
                // Extract the data attribute for tests and open the details popup with it
                const tests = event.target.parentElement.getAttribute('data-tests');
                this.showDetailsForTests(this.latestResult, tests);
            });
        });

        if (!this.loadedDetails) {
            this.loadResultsDetails();
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
    remarkablePlantUmlParser = function(state, startLine, endLine, silent) {
        const shift = state.tShift[startLine];
        const max = state.eMarks[startLine];
        let pos = state.bMarks[startLine];
        pos += shift;

        if (shift > 3 || pos + 2 >= max) { return false; }
        if (state.src.charCodeAt(pos) !== 0x40/* @ */) { return false; }

        const ch = state.src.charCodeAt(pos + 1);

        // e or s
        if (ch === 0x73) {
            // Probably start or end of tag
            if (ch === 0x73/* \ */) {
                // opening tag
                const match = state.src.slice(pos, max).match(/^@startuml/);
                if (!match) { return false; }
            }
            if (silent) { return true; }
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
        state.tokens.push({
            type: 'plantUml',
            level: state.level,
            lines: [ startLine, state.line ],
            content: state.getLines(startLine, state.line, 0, true)
        });

        return true;
    };

    /**
     * @function remarkablePlantUmlRenderer
     * @desc Renderer rule for Remarkable custom token PlantUml
     * @param tokens
     * @param id
     */
    remarkablePlantUmlRenderer(tokens, id) {
        let plantUml = tokens[id].content;

        plantUml = plantUml.replace('@startuml', '@startuml\nskinparam shadowing false\nskinparam classBorderColor black\nskinparam classArrowColor black\nskinparam DefaultFontSize 14\nskinparam ClassFontStyle bold\nskinparam classAttributeIconSize 0\nhide empty members\n');

        const that = this;
        plantUml = plantUml.replace(/testsColor\(([^)]+)\)/g, function(match, capture) {
            const tests = capture.split(',');
            const status = that.statusForTests(tests);
            return status['done'] ? 'green' : 'red';
        });

        console.log('remarkablePlantUmlRenderer', plantUml);
        console.log('encodeUriComponent', encodeURIComponent(plantUml));

        const test = new HttpParams().set('plantUml', plantUml);
        console.log('HttpParams', test);
        console.log(test.get('plantUml'));

        this.editorService.getPlantUmlImage(plantUml).subscribe( res => {
            console.log('getPlantUmlImage', res);
            const plantUmlSrcAttribute = res;
            console.log('plantUmlSrcAttribute', plantUmlSrcAttribute);
            return "<img src='data:image/jpeg;base64," + plantUmlSrcAttribute + " '/>";
        }, err => {
            console.log('Error getting plantUmlImage', err);
        });
    }

    /**
     * @function remarkableTestsStatusParser
     * @desc Parser rule for Remarkable custom token TestStatus
     * @param state
     * @param silent
     */
    remarkableTestsStatusParser(state, silent: boolean) {

        const regex = /^✅\[([^\]]*)\]\s*\(([^)]+)\)/;

        // it is surely not our rule, so we could stop early
        if (state.src[state.pos] !== '✅') { return false; }

        const match = regex.exec(state.src.slice(state.pos));
        if (!match) { return false; }

        // in silent mode it shouldn't output any tokens or modify pending
        if (!silent) {
            state.push({
                type: 'testsStatus',
                title: match[1],
                tests: match[2].split(','),
                level: state.level,
            });
        }

        // every rule should set state.pos to a position after token's contents
        state.pos += match[0].length;

        return true;
    }

    /**
     * @function remarkableTestsStatusRenderer
     * @desc Renderer rule for Remarkable custom token TestStatus
     * @param tokens
     * @param id
     * @param options
     * @param env
     */
    remarkableTestsStatusRenderer(tokens, id: number, options, env) {
        const tests = tokens[0].tests;
        const status = this.statusForTests(tests);

        let text = '<strong>';

        text += status['done'] ?
            '<i class="fa fa-lg fa-check-circle-o text-success" style="font-size: 1.7em;"></i>' :
            '<i class="fa fa-lg fa-times-circle-o text-danger" style="font-size: 1.7em;"></i>';
        text += ' ' + tokens[0].title;
        text += '</strong>: ';
        text += status['done'] ?
            ' <span class="text-success">' + status['label'] + '</span>' :
            '<a data-tests="' + tests.toString() + '" class="test-status"><span class="text-danger">' + status['label'] + '</span></a>';
        text += '<br />';

        this.steps.push({
            title: tokens[0].title,
            done: status['done']
        });

        return text;
    }

    /**
     * @function statusForTests
     * @desc Callback function for renderers to set the appropiate test status
     * @param tests
     */
    statusForTests(tests): object {
        let done = false;
        let label = 'No results';
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

            done = (this.latestResult && this.latestResult.successful) || failedTests === 0;
            if (totalTests === 1) {
                if (done) {
                    label = 'Test passing';
                } else {
                    label = 'Test failing';
                }
            } else {
                if (done) {
                    label = totalTests + ' tests passing';
                } else {
                    label = failedTests + ' of ' + totalTests + ' tests failing';
                }
            }

        } else if (this.latestResult && this.latestResult.successful) {
            done = true;
            label = 'Test passing';
        }

        return {
            done,
            label
        };
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
        const modalRef = this.modalService.open(EditorInstructionsResultDetailComponent, {keyboard: true, size: 'lg'});
        modalRef.componentInstance.result = result;
        modalRef.componentInstance.tests = tests;
    }

    /**
     * @function toggleEditorCollapse
     * @desc Calls the parent (editorComponent) toggleCollapse method
     * @param $event
     * @param {boolean} horizontal
     */
    toggleEditorCollapse($event: any, horizontal: boolean) {
        this.parent.toggleCollapse($event, horizontal);
    }
}
