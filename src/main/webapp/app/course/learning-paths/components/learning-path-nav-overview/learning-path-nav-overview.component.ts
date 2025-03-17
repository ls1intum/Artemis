import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked, viewChild } from '@angular/core';
import { NgbAccordionDirective, NgbAccordionModule, NgbDropdownModule, NgbModal } from '@ng-bootstrap/ng-bootstrap';

import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faCheckCircle } from '@fortawesome/free-solid-svg-icons';
import { AlertService } from 'app/shared/service/alert.service';
import { LearningPathCompetencyDTO } from 'app/entities/competency/learning-path.model';
import { LearningPathApiService } from 'app/course/learning-paths/services/learning-path-api.service';
import { CompetencyGraphModalComponent } from 'app/course/learning-paths/components/competency-graph-modal/competency-graph-modal.component';
import { LearningPathNavOverviewLearningObjectsComponent } from 'app/course/learning-paths/components/learning-path-nav-overview-learning-objects/learning-path-nav-overview-learning-objects.component';
import { LearningPathNavigationService } from 'app/course/learning-paths/services/learning-path-navigation.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-learning-path-nav-overview',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FontAwesomeModule, NgbDropdownModule, NgbAccordionModule, LearningPathNavOverviewLearningObjectsComponent, TranslateDirective],
    templateUrl: './learning-path-nav-overview.component.html',
    styleUrl: './learning-path-nav-overview.component.scss',
})
export class LearningPathNavOverviewComponent {
    protected readonly faCheckCircle = faCheckCircle;

    private readonly alertService = inject(AlertService);
    private readonly modalService = inject(NgbModal);
    private readonly learningPathApiService = inject(LearningPathApiService);
    private readonly learningPathNavigationService = inject(LearningPathNavigationService);

    readonly learningPathId = input.required<number>();

    readonly competencyAccordion = viewChild.required(NgbAccordionDirective);

    readonly onLearningObjectSelected = output<void>();
    readonly isLoading = signal(false);
    readonly competencies = signal<LearningPathCompetencyDTO[]>([]);

    // competency id of currently selected learning object
    readonly currentCompetencyId = computed(() => this.learningPathNavigationService.currentLearningObject()?.competencyId);
    // current competency of learning path (not the one of the selected learning object)
    readonly currentCompetencyOnPath = computed(() => this.competencies()?.find((competency) => competency.masteryProgress < 1));

    constructor() {
        effect(() => {
            const learningPathId = this.learningPathId();
            untracked(() => this.loadCompetencies(learningPathId));
        });
    }

    async loadCompetencies(learningPathId: number): Promise<void> {
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

    openCompetencyGraph(): void {
        CompetencyGraphModalComponent.openCompetencyGraphModal(this.modalService, this.learningPathId(), undefined);
    }
}
