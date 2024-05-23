import { Component, InputSignal, Signal, WritableSignal, computed, inject, input, signal } from '@angular/core';
import { LearningPathService } from 'app/course/learning-paths/learning-path.service';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { LearningPathNavigationDto, LearningPathNavigationObjectDto } from 'app/entities/competency/learning-path.model';
import { Observable, catchError, map, of, startWith, switchMap } from 'rxjs';
import { CommonModule } from '@angular/common';
import { NgbAccordionModule, NgbDropdownModule } from '@ng-bootstrap/ng-bootstrap';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { IconDefinition, faCheckCircle, faChevronDown } from '@fortawesome/free-solid-svg-icons';
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
    protected readonly faChevronDown: IconDefinition = faChevronDown;
    protected readonly faCheckCircle: IconDefinition = faCheckCircle;

    private learningPathService: LearningPathService = inject(LearningPathService);
    private alertService: AlertService = inject(AlertService);

    readonly learningPathId: InputSignal<number> = input.required<number>();

    private readonly selectedLearningObject: WritableSignal<LearningPathNavigationObjectDto | undefined> = signal(undefined);

    private readonly navigationData$: Observable<LoadedValue<LearningPathNavigationDto>> = toObservable(this.selectedLearningObject).pipe(
        switchMap((selectedLearningObject) => this.learningPathService.getLearningPathNavigation(this.learningPathId(), selectedLearningObject?.id, selectedLearningObject?.type)),
        map((response) => ({ isLoading: false, value: response.body })),
        catchError((error: HttpErrorResponse) => {
            onError(this.alertService, error);
            return of({ isLoading: false, error: error });
        }),
        startWith({ isLoading: true }),
    );

    private readonly navigationData: Signal<LoadedValue<LearningPathNavigationDto>> = toSignal(this.navigationData$, { requireSync: true });

    readonly showNavigationOverview: WritableSignal<boolean> = signal(false);

    readonly isLoading: Signal<boolean> = computed(() => this.navigationData().isLoading);

    readonly learningPathProgress: Signal<number> = computed(() => this.navigationData().value?.progress ?? 0);

    readonly predecessorLearningObject: Signal<LearningPathNavigationObjectDto | undefined> = computed(() => this.navigationData().value?.predecessorLearningObject);

    readonly currentLearningObject: Signal<LearningPathNavigationObjectDto | undefined> = computed(() => this.navigationData().value?.currentLearningObject);

    readonly successorLearningObject: Signal<LearningPathNavigationObjectDto | undefined> = computed(() => this.navigationData().value?.successorLearningObject);

    readonly isCurrentLearningObjectCompleted: WritableSignal<boolean> = signal(false);

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
