import { Component, computed, input } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faStar } from '@fortawesome/free-solid-svg-icons';
import { LeagueIconComponent } from 'app/quiz/overview/course-training/course-training-quiz/leaderboard/league/league-icon.component';

@Component({
    selector: 'jhi-league-badge',
    standalone: true,
    imports: [TranslateDirective, FontAwesomeModule, LeagueIconComponent],
    templateUrl: './league-badge.component.html',
    styleUrl: './league-badge.component.scss',
})
export class LeagueBadgeComponent {
    league = input<string>('');
    points = input<number>(0);
    pointsBronzeLeague = input<number>(100);
    pointsSilverLeague = input<number>(200);
    pointsGoldLeague = input<number>(300);
    pointsDiamondLeague = input<number>(400);

    protected readonly faStar = faStar;

    leagueLower = computed(() => this.league().toLowerCase());

    progressWidth = computed(() => {
        const leagueValue = this.league();
        const pointsValue = this.points();

        if (leagueValue === 'Bronze') {
            return `${(pointsValue / this.pointsBronzeLeague()) * 100}%`;
        } else if (leagueValue === 'Silver') {
            return `${((pointsValue - this.pointsBronzeLeague()) / (this.pointsSilverLeague() - this.pointsBronzeLeague())) * 100}%`;
        } else if (leagueValue === 'Gold') {
            return `${((pointsValue - this.pointsSilverLeague()) / (this.pointsGoldLeague() - this.pointsSilverLeague())) * 100}%`;
        } else if (leagueValue === 'Diamond') {
            return `${((pointsValue - this.pointsGoldLeague()) / (this.pointsDiamondLeague() - this.pointsGoldLeague())) * 100}%`;
        } else if (leagueValue === 'Master') {
            return '100%';
        }
        return '0%';
    });

    nextLeague = computed(() => {
        const leagueValue = this.league();
        if (leagueValue === 'Bronze') return 'Silver';
        if (leagueValue === 'Silver') return 'Gold';
        if (leagueValue === 'Gold') return 'Diamond';
        if (leagueValue === 'Diamond') return 'Master';
        return '';
    });

    maxPointsForCurrentLeague = computed(() => {
        const leagueValue = this.league();
        switch (leagueValue) {
            case 'Bronze':
                return this.pointsBronzeLeague();
            case 'Silver':
                return this.pointsSilverLeague();
            case 'Gold':
                return this.pointsGoldLeague();
            case 'Diamond':
                return this.pointsDiamondLeague();
            default:
                return 0;
        }
    });
}
