import { Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ApollonEditor, ApollonMode, Locale, UMLModel } from '@ls1intum/apollon';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiLanguageHelper } from 'app/core';
import { JhiAlertService, JhiLanguageService } from 'ng-jhipster';
import { ApollonDiagram, ApollonDiagramService } from '../entities/apollon-diagram';
import { ApollonQuizExerciseGenerationComponent } from './exercise-generation/apollon-quiz-exercise-generation.component';

@Component({
    selector: 'jhi-apollon-diagram-detail',
    templateUrl: './apollon-diagram-detail.component.html',
    providers: [ApollonDiagramService, JhiAlertService, { provide: JhiLanguageService, useClass: JhiLanguageService }],
})
export class ApollonDiagramDetailComponent implements OnInit, OnDestroy {
    @ViewChild('editorContainer') editorContainer: ElementRef;

    apollonDiagram: ApollonDiagram | null = null;
    apollonEditor: ApollonEditor | null = null;

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

                    const model = JSON.parse(diagram.jsonRepresentation || '{}');
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
}
