import { Component, computed, inject, input, output } from '@angular/core';
import { NgbAccordionModule, NgbDropdownModule } from '@ng-bootstrap/ng-bootstrap';
import { CommonModule } from '@angular/common';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faCheckCircle } from '@fortawesome/free-solid-svg-icons';
import { LearningPathService } from '../../learning-path.service';
import { Observable, catchError, map, of, switchMap } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { LoadedValue } from 'app/course/learning-paths/components/learning-path-student-nav/learning-path-student-nav.component';
import { LearningObjectType, LearningPathNavigationObjectDto, LearningPathNavigationOverviewDto } from 'app/entities/competency/learning-path.model';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@Component({
    selector: 'jhi-learning-path-student-nav-overview',
    standalone: true,
    imports: [FontAwesomeModule, CommonModule, NgbDropdownModule, NgbAccordionModule, ArtemisSharedModule],
    templateUrl: './learning-path-student-nav-overview.component.html',
})
export class LearningPathStudentNavOverviewComponent {
    protected readonly faCheckCircle = faCheckCircle;

    private readonly learningPathService = inject(LearningPathService);
    private readonly alertService = inject(AlertService);

    readonly learningPathId = input.required<number>();

    readonly currentLearningObject = input.required<LearningPathNavigationObjectDto | undefined>();

    private readonly navigationOverviewData$ = toObservable(this.learningPathId).pipe(
        switchMap((learningPathId) => this.learningPathService.getLearningPathNavigationOverview(learningPathId)),
        map((response) => ({ isLoading: false, value: response.body })),
        catchError((error: HttpErrorResponse) => {
            onError(this.alertService, error);
            return of({ isLoading: false, error: error });
        }),
    ) as Observable<LoadedValue<LearningPathNavigationOverviewDto>>;

    private readonly navigationOverviewData = toSignal(this.navigationOverviewData$, { initialValue: { isLoading: true } as LoadedValue<LearningPathNavigationOverviewDto> });

    readonly isLoading = computed(() => this.navigationOverviewData().isLoading);

    readonly learningObjects = computed(() => this.navigationOverviewData().value?.learningObjects ?? []);

    readonly onLearningObjectSelected = output<LearningPathNavigationObjectDto>();

    selectLearningObject(learningObject: LearningPathNavigationObjectDto) {
        this.onLearningObjectSelected.emit(learningObject);
    }

    isEqualToCurrentLearningObject(id: number, type: LearningObjectType): boolean {
        return this.currentLearningObject()?.id === id && this.currentLearningObject()?.type === type;
    }

    isLearningObjectSelectable(learningObject: LearningPathNavigationObjectDto): boolean {
        const indexOfLearningObject = this.learningObjects().indexOf(learningObject);
        return indexOfLearningObject > 0 ? this.learningObjects()[indexOfLearningObject - 1].completed : true;
    }
}
