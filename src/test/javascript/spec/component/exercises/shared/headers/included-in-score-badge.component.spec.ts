import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { IncludedInScoreBadgeComponent } from 'app/exercises/shared/exercise-headers/included-in-score-badge.component';
import { MockTranslateService } from '../../../../helpers/mocks/service/mock-translate.service';
import { IncludedInOverallScore } from 'app/entities/exercise.model';

describe('IncludedInScoreBadge', () => {
    let component: IncludedInScoreBadgeComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [IncludedInScoreBadgeComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                const fixture = TestBed.createComponent(IncludedInScoreBadgeComponent);
                component = fixture.componentInstance;
            });
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
