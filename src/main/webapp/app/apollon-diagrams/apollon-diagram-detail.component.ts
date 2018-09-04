import { Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import ApollonEditor, { State } from '@ls1intum/apollon';
import { JhiAlertService } from 'ng-jhipster';
import * as ApollonDiagramTitleFormatter from './apollonDiagramTitleFormatter';
import { ApollonQuizExerciseGenerationComponent } from './exercise-generation/apollon-quiz-exercise-generation.component';
import { ApollonDiagram, ApollonDiagramService } from '../entities/apollon-diagram';

@Component({
    selector: 'jhi-apollon-diagram-detail',
    templateUrl: './apollon-diagram-detail.component.html',
    providers: [ApollonDiagramService]
})
export class ApollonDiagramDetailComponent implements OnInit, OnDestroy {
    @ViewChild('editorContainer') editorContainer: ElementRef;

    diagramTitle = '';
    diagram: ApollonDiagram | null = null;
    apollonEditor: ApollonEditor | null = null;

    constructor(
        private apollonDiagramService: ApollonDiagramService,
        private jhiAlertService: JhiAlertService,
        private modalService: NgbModal,
        private route: ActivatedRoute
    ) {}

    ngOnInit() {
        this.route.params.subscribe(params => {
            const id = Number(params['id']);

            this.apollonDiagramService.find(id).subscribe(
                response => {
                    const diagram = response.body;

                    this.diagram = diagram;
                    this.diagramTitle = ApollonDiagramTitleFormatter.getTitle(diagram);

                    const state = JSON.parse(diagram.jsonRepresentation);
                    this.initializeApollonEditor(state);
                },
                response => {
                    this.jhiAlertService.error('Error while loading Apollon diagram');
                }
            );
        });
    }

    ngOnDestroy() {
        if (this.apollonEditor !== null) {
            this.apollonEditor.destroy();
        }
    }

    initializeApollonEditor(initialState: State) {
        if (this.apollonEditor !== null) {
            this.apollonEditor.destroy();
        }

        this.apollonEditor = new ApollonEditor(this.editorContainer.nativeElement, {
            mode: 'FULL',
            initialState,
            diagramType: 'CLASS'
        });
    }

    saveDiagram() {
        if (this.diagram === null) {
            // Should never happen, but let's be defensive anyway
            return;
        }

        const diagramState = this.apollonEditor.getState();
        const updatedDiagram: ApollonDiagram = {
            ...this.diagram,
            jsonRepresentation: JSON.stringify(diagramState)
        };

        this.apollonDiagramService.update(updatedDiagram).subscribe(
            () => {},
            response => {
                this.jhiAlertService.error('Error while updating Apollon diagram');
            }
        );
    }

    generateExercise() {
        const modalRef = this.modalService.open(ApollonQuizExerciseGenerationComponent, { backdrop: 'static' });
        const modalComponentInstance = modalRef.componentInstance as ApollonQuizExerciseGenerationComponent;
        modalComponentInstance.apollonEditor = this.apollonEditor;
    }
}
