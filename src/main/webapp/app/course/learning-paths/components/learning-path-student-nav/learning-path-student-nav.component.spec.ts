import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LearningPathStudentNavComponent } from './learning-path-student-nav.component';

describe('LearningPathStudentNavComponent', () => {
    let component: LearningPathStudentNavComponent;
    let fixture: ComponentFixture<LearningPathStudentNavComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LearningPathStudentNavComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(LearningPathStudentNavComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
