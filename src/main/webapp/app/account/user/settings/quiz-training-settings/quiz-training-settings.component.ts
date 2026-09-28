import { Component, OnInit, inject, signal } from '@angular/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { QuizTrainingApi } from 'app/openapi/api/quiz-training-api';
import { AlertService } from 'app/foundation/service/alert.service';
import { onError } from 'app/foundation/util/global.utils';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { TumAetUiCardComponent, TumAetUiMessageComponent, TumAetUiToggleSwitchComponent } from '@tumaet/ui-angular';

@Component({
    selector: 'jhi-quiz-training-settings',
    templateUrl: './quiz-training-settings.component.html',
    imports: [TranslateDirective, FormsModule, HelpIconComponent, TumAetUiCardComponent, TumAetUiMessageComponent, TumAetUiToggleSwitchComponent],
})
export class QuizTrainingSettingsComponent implements OnInit {
    quizTrainingApi = inject(QuizTrainingApi);
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
        this.quizTrainingApi.getLeaderboardSettings().subscribe({
            next: (settings) => {
                this.isVisibleInLeaderboard.set(settings.showInLeaderboard);
            },
            error: (error) => {
                onError(this.alertService, error);
            },
        });
    }

    private saveSettings(): void {
        this.quizTrainingApi.updateLeaderboardSettings({ showInLeaderboard: this.isVisibleInLeaderboard() }).subscribe({
            next: () => {
                this.alertService.success('artemisApp.userSettings.quizTrainingSettings.updateSuccess');
            },
            error: (error) => {
                onError(this.alertService, error);
            },
        });
    }
}
