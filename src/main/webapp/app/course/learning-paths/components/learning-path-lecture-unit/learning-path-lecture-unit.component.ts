import { Component, InputSignal, OnInit, inject, input, signal } from '@angular/core';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { AlertService } from 'app/core/util/alert.service';
import { LectureUnit, LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { ArtemisLectureUnitsModule } from 'app/overview/course-lectures/lecture-units.module';
import { LectureUnitCompletionEvent } from 'app/overview/course-lectures/course-lecture-details.component';
import { LearningPathNavigationService } from 'app/course/learning-paths/learning-path-navigation.service';

@Component({
    selector: 'jhi-learning-path-lecture-unit',
    standalone: true,
    imports: [ArtemisLectureUnitsModule],
    templateUrl: './learning-path-lecture-unit.component.html',
})
export class LearningPathLectureUnitComponent implements OnInit {
    protected readonly LectureUnitType = LectureUnitType;

    private readonly lectureUnitService: LectureUnitService = inject(LectureUnitService);
    private readonly learningPathNavigationService = inject(LearningPathNavigationService);
    private readonly alertService: AlertService = inject(AlertService);

    readonly lectureUnitId: InputSignal<number> = input.required<number>();
    readonly isLoading = signal(false);
    readonly lectureUnit = signal<LectureUnit | undefined>(undefined);

    async ngOnInit(): Promise<void> {
        await this.loadLectureUnit(this.lectureUnitId());
    }

    async loadLectureUnit(lectureUnitId: number) {
        this.isLoading.set(true);
        const lectureUnit = await this.lectureUnitService.getLectureUnitById(lectureUnitId);
        this.lectureUnit.set(lectureUnit);
        this.isLoading.set(false);
    }

    setLearningObjectCompletion(completionEvent: LectureUnitCompletionEvent): void {
        this.learningPathNavigationService.setCurrentLearningObjectCompletion(completionEvent.completed);
    }
}
