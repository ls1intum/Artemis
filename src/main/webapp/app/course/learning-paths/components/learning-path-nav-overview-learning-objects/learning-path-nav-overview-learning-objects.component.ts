import { Component, InputSignal, OutputEmitterRef, Signal, WritableSignal, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { AlertService } from 'app/core/util/alert.service';
import { LearningPathApiService } from 'app/course/learning-paths/services/learning-path-api.service';
import { LearningPathNavigationService } from 'app/course/learning-paths/services/learning-path-navigation.service';
import { LearningPathNavigationObjectDTO } from 'app/entities/competency/learning-path.model';
import { IconDefinition, faCheckCircle, faLock } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { NgbAccordionModule } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-learning-path-nav-overview-learning-objects',
    standalone: true,
    imports: [NgbAccordionModule, FontAwesomeModule, ArtemisSharedModule],
    templateUrl: './learning-path-nav-overview-learning-objects.component.html',
    styleUrl: './learning-path-nav-overview-learning-objects.component.scss',
})
export class LearningPathNavOverviewLearningObjectsComponent {
    protected readonly faCheckCircle: IconDefinition = faCheckCircle;
    protected readonly faLock: IconDefinition = faLock;

    private readonly alertService: AlertService = inject(AlertService);
    private readonly learningPathApiService: LearningPathApiService = inject(LearningPathApiService);
    private readonly learningPathNavigationService = inject(LearningPathNavigationService);

    readonly learningPathId: InputSignal<number> = input.required();
    readonly competencyId: InputSignal<number> = input.required();
    // competency id of current competency of learning path (not the one of the selected learning object)
    readonly currentCompetencyIdOnPath: InputSignal<number | undefined> = input.required();
    readonly currentLearningObject: Signal<LearningPathNavigationObjectDTO | undefined> = this.learningPathNavigationService.currentLearningObject;

    readonly isLoading: WritableSignal<boolean> = signal(false);
    readonly learningObjects: WritableSignal<LearningPathNavigationObjectDTO[] | undefined> = signal(undefined);

    readonly nextLearningObjectOnPath: Signal<LearningPathNavigationObjectDTO | undefined> = computed(() =>
        this.competencyId() === this.currentCompetencyIdOnPath() ? this.learningObjects()?.find((learningObject) => !learningObject.completed) : undefined,
    );

    readonly onLearningObjectSelected: OutputEmitterRef<void> = output();

    constructor() {
        effect(
            () => {
                untracked(async () => await this.loadLearningObjects());
            },
            { allowSignalWrites: true },
        );
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
