import { Component, input, model, output } from '@angular/core';
import { DialogModule } from 'primeng/dialog';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { TooltipModule } from 'primeng/tooltip';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { LeagueIconComponent } from 'app/quiz/overview/course-training/course-training-quiz/leaderboard/league/league-icon.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';

@Component({
    selector: 'jhi-quiz-training-dialog',
    imports: [DialogModule, FormsModule, TranslateDirective, ToggleSwitchModule, TooltipModule, ArtemisTranslatePipe, LeagueIconComponent, ButtonComponent],
    templateUrl: './quiz-training-dialog.component.html',
})
export class QuizTrainingDialogComponent {
    visible = model<boolean>(false);
    showInLeaderboard = model<boolean>(true);
    closable = input<boolean>(false);
    showLeaderboardSettings = input<boolean>(true);
    save = output<void>();
}
