import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ProgrammingExerciseTimeline } from './programming-exercise-timeline';

describe('ProgrammingExerciseTimeline', () => {
    let component: ProgrammingExerciseTimeline;
    let fixture: ComponentFixture<ProgrammingExerciseTimeline>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ProgrammingExerciseTimeline],
        }).compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseTimeline);
        component = fixture.componentInstance;
        await fixture.whenStable();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
