import { ComponentFixture, TestBed } from '@angular/core/testing';

import { FeedbackLearnerProfileComponent } from './feedback-learner-profile.component';

describe('FeedbackLearnerProfileComponent', () => {
    let component: FeedbackLearnerProfileComponent;
    let fixture: ComponentFixture<FeedbackLearnerProfileComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FeedbackLearnerProfileComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(FeedbackLearnerProfileComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
