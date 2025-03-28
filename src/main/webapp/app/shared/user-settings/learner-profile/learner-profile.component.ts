import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MatSliderModule } from '@angular/material/slider';
import { FeedbackLearnerProfile, LearnerProfileService } from './learner-profile.service';
import { AlertService } from 'app/shared/service/alert.service';

@Component({
    selector: 'jhi-learner-profile',
    templateUrl: './learner-profile.component.html',
    styleUrls: ['./learner-profile.component.scss'],
    standalone: true,
    imports: [FormsModule, TranslateDirective, MatSliderModule],
})
export class LearnerProfileComponent implements OnInit {
    private readonly learnerProfileService = inject(LearnerProfileService);
    private readonly alertService = inject(AlertService);

    profile: FeedbackLearnerProfile = {
        practicalVsTheoretical: 0,
        creativeVsFocused: 0,
        followUpVsSummary: 0,
        briefVsDetailed: 2,
    };

    settingsChanged = false;
    isSaving = false;

    ngOnInit(): void {
        this.loadSettings();
    }

    loadSettings(): void {
        this.learnerProfileService.getProfile().subscribe({
            next: (profile: FeedbackLearnerProfile) => {
                this.profile = profile;
            },
            error: () => {
                this.alertService.error('Error loading learner profile');
            },
        });
    }

    onSliderChange(): void {
        this.settingsChanged = true;
    }

    save(): void {
        if (this.isSaving) {
            return;
        }

        this.isSaving = true;
        this.learnerProfileService.updateProfile(this.profile).subscribe({
            next: (updatedProfile: FeedbackLearnerProfile) => {
                this.profile = updatedProfile;
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
