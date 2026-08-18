import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { describe, expect, it, vi } from 'vitest';
import { TimelineStubComponent } from 'test/helpers/stubs/modeling/timeline-stub.component';

import { ExerciseTimelineComponent } from './exercise-timeline.component';

describe('ExerciseTimelineComponent', () => {
    let component: ExerciseTimelineComponent;
    let fixture: ComponentFixture<ExerciseTimelineComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExerciseTimelineComponent],
        })
            .overrideComponent(ExerciseTimelineComponent, {
                set: { imports: [TimelineStubComponent] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ExerciseTimelineComponent);
        component = fixture.componentInstance;
        await fixture.whenStable();
    });

    it('should expose all exercise dates in chronological order', () => {
        const timelineItems = component.timelineItems();

        expect(timelineItems).toHaveLength(5);
        expect(timelineItems.map((item) => item.labelStringKey)).toEqual([
            'artemisApp.exercise.releaseDate',
            'artemisApp.exercise.startDate',
            'artemisApp.exercise.dueDate',
            'artemisApp.exercise.assessmentDueDate',
            'artemisApp.exercise.exampleSolutionPublicationDate',
        ]);
        expect(timelineItems.map((item) => item.date)).toEqual([
            component.releaseDate,
            component.startDate,
            component.dueDate,
            component.assessmentDueDate,
            component.exampleSolutionPublicationDate,
        ]);
        expect(timelineItems.every((item) => item.kind === 'optional')).toBe(true);
        expect(timelineItems[3].otherRequiredItem).toBe(timelineItems[2]);
        expect(timelineItems[4].otherRequiredItem).toBeUndefined();
        expect(timelineItems.every((item) => !item.disabled)).toBe(true);
    });

    it('should disable all group-managed dates', () => {
        fixture.componentRef.setInput('exercisePartOfExerciseGroup', true);
        fixture.detectChanges();

        expect(component.timelineItems().every((item) => item.disabled)).toBe(true);
    });

    it('should forward timeline status changes', () => {
        fixture.detectChanges();
        const timelineStatus = { valid: false, empty: true };
        const emitSpy = vi.spyOn(component.timelineStatus, 'emit');
        const timeline = fixture.debugElement.query(By.directive(TimelineStubComponent)).componentInstance as TimelineStubComponent;

        timeline.timelineStatusChange.emit(timelineStatus);

        expect(emitSpy).toHaveBeenCalledExactlyOnceWith(timelineStatus);
    });
});
