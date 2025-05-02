import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CompetencyContributionCardComponent } from './competency-contribution-card.component';

describe('CompetncyContributionCardComponent', () => {
    let component: CompetencyContributionCardComponent;
    let fixture: ComponentFixture<CompetencyContributionCardComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CompetencyContributionCardComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(CompetencyContributionCardComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
