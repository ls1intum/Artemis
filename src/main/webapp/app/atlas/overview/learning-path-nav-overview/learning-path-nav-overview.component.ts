import { TumAetUiButtonDirective, TumAetUiDialogService, TumAetUiProgressSpinnerComponent } from '@tumaet/ui-angular';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, linkedSignal, output, signal, untracked } from '@angular/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faCheckCircle, faChevronDown } from '@fortawesome/free-solid-svg-icons';
import { AlertService } from 'app/foundation/service/alert.service';
import { getErrorMessage } from 'app/foundation/util/global.utils';
import { LearningPathCompetencyDTO } from 'app/atlas/shared/entities/learning-path.model';
import { LearningPathApiService } from 'app/atlas/shared/services/learning-path-api.service';
import { CompetencyGraphModalComponent } from 'app/atlas/manage/competency-graph-modal/competency-graph-modal.component';
import { LearningPathNavOverviewLearningObjectsComponent } from 'app/atlas/overview/learning-path-nav-overview-learning-objects/learning-path-nav-overview-learning-objects.component';
import { LearningPathNavigationService } from 'app/atlas/overview/learning-path-navigation.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ScienceEventType } from 'app/foundation/science/science.model';
import { ScienceService } from 'app/foundation/science/science.service';

@Component({
    selector: 'jhi-learning-path-nav-overview',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        FontAwesomeModule,
        TumAetUiButtonDirective,
        TumAetUiProgressSpinnerComponent,
        LearningPathNavOverviewLearningObjectsComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
    ],
    templateUrl: './learning-path-nav-overview.component.html',
})
export class LearningPathNavOverviewComponent {
    private readonly alertService = inject(AlertService);
    private readonly dialogService = inject(TumAetUiDialogService);
    private readonly learningPathApiService = inject(LearningPathApiService);
    private readonly learningPathNavigationService = inject(LearningPathNavigationService);
    private readonly scienceService = inject(ScienceService);

    protected readonly faCheckCircle = faCheckCircle;
    protected readonly faChevronDown = faChevronDown;

    readonly learningPathId = input.required<number>();

    readonly onLearningObjectSelected = output<void>();
    readonly isLoading = signal(false);
    readonly competencies = signal<LearningPathCompetencyDTO[]>([]);

    // competency id of currently selected learning object
    readonly currentCompetencyId = computed(() => this.learningPathNavigationService.currentLearningObject()?.competencyId);
    // current competency of learning path (not the one of the selected learning object)
    readonly currentCompetencyOnPath = computed(() => this.competencies()?.find((competency) => competency.masteryProgress < 1));

    // ids of the expanded competencies: when the competency of the selected learning object changes, it is expanded and the previous one is collapsed
    private readonly expandedCompetencyIds = linkedSignal<number | undefined, Set<number>>({
        source: this.currentCompetencyId,
        computation: (currentCompetencyId, previous) => {
            const expandedCompetencyIds = new Set(previous?.value);
            if (previous?.source !== undefined) {
                expandedCompetencyIds.delete(previous.source);
            }
            if (currentCompetencyId !== undefined) {
                expandedCompetencyIds.add(currentCompetencyId);
            }
            return expandedCompetencyIds;
        },
    });

    constructor() {
        effect(() => {
            const learningPathId = this.learningPathId();

            this.scienceService.logEvent(ScienceEventType.LEARNING_PATH__OPEN_NAVIGATION, learningPathId);

            void untracked(() => this.loadCompetencies(learningPathId));
        });
    }

    async loadCompetencies(learningPathId: number): Promise<void> {
        try {
            this.isLoading.set(true);
            const competencies = await this.learningPathApiService.getLearningPathCompetencies(learningPathId);
            this.competencies.set(competencies);
        } catch (error) {
            this.alertService.error(getErrorMessage(error));
        } finally {
            this.isLoading.set(false);
        }
    }

    selectLearningObject(): void {
        this.onLearningObjectSelected.emit();
        this.expandedCompetencyIds.set(new Set());
    }

    isExpanded(competencyId: number): boolean {
        return this.expandedCompetencyIds().has(competencyId);
    }

    toggleCompetency(competencyId: number): void {
        this.expandedCompetencyIds.update((expandedCompetencyIds) => {
            const updatedCompetencyIds = new Set(expandedCompetencyIds);
            if (!updatedCompetencyIds.delete(competencyId)) {
                updatedCompetencyIds.add(competencyId);
            }
            return updatedCompetencyIds;
        });
    }

    openCompetencyGraph(): void {
        // Logged here rather than in the modal, which is created outside the course overview that provides the ScienceService.
        this.scienceService.logEvent(ScienceEventType.LEARNING_PATH__OPEN_GRAPH, this.learningPathId());
        CompetencyGraphModalComponent.openCompetencyGraphModal(this.dialogService, this.learningPathId(), undefined);
    }
}
