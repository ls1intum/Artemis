import { Component, WritableSignal, inject, signal } from '@angular/core';
import { faCheckCircle, faChevronDown } from '@fortawesome/free-solid-svg-icons';
import { LearningPathService } from 'app/course/learning-paths/learning-path.service';
import { LearningObjectType, LearningPathNavigationDto } from 'app/entities/competency/learning-path.model';
import { Observable, catchError, map, of, startWith, switchMap } from 'rxjs';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { HttpErrorResponse } from '@angular/common/http';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { NgbAccordionModule, NgbDropdownModule } from '@ng-bootstrap/ng-bootstrap';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';

export type RelativeLearningObject = {
    id: number;
    type: LearningObjectType;
};

export type LoadedValue<T> = {
    isLoading: boolean;
    value?: T;
    error?: Error;
};

@Component({
    selector: 'jhi-learning-path-student-page',
    templateUrl: './learning-path-student-page.component.html',
    styleUrl: './learning-path-student-page.component.scss',
    standalone: true,
    imports: [CommonModule, NgbDropdownModule, NgbAccordionModule, FontAwesomeModule],
})
export class LearningPathStudentPageComponent {
    protected readonly faChevronDown = faChevronDown;
    protected readonly faCheckCircle = faCheckCircle;

    private learningPathService = inject(LearningPathService);
    private alertService = inject(AlertService);

    public readonly learningPathId = signal(5);

    private readonly relativeLearningObject: WritableSignal<RelativeLearningObject | undefined> = signal(undefined);

    private readonly data$ = toObservable(this.relativeLearningObject).pipe(
        switchMap((relativeLearningObject) => this.learningPathService.getLearningPathNavigation(this.learningPathId(), relativeLearningObject?.id, relativeLearningObject?.type)),
        map((response) => ({ isLoading: false, value: response.body })),
        catchError((error: HttpErrorResponse) => {
            onError(this.alertService, error);
            return of({ isLoading: false, error: error });
        }),
        startWith({ isLoading: true }),
    ) as Observable<LoadedValue<LearningPathNavigationDto>>;

    public readonly isLoading = toSignal(this.data$.pipe(map((loadedValue) => loadedValue.isLoading)), { initialValue: false });

    public readonly learningPathProgress = toSignal(this.data$.pipe(map((loadedValue) => loadedValue.value?.progress ?? 0)), { initialValue: 0 });

    public readonly predecessorLearningObject = toSignal(this.data$.pipe(map((loadedValue) => loadedValue.value?.predecessorLearningObject)));

    public readonly currentLearningObject = toSignal(this.data$.pipe(map((loadedValue) => loadedValue.value?.currentLearningObject)));

    public readonly successorLearningObject = toSignal(this.data$.pipe(map((loadedValue) => loadedValue.value?.successorLearningObject)));

    public setRelativeLearningObject(learningObjectId: number, learningObjectType: LearningObjectType) {
        this.relativeLearningObject.set({ id: learningObjectId, type: learningObjectType });
    }
}
