import { Component, InputSignal, OutputEmitterRef, Signal, WritableSignal, inject, input, output, signal } from '@angular/core';
import { LearningPathService } from 'app/course/learning-paths/learning-path.service';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { LearningObjectType, LearningPathNavigationDto, LearningPathNavigationObjectDto } from 'app/entities/competency/learning-path.model';
import { Observable, catchError, map, of, shareReplay, startWith, switchMap, tap } from 'rxjs';
import { CommonModule } from '@angular/common';
import { NgbAccordionModule, NgbDropdownModule } from '@ng-bootstrap/ng-bootstrap';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { IconDefinition, faCheckCircle, faChevronDown } from '@fortawesome/free-solid-svg-icons';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';

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
    selector: 'jhi-learning-path-student-nav',
    standalone: true,
    imports: [CommonModule, NgbDropdownModule, NgbAccordionModule, FontAwesomeModule],
    templateUrl: './learning-path-student-nav.component.html',
    styleUrl: './learning-path-student-nav.component.scss',
})
export class LearningPathStudentNavComponent {
    protected readonly faChevronDown: IconDefinition = faChevronDown;
    protected readonly faCheckCircle: IconDefinition = faCheckCircle;

    private learningPathService: LearningPathService = inject(LearningPathService);
    private alertService: AlertService = inject(AlertService);

    public readonly learningPathId: InputSignal<number> = input.required<number>();

    private readonly relativeLearningObject: WritableSignal<RelativeLearningObject | undefined> = signal(undefined);

    private readonly data$: Observable<LoadedValue<LearningPathNavigationDto>> = toObservable(this.relativeLearningObject).pipe(
        switchMap((relativeLearningObject) => this.learningPathService.getLearningPathNavigation(this.learningPathId(), relativeLearningObject?.id, relativeLearningObject?.type)),
        map((response) => ({ isLoading: false, value: response.body })),
        catchError((error: HttpErrorResponse) => {
            onError(this.alertService, error);
            return of({ isLoading: false, error: error });
        }),
        startWith({ isLoading: true }),
        shareReplay(1),
    );

    public readonly isLoading: Signal<boolean> = toSignal(this.data$.pipe(map((loadedValue) => loadedValue.isLoading)), { initialValue: false });

    public readonly learningPathProgress: Signal<number> = toSignal(this.data$.pipe(map((loadedValue) => loadedValue.value?.progress ?? 0)), { initialValue: 0 });

    public readonly predecessorLearningObject: Signal<LearningPathNavigationObjectDto | undefined> = toSignal(
        this.data$.pipe(map((loadedValue) => loadedValue.value?.predecessorLearningObject)),
    );

    public readonly onCurrentLearningObjectChange: OutputEmitterRef<LearningPathNavigationObjectDto | undefined> = output<LearningPathNavigationObjectDto | undefined>();

    public readonly currentLearningObject = toSignal(
        this.data$.pipe(
            map((loadedValue) => loadedValue.value?.currentLearningObject),
            tap((currentLearningObject) => this.onCurrentLearningObjectChange.emit(currentLearningObject)),
        ),
    );

    public readonly successorLearningObject: Signal<LearningPathNavigationObjectDto | undefined> = toSignal(
        this.data$.pipe(map((loadedValue) => loadedValue.value?.successorLearningObject)),
    );

    public setRelativeLearningObject(learningObjectId: number, learningObjectType: LearningObjectType): void {
        this.relativeLearningObject.set({ id: learningObjectId, type: learningObjectType });
    }
}
