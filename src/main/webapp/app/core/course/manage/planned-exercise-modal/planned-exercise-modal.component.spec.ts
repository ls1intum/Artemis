import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PlannedExerciseModalComponent } from './planned-exercise-modal.component';

describe('PlannedExerciseModal', () => {
    let component: PlannedExerciseModalComponent;
    let fixture: ComponentFixture<PlannedExerciseModalComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [PlannedExerciseModalComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(PlannedExerciseModalComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
