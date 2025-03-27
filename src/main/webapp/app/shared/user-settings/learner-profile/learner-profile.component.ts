import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { UserSettingsDirective } from '../user-settings.directive';
import { UserSettingsCategory } from 'app/shared/constants/user-settings.constants';
import { MatSliderModule } from '@angular/material/slider';

@Component({
    selector: 'jhi-learner-profile',
    templateUrl: './learner-profile.component.html',
    styleUrls: ['./learner-profile.component.scss'],
    standalone: true,
    imports: [FormsModule, TranslateDirective, MatSliderModule],
})
export class LearnerProfileComponent extends UserSettingsDirective implements OnInit {
    // Slider values
    practicalVsTheoretical = 0;
    creativeVsFocused = 0;
    followUpVsSummary = 0;
    briefVsDetailed = 2;

    ngOnInit(): void {
        this.userSettingsCategory = UserSettingsCategory.LEARNER_PROFILE;
        super.ngOnInit();
    }

    onSliderChange(): void {
        // Here we would typically save the changes to the backend
        this.settingsChanged = true;
    }

    save(): void {
        // TODO: Implement actual save functionality when backend is ready
        this.settingsChanged = false;
    }
}
