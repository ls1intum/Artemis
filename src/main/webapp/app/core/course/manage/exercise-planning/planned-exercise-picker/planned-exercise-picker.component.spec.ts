import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PlannedExercisePickerComponent } from './planned-exercise-picker.component';

describe('PlannedExercisePicker', () => {
    let component: PlannedExercisePickerComponent;
    let fixture: ComponentFixture<PlannedExercisePickerComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [PlannedExercisePickerComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(PlannedExercisePickerComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
