import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PlannedExerciseSeriesCreateComponent } from './planned-exercise-series-create.component';

describe('PlannedExerciseSeriesCreate', () => {
    let component: PlannedExerciseSeriesCreateComponent;
    let fixture: ComponentFixture<PlannedExerciseSeriesCreateComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [PlannedExerciseSeriesCreateComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(PlannedExerciseSeriesCreateComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
