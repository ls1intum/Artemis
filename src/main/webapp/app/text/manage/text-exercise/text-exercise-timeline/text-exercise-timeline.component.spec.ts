import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { describe, expect, it, vi } from 'vitest';
import { ExerciseTimelineStubComponent } from 'test/helpers/stubs/modeling/exercise-timeline-stub.component';

import { TextExerciseTimelineComponent } from './text-exercise-timeline.component';

describe('TextExerciseTimeline', () => {
    let component: TextExerciseTimelineComponent;
    let fixture: ComponentFixture<TextExerciseTimelineComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TextExerciseTimelineComponent],
        })
            .overrideComponent(TextExerciseTimelineComponent, {
                set: { imports: [ExerciseTimelineStubComponent] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(TextExerciseTimelineComponent);
        component = fixture.componentInstance;
        await fixture.whenStable();
    });

    it('should expose all text exercise dates in chronological order', () => {
        expect(component.timelineItems).toHaveLength(4);
        expect(component.timelineItems.map((item) => item.labelStringKey)).toEqual([
            'artemisApp.exercise.releaseDate',
            'artemisApp.exercise.startDate',
            'artemisApp.exercise.dueDate',
            'artemisApp.exercise.assessmentDueDate',
        ]);
        expect(component.timelineItems.map((item) => item.date)).toEqual([component.releaseDate, component.startDate, component.dueDate, component.assessmentDueDate]);
        expect(component.timelineItems.every((item) => item.kind === 'optional')).toBe(true);
        expect(component.timelineItems[3].otherRequiredItem).toBe(component.timelineItems[2]);
    });

    it('should forward timeline status changes from the exercise timeline', () => {
        fixture.detectChanges();
        const timelineStatus = { valid: false, empty: true, invalidItems: [] };
        const emitSpy = vi.spyOn(component.timelineStatus, 'emit');
        const exerciseTimeline = fixture.debugElement.query(By.directive(ExerciseTimelineStubComponent)).componentInstance as ExerciseTimelineStubComponent;

        exerciseTimeline.timelineStatusChange.emit(timelineStatus);

        expect(emitSpy).toHaveBeenCalledExactlyOnceWith(timelineStatus);
    });
});
