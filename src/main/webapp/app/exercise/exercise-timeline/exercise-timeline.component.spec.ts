import { WritableSignal, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import dayjs, { Dayjs } from 'dayjs/esm';
import { vi } from 'vitest';

import { ExerciseTimelineComponent, TimelineItem } from './exercise-timeline.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ExerciseTimeline', () => {
    setupTestBed({ zoneless: true });
    let component: ExerciseTimelineComponent;
    let fixture: ComponentFixture<ExerciseTimelineComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExerciseTimelineComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseTimelineComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('timelineItems', []);
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
            violationKey: undefined,
        });
        expect(internalTimelineItems[1]).toMatchObject({
            kind: 'required',
            labelStringKey: 'due',
            internalDate: dueDate.toDate(),
            violationKey: 'artemisApp.exercise.timelineDateOrderTooltip',
        });
        expect(internalTimelineItems[2]).toMatchObject({
            kind: 'required',
            labelStringKey: 'assessment',
            internalDate: undefined,
            violationKey: 'artemisApp.exercise.timelineDateRequiredTooltip',
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

        (timelineItems[1].date as WritableSignal<Dayjs | undefined>).set(undefined);
        fixture.detectChanges();

        expect(component.timelineStatus()).toEqual({ valid: false, empty: false });
        expect(emittedStatuses.at(-1)).toEqual({ valid: false, empty: false });
    });

    it('should require another timeline item only when the dependent date is set', () => {
        const dueDateItem: TimelineItem = { kind: 'optional', labelStringKey: 'due', date: signal(undefined) };
        const assessmentDateItem: TimelineItem = {
            kind: 'optional',
            labelStringKey: 'assessment',
            date: signal(undefined),
            otherRequiredItem: dueDateItem,
        };
        fixture.componentRef.setInput('timelineItems', [dueDateItem, assessmentDateItem]);

        expect(component.internalTimelineItems()[1]).toMatchObject({
            violationKey: undefined,
        });
        expect(component.timelineStatus()).toEqual({ valid: true, empty: true });

        assessmentDateItem.date.set(dayjs('2026-01-10T10:00:00Z'));

        expect(component.internalTimelineItems()[1]).toMatchObject({
            violationKey: 'artemisApp.exercise.timelineOtherRequiredDateTooltip',
        });
        expect(component.timelineStatus()).toEqual({ valid: false, empty: false });
    });

    it('should update timeline item date', () => {
        const initialDate = dayjs('2026-01-01T10:00:00Z');
        const newDate = new Date('2026-01-02T10:00:00Z');
        const item: TimelineItem = { kind: 'optional', labelStringKey: 'release', date: signal(initialDate) };
        const setSpy = vi.spyOn(item.date, 'set');

        component.updateDate(item, initialDate.toDate());

        expect(setSpy).not.toHaveBeenCalled();

        component.updateDate(item, newDate);

        expect(item.date()?.isSame(dayjs(newDate))).toBe(true);

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

        expect(item.date()?.isSame(dayjs('2026-01-02T12:30:00'))).toBe(true);
        expect(component.internalTimelineItems()[0].internalDate).toEqual(item.date()?.toDate());
    });

    it('should clear timeline item date for empty manual input', () => {
        const item: TimelineItem = { kind: 'optional', labelStringKey: 'release', date: signal(dayjs('2026-01-01T11:11:00')) };
        fixture.componentRef.setInput('timelineItems', [item]);

        component.handleManualInput(item, { target: { value: '' } } as unknown as Event);

        expect(item.date()).toBeUndefined();
        expect(component.internalTimelineItems()[0].internalDate).toBeUndefined();
    });

    it('should keep incomplete manual input on blur and flag the field invalid (no silent revert)', () => {
        const initialDate = dayjs('2026-06-06T16:23:00');
        const item: TimelineItem = { kind: 'optional', labelStringKey: 'release', date: signal(initialDate) };
        fixture.componentRef.setInput('timelineItems', [item]);
        const input = { value: '0.06.2026 16:23' } as HTMLInputElement;

        component.handleBlur(item, { target: input } as unknown as Event);

        // The entered text is kept (not reverted), the bound date is left untouched, and the field is
        // flagged invalid so the user is notified and saving is blocked (PR #13009 review).
        expect(input.value).toBe('0.06.2026 16:23');
        expect(item.date()).toBe(initialDate);
        expect(component.internalTimelineItems()[0].violationKey).toBe('artemisApp.exercise.timelineDateInvalidTooltip');
        expect(component.timelineStatus().valid).toBe(false);
    });

    it('should keep complete-but-invalid manual input on blur and flag the field invalid', () => {
        const initialDate = dayjs('2026-06-06T16:23:00');
        const item: TimelineItem = { kind: 'optional', labelStringKey: 'release', date: signal(initialDate) };
        fixture.componentRef.setInput('timelineItems', [item]);
        const input = { value: '00.06.2026 16:23' } as HTMLInputElement;

        component.handleBlur(item, { target: input } as unknown as Event);

        expect(input.value).toBe('00.06.2026 16:23');
        expect(item.date()).toBe(initialDate);
        expect(component.internalTimelineItems()[0].violationKey).toBe('artemisApp.exercise.timelineDateInvalidTooltip');
    });

    it('should flag invalid manual input on blur even without a current date value', () => {
        const item: TimelineItem = { kind: 'optional', labelStringKey: 'release', date: signal(undefined) };
        fixture.componentRef.setInput('timelineItems', [item]);
        const input = { value: '0.06.2026 16:23' } as HTMLInputElement;

        component.handleBlur(item, { target: input } as unknown as Event);

        expect(input.value).toBe('0.06.2026 16:23');
        expect(item.date()).toBeUndefined();
        expect(component.internalTimelineItems()[0].violationKey).toBe('artemisApp.exercise.timelineDateInvalidTooltip');
    });

    it('should clear the invalid flag and set the date when a valid date is entered after an invalid one', () => {
        const item: TimelineItem = { kind: 'optional', labelStringKey: 'release', date: signal(undefined) };
        fixture.componentRef.setInput('timelineItems', [item]);

        component.handleBlur(item, { target: { value: 'error' } } as unknown as Event);
        expect(component.internalTimelineItems()[0].violationKey).toBe('artemisApp.exercise.timelineDateInvalidTooltip');

        component.handleManualInput(item, { target: { value: '02.01.2026 12:30' } } as unknown as Event);

        expect(component.internalTimelineItems()[0].violationKey).toBeUndefined();
        expect(item.date()?.isSame(dayjs('2026-01-02T12:30:00'))).toBe(true);
    });

    it('should clear the invalid flag when the field is emptied after an invalid entry', () => {
        const item: TimelineItem = { kind: 'optional', labelStringKey: 'release', date: signal(dayjs('2026-01-01T11:11:00')) };
        fixture.componentRef.setInput('timelineItems', [item]);

        component.handleBlur(item, { target: { value: 'error' } } as unknown as Event);
        expect(component.internalTimelineItems()[0].violationKey).toBe('artemisApp.exercise.timelineDateInvalidTooltip');

        component.handleManualInput(item, { target: { value: '' } } as unknown as Event);

        expect(component.internalTimelineItems()[0].violationKey).toBeUndefined();
        expect(item.date()).toBeUndefined();
    });

    it('should keep valid manual input unchanged on blur', () => {
        const item: TimelineItem = { kind: 'optional', labelStringKey: 'release', date: signal(dayjs('2026-06-06T16:23:00')) };
        const input = { value: '07.06.2026 17:24' } as HTMLInputElement;

        component.handleManualInput(item, { target: input } as unknown as Event);
        component.handleBlur(item, { target: input } as unknown as Event);

        expect(input.value).toBe('07.06.2026 17:24');
        expect(item.date()?.isSame(dayjs('2026-06-07T17:24:00'))).toBe(true);
    });
});
