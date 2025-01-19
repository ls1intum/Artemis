import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { AlertService } from 'app/core/util/alert.service';
import { LearningPathApiService } from 'app/course/learning-paths/services/learning-path-api.service';
import { LearningPathNavigationService } from 'app/course/learning-paths/services/learning-path-navigation.service';
import { LearningPathNavigationObjectDTO } from 'app/entities/competency/learning-path.model';
import { faCheckCircle, faLock } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { NgbAccordionModule } from '@ng-bootstrap/ng-bootstrap';

import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgClass } from '@angular/common';

@Component({
    selector: 'jhi-learning-path-nav-overview-learning-objects',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [NgbAccordionModule, FontAwesomeModule, TranslateDirective, NgClass],
    templateUrl: './learning-path-nav-overview-learning-objects.component.html',
    styleUrl: './learning-path-nav-overview-learning-objects.component.scss',
})
export class LearningPathNavOverviewLearningObjectsComponent {
    protected readonly faCheckCircle = faCheckCircle;
    protected readonly faLock = faLock;

    private readonly alertService = inject(AlertService);
    private readonly learningPathApiService = inject(LearningPathApiService);
    private readonly learningPathNavigationService = inject(LearningPathNavigationService);

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
            untracked(() => this.loadLearningObjects());
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
            this.alertService.error(error);
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
