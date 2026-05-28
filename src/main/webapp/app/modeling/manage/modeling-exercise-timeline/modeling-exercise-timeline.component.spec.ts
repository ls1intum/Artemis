import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ModelingExerciseTimelineComponent } from './modeling-exercise-timeline.component';

describe('ModelingExerciseTimeline', () => {
    let component: ModelingExerciseTimelineComponent;
    let fixture: ComponentFixture<ModelingExerciseTimelineComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ModelingExerciseTimelineComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(ModelingExerciseTimelineComponent);
        component = fixture.componentInstance;
        await fixture.whenStable();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
