import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { LearningPathNavigationObjectDTO } from 'app/entities/competency/learning-path.model';
import { CommonModule } from '@angular/common';
import { NgbAccordionModule, NgbDropdownModule } from '@ng-bootstrap/ng-bootstrap';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faCheckCircle, faChevronDown, faChevronLeft, faChevronRight, faFlag, faSpinner } from '@fortawesome/free-solid-svg-icons';
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
    protected readonly faChevronDown = faChevronDown;
    protected readonly faCheckCircle = faCheckCircle;
    protected readonly faFlag = faFlag;
    protected readonly faSpinner = faSpinner;
    protected readonly faChevronLeft = faChevronLeft;
    protected readonly faChevronRight = faChevronRight;

    private learningPathNavigationService = inject(LearningPathNavigationService);

    readonly learningPathId = input.required<number>();

    readonly isLoading = this.learningPathNavigationService.isLoading;
    readonly isLoadingPredecessor = signal<boolean>(false);
    readonly isLoadingSuccessor = signal<boolean>(false);

    private readonly learningPathNavigation = this.learningPathNavigationService.learningPathNavigation;
    readonly learningPathProgress = computed(() => this.learningPathNavigation()?.progress ?? 0);
    readonly predecessorLearningObject = computed(() => this.learningPathNavigation()?.predecessorLearningObject);
    readonly currentLearningObject = computed(() => this.learningPathNavigation()?.currentLearningObject);
    readonly successorLearningObject = computed(() => this.learningPathNavigation()?.successorLearningObject);

    readonly isDropdownOpen = signal<boolean>(false);

    constructor() {
        effect(
            () => {
                const learningPathId = this.learningPathId();
                untracked(() => this.learningPathNavigationService.loadLearningPathNavigation(learningPathId));
            },
            { allowSignalWrites: true },
        );
    }

    async selectLearningObject(selectedLearningObject: LearningPathNavigationObjectDTO, isSuccessor: boolean): Promise<void> {
        const loadingSpinner = isSuccessor ? this.isLoadingSuccessor : this.isLoadingPredecessor;
        loadingSpinner.set(true);
        await this.learningPathNavigationService.loadRelativeLearningPathNavigation(this.learningPathId(), selectedLearningObject);
        loadingSpinner.set(false);
    }

    completeLearningPath(): void {
        this.learningPathNavigationService.completeLearningPath();
    }

    setIsDropdownOpen(isOpen: boolean): void {
        this.isDropdownOpen.set(isOpen);
    }
}
