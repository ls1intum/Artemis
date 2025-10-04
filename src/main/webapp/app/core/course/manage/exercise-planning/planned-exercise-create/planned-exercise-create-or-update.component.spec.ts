import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PlannedExerciseCreateOrUpdateComponent } from './planned-exercise-create-or-update.component';

describe('PlannedExerciseCreate', () => {
    let component: PlannedExerciseCreateOrUpdateComponent;
    let fixture: ComponentFixture<PlannedExerciseCreateOrUpdateComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [PlannedExerciseCreateOrUpdateComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(PlannedExerciseCreateOrUpdateComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
