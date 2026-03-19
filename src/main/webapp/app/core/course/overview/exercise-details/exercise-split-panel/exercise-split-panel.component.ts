import { Component, computed, input } from '@angular/core';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { faComment, faGear, faGraduationCap } from '@fortawesome/free-solid-svg-icons';
import { ProblemStatementComponent } from 'app/core/course/overview/exercise-details/problem-statement/problem-statement.component';
import { DiscussionSectionComponent } from 'app/communication/shared/discussion-section/discussion-section.component';
import { isCommunicationEnabled, isMessagingEnabled } from 'app/core/course/shared/entities/course.model';
import { PanelDirective, ResizablePanelsComponent } from 'app/shared/components/resizable-panels/resizable-panels.component';

@Component({
    selector: 'jhi-exercise-split-panel',
    templateUrl: './exercise-split-panel.component.html',
    imports: [ResizablePanelsComponent, PanelDirective, ProblemStatementComponent, DiscussionSectionComponent],
})
export class ExerciseSplitPanelComponent {
    protected readonly faGear = faGear;
    protected readonly faComment = faComment;
    protected readonly faGraduationCap = faGraduationCap;

    readonly exercise = input.required<Exercise>();
    readonly studentParticipation = input<StudentParticipation>();

    readonly showDiscussion = computed(() => {
        const course = this.exercise().course;
        return !!course && (isCommunicationEnabled(course) || isMessagingEnabled(course));
    });
}
