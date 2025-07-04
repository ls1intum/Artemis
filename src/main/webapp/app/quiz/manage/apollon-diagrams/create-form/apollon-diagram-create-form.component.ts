import { AfterViewInit, Component, ElementRef, inject, viewChild } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/shared/service/alert.service';
import { ApollonDiagramService } from 'app/quiz/manage/apollon-diagrams/services/apollon-diagram.service';
import { ApollonDiagram } from 'app/modeling/shared/entities/apollon-diagram.model';
import { faSave } from '@fortawesome/free-solid-svg-icons';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-apollon-diagram-create-form',
    templateUrl: './apollon-diagram-create-form.component.html',
    providers: [ApollonDiagramService],
    imports: [FormsModule, TranslateDirective, FaIconComponent],
})
export class ApollonDiagramCreateFormComponent implements AfterViewInit {
    private activeModal = inject(NgbActiveModal);
    private apollonDiagramService = inject(ApollonDiagramService);
    private alertService = inject(AlertService);

    apollonDiagram: ApollonDiagram;
    isSaving: boolean;
    readonly titleInput = viewChild.required<ElementRef>('titleInput');

    // Icons
    faSave = faSave;

    /**
     * Adds focus on the title input field
     */
    ngAfterViewInit() {
        this.titleInput().nativeElement.focus();
    }

    /**
     * Saves the diagram
     */
    save() {
        this.isSaving = true;
        this.apollonDiagramService.create(this.apollonDiagram, this.apollonDiagram.courseId!).subscribe({
            next: ({ body }) => {
                if (body) {
                    this.isSaving = false;
                    this.activeModal.close(body);
                }
            },
            error: () => {
                this.alertService.error('artemisApp.apollonDiagram.create.error');
            },
        });
    }

    /**
     * Cancels the modal
     */
    dismiss() {
        this.activeModal.dismiss('cancel');
    }
}
