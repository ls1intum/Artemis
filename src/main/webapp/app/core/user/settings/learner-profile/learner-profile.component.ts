import { LearnerProfileApiService } from 'app/learner-profile/service/learner-profile-api.service';
import { Component, OnInit, inject } from '@angular/core';
import { LearnerProfileDTO } from 'app/learner-profile/shared/entities/learner-profile.model';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MatSliderModule } from '@angular/material/slider';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faInfoCircle, faSave } from '@fortawesome/free-solid-svg-icons';
import { AlertService } from 'app/shared/service/alert.service';

@Component({
    selector: 'jhi-learner-profile',
    templateUrl: './learner-profile.component.html',
    styleUrls: ['./learner-profile.component.scss', '../user-settings.scss'],
    standalone: true,
    imports: [FormsModule, TranslateDirective, MatSliderModule, FontAwesomeModule],
})
export class LearnerProfileComponent implements OnInit {
    learnerProfile: LearnerProfileDTO = new LearnerProfileDTO();
    settingsChanged = false;
    isSaving = false;
    private readonly learnerProfileApiService = inject(LearnerProfileApiService);
    private readonly alertService = inject(AlertService);

    // Icons
    faInfoCircle = faInfoCircle;
    faSave = faSave;

    hideTooltip = () => '';

    ngOnInit(): void {
        this.learnerProfileApiService.getLearnerProfileForCurrentUser().subscribe((learnerProfile: LearnerProfileDTO) => {
            this.learnerProfile = learnerProfile;
        });
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
                this.alertService.success('Learner profile saved successfully');
            },
            error: () => {
                this.alertService.error('Error saving learner profile');
                this.isSaving = false;
            },
        });
    }
}
