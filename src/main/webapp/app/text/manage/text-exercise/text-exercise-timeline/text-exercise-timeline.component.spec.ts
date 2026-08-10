import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import dayjs from 'dayjs/esm';
import { describe, expect, it, vi } from 'vitest';
import { ExerciseUpdateTimelineStubComponent } from 'test/helpers/stubs/exercise/exercise-update-timeline-stub.component';

import { TextExerciseTimelineComponent } from './text-exercise-timeline.component';

describe('TextExerciseTimeline', () => {
    let component: TextExerciseTimelineComponent;
    let fixture: ComponentFixture<TextExerciseTimelineComponent>;
    let innerTimeline: ExerciseUpdateTimelineStubComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TextExerciseTimelineComponent],
        })
            .overrideComponent(TextExerciseTimelineComponent, {
                set: { imports: [ExerciseUpdateTimelineStubComponent] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(TextExerciseTimelineComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();
        innerTimeline = fixture.debugElement.query(By.directive(ExerciseUpdateTimelineStubComponent)).componentInstance;
    });

    it('should pass every date and the example solution state down to the shared timeline', () => {
        const dueDate = dayjs().add(2, 'day');
        const publicationDate = dayjs().add(5, 'day');
        fixture.componentRef.setInput('hasExampleSolution', true);
        fixture.componentRef.setInput('dueDate', dueDate);
        fixture.componentRef.setInput('exampleSolutionPublicationDate', publicationDate);
        fixture.detectChanges();

        expect(innerTimeline.hasExampleSolution()).toBe(true);
        expect(innerTimeline.isImport()).toBe(false);
        expect(innerTimeline.dueDate()).toBe(dueDate);
        expect(innerTimeline.exampleSolutionPublicationDate()).toBe(publicationDate);
    });

    it('should propagate a publication date cleared by the shared timeline back to the form', () => {
        fixture.componentRef.setInput('exampleSolutionPublicationDate', dayjs());
        fixture.detectChanges();

        innerTimeline.exampleSolutionPublicationDate.set(undefined);
        fixture.detectChanges();

        expect(component.exampleSolutionPublicationDate()).toBeUndefined();
    });

    it('should forward timeline status changes from the exercise timeline', () => {
        const timelineStatus = { valid: false, empty: true };
        const emitSpy = vi.spyOn(component.timelineStatus, 'emit');

        innerTimeline.timelineStatus.emit(timelineStatus);

        expect(emitSpy).toHaveBeenCalledExactlyOnceWith(timelineStatus);
    });
});
