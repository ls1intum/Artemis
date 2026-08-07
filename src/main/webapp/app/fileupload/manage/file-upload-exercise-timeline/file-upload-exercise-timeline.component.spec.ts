import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { describe, expect, it, vi } from 'vitest';
import { ExerciseTimelineStubComponent } from 'test/helpers/stubs/modeling/exercise-timeline-stub.component';

import { FileUploadExerciseTimelineComponent } from './file-upload-exercise-timeline.component';

describe('FileUploadExerciseTimeline', () => {
    let component: FileUploadExerciseTimelineComponent;
    let fixture: ComponentFixture<FileUploadExerciseTimelineComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FileUploadExerciseTimelineComponent],
        })
            .overrideComponent(FileUploadExerciseTimelineComponent, {
                set: { imports: [ExerciseTimelineStubComponent] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(FileUploadExerciseTimelineComponent);
        component = fixture.componentInstance;
        await fixture.whenStable();
    });

    it('should expose all file upload exercise dates in chronological order', () => {
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
        const timelineStatus = { valid: false, empty: true };
        const emitSpy = vi.spyOn(component.timelineStatus, 'emit');
        const exerciseTimeline = fixture.debugElement.query(By.directive(ExerciseTimelineStubComponent)).componentInstance as ExerciseTimelineStubComponent;

        exerciseTimeline.timelineStatusChange.emit(timelineStatus);

        expect(emitSpy).toHaveBeenCalledExactlyOnceWith(timelineStatus);
    });
});
