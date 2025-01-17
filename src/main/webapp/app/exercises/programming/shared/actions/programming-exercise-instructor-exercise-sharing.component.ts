import { Component, Input } from '@angular/core';
import { ButtonComponent, ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { AlertService } from 'app/core/util/alert.service';
import { faDownload } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseSharingService } from '../../manage/services/programming-exercise-sharing.service';
import { HttpResponse } from '@angular/common/http';

@Component({
    selector: 'jhi-programming-exercise-instructor-exercise-sharing',
    template: `
        <jhi-button
            [disabled]="!exerciseId"
            [btnType]="ButtonType.INFO"
            [btnSize]="ButtonSize.SMALL"
            [shouldSubmit]="false"
            [featureToggle]="[FeatureToggle.ProgrammingExercises, FeatureToggle.Exports]"
            [icon]="faDownload"
            [title]="'artemisApp.programmingExercise.sharing.export'"
            (onClick)="preOpenSharingTab(); exportExerciseToSharing(exerciseId)"
        />
    `,
    imports: [ButtonComponent],
})
export class ProgrammingExerciseInstructorExerciseSharingComponent {
    ButtonType = ButtonType;
    ButtonSize = ButtonSize;
    readonly FeatureToggle = FeatureToggle;
    sharingTab: WindowProxy | null;

    @Input()
    exerciseId: number;

    // Icons
    faDownload = faDownload;

    constructor(
        private sharingService: ProgrammingExerciseSharingService,
        private alertService: AlertService,
    ) {}

    preOpenSharingTab() {
        // the focus back to this window is not working, so we open in this window
        /*
        if(!this.sharingTab) {
            this.sharingTab = window.open("about:blank", "sharing");
        }
        */
    }
    /**
     * **CodeAbility changes**: Used to initiate export of an exercise to
     * Sharing.
     * Results in a redirect containing a callback-link to exposed exercise
     * @param programmingExerciseId the id of the exercise to export
     */
    exportExerciseToSharing(programmingExerciseId: number) {
        this.sharingService.exportProgrammingExerciseToSharing(programmingExerciseId, window.location.href).subscribe({
            next: (redirect: HttpResponse<string>) => {
                if (redirect) {
                    const redirectURL = redirect.body?.toString();
                    if (this.sharingTab) {
                        if (!window.name) {
                            window.name = 'artemis';
                        }
                        this.sharingTab.location.href = redirectURL! + '&window=' + window.name;
                        this.sharingTab.focus();
                        //                    const sharingWindow = window.open(redirectURL, 'sharing');
                    } else {
                        window.location.href = redirectURL!;
                    }
                }
            },
            error: (errorResponse) => {
                this.alertService.error('Unable to export exercise. Error: ' + errorResponse.message);
            },
        });
    }
}
