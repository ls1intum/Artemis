import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LearningPathInstructorPageComponent } from './learning-path-instructor-page.component';

describe('LearningPathInstructorPageComponent', () => {
    let component: LearningPathInstructorPageComponent;
    let fixture: ComponentFixture<LearningPathInstructorPageComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LearningPathInstructorPageComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(LearningPathInstructorPageComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
