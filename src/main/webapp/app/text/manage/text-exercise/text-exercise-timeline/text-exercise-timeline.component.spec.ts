import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { describe, expect, it, vi } from 'vitest';
import { TimelineStubComponent } from 'test/helpers/stubs/modeling/timeline-stub.component';

import { TextExerciseTimelineComponent } from './text-exercise-timeline.component';

describe('TextExerciseTimeline', () => {
    let component: TextExerciseTimelineComponent;
    let fixture: ComponentFixture<TextExerciseTimelineComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TextExerciseTimelineComponent],
        })
            .overrideComponent(TextExerciseTimelineComponent, {
                set: { imports: [TimelineStubComponent] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(TextExerciseTimelineComponent);
        component = fixture.componentInstance;
        await fixture.whenStable();
    });

    it('should expose all text exercise dates in chronological order', () => {
        const timelineItems = component.timelineItems();

        expect(timelineItems).toHaveLength(4);
        expect(timelineItems.map((item) => item.labelStringKey)).toEqual([
            'artemisApp.exercise.releaseDate',
            'artemisApp.exercise.startDate',
            'artemisApp.exercise.dueDate',
            'artemisApp.exercise.assessmentDueDate',
        ]);
        expect(timelineItems.map((item) => item.date)).toEqual([component.releaseDate, component.startDate, component.dueDate, component.assessmentDueDate]);
        expect(timelineItems.every((item) => item.kind === 'optional')).toBe(true);
        expect(timelineItems[3].otherRequiredItem).toBe(timelineItems[2]);
    });

    it('should disable group-managed dates', () => {
        fixture.componentRef.setInput('exercisePartOfExerciseGroup', true);
        fixture.detectChanges();

        expect(component.timelineItems().every((item) => item.disabled)).toBe(true);
    });

    it('should forward timeline status changes from the exercise timeline', () => {
        fixture.detectChanges();
        const timelineStatus = { valid: false, empty: true };
        const emitSpy = vi.spyOn(component.timelineStatus, 'emit');
        const exerciseTimeline = fixture.debugElement.query(By.directive(TimelineStubComponent)).componentInstance as TimelineStubComponent;

        exerciseTimeline.timelineStatusChange.emit(timelineStatus);

        expect(emitSpy).toHaveBeenCalledExactlyOnceWith(timelineStatus);
    });
});
