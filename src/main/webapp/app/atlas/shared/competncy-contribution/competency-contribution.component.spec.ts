import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CompetencyContributionComponent } from './competency-contribution.component';

describe('CompetncyContributionComponent', () => {
    let component: CompetencyContributionComponent;
    let fixture: ComponentFixture<CompetencyContributionComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CompetencyContributionComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(CompetencyContributionComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
