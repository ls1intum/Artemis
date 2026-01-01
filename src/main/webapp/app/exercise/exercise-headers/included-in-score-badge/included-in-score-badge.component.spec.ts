import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { IncludedInScoreBadgeComponent } from 'app/exercise/exercise-headers/included-in-score-badge/included-in-score-badge.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';

describe('IncludedInScoreBadge', () => {
    let component: IncludedInScoreBadgeComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();
        const fixture = TestBed.createComponent(IncludedInScoreBadgeComponent);
        component = fixture.componentInstance;
    });

    it('should show no badge if no information about score inclusion is known', () => {
        component.ngOnChanges();
        component.ngOnInit();

        expect(component.badgeClass).toBeUndefined();
    });

    it('should show a warning badge if the score is included as bonus', () => {
        component.includedInOverallScore = IncludedInOverallScore.INCLUDED_AS_BONUS;
        component.ngOnChanges();
        component.ngOnInit();

        expect(component.badgeClass).toBe('bg-warning');
    });

    it('should show a success badge if the score is fully included', () => {
        component.includedInOverallScore = IncludedInOverallScore.INCLUDED_COMPLETELY;
        component.ngOnChanges();
        component.ngOnInit();

        expect(component.badgeClass).toBe('bg-success');
    });

    it('should show a danger badge if the score is not included', () => {
        component.includedInOverallScore = IncludedInOverallScore.NOT_INCLUDED;
        component.ngOnChanges();
        component.ngOnInit();

        expect(component.badgeClass).toBe('bg-secondary');
    });
});
