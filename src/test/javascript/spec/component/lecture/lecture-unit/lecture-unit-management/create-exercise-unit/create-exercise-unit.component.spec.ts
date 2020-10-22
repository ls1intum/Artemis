import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CreateExerciseUnitComponent } from 'app/lecture/lecture-unit/lecture-unit-management/create-exercise-unit/create-exercise-unit.component';

describe('CreateExerciseUnitComponent', () => {
    let component: CreateExerciseUnitComponent;
    let fixture: ComponentFixture<CreateExerciseUnitComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [CreateExerciseUnitComponent],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(CreateExerciseUnitComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
