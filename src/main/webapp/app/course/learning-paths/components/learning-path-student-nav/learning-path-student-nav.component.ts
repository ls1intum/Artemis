import { Component, InputSignal, OnInit, computed, inject, input, viewChild } from '@angular/core';
import { LearningPathNavigationObjectDTO } from 'app/entities/competency/learning-path.model';
import { CommonModule } from '@angular/common';
import { NgbAccordionModule, NgbDropdownModule } from '@ng-bootstrap/ng-bootstrap';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { IconDefinition, faCheckCircle, faChevronDown } from '@fortawesome/free-solid-svg-icons';
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
export class LearningPathNavComponent implements OnInit {
    protected readonly faChevronDown: IconDefinition = faChevronDown;
    protected readonly faCheckCircle: IconDefinition = faCheckCircle;

    private learningPathNavigationService = inject(LearningPathNavigationService);

    readonly learningPathId: InputSignal<number> = input.required<number>();

    readonly isLoading = this.learningPathNavigationService.isLoading;

    readonly learningPathProgress = computed(() => this.learningPathNavigationService.learningPathNavigation()?.progress ?? 0);
    readonly predecessorLearningObject = computed(() => this.learningPathNavigationService.learningPathNavigation()?.predecessorLearningObject);
    readonly currentLearningObject = computed(() => this.learningPathNavigationService.currentLearningObject());
    readonly successorLearningObject = computed(() => this.learningPathNavigationService.learningPathNavigation()?.successorLearningObject);

    readonly navOverview = viewChild.required(LearningPathNavOverviewComponent);

    ngOnInit(): void {
        this.learningPathNavigationService.loadInitialLearningPathNavigation(this.learningPathId());
    }

    selectLearningObject(selectedLearningObject: LearningPathNavigationObjectDTO): void {
        this.learningPathNavigationService.loadRelativeLearningPathNavigation(this.learningPathId(), selectedLearningObject);
    }

    setShowNavigationOverview(show: boolean): void {
        if (show) {
            this.navOverview().loadCompetencies(this.learningPathId());
        }
    }
}
