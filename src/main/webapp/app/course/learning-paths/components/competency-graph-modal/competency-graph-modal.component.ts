import { Component, inject } from '@angular/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faXmark } from '@fortawesome/free-solid-svg-icons';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { CompetencyGraphComponent } from 'app/course/learning-paths/components/competency-graph/competency-graph.component';

@Component({
    selector: 'jhi-competency-graph-modal',
    standalone: true,
    imports: [FontAwesomeModule, CompetencyGraphComponent],
    templateUrl: './competency-graph-modal.component.html',
    styleUrl: './competency-graph-modal.component.scss',
})
export class CompetencyGraphModalComponent {
    protected readonly closeIcon = faXmark;

    private readonly activeModal = inject(NgbActiveModal);

    closeModal() {
        this.activeModal.close();
    }
}
