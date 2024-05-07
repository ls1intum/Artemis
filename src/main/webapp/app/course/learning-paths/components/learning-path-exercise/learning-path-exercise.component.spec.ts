import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LearningPathExerciseComponent } from './learning-path-exercise.component';

describe('LearningPathExerciseComponent', () => {
    let component: LearningPathExerciseComponent;
    let fixture: ComponentFixture<LearningPathExerciseComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LearningPathExerciseComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(LearningPathExerciseComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
