import { TumAetUiMessageComponent } from '@tumaet/ui-angular';
import { Component, computed, inject, input } from '@angular/core';

import { RouterLink } from '@angular/router';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ParticipationPrivacyService } from './participation-privacy.service';

@Component({
    selector: 'jhi-participation-access-notice',
    imports: [TumAetUiMessageComponent, TranslateDirective, ArtemisDatePipe, RouterLink],
    template: `
        @if (exercise().type === ExerciseType.PROGRAMMING && exercise().isAtLeastTutor && !exercise().isAtLeastInstructor && !exercise().exerciseGroup) {
            <tumaet-ui-message severity="info" class="mb-4! block" data-testid="tutor-participation-access-notice">
                <span [jhiTranslate]="messageKey()" [translateValues]="{ dueDate: exercise().dueDate | artemisDate }"></span>
                @if (anonymous()) {
                    <a
                        class="underline"
                        [routerLink]="['/course-management', exercise().course?.id, 'assessment-dashboard', exercise().id]"
                        jhiTranslate="artemisApp.participation.tutorAccess.assessmentDashboard"
                    ></a>
                }
            </tumaet-ui-message>
        }
    `,
})
export class ParticipationAccessNoticeComponent {
    private readonly privacyService = inject(ParticipationPrivacyService);

    protected readonly ExerciseType = ExerciseType;

    readonly exercise = input.required<Exercise>();
    readonly anonymous = computed(() => this.privacyService.isParticipantInformationHidden(this.exercise()));
    readonly messageKey = computed(() => {
        if (this.anonymous()) return 'artemisApp.participation.tutorAccess.anonymousAssessment';
        if (this.privacyService.hasManualAssessmentDeadline(this.exercise())) return 'artemisApp.participation.tutorAccess.untilDueDate';
        return this.privacyService.hasManualAssessment(this.exercise()) ? 'artemisApp.participation.tutorAccess.noDueDate' : 'artemisApp.participation.tutorAccess.available';
    });
}
