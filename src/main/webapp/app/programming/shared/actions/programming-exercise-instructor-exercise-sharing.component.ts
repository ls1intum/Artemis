import { Component, inject, input } from '@angular/core';
import { ButtonComponent, ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { faDownload } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseSharingService } from '../../manage/services/programming-exercise-sharing.service';
import { HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/shared/service/alert.service';

@Component({
    selector: 'jhi-programming-exercise-instructor-exercise-sharing',
    template: `
        <jhi-button
            [disabled]="!exerciseId()"
            [btnType]="ButtonType.INFO"
            [btnSize]="ButtonSize.SMALL"
            [shouldSubmit]="false"
            [featureToggle]="[FeatureToggle.ProgrammingExercises, FeatureToggle.Exports]"
            [icon]="faDownload"
            [title]="'artemisApp.programmingExercise.sharing.export'"
            (onClick)="exportExerciseToSharing()"
        />
    `,
    imports: [ButtonComponent],
})
export class ProgrammingExerciseInstructorExerciseSharingComponent {
    // Icons
    protected readonly faDownload = faDownload;

    ButtonType = ButtonType;
    ButtonSize = ButtonSize;
    readonly FeatureToggle = FeatureToggle;
    sharingTab: WindowProxy | null = null;
    private readonly sharingService = inject(ProgrammingExerciseSharingService);
    private readonly alertService = inject(AlertService);

    exerciseId = input<number>();

    /**
     * Used to initiate export of an exercise to
     * Sharing.
     * Results in a redirect containing a callback-link to exposed exercise
     */
    exportExerciseToSharing() {
        const programmingExerciseId = this.exerciseId();
        if (programmingExerciseId)
            this.sharingService.exportProgrammingExerciseToSharing(programmingExerciseId, window.location.href).subscribe({
                next: (redirect: HttpResponse<string>) => {
                    const redirectURL = redirect?.body;
                    if (redirectURL) {
                        if (this.sharingTab) {
                            if (!window.name) {
                                window.name = 'artemis';
                            }
                            const targetUrl = new URL(redirectURL, window.location.origin);
                            targetUrl.searchParams.set('window', window.name);
                            this.sharingTab.location.href = targetUrl.toString();
                            this.sharingTab.focus();
                        } else {
                            window.location.href = redirectURL;
                        }
                    } else {
                        this.alertService.error('artemisApp.programmingExercise.sharing.error.noRedirect');
                    }
                },
                error: (errorResponse) => {
                    const errorMessage = errorResponse?.error?.message || errorResponse?.error || 'Unknown error';
                    this.alertService.error('artemisApp.programmingExercise.sharing.error.export', { message: errorMessage });
                },
            });
    }
}
