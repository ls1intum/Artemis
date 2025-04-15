import { LearnerProfileApiService } from 'app/learner-profile/service/learner-profile-api.service';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { LearnerProfileDTO } from 'app/learner-profile/shared/entities/learner-profile.model';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MatSliderModule } from '@angular/material/slider';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faInfoCircle, faSave } from '@fortawesome/free-solid-svg-icons';
import { AlertService } from 'app/shared/service/alert.service';
import { Subject, takeUntil } from 'rxjs';

@Component({
    selector: 'jhi-learner-profile',
    templateUrl: './learner-profile.component.html',
    styleUrls: ['./learner-profile.component.scss', '../user-settings.scss'],
    standalone: true,
    imports: [FormsModule, TranslateDirective, MatSliderModule, FontAwesomeModule],
})
export class LearnerProfileComponent implements OnInit, OnDestroy {
    learnerProfile: LearnerProfileDTO = new LearnerProfileDTO();
    settingsChanged = false;
    isSaving = false;
    private readonly learnerProfileApiService = inject(LearnerProfileApiService);
    private readonly alertService = inject(AlertService);
    private destroy$ = new Subject<void>();

    // Icons
    faInfoCircle = faInfoCircle;
    faSave = faSave;

    hideTooltip = () => '';

    ngOnInit(): void {
        this.learnerProfileApiService
            .getLearnerProfileForCurrentUser()
            .pipe(takeUntil(this.destroy$))
            .subscribe({
                next: (learnerProfile: LearnerProfileDTO) => {
                    this.learnerProfile = learnerProfile;
                },
                error: () => {
                    this.alertService.error('artemis.learnerProfile.error.loading');
                },
            });
    }

    ngOnDestroy(): void {
        this.destroy$.next();
        this.destroy$.complete();
    }

    onSliderChange() {
        this.settingsChanged = true;
    }

    save(): void {
        if (this.isSaving) {
            return;
        }

        this.isSaving = true;
        this.learnerProfileApiService.putUpdatedLearnerProfile(this.learnerProfile).subscribe({
            next: (updatedProfile: LearnerProfileDTO) => {
                this.learnerProfile = updatedProfile;
                this.settingsChanged = false;
                this.isSaving = false;
                this.alertService.success('artemis.learnerProfile.success.saved');
            },
            error: () => {
                this.alertService.error('artemis.learnerProfile.error.saving');
                this.isSaving = false;
            },
        });
    }
}
