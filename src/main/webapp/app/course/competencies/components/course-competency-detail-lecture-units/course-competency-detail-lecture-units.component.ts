import { Component, computed, inject, input, output } from '@angular/core';
import { LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { ArtemisLectureUnitsModule } from 'app/overview/course-lectures/lecture-units.module';
import { ExerciseUnit } from 'app/entities/lecture-unit/exerciseUnit.model';
import { CourseCompetency } from 'app/entities/competency.model';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { LectureUnitCompletionEvent } from 'app/overview/course-lectures/course-lecture-details.component';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { lastValueFrom } from 'rxjs';
import { AttachmentUnitComponent } from 'app/overview/course-lectures/attachment-unit/attachment-unit.component';
import { VideoUnitComponent } from 'app/overview/course-lectures/video-unit/video-unit.component';
import { TextUnitComponent } from 'app/overview/course-lectures/text-unit/text-unit.component';
import { OnlineUnitComponent } from 'app/overview/course-lectures/online-unit/online-unit.component';

@Component({
    selector: 'jhi-course-competency-detail-lecture-units',
    standalone: true,
    imports: [ArtemisLectureUnitsModule, AttachmentUnitComponent, VideoUnitComponent, TextUnitComponent, OnlineUnitComponent],
    templateUrl: './course-competency-detail-lecture-units.component.html',
})
export class CourseCompetencyDetailLectureUnitsComponent {
    protected readonly LectureUnitType = LectureUnitType;

    private readonly lectureUnitService = inject(LectureUnitService);
    private readonly alertService = inject(AlertService);

    readonly courseCompetency = input.required<CourseCompetency>();

    readonly onLectureUnitCompletion = output<void>();

    readonly units = computed(() => {
        const lectureUnits = this.courseCompetency().lectureUnits ?? [];
        const exercises = this.courseCompetency().exercises ?? [];
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
