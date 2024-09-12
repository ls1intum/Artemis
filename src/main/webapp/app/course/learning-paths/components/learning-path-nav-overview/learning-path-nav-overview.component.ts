import { Component, InputSignal, OutputEmitterRef, Signal, WritableSignal, computed, effect, inject, input, output, signal, viewChild } from '@angular/core';
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
import { LearningPathNavigationService } from 'app/course/learning-paths/services/learning-path-navigation.service';

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
    private readonly learningPathNavigationService = inject(LearningPathNavigationService);

    readonly learningPathId: InputSignal<number> = input.required();

    readonly competencyAccordion: Signal<NgbAccordionDirective> = viewChild.required(NgbAccordionDirective);

    readonly onLearningObjectSelected: OutputEmitterRef<void> = output();
    readonly isLoading: WritableSignal<boolean> = signal(false);
    readonly competencies = signal<LearningPathCompetencyDTO[]>([]);

    // competency id of currently selected learning object
    readonly currentCompetencyId: Signal<number | undefined> = computed(() => this.learningPathNavigationService.currentLearningObject()?.competencyId);
    // current competency of learning path (not the one of the selected learning object)
    readonly currentCompetencyOnPath: Signal<LearningPathCompetencyDTO | undefined> = computed(() => this.competencies()?.find((competency) => competency.masteryProgress < 1));

    constructor() {
        effect(async () => await this.loadCompetencies(this.learningPathId()), { allowSignalWrites: true });
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
        const modalRef = this.modalService.open(CompetencyGraphModalComponent, {
            size: 'xl',
            backdrop: 'static',
            windowClass: 'competency-graph-modal',
        });
        modalRef.componentInstance.learningPathId = this.learningPathId;
    }
}
