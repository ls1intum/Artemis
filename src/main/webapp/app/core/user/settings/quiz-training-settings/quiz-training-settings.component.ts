import { Component, OnInit, inject } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { QuizTrainingSettingsService } from 'app/core/user/settings/quiz-training-settings/quiz-training-settings.service';
import { LeaderboardSettingsDTO } from 'app/quiz/overview/course-training/course-training-quiz/leaderboard/leaderboard-types';
import { AlertService } from 'app/shared/service/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { TooltipModule } from 'primeng/tooltip';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';

@Component({
    selector: 'jhi-quiz-training-settings',
    templateUrl: './quiz-training-settings.component.html',
    imports: [TranslateDirective, FormsModule, TooltipModule, HelpIconComponent],
})
export class QuizTrainingSettingsComponent implements OnInit {
    quizService = inject(QuizTrainingSettingsService);
    alertService = inject(AlertService);

    isVisibleInLeaderboard: boolean | undefined;

    ngOnInit(): void {
        this.loadSettings();
    }

    toggleLeaderboardVisibility(): void {
        this.saveSettings();
    }

    private loadSettings(): void {
        this.quizService.getSettings().subscribe({
            next: (response) => {
                if (response.body) {
                    this.isVisibleInLeaderboard = response.body.showInLeaderboard;
                }
            },
            error: (error) => {
                onError(this.alertService, error);
            },
        });
    }

    private saveSettings(): void {
        const leaderboardSettingsDTO = new LeaderboardSettingsDTO();
        leaderboardSettingsDTO.showInLeaderboard = this.isVisibleInLeaderboard;
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
