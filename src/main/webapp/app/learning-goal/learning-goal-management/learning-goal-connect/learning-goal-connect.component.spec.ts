import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LearningGoalConnectComponent } from './learning-goal-connect.component';

describe('LearningGoalConnectComponent', () => {
    let component: LearningGoalConnectComponent;
    let fixture: ComponentFixture<LearningGoalConnectComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [LearningGoalConnectComponent],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(LearningGoalConnectComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
