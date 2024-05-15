import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LearningPathStudentNavOverviewComponent } from './learning-path-student-nav-overview.component';

describe('LearningPathStudentNavOverviewComponent', () => {
    let component: LearningPathStudentNavOverviewComponent;
    let fixture: ComponentFixture<LearningPathStudentNavOverviewComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LearningPathStudentNavOverviewComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(LearningPathStudentNavOverviewComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
