import { Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';
import { ApollonDiagram, ApollonDiagramService } from '../entities/apollon-diagram';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ApollonEditor, UMLModel, ApollonMode, DiagramType } from '@ls1intum/apollon';
import { ActivatedRoute } from '@angular/router';
import * as ApollonDiagramTitleFormatter from './apollon-diagram-title-formatter';

@Component({
    selector: 'jhi-apollon-diagram-student',
    templateUrl: './apollon-diagram-student.component.html',
    providers: []
})
export class ApollonDiagramStudentComponent implements OnInit, OnDestroy {
    @ViewChild('editorContainer') editorContainer: ElementRef;

    diagramTitle = '';
    diagram: ApollonDiagram | null = null;
    apollonEditor: ApollonEditor | null = null;
    submitted: boolean;

    constructor(
        private apollonDiagramService: ApollonDiagramService,
        private jhiAlertService: JhiAlertService,
        private modalService: NgbModal,
        private route: ActivatedRoute
    ) {
        this.submitted = false;
    }

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

    initializeApollonEditor(model: UMLModel) {
        if (this.apollonEditor !== null) {
            this.apollonEditor.destroy();
        }

        this.apollonEditor = new ApollonEditor(this.editorContainer.nativeElement, {
            model,
            mode: ApollonMode.Modelling,
            type: DiagramType.ClassDiagram
        });
    }

    saveDiagram() {
        if (this.diagram === null) {
            // Should never happen, but let's be defensive anyway
            return;
        }

        const diagramState = this.apollonEditor.model;
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

    submit() {
        // TODO: handle submit case. set diagram to readonly
    }
}
