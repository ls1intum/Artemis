import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { describe, expect, it, vi } from 'vitest';
import { TimelineStubComponent } from 'test/helpers/stubs/modeling/timeline-stub.component';

import { ModelingExerciseTimelineComponent } from './modeling-exercise-timeline.component';

describe('ModelingExerciseTimeline', () => {
    let component: ModelingExerciseTimelineComponent;
    let fixture: ComponentFixture<ModelingExerciseTimelineComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ModelingExerciseTimelineComponent],
        })
            .overrideComponent(ModelingExerciseTimelineComponent, {
                set: { imports: [TimelineStubComponent] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ModelingExerciseTimelineComponent);
        component = fixture.componentInstance;
        await fixture.whenStable();
    });

    it('should expose timeline items for modeling exercises', () => {
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
    });

    it('should disable group-managed dates', () => {
        fixture.componentRef.setInput('exercisePartOfExerciseGroup', true);
        fixture.detectChanges();

        expect(component.timelineItems().every((item) => item.disabled)).toBe(true);
    });

    it('should forward timeline status changes from the child component', () => {
        fixture.detectChanges();
        const timelineStatus = { valid: true, empty: false };
        const emitSpy = vi.spyOn(component.timelineStatus, 'emit');

        const childComponent = fixture.debugElement.query(By.directive(TimelineStubComponent)).componentInstance as TimelineStubComponent;
        childComponent.timelineStatusChange.emit(timelineStatus);

        expect(emitSpy).toHaveBeenCalledExactlyOnceWith(timelineStatus);
    });
});
