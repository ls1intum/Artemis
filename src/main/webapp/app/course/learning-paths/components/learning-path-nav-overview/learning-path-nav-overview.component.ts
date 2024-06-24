import { Component, inject, input, output, signal, viewChild } from '@angular/core';
import { NgbAccordionDirective, NgbAccordionModule, NgbDropdownModule, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { CommonModule } from '@angular/common';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { IconDefinition, faCheckCircle } from '@fortawesome/free-solid-svg-icons';
import { AlertService } from 'app/core/util/alert.service';
import { LearningPathCompetencyDTO } from 'app/entities/competency/learning-path.model';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { LearningPathApiService } from 'app/course/learning-paths/services/learning-path-api.service';
import { CompetencyGraphModalComponent } from 'app/course/learning-paths/components/competency-graph-modal/competency-graph-modal.component';
import { LearningPathNavOverviewLearningObjectsComponent } from 'app/course/learning-paths/components/learning-path-nav-overview-learning-objects/learning-path-nav-overview-learning-objects.component';

@Component({
    selector: 'jhi-learning-path-nav-overview',
    standalone: true,
    imports: [FontAwesomeModule, CommonModule, NgbDropdownModule, NgbAccordionModule, ArtemisSharedModule, LearningPathNavOverviewLearningObjectsComponent],
    templateUrl: './learning-path-nav-overview.component.html',
    styleUrl: './learning-path-nav-overview.component.scss',
})
export class LearningPathNavOverviewComponent {
    protected readonly faCheckCircle: IconDefinition = faCheckCircle;

    private readonly alertService: AlertService = inject(AlertService);
    private readonly modalService: NgbModal = inject(NgbModal);
    private readonly learningPathApiService: LearningPathApiService = inject(LearningPathApiService);

    readonly learningPathId = input.required<number>();

    readonly competencyAccordion = viewChild.required(NgbAccordionDirective);

    readonly onLearningObjectSelected = output<void>();
    readonly isLoading = signal(false);
    readonly competencies = signal<LearningPathCompetencyDTO[] | undefined>(undefined);

    async loadCompetencies(learningPathId: number): Promise<void> {
        if (this.competencies()) {
            return;
        }
        try {
            this.isLoading.set(true);
            const competencies = await this.learningPathApiService.getLearningPathCompetencies(learningPathId);
            this.competencies.set(competencies);
        } catch (error) {
            this.alertService.error(error);
        } finally {
            this.isLoading.set(false);
        }
    }

    selectLearningObject(): void {
        this.onLearningObjectSelected.emit();
        this.competencyAccordion().collapseAll();
    }

    openCompetencyGraph() {
        const modalRef = this.modalService.open(CompetencyGraphModalComponent, {
            size: 'xl',
            backdrop: 'static',
            windowClass: 'competency-graph-modal',
        });
        modalRef.componentInstance.learningPathId = this.learningPathId;
    }
}
