import { AfterViewInit, Component, ElementRef, ViewChild } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/core/util/alert.service';
import { ApollonDiagramService } from 'app/exercises/quiz/manage/apollon-diagrams/apollon-diagram.service';
import { ApollonDiagram } from 'app/entities/apollon-diagram.model';
import { faBan, faSave } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-apollon-diagram-create-form',
    templateUrl: './apollon-diagram-create-form.component.html',
    providers: [ApollonDiagramService],
})
export class ApollonDiagramCreateFormComponent implements AfterViewInit {
    apollonDiagram: ApollonDiagram;
    isSaving: boolean;
    @ViewChild('titleInput', { static: false }) titleInput: ElementRef;

    // Icons
    faBan = faBan;
    faSave = faSave;

    constructor(
        private activeModal: NgbActiveModal,
        private apollonDiagramService: ApollonDiagramService,
        private alertService: AlertService,
    ) {}

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
        this.apollonDiagramService.create(this.apollonDiagram, this.apollonDiagram.courseId!).subscribe({
            next: () => {
                this.isSaving = false;
                this.dismiss();
            },
            error: () => {
                this.alertService.error('artemisApp.apollonDiagram.create.error');
            },
        });
    }

    /**
     * Closes the modal
     */
    dismiss() {
        this.activeModal.dismiss('cancel');
    }
}
