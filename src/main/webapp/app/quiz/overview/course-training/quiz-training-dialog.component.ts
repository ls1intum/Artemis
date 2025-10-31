import { Component, computed, input, model, output } from '@angular/core';
import { DialogModule } from 'primeng/dialog';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { TooltipModule } from 'primeng/tooltip';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { LeagueIconComponent } from 'app/quiz/overview/course-training/course-training-quiz/leaderboard/league/league-icon.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { faQuestionCircle } from '@fortawesome/free-regular-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-quiz-training-dialog',
    styleUrl: 'course-training.component.scss',
    imports: [DialogModule, FormsModule, TranslateDirective, ToggleSwitchModule, TooltipModule, ArtemisTranslatePipe, LeagueIconComponent, ButtonComponent, FontAwesomeModule],
    templateUrl: './quiz-training-dialog.component.html',
})
export class QuizTrainingDialogComponent {
    protected readonly faQuestionCircle = faQuestionCircle;
    protected readonly leagues = [
        { league: 'Bronze', translationKey: 'artemisApp.quizTraining.bronze', range: '0 - 50' },
        { league: 'Silver', translationKey: 'artemisApp.quizTraining.silver', range: '50 - 150' },
        { league: 'Gold', translationKey: 'artemisApp.quizTraining.gold', range: '150 - 300' },
        { league: 'Diamond', translationKey: 'artemisApp.quizTraining.diamond', range: '300 - 500' },
        { league: 'Master', translationKey: 'artemisApp.quizTraining.master', range: '500+' },
    ];

    visible = model<boolean>(false);
    showInLeaderboard = model<boolean>(true);
    closable = input<boolean>(false);
    showLeaderboardSettings = input<boolean>(true);
    initialShowInLeaderboard = input<boolean>(true);
    disableSaveValidation = input<boolean>(false);

    save = output<void>();

    saveDisabled = computed(() => {
        if (this.disableSaveValidation()) {
            return false;
        }
        return this.showInLeaderboard() === this.initialShowInLeaderboard();
    });
}
