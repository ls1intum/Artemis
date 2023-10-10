import { Component, Input } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { LectureUnit, LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { onError } from 'app/shared/util/global.utils';
import { Lecture } from 'app/entities/lecture.model';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { AlertService } from 'app/core/util/alert.service';
import { isCommunicationEnabled, isMessagingEnabled } from 'app/entities/course.model';
import { DiscussionSectionComponent } from 'app/overview/discussion-section/discussion-section.component';

export interface LectureUnitCompletionEvent {
    lectureUnit: LectureUnit;
    completed: boolean;
}

@Component({
    selector: 'jhi-learning-path-lecture-unit-view',
    styleUrls: ['./learning-path-lecture-unit-view.component.scss'],
    templateUrl: './learning-path-lecture-unit-view.component.html',
})
export class LearningPathLectureUnitViewComponent {
    @Input() lecture: Lecture;
    @Input() lectureUnit: LectureUnit;
    readonly LectureUnitType = LectureUnitType;

    discussionComponent?: DiscussionSectionComponent;

    protected readonly isMessagingEnabled = isMessagingEnabled;
    protected readonly isCommunicationEnabled = isCommunicationEnabled;

    constructor(
        private lectureUnitService: LectureUnitService,
        private alertService: AlertService,
    ) {}

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

    /**
     * This function gets called if the router outlet gets activated. This is
     * used only for the DiscussionComponent
     * @param instance The component instance
     */
    onChildActivate(instance: DiscussionSectionComponent) {
        this.discussionComponent = instance; // save the reference to the component instance
        if (this.lecture) {
            instance.lecture = this.lecture;
            instance.isCommunicationPage = false;
        }
    }
}
