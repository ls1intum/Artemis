import { AfterViewInit, Component, ElementRef, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/core/alert/alert.service';
import { ApollonDiagramService } from 'app/entities/apollon-diagram/apollon-diagram.service';
import { ApollonDiagram } from 'app/entities/apollon-diagram/apollon-diagram.model';

@Component({
    selector: 'jhi-apollon-diagram-create-form',
    templateUrl: './apollon-diagram-create-form.component.html',
    providers: [ApollonDiagramService],
})
export class ApollonDiagramCreateFormComponent implements AfterViewInit {
    apollonDiagram: ApollonDiagram;
    isSaving: boolean;
    @ViewChild('titleInput', { static: false }) titleInput: ElementRef;

    constructor(private activeModal: NgbActiveModal, private apollonDiagramService: ApollonDiagramService, private router: Router, private jhiAlertService: AlertService) {}

    /**
     * Adds focus on the title input field
     */
    ngAfterViewInit() {
        this.titleInput.nativeElement.focus();
    }

    /**
     * Saves the diagram
     */
    save() {
        this.isSaving = true;
        this.apollonDiagramService.create(this.apollonDiagram).subscribe(
            response => {
                const newDiagram = response.body as ApollonDiagram;
                this.isSaving = false;
                this.dismiss();
                this.router.navigate(['apollon-diagrams', newDiagram.id]);
            },
            response => {
                this.jhiAlertService.error('artemisApp.apollonDiagram.create.error');
            },
        );
    }

    /**
     * Closes the modal
     */
    dismiss() {
        this.activeModal.dismiss('cancel');
    }
}
