import { Component, computed, inject, input, signal } from '@angular/core';
import { LearningPathService } from 'app/course/learning-paths/learning-path.service';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { LearningPathNavigationDto, LearningPathNavigationObjectDto } from 'app/entities/competency/learning-path.model';
import { Observable, catchError, map, of, startWith, switchMap } from 'rxjs';
import { CommonModule } from '@angular/common';
import { NgbAccordionModule, NgbDropdownModule } from '@ng-bootstrap/ng-bootstrap';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faCheckCircle, faChevronDown } from '@fortawesome/free-solid-svg-icons';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { LearningPathStudentNavOverviewComponent } from 'app/course/learning-paths/components/learning-path-student-nav-overview/learning-path-student-nav-overview.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

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
export class LearningPathStudentNavComponent {
    protected readonly faChevronDown = faChevronDown;
    protected readonly faCheckCircle = faCheckCircle;

    private learningPathService = inject(LearningPathService);
    private alertService = inject(AlertService);

    readonly learningPathId = input.required<number>();

    private readonly selectedLearningObject = signal<LearningPathNavigationObjectDto | undefined>(undefined);

    private readonly navigationData$ = toObservable(this.selectedLearningObject).pipe(
        switchMap((selectedLearningObject) => this.learningPathService.getLearningPathNavigation(this.learningPathId(), selectedLearningObject?.id, selectedLearningObject?.type)),
        map((response) => ({ isLoading: false, value: response.body })),
        catchError((error: HttpErrorResponse) => {
            onError(this.alertService, error);
            return of({ isLoading: false, error: error });
        }),
        startWith({ isLoading: true }),
    ) as Observable<LoadedValue<LearningPathNavigationDto>>;

    private readonly navigationData = toSignal(this.navigationData$, { requireSync: true });

    readonly showNavigationOverview = signal<boolean>(false);

    readonly isLoading = computed(() => this.navigationData().isLoading);

    readonly learningPathProgress = computed(() => this.navigationData().value?.progress ?? 0);

    readonly predecessorLearningObject = computed(() => this.navigationData().value?.predecessorLearningObject);

    readonly currentLearningObject = computed(() => this.navigationData().value?.currentLearningObject);

    readonly successorLearningObject = computed(() => this.navigationData().value?.successorLearningObject);

    readonly isCurrentLearningObjectCompleted = signal<boolean>(false);

    selectLearningObject(selectedLearningObject: LearningPathNavigationObjectDto): void {
        this.selectedLearningObject.set(selectedLearningObject);
        this.setCurrentLearningObjectCompletion(selectedLearningObject?.completed ?? false);
    }

    setShowNavigationOverview(show: boolean): void {
        this.showNavigationOverview.set(show);
    }

    setCurrentLearningObjectCompletion(completed: boolean): void {
        this.isCurrentLearningObjectCompleted.set(completed);
    }
}
