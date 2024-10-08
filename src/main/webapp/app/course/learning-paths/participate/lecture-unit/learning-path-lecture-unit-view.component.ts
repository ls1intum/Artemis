import { Component, Input, inject } from '@angular/core';
import { isCommunicationEnabled, isMessagingEnabled } from 'app/entities/course.model';
import { LectureUnit, LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { Lecture } from 'app/entities/lecture.model';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
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
    private lectureUnitService = inject(LectureUnitService);

    @Input() lecture: Lecture;
    @Input() lectureUnit: LectureUnit;
    readonly LectureUnitType = LectureUnitType;

    discussionComponent?: DiscussionSectionComponent;

    protected readonly isMessagingEnabled = isMessagingEnabled;
    protected readonly isCommunicationEnabled = isCommunicationEnabled;

    completeLectureUnit(event: LectureUnitCompletionEvent): void {
        this.lectureUnitService.completeLectureUnit(this.lecture, event);
    }

    /**
     * This function gets called if the router outlet gets activated. This is
     * used only for the DiscussionComponent
     * @param instance The component instance
     */
    onChildActivate(instance: DiscussionSectionComponent) {
        this.discussionComponent = instance; // save the reference to the component instance
    }
}
