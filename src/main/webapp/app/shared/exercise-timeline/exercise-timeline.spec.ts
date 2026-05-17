import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ExerciseTimeline } from './exercise-timeline';

describe('ExerciseTimeline', () => {
    let component: ExerciseTimeline;
    let fixture: ComponentFixture<ExerciseTimeline>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExerciseTimeline],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseTimeline);
        component = fixture.componentInstance;
        await fixture.whenStable();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
