import { Component, InputSignal, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { NgbAccordionModule, NgbDropdownModule } from '@ng-bootstrap/ng-bootstrap';
import { CommonModule } from '@angular/common';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { IconDefinition, faCheckCircle } from '@fortawesome/free-solid-svg-icons';
import { AlertService } from 'app/core/util/alert.service';
import { LearningObjectType, LearningPathNavigationObjectDto, LearningPathNavigationOverviewDto } from 'app/entities/competency/learning-path.model';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { LearningPathNavigationService } from 'app/course/learning-paths/services/learning-path-navigation.service';
import { LearningPathApiService } from 'app/course/learning-paths/services/learning-path-api.service';

@Component({
    selector: 'jhi-learning-path-student-nav-overview',
    standalone: true,
    imports: [FontAwesomeModule, CommonModule, NgbDropdownModule, NgbAccordionModule, ArtemisSharedModule],
    templateUrl: './learning-path-student-nav-overview.component.html',
})
export class LearningPathStudentNavOverviewComponent implements OnInit {
    protected readonly faCheckCircle: IconDefinition = faCheckCircle;

    private readonly alertService: AlertService = inject(AlertService);
    private readonly learningPathApiService: LearningPathApiService = inject(LearningPathApiService);
    private readonly learningPathNavigationService = inject(LearningPathNavigationService);

    readonly learningPathId: InputSignal<number> = input.required();

    readonly onLearningObjectSelected = output<void>();
    readonly isLoading = signal(false);
    private readonly navigationOverview = signal<LearningPathNavigationOverviewDto | undefined>(undefined);
    readonly learningObjects = computed(() => this.navigationOverview()?.learningObjects ?? []);
    readonly currentLearningObject = this.learningPathNavigationService.currentLearningObject;

    async ngOnInit(): Promise<void> {
        await this.loadNavigationOverview(this.learningPathId());
    }

    private async loadNavigationOverview(learningPathId: number): Promise<void> {
        this.isLoading.set(true);
        try {
            const navigationOverview = await this.learningPathApiService.getLearningPathNavigationOverview(learningPathId);
            this.navigationOverview.set(navigationOverview);
        } catch (error) {
            this.alertService.error(error);
        }
        this.isLoading.set(false);
    }

    selectLearningObject(learningObject: LearningPathNavigationObjectDto): void {
        if (this.isLearningObjectSelectable(learningObject)) {
            this.learningPathNavigationService.loadRelativeLearningPathNavigation(this.learningPathId(), learningObject);
            this.onLearningObjectSelected.emit();
        }
    }

    isEqualToCurrentLearningObject(id: number, type: LearningObjectType): boolean {
        return this.currentLearningObject()?.id === id && this.currentLearningObject()?.type === type;
    }

    isLearningObjectSelectable(learningObject: LearningPathNavigationObjectDto): boolean {
        const indexOfLearningObject = this.learningObjects().indexOf(learningObject);
        return indexOfLearningObject > 0 ? this.learningObjects()[indexOfLearningObject - 1].completed : true;
    }
}
