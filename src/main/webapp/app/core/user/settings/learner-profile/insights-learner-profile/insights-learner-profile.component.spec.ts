import { ComponentFixture, TestBed } from '@angular/core/testing';

import { InsightsLearnerProfileComponent } from './insights-learner-profile.component';

describe('InsightsLearnerProfile', () => {
    let component: InsightsLearnerProfileComponent;
    let fixture: ComponentFixture<InsightsLearnerProfileComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [InsightsLearnerProfileComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(InsightsLearnerProfileComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
