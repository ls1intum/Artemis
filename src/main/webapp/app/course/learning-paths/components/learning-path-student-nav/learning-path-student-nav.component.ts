import { Component, InputSignal, OnInit, WritableSignal, computed, inject, input, signal } from '@angular/core';
import { LearningPathNavigationObjectDto } from 'app/entities/competency/learning-path.model';
import { CommonModule } from '@angular/common';
import { NgbAccordionModule, NgbDropdownModule } from '@ng-bootstrap/ng-bootstrap';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { IconDefinition, faCheckCircle, faChevronDown } from '@fortawesome/free-solid-svg-icons';
import { LearningPathStudentNavOverviewComponent } from 'app/course/learning-paths/components/learning-path-student-nav-overview/learning-path-student-nav-overview.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { LearningPathNavigationService } from 'app/course/learning-paths/learning-path-navigation.service';

export type LoadedValue<T> = {
    isLoading: boolean;
    value?: T | undefined | null;
    error?: Error;
};

@Component({
    selector: 'jhi-learning-path-student-nav',
    standalone: true,
    imports: [CommonModule, NgbDropdownModule, NgbAccordionModule, FontAwesomeModule, LearningPathStudentNavOverviewComponent, ArtemisSharedModule],
    templateUrl: './learning-path-student-nav.component.html',
    styleUrl: './learning-path-student-nav.component.scss',
})
export class LearningPathStudentNavComponent implements OnInit {
    protected readonly faChevronDown: IconDefinition = faChevronDown;
    protected readonly faCheckCircle: IconDefinition = faCheckCircle;

    private learningPathNavigationService = inject(LearningPathNavigationService);

    readonly learningPathId: InputSignal<number> = input.required<number>();

    readonly showNavigationOverview: WritableSignal<boolean> = signal(false);

    readonly isLoading = this.learningPathNavigationService.isLoading;

    readonly learningPathProgress = computed(() => this.learningPathNavigationService.learningPathNavigation()?.progress ?? 0);
    readonly predecessorLearningObject = computed(() => this.learningPathNavigationService.learningPathNavigation()?.predecessorLearningObject);
    readonly currentLearningObject = computed(() => this.learningPathNavigationService.learningPathNavigation()?.currentLearningObject);
    readonly successorLearningObject = computed(() => this.learningPathNavigationService.learningPathNavigation()?.successorLearningObject);

    readonly isCurrentLearningObjectCompleted = this.learningPathNavigationService.isCurrentLearningObjectCompleted;

    ngOnInit(): void {
        this.learningPathNavigationService.loadInitialLearningPathNavigation(this.learningPathId());
    }

    selectLearningObject(selectedLearningObject: LearningPathNavigationObjectDto): void {
        this.learningPathNavigationService.loadRelativeLearningPathNavigation(this.learningPathId(), selectedLearningObject);
    }

    setShowNavigationOverview(show: boolean): void {
        this.showNavigationOverview.set(show);
    }

    setCurrentLearningObjectCompletion(completed: boolean): void {
        this.learningPathNavigationService.setCurrentLearningObjectCompletion(completed);
    }
}
