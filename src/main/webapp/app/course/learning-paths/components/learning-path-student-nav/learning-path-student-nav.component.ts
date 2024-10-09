import { Component, InputSignal, Signal, WritableSignal, computed, effect, inject, input, signal } from '@angular/core';
import { LearningPathNavigationObjectDTO } from 'app/entities/competency/learning-path.model';
import { CommonModule } from '@angular/common';
import { NgbAccordionModule, NgbDropdownModule } from '@ng-bootstrap/ng-bootstrap';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { IconDefinition, faCheckCircle, faChevronDown, faFlag } from '@fortawesome/free-solid-svg-icons';
import { LearningPathNavOverviewComponent } from 'app/course/learning-paths/components/learning-path-nav-overview/learning-path-nav-overview.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { LearningPathNavigationService } from 'app/course/learning-paths/services/learning-path-navigation.service';

@Component({
    selector: 'jhi-learning-path-student-nav',
    standalone: true,
    imports: [CommonModule, NgbDropdownModule, NgbAccordionModule, FontAwesomeModule, LearningPathNavOverviewComponent, ArtemisSharedModule],
    templateUrl: './learning-path-student-nav.component.html',
    styleUrl: './learning-path-student-nav.component.scss',
})
export class LearningPathNavComponent {
    protected readonly faChevronDown: IconDefinition = faChevronDown;
    protected readonly faCheckCircle: IconDefinition = faCheckCircle;
    protected readonly faFlag: IconDefinition = faFlag;

    private learningPathNavigationService: LearningPathNavigationService = inject(LearningPathNavigationService);

    readonly learningPathId: InputSignal<number> = input.required<number>();

    readonly isLoading: WritableSignal<boolean> = this.learningPathNavigationService.isLoading;

    readonly learningPathProgress: Signal<number> = computed(() => this.learningPathNavigationService.learningPathNavigation()?.progress ?? 0);
    readonly predecessorLearningObject: Signal<LearningPathNavigationObjectDTO | undefined> = computed(
        () => this.learningPathNavigationService.learningPathNavigation()?.predecessorLearningObject,
    );
    readonly currentLearningObject: Signal<LearningPathNavigationObjectDTO | undefined> = computed(() => this.learningPathNavigationService.currentLearningObject());
    readonly successorLearningObject: Signal<LearningPathNavigationObjectDTO | undefined> = computed(
        () => this.learningPathNavigationService.learningPathNavigation()?.successorLearningObject,
    );

    readonly isDropdownOpen: WritableSignal<boolean> = signal(false);

    constructor() {
        effect(async () => await this.learningPathNavigationService.loadLearningPathNavigation(this.learningPathId()), { allowSignalWrites: true });
    }

    async selectLearningObject(selectedLearningObject: LearningPathNavigationObjectDTO): Promise<void> {
        await this.learningPathNavigationService.loadRelativeLearningPathNavigation(this.learningPathId(), selectedLearningObject);
    }

    completeLearningPath(): void {
        this.learningPathNavigationService.completeLearningPath();
    }

    setIsDropdownOpen(isOpen: boolean): void {
        this.isDropdownOpen.set(isOpen);
    }
}
