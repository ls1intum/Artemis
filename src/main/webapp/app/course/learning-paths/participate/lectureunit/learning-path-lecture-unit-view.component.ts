import { Component, Input } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { LectureUnit, LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { onError } from 'app/shared/util/global.utils';
import { Lecture } from 'app/entities/lecture.model';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { AlertService } from 'app/core/util/alert.service';

export interface LectureUnitCompletionEvent {
    lectureUnit: LectureUnit;
    completed: boolean;
}

@Component({
    selector: 'jhi-learning-path-lecture-unit-view',
    templateUrl: './learning-path-lecture-unit-view.component.html',
})
export class LearningPathLectureUnitViewComponent {
    @Input()
    lecture: Lecture;
    @Input()
    lectureUnit: LectureUnit;

    readonly LectureUnitType = LectureUnitType;

    constructor(private lectureUnitService: LectureUnitService, private alertService: AlertService) {}

    completeLectureUnit(event: LectureUnitCompletionEvent): void {
        if (this.lecture && event.lectureUnit.visibleToStudents && event.lectureUnit.completed !== event.completed) {
            this.lectureUnitService.setCompletion(event.lectureUnit.id!, this.lecture.id!, event.completed).subscribe({
                next: () => {
                    event.lectureUnit.completed = event.completed;
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
        }
    }
}
