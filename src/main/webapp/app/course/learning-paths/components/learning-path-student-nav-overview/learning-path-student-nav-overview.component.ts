import { Component, InputSignal, OutputEmitterRef, Signal, computed, inject, input, output } from '@angular/core';
import { NgbAccordionModule, NgbDropdownModule } from '@ng-bootstrap/ng-bootstrap';
import { CommonModule } from '@angular/common';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { IconDefinition, faCheckCircle } from '@fortawesome/free-solid-svg-icons';
import { LearningPathService } from '../../learning-path.service';
import { Observable, catchError, map, of, startWith, switchMap } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { LoadedValue } from 'app/course/learning-paths/components/learning-path-student-nav/learning-path-student-nav.component';
import { LearningObjectType, LearningPathNavigationObjectDto, LearningPathNavigationOverviewDto } from 'app/entities/competency/learning-path.model';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { LearningPathNavigationService } from 'app/course/learning-paths/learning-path-navigation.service';

@Component({
    selector: 'jhi-learning-path-student-nav-overview',
    standalone: true,
    imports: [FontAwesomeModule, CommonModule, NgbDropdownModule, NgbAccordionModule, ArtemisSharedModule],
    templateUrl: './learning-path-student-nav-overview.component.html',
})
export class LearningPathStudentNavOverviewComponent {
    protected readonly faCheckCircle: IconDefinition = faCheckCircle;

    private readonly learningPathService: LearningPathService = inject(LearningPathService);
    private readonly alertService: AlertService = inject(AlertService);
    private readonly learningPathNavigationService = inject(LearningPathNavigationService);

    readonly learningPathId: InputSignal<number> = input.required();

    readonly currentLearningObject: InputSignal<LearningPathNavigationObjectDto | undefined> = input.required();

    private readonly navigationOverviewData$: Observable<LoadedValue<LearningPathNavigationOverviewDto>> = toObservable(this.learningPathId).pipe(
        switchMap((learningPathId) => this.learningPathService.getLearningPathNavigationOverview(learningPathId)),
        map((response) => ({ isLoading: false, value: response.body })),
        catchError((error: HttpErrorResponse) => {
            onError(this.alertService, error);
            return of({ isLoading: false, error: error });
        }),
        startWith({ isLoading: true }),
    );

    private readonly navigationOverviewData: Signal<LoadedValue<LearningPathNavigationOverviewDto>> = toSignal(this.navigationOverviewData$, { requireSync: true });

    readonly isLoading: Signal<boolean> = computed(() => this.navigationOverviewData().isLoading);

    readonly learningObjects: Signal<LearningPathNavigationObjectDto[]> = computed(() => this.navigationOverviewData().value?.learningObjects ?? []);

    readonly onLearningObjectSelected: OutputEmitterRef<LearningPathNavigationObjectDto> = output();

    selectLearningObject(learningObject: LearningPathNavigationObjectDto): void {
        if (this.isLearningObjectSelectable(learningObject)) {
            this.learningPathNavigationService.loadRelativeLearningPathNavigation(this.learningPathId(), learningObject);
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
