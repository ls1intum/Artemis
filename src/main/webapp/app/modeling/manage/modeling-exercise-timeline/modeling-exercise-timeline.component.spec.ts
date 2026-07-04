import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ExerciseTimelineStubComponent } from 'test/helpers/stubs/modeling/exercise-timeline-stub.component';

import { ModelingExerciseTimelineComponent } from './modeling-exercise-timeline.component';

describe('ModelingExerciseTimeline', () => {
    setupTestBed({ zoneless: true });

    let component: ModelingExerciseTimelineComponent;
    let fixture: ComponentFixture<ModelingExerciseTimelineComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ModelingExerciseTimelineComponent],
        })
            .overrideComponent(ModelingExerciseTimelineComponent, {
                set: { imports: [ExerciseTimelineStubComponent] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ModelingExerciseTimelineComponent);
        component = fixture.componentInstance;
        await fixture.whenStable();
    });

    it('should expose timeline items for modeling exercises', () => {
        expect(component.timelineItems).toHaveLength(4);
        expect(component.timelineItems.map((item) => item.labelStringKey)).toEqual([
            'artemisApp.exercise.releaseDate',
            'artemisApp.exercise.startDate',
            'artemisApp.exercise.dueDate',
            'artemisApp.exercise.assessmentDueDate',
        ]);
        expect(component.timelineItems.map((item) => item.date)).toEqual([component.releaseDate, component.startDate, component.dueDate, component.assessmentDueDate]);
        expect(component.timelineItems.every((item) => item.kind === 'optional')).toBe(true);
    });

    it('should forward timeline status changes from the child component', () => {
        fixture.detectChanges();
        const timelineStatus = { valid: true, empty: false };
        const emitSpy = vi.spyOn(component.timelineStatus, 'emit');

        const childComponent = fixture.debugElement.query(By.directive(ExerciseTimelineStubComponent)).componentInstance as ExerciseTimelineStubComponent;
        childComponent.timelineStatusChange.emit(timelineStatus);

        expect(emitSpy).toHaveBeenCalledExactlyOnceWith(timelineStatus);
    });
});
