import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import dayjs from 'dayjs/esm';
import { describe, expect, it, vi } from 'vitest';
import { ExerciseUpdateTimelineStubComponent } from 'test/helpers/stubs/exercise/exercise-update-timeline-stub.component';

import { ModelingExerciseTimelineComponent } from './modeling-exercise-timeline.component';

describe('ModelingExerciseTimeline', () => {
    let component: ModelingExerciseTimelineComponent;
    let fixture: ComponentFixture<ModelingExerciseTimelineComponent>;
    let innerTimeline: ExerciseUpdateTimelineStubComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ModelingExerciseTimelineComponent],
        })
            .overrideComponent(ModelingExerciseTimelineComponent, {
                set: { imports: [ExerciseUpdateTimelineStubComponent] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ModelingExerciseTimelineComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();
        innerTimeline = fixture.debugElement.query(By.directive(ExerciseUpdateTimelineStubComponent)).componentInstance;
    });

    it('should pass every date and the example solution state down to the shared timeline', () => {
        const releaseDate = dayjs().add(1, 'day');
        const publicationDate = dayjs().add(5, 'day');
        fixture.componentRef.setInput('hasExampleSolution', true);
        fixture.componentRef.setInput('isImport', true);
        fixture.componentRef.setInput('releaseDate', releaseDate);
        fixture.componentRef.setInput('exampleSolutionPublicationDate', publicationDate);
        fixture.detectChanges();

        expect(innerTimeline.hasExampleSolution()).toBe(true);
        expect(innerTimeline.isImport()).toBe(true);
        expect(innerTimeline.releaseDate()).toBe(releaseDate);
        expect(innerTimeline.exampleSolutionPublicationDate()).toBe(publicationDate);
    });

    it('should propagate a publication date cleared by the shared timeline back to the form', () => {
        fixture.componentRef.setInput('exampleSolutionPublicationDate', dayjs());
        fixture.detectChanges();

        innerTimeline.exampleSolutionPublicationDate.set(undefined);
        fixture.detectChanges();

        expect(component.exampleSolutionPublicationDate()).toBeUndefined();
    });

    it('should forward timeline status changes from the child component', () => {
        const timelineStatus = { valid: true, empty: false };
        const emitSpy = vi.spyOn(component.timelineStatus, 'emit');

        innerTimeline.timelineStatus.emit(timelineStatus);

        expect(emitSpy).toHaveBeenCalledExactlyOnceWith(timelineStatus);
    });
});
