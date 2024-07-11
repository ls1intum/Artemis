import { Component, computed, inject, input, output } from '@angular/core';
import { LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { ArtemisLectureUnitsModule } from 'app/overview/course-lectures/lecture-units.module';
import { ExerciseUnit } from 'app/entities/lecture-unit/exerciseUnit.model';
import { Competency } from 'app/entities/competency.model';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { LectureUnitCompletionEvent } from 'app/overview/course-lectures/course-lecture-details.component';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { lastValueFrom } from 'rxjs';

@Component({
    selector: 'jhi-competency-detail-lecture-units',
    standalone: true,
    imports: [ArtemisLectureUnitsModule],
    templateUrl: './competency-detail-lecture-units.component.html',
})
export class CompetencyDetailLectureUnitsComponent {
    protected readonly LectureUnitType = LectureUnitType;

    private readonly lectureUnitService = inject(LectureUnitService);
    private readonly alertService = inject(AlertService);

    readonly competency = input.required<Competency>();

    readonly onLectureUnitCompletion = output<void>();

    readonly units = computed(() => {
        const lectureUnits = this.competency().lectureUnits ?? [];
        const exercises = this.competency().exercises ?? [];
        const exerciseUnits = exercises.map(
            (exercise) =>
                <ExerciseUnit>{
                    id: exercise.id,
                    exercise: exercise,
                },
        );
        return [...lectureUnits, ...exerciseUnits];
    });

    async setLearningObjectCompletion(completionEvent: LectureUnitCompletionEvent): Promise<void> {
        try {
            const lectureUnit = completionEvent.lectureUnit;
            const isCompleted = completionEvent.completed;
            await lastValueFrom(this.lectureUnitService.setCompletion(lectureUnit.id!, lectureUnit.lecture!.id!, isCompleted));
            lectureUnit.completed = isCompleted;
            this.onLectureUnitCompletion.emit();
        } catch (error) {
            onError(this.alertService, error);
        }
    }
}
