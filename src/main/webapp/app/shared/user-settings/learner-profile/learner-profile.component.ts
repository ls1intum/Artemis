import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { UserSettingsDirective } from '../user-settings.directive';
import { UserSettingsCategory } from 'app/shared/constants/user-settings.constants';
import { MatSliderModule } from '@angular/material/slider';
import { FeedbackLearnerProfile, LearnerProfileService } from './learner-profile.service';

@Component({
    selector: 'jhi-learner-profile',
    templateUrl: './learner-profile.component.html',
    styleUrls: ['./learner-profile.component.scss'],
    standalone: true,
    imports: [FormsModule, TranslateDirective, MatSliderModule],
})
export class LearnerProfileComponent extends UserSettingsDirective implements OnInit {
    private readonly learnerProfileService = inject(LearnerProfileService);

    profile: FeedbackLearnerProfile = {
        practicalVsTheoretical: 0,
        creativeVsFocused: 0,
        followUpVsSummary: 0,
        briefVsDetailed: 2,
    };

    ngOnInit(): void {
        this.userSettingsCategory = UserSettingsCategory.LEARNER_PROFILE;
        super.ngOnInit();
        this.loadSettings();
    }

    loadSettings(): void {
        this.learnerProfileService.getProfile().subscribe({
            next: (profile: FeedbackLearnerProfile) => {
                this.profile = profile;
            },
            error: () => {
                this.onError({ error: { message: 'Error loading learner profile' } } as any);
            },
        });
    }

    onSliderChange(): void {
        // Here we would typically save the changes to the backend
        this.settingsChanged = true;
    }

    save(): void {
        this.learnerProfileService.updateProfile(this.profile).subscribe({
            next: (updatedProfile: FeedbackLearnerProfile) => {
                this.profile = updatedProfile;
                this.finishSaving();
            },
            error: () => {
                this.onError({ error: { message: 'Error saving learner profile' } } as any);
            },
        });
    }
}
