import { Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ApollonEditor, ApollonMode, Locale, UMLModel } from '@ls1intum/apollon';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiLanguageHelper } from 'app/core';
import { JhiAlertService, JhiLanguageService } from 'ng-jhipster';
import { ApollonDiagram, ApollonDiagramService } from '../entities/apollon-diagram';
import { ApollonQuizExerciseGenerationComponent } from './exercise-generation/apollon-quiz-exercise-generation.component';
import { convertRenderedSVGToPNG } from './exercise-generation/svg-renderer';

@Component({
    selector: 'jhi-apollon-diagram-detail',
    templateUrl: './apollon-diagram-detail.component.html',
    providers: [ApollonDiagramService, JhiAlertService, { provide: JhiLanguageService, useClass: JhiLanguageService }],
})
export class ApollonDiagramDetailComponent implements OnInit, OnDestroy {
    @ViewChild('editorContainer') editorContainer: ElementRef;

    apollonDiagram: ApollonDiagram | null = null;
    apollonEditor: ApollonEditor | null = null;

    /** Wether to crop the downloaded image to the selection. */
    crop = true;

    constructor(
        private apollonDiagramService: ApollonDiagramService,
        private jhiAlertService: JhiAlertService,
        private languageService: JhiLanguageService,
        private languageHelper: JhiLanguageHelper,
        private modalService: NgbModal,
        private route: ActivatedRoute,
    ) {}

    ngOnInit() {
        this.route.params.subscribe(params => {
            const id = Number(params['id']);

            this.apollonDiagramService.find(id).subscribe(
                response => {
                    const diagram = response.body;

                    this.apollonDiagram = diagram;

                    const model: UMLModel = diagram.jsonRepresentation && JSON.parse(diagram.jsonRepresentation);
                    this.initializeApollonEditor(model);
                },
                response => {
                    this.jhiAlertService.error('arTeMiSApp.apollonDiagram.detail.error.loading');
                },
            );
        });

        this.languageHelper.language.subscribe((languageKey: string) => {
            if (this.apollonEditor !== null) {
                this.apollonEditor.locale = languageKey as Locale;
            }
        });
    }

    ngOnDestroy() {
        if (this.apollonEditor !== null) {
            this.apollonEditor.destroy();
        }
    }

    initializeApollonEditor(initialModel: UMLModel) {
        if (this.apollonEditor !== null) {
            this.apollonEditor.destroy();
        }

        this.apollonEditor = new ApollonEditor(this.editorContainer.nativeElement, {
            mode: ApollonMode.Exporting,
            model: initialModel,
            type: this.apollonDiagram.diagramType,
            locale: this.languageService.currentLang as Locale,
        });
    }

    saveDiagram() {
        if (this.apollonDiagram === null) {
            // Should never happen, but let's be defensive anyway
            return;
        }

        const umlModel = this.apollonEditor.model;
        const updatedDiagram: ApollonDiagram = {
            ...this.apollonDiagram,
            jsonRepresentation: JSON.stringify(umlModel),
        };

        this.apollonDiagramService.update(updatedDiagram).subscribe(
            () => {},
            response => {
                this.jhiAlertService.error('arTeMiSApp.apollonDiagram.update.error');
            },
        );
    }

    generateExercise() {
        const modalRef = this.modalService.open(ApollonQuizExerciseGenerationComponent, { backdrop: 'static' });
        const modalComponentInstance = modalRef.componentInstance as ApollonQuizExerciseGenerationComponent;
        modalComponentInstance.apollonEditor = this.apollonEditor;
        modalComponentInstance.diagramTitle = this.apollonDiagram.title;
    }

    /**
     * Download the current selection of the diagram as a PNG image.
     *
     * @async
     */
    async downloadSelection() {
        const { selection } = this.apollonEditor;
        const svg = this.apollonEditor.exportAsSVG({
            keepOriginalSize: !this.crop,
            include: [...selection.elements, ...selection.relationships],
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
        anchor.download = `${this.apollonDiagram.title}.png`;
        anchor.click();

        // Async revoke of ObjectURL to prevent failure on larger files.
        setTimeout(() => {
            window.URL.revokeObjectURL(url);
            document.body.removeChild(anchor);
        }, 0);
    }
}
