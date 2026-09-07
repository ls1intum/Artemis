import { Component, OnInit, inject, signal } from '@angular/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { QuizTrainingSettingsService } from 'app/account/user/settings/quiz-training-settings/quiz-training-settings.service';
import { LeaderboardSettingsDTO } from 'app/quiz/overview/course-training/course-training-quiz/leaderboard/leaderboard-types';
import { AlertService } from 'app/foundation/service/alert.service';
import { onError } from 'app/foundation/util/global.utils';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { TumUiCardComponent, TumUiMessageComponent, TumUiToggleSwitchComponent } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-quiz-training-settings',
    templateUrl: './quiz-training-settings.component.html',
    imports: [TranslateDirective, FormsModule, HelpIconComponent, TumUiCardComponent, TumUiMessageComponent, TumUiToggleSwitchComponent, ArtemisTranslatePipe],
})
export class QuizTrainingSettingsComponent implements OnInit {
    quizService = inject(QuizTrainingSettingsService);
    alertService = inject(AlertService);

    readonly isVisibleInLeaderboard = signal<boolean | undefined>(undefined);

    ngOnInit(): void {
        this.loadSettings();
    }

    onLeaderboardVisibilityChange(visible: boolean): void {
        this.isVisibleInLeaderboard.set(visible);
        this.saveSettings();
    }

    private loadSettings(): void {
        this.quizService.getSettings().subscribe({
            next: (response) => {
                if (response.body) {
                    this.isVisibleInLeaderboard.set(response.body.showInLeaderboard);
                }
            },
            error: (error) => {
                onError(this.alertService, error);
            },
        });
    }

    private saveSettings(): void {
        const leaderboardSettingsDTO = new LeaderboardSettingsDTO();
        leaderboardSettingsDTO.showInLeaderboard = this.isVisibleInLeaderboard();
        this.quizService.updateSettings(leaderboardSettingsDTO).subscribe({
            next: () => {
                this.alertService.success('artemisApp.userSettings.quizTrainingSettings.updateSuccess');
            },
            error: (error) => {
                onError(this.alertService, error);
            },
        });
    }
}
