import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import dayjs from 'dayjs/esm';
import { TimelineValidationMode } from 'app/shared-ui/timeline/timeline.component';
import { TimelineStubComponent } from 'test/helpers/stubs/exercise/timeline-stub.component';

import { ProgrammingExerciseReadonlyTimelineComponent } from './programming-exercise-readonly-timeline.component';

describe('ProgrammingExerciseTimelineComponent', () => {
    let component: ProgrammingExerciseReadonlyTimelineComponent;
    let fixture: ComponentFixture<ProgrammingExerciseReadonlyTimelineComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ProgrammingExerciseReadonlyTimelineComponent],
        })
            .overrideComponent(ProgrammingExerciseReadonlyTimelineComponent, { set: { imports: [TimelineStubComponent] } })
            .compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseReadonlyTimelineComponent);
        component = fixture.componentInstance;
    });

    it('should use strict sequential validation', () => {
        fixture.detectChanges();

        const timeline = fixture.debugElement.query(By.directive(TimelineStubComponent)).componentInstance as TimelineStubComponent;

        expect(timeline.validationMode()).toBe(TimelineValidationMode.SEQUENTIALLY_STRICT);
    });

    it('should only expose timeline items with defined dates', () => {
        const startDate = dayjs('2026-01-01T10:00:00Z');
        const buildAndTestAfterDueDate = dayjs('2026-01-10T10:00:00Z');
        const assessmentDueDate = dayjs('2026-01-12T10:00:00Z');

        fixture.componentRef.setInput('releaseDate', undefined);
        fixture.componentRef.setInput('startDate', startDate);
        fixture.componentRef.setInput('dueDate', undefined);
        fixture.componentRef.setInput('buildAndTestStudentSubmissionsAfterDueDate', buildAndTestAfterDueDate);
        fixture.componentRef.setInput('assessmentDueDate', assessmentDueDate);
        fixture.componentRef.setInput('exampleSolutionPublicationDate', undefined);

        const timelineItems = component.timelineItems();

        expect(timelineItems).toHaveLength(3);
        expect(timelineItems.map((item) => item.labelStringKey)).toEqual([
            'artemisApp.exercise.startDate',
            'artemisApp.exercise.dateForRunningTestsAfterDueDate',
            'artemisApp.exercise.assessmentDueDate',
        ]);
        expect(timelineItems.map((item) => item.date())).toEqual([startDate, buildAndTestAfterDueDate, assessmentDueDate]);
        expect(timelineItems.every((item) => item.kind === 'optional')).toBe(true);
        expect(timelineItems.every((item) => item.disabled)).toBe(true);
    });
});
