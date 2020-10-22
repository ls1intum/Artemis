import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ExerciseUnitComponent } from 'app/overview/course-lectures/exercise-unit/exercise-unit.component';

describe('ExerciseUnitComponent', () => {
    let component: ExerciseUnitComponent;
    let fixture: ComponentFixture<ExerciseUnitComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [ExerciseUnitComponent],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(ExerciseUnitComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
