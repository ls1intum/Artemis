import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PlannedExerciseCreateComponent } from './planned-exercise-create.component';

describe('PlannedExerciseCreate', () => {
    let component: PlannedExerciseCreateComponent;
    let fixture: ComponentFixture<PlannedExerciseCreateComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [PlannedExerciseCreateComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(PlannedExerciseCreateComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
