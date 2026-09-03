import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import dayjs from 'dayjs/esm';
import { describe, expect, it, vi } from 'vitest';
import { TimelineStubComponent } from 'test/helpers/stubs/exercise/timeline-stub.component';

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
        expect(timelineItems[3].errorStringKey?.()).toBeUndefined();
        expect(timelineItems.every((item) => !item.disabled)).toBe(true);
    });

    it('should disable all group-managed dates', () => {
        fixture.componentRef.setInput('exercisePartOfExerciseGroup', true);
        fixture.detectChanges();

        expect(component.timelineItems().every((item) => item.disabled)).toBe(true);
    });

    it('should pass an external error to the example solution publication date', () => {
        fixture.componentRef.setInput('exampleSolutionPublicationDateErrorStringKey', 'timeline.externalError');

        const publicationDateItem = component.timelineItems()[4];

        expect(publicationDateItem.errorStringKey?.()).toBe('timeline.externalError');
    });

    it('should require a due date when an assessment due date is set', () => {
        component.assessmentDueDate.set(dayjs('2026-01-10T10:00:00Z'));

        expect(component.timelineItems()[3].errorStringKey?.()).toBe('artemisApp.exercise.assessmentDueDateRequiresDueDate');

        component.dueDate.set(dayjs('2026-01-09T10:00:00Z'));

        expect(component.timelineItems()[3].errorStringKey?.()).toBeUndefined();
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
