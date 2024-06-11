import { Component, InputSignal, inject, input } from '@angular/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faXmark } from '@fortawesome/free-solid-svg-icons';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { CompetencyGraphComponent } from 'app/course/learning-paths/components/competency-graph/competency-graph.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@Component({
    selector: 'jhi-competency-graph-modal',
    standalone: true,
    imports: [FontAwesomeModule, CompetencyGraphComponent, ArtemisSharedModule],
    templateUrl: './competency-graph-modal.component.html',
    styleUrl: './competency-graph-modal.component.scss',
})
export class CompetencyGraphModalComponent {
    protected readonly closeIcon = faXmark;

    private readonly activeModal = inject(NgbActiveModal);
    learningPathId: InputSignal<number> = input.required();

    closeModal() {
        this.activeModal.close();
    }
}
