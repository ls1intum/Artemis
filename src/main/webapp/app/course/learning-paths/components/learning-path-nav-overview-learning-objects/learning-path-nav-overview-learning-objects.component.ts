import { Component, inject, input, signal } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { AlertService } from 'app/core/util/alert.service';
import { LearningPathApiService } from 'app/course/learning-paths/services/learning-path-api.service';
import { LearningPathNavigationService } from 'app/course/learning-paths/services/learning-path-navigation.service';
import { LearningObjectType, LearningPathNavigationObjectDTO } from 'app/entities/competency/learning-path.model';
import { IconDefinition, faCheckCircle } from '@fortawesome/free-solid-svg-icons';
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

    private readonly alertService: AlertService = inject(AlertService);
    private readonly learningPathApiService: LearningPathApiService = inject(LearningPathApiService);
    private readonly learningPathNavigationService = inject(LearningPathNavigationService);

    readonly learningPathId = input.required<number>();
    readonly competencyId = input.required<number>();
    readonly currentLearningObject = this.learningPathNavigationService.currentLearningObject;

    readonly isLoading = signal(false);
    readonly learningObjects = signal<LearningPathNavigationObjectDTO[] | undefined>(undefined);

    async loadLearningObjects() {
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

    selectLearningObject(learningObject: LearningPathNavigationObjectDTO): void {
        if (this.isLearningObjectSelectable(learningObject)) {
            this.learningPathNavigationService.loadRelativeLearningPathNavigation(this.learningPathId(), learningObject);
        }
    }

    isEqualToCurrentLearningObject(id: number, type: LearningObjectType): boolean {
        return this.currentLearningObject()?.id === id && this.currentLearningObject()?.type === type;
    }

    isLearningObjectSelectable(learningObject: LearningPathNavigationObjectDTO): boolean {
        const indexOfLearningObject = this.learningObjects()!.indexOf(learningObject);
        return indexOfLearningObject > 0 ? this.learningObjects()![indexOfLearningObject - 1].completed : true;
    }
}
