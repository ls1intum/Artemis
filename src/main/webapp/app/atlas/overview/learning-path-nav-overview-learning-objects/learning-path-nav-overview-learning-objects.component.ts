import { TumAetUiProgressSpinnerComponent, TumAetUiTagComponent } from '@tumaet/ui-angular';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { AlertService } from 'app/foundation/service/alert.service';
import { getErrorMessage } from 'app/foundation/util/global.utils';
import { LearningPathApiService } from 'app/atlas/shared/services/learning-path-api.service';
import { LearningPathNavigationService } from 'app/atlas/overview/learning-path-navigation.service';
import { LearningPathNavigationObjectDTO } from 'app/atlas/shared/entities/learning-path.model';
import { faCheckCircle, faLock } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { NgClass } from '@angular/common';

@Component({
    selector: 'jhi-learning-path-nav-overview-learning-objects',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FontAwesomeModule, TranslateDirective, ArtemisTranslatePipe, NgClass, TumAetUiProgressSpinnerComponent, TumAetUiTagComponent],
    templateUrl: './learning-path-nav-overview-learning-objects.component.html',
})
export class LearningPathNavOverviewLearningObjectsComponent {
    private readonly alertService = inject(AlertService);
    private readonly learningPathApiService = inject(LearningPathApiService);
    private readonly learningPathNavigationService = inject(LearningPathNavigationService);

    protected readonly faCheckCircle = faCheckCircle;
    protected readonly faLock = faLock;

    readonly learningPathId = input.required<number>();
    readonly competencyId = input.required<number>();
    // competency id of current competency of learning path (not the one of the selected learning object)
    readonly currentCompetencyIdOnPath = input.required();
    readonly currentLearningObject = this.learningPathNavigationService.currentLearningObject;

    readonly isLoading = signal<boolean>(false);
    readonly learningObjects = signal<LearningPathNavigationObjectDTO[] | undefined>(undefined);

    readonly nextLearningObjectOnPath = computed(() =>
        this.competencyId() === this.currentCompetencyIdOnPath() ? this.learningObjects()?.find((learningObject) => !learningObject.completed) : undefined,
    );

    readonly onLearningObjectSelected = output<void>();

    constructor() {
        effect(() => {
            untracked(() => void this.loadLearningObjects());
        });
    }

    async loadLearningObjects(): Promise<void> {
        if (this.learningObjects()) {
            return;
        }
        try {
            this.isLoading.set(true);
            const learningObjects = await this.learningPathApiService.getLearningPathCompetencyLearningObjects(this.learningPathId(), this.competencyId());
            this.learningObjects.set(learningObjects);
        } catch (error) {
            this.alertService.error(getErrorMessage(error));
        } finally {
            this.isLoading.set(false);
        }
    }

    async selectLearningObject(learningObject: LearningPathNavigationObjectDTO): Promise<void> {
        if (!learningObject.unreleased) {
            await this.learningPathNavigationService.loadRelativeLearningPathNavigation(this.learningPathId(), learningObject);
            this.onLearningObjectSelected.emit();
        }
    }
}
