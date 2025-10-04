import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PlannedExerciseComponent } from './planned-exercise.component';

describe('PlannedExercise', () => {
    let component: PlannedExerciseComponent;
    let fixture: ComponentFixture<PlannedExerciseComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [PlannedExerciseComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(PlannedExerciseComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
