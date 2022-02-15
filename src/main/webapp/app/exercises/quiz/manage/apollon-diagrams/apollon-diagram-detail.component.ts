import { Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ApollonEditor, ApollonMode, Locale, UMLModel } from '@ls1intum/apollon';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { ApollonQuizExerciseGenerationComponent } from './exercise-generation/apollon-quiz-exercise-generation.component';
import { convertRenderedSVGToPNG } from './exercise-generation/svg-renderer';
import { ApollonDiagramService } from 'app/exercises/quiz/manage/apollon-diagrams/apollon-diagram.service';
import { ApollonDiagram } from 'app/entities/apollon-diagram.model';
import { AlertService } from 'app/core/util/alert.service';
import { AUTOSAVE_CHECK_INTERVAL, AUTOSAVE_EXERCISE_INTERVAL } from 'app/shared/constants/exercise-exam-constants';
import { TranslateService } from '@ngx-translate/core';
import { faDownload, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-apollon-diagram-detail',
    templateUrl: './apollon-diagram-detail.component.html',
    providers: [ApollonDiagramService],
})
export class ApollonDiagramDetailComponent implements OnInit, OnDestroy {
    @ViewChild('editorContainer', { static: false }) editorContainer: ElementRef;

    apollonDiagram?: ApollonDiagram;
    apollonEditor?: ApollonEditor;

    /**  */
    autoSaveInterval: number;
    autoSaveTimer: number;

    /** Whether to crop the downloaded image to the selection. */
    crop = true;
    private courseId: number;

    /** Whether some elements are interactive in the apollon editor. */
    get hasInteractive(): boolean {
        return !!this.apollonEditor && !![...this.apollonEditor.model.interactive.elements, ...this.apollonEditor.model.interactive.relationships].length;
    }

    /** Whether some elements are selected in the apollon editor. */
    get hasSelection(): boolean {
        return !!this.apollonEditor && !![...this.apollonEditor.selection.elements, ...this.apollonEditor.selection.relationships].length;
    }

    // Icons
    faDownload = faDownload;
    faQuestionCircle = faQuestionCircle;

    constructor(
        private apollonDiagramService: ApollonDiagramService,
        private alertService: AlertService,
        private translateService: TranslateService,
        private languageHelper: JhiLanguageHelper,
        private modalService: NgbModal,
        private route: ActivatedRoute,
    ) {}

    /**
     * Initializes Apollon Editor and sets auto save timer
     */
    ngOnInit() {
        this.route.params.subscribe((params) => {
            const id = Number(params['id']);
            this.courseId = Number(params['courseId']);

            this.apollonDiagramService.find(id, this.courseId).subscribe({
                next: (response) => {
                    const diagram = response.body!;

                    this.apollonDiagram = diagram;

                    const model: UMLModel = diagram.jsonRepresentation && JSON.parse(diagram.jsonRepresentation);
                    this.initializeApollonEditor(model);
                    this.setAutoSaveTimer();
                },
                error: () => {
                    this.alertService.error('artemisApp.apollonDiagram.detail.error.loading');
                },
            });
        });

        this.languageHelper.language.subscribe((languageKey: string) => {
            if (this.apollonEditor) {
                this.apollonEditor.locale = languageKey as Locale;
            }
        });
    }

    /**
     * Clears auto save interval and destroys Apollon Editor
     */
    ngOnDestroy() {
        clearInterval(this.autoSaveInterval);
        if (this.apollonEditor) {
            this.apollonEditor.destroy();
        }
    }

    /**
     * Initializes Apollon Editor with UML Model
     * @param initialModel
     */
    initializeApollonEditor(initialModel: UMLModel) {
        if (this.apollonEditor) {
            this.apollonEditor.destroy();
        }

        this.apollonEditor = new ApollonEditor(this.editorContainer.nativeElement, {
            mode: ApollonMode.Exporting,
            model: initialModel,
            type: this.apollonDiagram!.diagramType,
            locale: this.translateService.currentLang as Locale,
        });
    }

    /**
     * Saves the diagram
     */
    saveDiagram() {
        if (!this.apollonDiagram) {
            return;
        }

        const umlModel = this.apollonEditor!.model;
        const updatedDiagram: ApollonDiagram = {
            ...this.apollonDiagram,
            jsonRepresentation: JSON.stringify(umlModel),
        };

        this.apollonDiagramService.update(updatedDiagram, this.courseId).subscribe({
            next: () => this.setAutoSaveTimer(),
            error: () => this.alertService.error('artemisApp.apollonDiagram.update.error'),
        });
    }

    /**
     * This function sets and starts an auto-save timer that automatically saves changes
     * to the model after 30 seconds.
     */
    private setAutoSaveTimer(): void {
        this.autoSaveInterval = window.setInterval(() => {
            this.autoSaveTimer++;
            if (this.autoSaveTimer >= AUTOSAVE_EXERCISE_INTERVAL) {
                this.autoSaveTimer = 0;
                this.saveDiagram();
            }
        }, AUTOSAVE_CHECK_INTERVAL);
    }

    /**
     * Opens a modal to select a course and finally generate the Drag and Drop Model Quiz.
     *
     * @async
     */
    async generateExercise() {
        if (!this.hasInteractive) {
            return;
        }

        const modalRef = this.modalService.open(ApollonQuizExerciseGenerationComponent, { backdrop: 'static' });
        const modalComponentInstance = modalRef.componentInstance as ApollonQuizExerciseGenerationComponent;
        modalComponentInstance.apollonEditor = this.apollonEditor!;
        modalComponentInstance.diagramTitle = this.apollonDiagram!.title!;

        try {
            const result = await modalRef.result;
            if (result) {
                this.alertService.success('artemisApp.apollonDiagram.create.success', { title: result.title });
            }
        } catch (error) {
            this.alertService.error('artemisApp.apollonDiagram.create.error');
            throw error;
        }
    }

    /**
     * Download the current selection of the diagram as a PNG image.
     *
     * @async
     */
    async downloadSelection() {
        if (!this.hasSelection) {
            return;
        }

        const selection = [...this.apollonEditor!.selection.elements, ...this.apollonEditor!.selection.relationships];
        const svg = this.apollonEditor!.exportAsSVG({
            keepOriginalSize: !this.crop,
            include: selection,
        });
        const png = await convertRenderedSVGToPNG(svg);
        this.download(png);
    }

    /**
     * Automatically trigger the download of a file.
     *
     * @param {Blob | File} file A `Blob` or `File` object which should be downloaded.
     */
    private download(file: Blob | File) {
        const anchor = document.createElement('a');
        document.body.appendChild(anchor);
        const url = window.URL.createObjectURL(file);
        anchor.href = url;
        anchor.download = `${this.apollonDiagram!.title}.png`;
        anchor.click();

        // Async revoke of ObjectURL to prevent failure on larger files.
        setTimeout(() => {
            window.URL.revokeObjectURL(url);
            document.body.removeChild(anchor);
        }, 0);
    }
}
