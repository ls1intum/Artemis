import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import dayjs from 'dayjs/esm';

import { ExerciseTimelineComponent, TimelineItem } from './exercise-timeline.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ExerciseTimeline', () => {
    let component: ExerciseTimelineComponent;
    let fixture: ComponentFixture<ExerciseTimelineComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExerciseTimelineComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseTimelineComponent);
        component = fixture.componentInstance;
        await fixture.whenStable();
    });

    it('should expose correct internal timeline items', () => {
        const releaseDate = dayjs('2026-01-10T10:00:00Z');
        const dueDate = dayjs('2026-01-05T10:00:00Z');
        const timelineItems: TimelineItem[] = [
            { kind: 'optional', labelStringKey: 'release', date: signal(releaseDate) },
            { kind: 'required', labelStringKey: 'due', date: signal(dueDate) },
            { kind: 'required', labelStringKey: 'assessment', date: signal(undefined) },
        ];
        fixture.componentRef.setInput('timelineItems', timelineItems);

        const internalTimelineItems = component.internalTimelineItems();

        expect(internalTimelineItems).toHaveLength(3);
        expect(internalTimelineItems[0]).toMatchObject({
            kind: 'optional',
            labelStringKey: 'release',
            internalDate: releaseDate.toDate(),
            isBeforePreviousDate: false,
            isInputRequiredButUndefined: false,
            tooltip: undefined,
        });
        expect(internalTimelineItems[1]).toMatchObject({
            kind: 'required',
            labelStringKey: 'due',
            internalDate: dueDate.toDate(),
            isBeforePreviousDate: true,
            isInputRequiredButUndefined: false,
            tooltip: 'artemisApp.exercise.timelineDateOrderTooltip',
        });
        expect(internalTimelineItems[2]).toMatchObject({
            kind: 'required',
            labelStringKey: 'assessment',
            internalDate: undefined,
            isBeforePreviousDate: false,
            isInputRequiredButUndefined: true,
            tooltip: 'artemisApp.exercise.timelineDateRequiredTooltip',
        });
    });

    it('should expose and emit timeline status changes', () => {
        const timelineItems: TimelineItem[] = [
            { kind: 'optional', labelStringKey: 'release', date: signal(dayjs('2026-01-01T10:00:00Z')) },
            { kind: 'required', labelStringKey: 'due', date: signal(dayjs('2026-01-10T10:00:00Z')) },
        ];
        const emittedStatuses: Array<{ valid: boolean; empty: boolean }> = [];
        component.timelineStatusChange.subscribe((status) => emittedStatuses.push(status));

        fixture.componentRef.setInput('timelineItems', timelineItems);
        fixture.detectChanges();

        expect(component.timelineStatus()).toEqual({ valid: true, empty: false });
        expect(emittedStatuses.at(-1)).toEqual({ valid: true, empty: false });

        timelineItems[1].date.set(undefined);
        fixture.detectChanges();

        expect(component.timelineStatus()).toEqual({ valid: false, empty: true });
        expect(emittedStatuses.at(-1)).toEqual({ valid: false, empty: true });
    });

    it('should update timeline item date', () => {
        const initialDate = dayjs('2026-01-01T10:00:00Z');
        const newDate = new Date('2026-01-02T10:00:00Z');
        const item: TimelineItem = { kind: 'optional', labelStringKey: 'release', date: signal(initialDate) };
        const setSpy = jest.spyOn(item.date, 'set');

        component.updateDate(item, initialDate.toDate());

        expect(setSpy).not.toHaveBeenCalled();

        component.updateDate(item, newDate);

        expect(item.date()?.isSame(dayjs(newDate))).toBeTrue();

        component.updateDate(item, null);

        expect(item.date()).toBeUndefined();
    });

    it('should not update timeline item date for partial manual input', () => {
        const initialDate = dayjs('2026-01-01T11:11:00');
        const item: TimelineItem = { kind: 'optional', labelStringKey: 'release', date: signal(initialDate) };
        const input = { value: '01.01.2026 11:1' } as HTMLInputElement;
        fixture.componentRef.setInput('timelineItems', [item]);

        component.handleManualInput(item, { target: input } as unknown as Event);

        expect(item.date()).toBe(initialDate);
        expect(component.internalTimelineItems()[0].internalDate).toEqual(initialDate.toDate());
    });

    it('should update timeline item date for complete valid manual input', () => {
        const item: TimelineItem = { kind: 'optional', labelStringKey: 'release', date: signal(dayjs('2026-01-01T11:11:00')) };
        fixture.componentRef.setInput('timelineItems', [item]);

        component.handleManualInput(item, { target: { value: '02.01.2026 12:30' } } as unknown as Event);

        expect(item.date()?.isSame(dayjs('2026-01-02T12:30:00'))).toBeTrue();
        expect(component.internalTimelineItems()[0].internalDate).toEqual(item.date()?.toDate());
    });

    it('should clear timeline item date for empty manual input', () => {
        const item: TimelineItem = { kind: 'optional', labelStringKey: 'release', date: signal(dayjs('2026-01-01T11:11:00')) };
        fixture.componentRef.setInput('timelineItems', [item]);

        component.handleManualInput(item, { target: { value: '' } } as unknown as Event);

        expect(item.date()).toBeUndefined();
        expect(component.internalTimelineItems()[0].internalDate).toBeUndefined();
    });
});
