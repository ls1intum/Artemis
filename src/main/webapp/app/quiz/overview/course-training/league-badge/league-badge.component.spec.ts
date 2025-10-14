import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LeagueBadgeComponent } from './league-badge.component';
import { MockBuilder } from 'ng-mocks';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { LeagueIconComponent } from '../course-training-quiz/leaderboard/league/league-icon.component';

describe('LeagueBadgeComponent', () => {
    let fixture: ComponentFixture<LeagueBadgeComponent>;
    let component: LeagueBadgeComponent;

    beforeEach(async () => {
        await MockBuilder(LeagueBadgeComponent).keep(LeagueBadgeComponent).keep(FontAwesomeModule).mock(LeagueIconComponent).mock(TranslateDirective);

        fixture = TestBed.createComponent(LeagueBadgeComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    describe('progressWidth calculation', () => {
        it('should calculate progress width correctly for Bronze league', () => {
            fixture.componentRef.setInput('league', 'Bronze');
            fixture.componentRef.setInput('points', 50);
            fixture.detectChanges();

            expect(component.progressWidth()).toBe('50%');

            fixture.componentRef.setInput('points', 75);
            fixture.detectChanges();
            expect(component.progressWidth()).toBe('75%');
        });

        it('should calculate progress width correctly for Silver league', () => {
            fixture.componentRef.setInput('league', 'Silver');
            fixture.componentRef.setInput('points', 150);
            fixture.detectChanges();

            expect(component.progressWidth()).toBe('50%');
        });

        it('should calculate progress width correctly for Gold league', () => {
            fixture.componentRef.setInput('league', 'Gold');
            fixture.componentRef.setInput('points', 250);
            fixture.detectChanges();

            expect(component.progressWidth()).toBe('50%');
        });

        it('should calculate progress width correctly for Diamond league', () => {
            fixture.componentRef.setInput('league', 'Diamond');
            fixture.componentRef.setInput('points', 350);
            fixture.detectChanges();

            expect(component.progressWidth()).toBe('50%');
        });

        it('should return 100% for Master league', () => {
            fixture.componentRef.setInput('league', 'Master');
            fixture.componentRef.setInput('points', 500);
            fixture.detectChanges();

            expect(component.progressWidth()).toBe('100%');
        });

        it('should return 0% for unknown league', () => {
            fixture.componentRef.setInput('league', 'Unknown');
            fixture.detectChanges();

            expect(component.progressWidth()).toBe('0%');
        });
    });

    describe('nextLeague calculation', () => {
        it('should return Silver as next league for Bronze', () => {
            fixture.componentRef.setInput('league', 'Bronze');
            fixture.detectChanges();

            expect(component.nextLeague()).toBe('Silver');
        });

        it('should return Gold as next league for Silver', () => {
            fixture.componentRef.setInput('league', 'Silver');
            fixture.detectChanges();

            expect(component.nextLeague()).toBe('Gold');
        });

        it('should return Diamond as next league for Gold', () => {
            fixture.componentRef.setInput('league', 'Gold');
            fixture.detectChanges();

            expect(component.nextLeague()).toBe('Diamond');
        });

        it('should return Master as next league for Diamond', () => {
            fixture.componentRef.setInput('league', 'Diamond');
            fixture.detectChanges();

            expect(component.nextLeague()).toBe('Master');
        });

        it('should return empty string for Master or unknown league', () => {
            fixture.componentRef.setInput('league', 'Master');
            fixture.detectChanges();
            expect(component.nextLeague()).toBe('');

            fixture.componentRef.setInput('league', 'Unknown');
            fixture.detectChanges();
            expect(component.nextLeague()).toBe('');
        });
    });

    it('should handle No League case properly', () => {
        fixture.componentRef.setInput('league', 'No League');
        fixture.detectChanges();
        expect(component.progressWidth()).toBe('0%');
        expect(component.nextLeague()).toBe('');
    });
});
