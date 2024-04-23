import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LearningPathStudentPageComponent } from './learning-path-student-page.component';

describe('LearningPathStudentPageComponent', () => {
    let component: LearningPathStudentPageComponent;
    let fixture: ComponentFixture<LearningPathStudentPageComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LearningPathStudentPageComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(LearningPathStudentPageComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
