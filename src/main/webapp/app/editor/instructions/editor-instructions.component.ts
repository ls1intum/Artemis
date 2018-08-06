import {Participation} from '../../entities/participation';
import {JhiAlertService} from 'ng-jhipster';
import {
    AfterViewInit,
    Component,
    Input,
    OnChanges, OnDestroy,
    OnInit,
    SimpleChanges
} from '@angular/core';
import {WindowRef} from '../../shared/websocket/window.service';
import {RepositoryFileService, RepositoryService} from '../../entities/repository/repository.service';
import {EditorComponent} from '../editor.component';
import {JhiWebsocketService} from '../../shared';
import {Result, ResultService, ParticipationResultService} from '../../entities/result';
import {ResultDetailComponent} from '../../courses/results/result.component';
import {NgbModal} from '@ng-bootstrap/ng-bootstrap';
import {Feedback} from '../../entities/feedback';
import * as $ from 'jquery';
import * as Remarkable from 'Remarkable';
import * as interact from 'interactjs';

@Component({
    selector: 'jhi-editor-instructions',
    templateUrl: './editor-instructions.component.html',
    providers: [
        JhiAlertService,
        WindowRef,
        RepositoryService,
        ResultService,
        ParticipationResultService
    ]
})

export class EditorInstructionsComponent implements OnInit, AfterViewInit, OnDestroy, OnChanges {

    isLoading = true;
    steps = [];
    loadedDetails = false;
    initialInstructionsWidth: number;
    readMeFileContent: string;
    readMeFileRendered;
    resultDetails: Feedback[];
    markDown;

    @Input() participation: Participation;
    @Input() latestResult: Result;

    constructor(private parent: EditorComponent,
                private $window: WindowRef,
                private modalService: NgbModal,
                private jhiWebsocketService: JhiWebsocketService,
                private repositoryService: RepositoryService,
                private repositoryFileService: RepositoryFileService,
                private resultService: ResultService) {
    }

    /**
     * @function ngOnInit
     * @desc Framework function which is executed when the component is instantiated.
     * Used to assign parameters which are used by the component
     */
    ngOnInit(): void {
        this.loadReadme();
        this.setupMarkDown();
    }

    /**
     * @function ngAfterViewInit
     * @desc Framework lifecycle hook that is called after Angular has fully initialized a component's view;
     * used to handle any additional initialization tasks
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
     * @desc Framework lifecycle hook that is called when any data-bound property of a directive changes
     * @param {SimpleChanges} changes
     */
    ngOnChanges(changes: SimpleChanges): void {
        if (changes.participation && this.participation) {
            this.loadReadme();
            this.setupMarkDown();
        }
        if (changes.latestResult && changes.latestResult.currentValue && !this.isLoading) {
            // New result available
            this.loadResultsDetails();
        }
    }

    setupMarkDown() {
        this.markDown = new Remarkable();
        this.markDown.inline.ruler.before('text', 'testsStatus', this.remarkableTestsStatusParser, {});
        this.markDown.block.ruler.before('paragraph', 'plantUml', this.remarkablePlantUmlParser, {});
        this.markDown.renderer.rules['testsStatus'] = this.remarkableTestsStatusRenderer.bind(this);
        this.markDown.renderer.rules['plantUml'] = this.remarkablePlantUmlRenderer.bind(this);
    }

    loadReadme() {
        this.repositoryFileService.get(this.participation.id, 'README.md').subscribe( fileObj => {
           this.readMeFileContent = fileObj.fileContent;
           this.renderReadme();
        }, err => {
            console.log('Error while getting README.md file!', err);
        });
    }

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

    renderReadme(readmeFileContent?: string) {
        this.isLoading = true;
        this.steps = [];
        // TODO: https://angular.io/guide/dynamic-component-loader
        // TODO: vm.readmeRendered = $compile(vm.md.render(vm.readme))($scope);
        console.log('renderReadme!');
        this.readMeFileRendered = this.markDown.render(this.readMeFileContent);

        $('.instructions').html(this.readMeFileRendered);

        this.isLoading = false;

        if ($('.editor-sidebar-right .panel').height() > $('.editor-sidebar-right').height()) {
            // Safari bug workaround
            $('.editor-sidebar-right .panel').height($('.editor-sidebar-right').height() - 2);
        }

        if (!this.loadedDetails) {
            this.loadResultsDetails();
        }
    }

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

    remarkablePlantUmlRenderer(tokens, id) {
        let plantUml = tokens[id].content;

        plantUml = plantUml.replace('@startuml', '@startuml\nskinparam shadowing false\nskinparam classBorderColor black\nskinparam classArrowColor black\nskinparam DefaultFontSize 14\nskinparam ClassFontStyle bold\nskinparam classAttributeIconSize 0\nhide empty members\n');

        const that = this;
        plantUml = plantUml.replace(/testsColor\(([^)]+)\)/g, function(match, capture) {
            const tests = capture.split(',');
            const status = that.statusForTests(tests);
            return status['done'] ? 'green' : 'red';
        });

        return "<img http-src='/api/plantuml/png?plantuml=" + encodeURIComponent(plantUml) + " '/>";
    }

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
            '<a (click)="showDetailsForTests(latestResult,\'' + tests.toString() + '\')"><span class="text-danger">' + status['label'] + '</span></a>';
        text += '<br />';

        this.steps.push({
            title: tokens[0].title,
            done: status['done']
        });

        return text;
    }

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

    showDetailsForTests(result, tests) {
        if (!result) {
            return;
        }
        const modalRef = this.modalService.open(ResultDetailComponent, {keyboard: true, size: 'lg'});
        modalRef.componentInstance.result = result;
        modalRef.componentInstance.tests = tests;
    }

    /**
     * @function toggleEditorCollapse
     * @descCalls the parent (editorComponent) toggleCollapse method
     * @param $event
     * @param {boolean} horizontal
     */
    toggleEditorCollapse($event: any, horizontal: boolean) {
        this.parent.toggleCollapse($event, horizontal);
    }

    /**
     * @function ngOnDestroy
     * @desc Framework function which is executed when the component is destroyed.
     * Used for component cleanup, close open sockets, connections, subscriptions...
     */
    ngOnDestroy(): void {}
}
