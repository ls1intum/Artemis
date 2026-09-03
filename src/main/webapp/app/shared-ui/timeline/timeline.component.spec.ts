import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import dayjs from 'dayjs/esm';
import { DatePicker } from 'primeng/datepicker';
import { vi } from 'vitest';

import { TimelineComponent, TimelineItem, TimelineValidationMode } from './timeline.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ExerciseTimeline', () => {
    let component: TimelineComponent;
    let fixture: ComponentFixture<TimelineComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TimelineComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(TimelineComponent);
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
            hasInvalidDateOrder: false,
            isInputRequiredButUndefined: false,
            tooltip: undefined,
        });
        expect(internalTimelineItems[1]).toMatchObject({
            kind: 'required',
            labelStringKey: 'due',
            internalDate: dueDate.toDate(),
            hasInvalidDateOrder: true,
            isInputRequiredButUndefined: false,
            tooltip: 'artemisApp.exercise.timelineDateOrderTooltip',
        });
        expect(internalTimelineItems[2]).toMatchObject({
            kind: 'required',
            labelStringKey: 'assessment',
            internalDate: undefined,
            hasInvalidDateOrder: false,
            isInputRequiredButUndefined: true,
            tooltip: 'artemisApp.exercise.timelineDateRequiredTooltip',
        });
    });

    it('should disable individual items', () => {
        fixture.componentRef.setInput('timelineItems', [
            { kind: 'optional', labelStringKey: 'release', date: signal(undefined), disabled: true },
            { kind: 'optional', labelStringKey: 'due', date: signal(undefined) },
        ] satisfies TimelineItem[]);

        expect(component.internalTimelineItems().map((item) => item.isDisabled)).toEqual([true, false]);
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
            isOtherRequiredItemDateUndefined: false,
            tooltip: undefined,
        });
        expect(component.timelineStatus()).toEqual({ valid: true, empty: true });

        assessmentDateItem.date.set(dayjs('2026-01-10T10:00:00Z'));

        expect(component.internalTimelineItems()[1]).toMatchObject({
            isOtherRequiredItemDateUndefined: true,
            tooltip: 'artemisApp.exercise.timelineOtherRequiredDateTooltip',
        });
        expect(component.timelineStatus()).toEqual({ valid: false, empty: true });
    });

    it('should allow equal dates by default and reject them in sequentially strict mode', () => {
        const date = dayjs('2026-01-01T10:00:00Z');
        const timelineItems: TimelineItem[] = [
            { kind: 'optional', labelStringKey: 'release', date: signal(date) },
            { kind: 'optional', labelStringKey: 'start', date: signal(date) },
        ];
        fixture.componentRef.setInput('timelineItems', timelineItems);

        expect(component.internalTimelineItems()[1]).toMatchObject({ hasInvalidDateOrder: false, tooltip: undefined });
        expect(component.timelineStatus().valid).toBe(true);

        fixture.componentRef.setInput('validationMode', TimelineValidationMode.SEQUENTIALLY_STRICT);

        expect(component.internalTimelineItems()[1]).toMatchObject({
            hasInvalidDateOrder: true,
            tooltip: 'artemisApp.exercise.timelineDateStrictOrderTooltip',
        });
        expect(component.timelineStatus().valid).toBe(false);
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
        expect(component.internalTimelineItems()[0].isInvalidInput).toBe(true);
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
        expect(component.internalTimelineItems()[0].isInvalidInput).toBe(true);
    });

    it('should flag invalid manual input on blur even without a current date value', () => {
        const item: TimelineItem = { kind: 'optional', labelStringKey: 'release', date: signal(undefined) };
        fixture.componentRef.setInput('timelineItems', [item]);
        const input = { value: '0.06.2026 16:23' } as HTMLInputElement;

        component.handleBlur(item, { target: input } as unknown as Event);

        expect(input.value).toBe('0.06.2026 16:23');
        expect(item.date()).toBeUndefined();
        expect(component.internalTimelineItems()[0].isInvalidInput).toBe(true);
    });

    it('should clear the invalid flag and set the date when a valid date is entered after an invalid one', () => {
        const item: TimelineItem = { kind: 'optional', labelStringKey: 'release', date: signal(undefined) };
        fixture.componentRef.setInput('timelineItems', [item]);

        component.handleBlur(item, { target: { value: 'error' } } as unknown as Event);
        expect(component.internalTimelineItems()[0].isInvalidInput).toBe(true);

        component.handleManualInput(item, { target: { value: '02.01.2026 12:30' } } as unknown as Event);

        expect(component.internalTimelineItems()[0].isInvalidInput).toBe(false);
        expect(item.date()?.isSame(dayjs('2026-01-02T12:30:00'))).toBe(true);
    });

    it('should clear the invalid flag when the field is emptied after an invalid entry', () => {
        const item: TimelineItem = { kind: 'optional', labelStringKey: 'release', date: signal(dayjs('2026-01-01T11:11:00')) };
        fixture.componentRef.setInput('timelineItems', [item]);

        component.handleBlur(item, { target: { value: 'error' } } as unknown as Event);
        expect(component.internalTimelineItems()[0].isInvalidInput).toBe(true);

        component.handleManualInput(item, { target: { value: '' } } as unknown as Event);

        expect(component.internalTimelineItems()[0].isInvalidInput).toBe(false);
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

    it('should display a reactive warning without invalidating the timeline', () => {
        const warningStringKey = signal<string | undefined>('timeline.warning');
        const timelineItem: TimelineItem = {
            kind: 'required',
            labelStringKey: 'release',
            date: signal(dayjs('2026-01-01T10:00:00Z')),
            warningStringKey,
        };
        fixture.componentRef.setInput('timelineItems', [timelineItem]);
        fixture.detectChanges();

        expect(component.internalTimelineItems()[0]).toMatchObject({
            hasWarning: true,
            tooltip: 'timeline.warning',
        });
        expect(component.timelineStatus()).toEqual({ valid: true, empty: false });

        const datePicker = fixture.debugElement.query(By.directive(DatePicker)).componentInstance as DatePicker;
        expect(datePicker.inputStyle?.['border-color']).toBe('var(--warning)');
        expect(fixture.nativeElement.querySelector('.timeline-datepicker-info-icon.warning')).not.toBeNull();

        warningStringKey.set(undefined);
        fixture.detectChanges();

        expect(component.internalTimelineItems()[0]).toMatchObject({
            hasWarning: false,
            tooltip: undefined,
        });
        expect(datePicker.inputStyle?.['border-color']).toBeUndefined();
        expect(fixture.nativeElement.querySelector('.timeline-datepicker-info-icon')).toBeNull();
    });

    it('should let a validation error supersede a warning', () => {
        const timelineItems: TimelineItem[] = [
            { kind: 'required', labelStringKey: 'release', date: signal(dayjs('2026-01-10T10:00:00Z')) },
            {
                kind: 'required',
                labelStringKey: 'due',
                date: signal(dayjs('2026-01-05T10:00:00Z')),
                warningStringKey: signal('timeline.warning'),
            },
        ];
        fixture.componentRef.setInput('timelineItems', timelineItems);
        fixture.detectChanges();

        expect(component.internalTimelineItems()[1]).toMatchObject({
            hasInvalidDateOrder: true,
            hasWarning: false,
            tooltip: 'artemisApp.exercise.timelineDateOrderTooltip',
        });
        expect(component.timelineStatus().valid).toBe(false);

        const secondRow = fixture.nativeElement.querySelectorAll('.timeline-item-row')[1] as HTMLElement;
        const infoIcon = secondRow.querySelector('.timeline-datepicker-info-icon') as HTMLElement;
        const secondDatePicker = fixture.debugElement.queryAll(By.directive(DatePicker))[1].componentInstance as DatePicker;
        expect(secondDatePicker.inputStyle?.['border-color']).toBeUndefined();
        expect(infoIcon.classList.contains('warning')).toBe(false);
    });

    it('should display a reactive external error and invalidate the timeline', () => {
        const errorStringKey = signal<string | undefined>('timeline.externalError');
        const timelineItem: TimelineItem = {
            kind: 'optional',
            labelStringKey: 'release',
            date: signal(dayjs('2026-01-01T10:00:00Z')),
            errorStringKey,
        };
        fixture.componentRef.setInput('timelineItems', [timelineItem]);
        fixture.detectChanges();

        expect(component.internalTimelineItems()[0]).toMatchObject({
            hasExternalError: true,
            tooltip: 'timeline.externalError',
        });
        expect(component.timelineStatus()).toEqual({ valid: false, empty: false });
        expect(fixture.nativeElement.querySelector('.timeline-datepicker-info-icon')).not.toBeNull();

        errorStringKey.set(undefined);
        fixture.detectChanges();

        expect(component.internalTimelineItems()[0]).toMatchObject({
            hasExternalError: false,
            tooltip: undefined,
        });
        expect(component.timelineStatus()).toEqual({ valid: true, empty: false });
    });

    it('should let an internal validation error supersede an external error', () => {
        const timelineItems: TimelineItem[] = [
            { kind: 'optional', labelStringKey: 'release', date: signal(dayjs('2026-01-10T10:00:00Z')) },
            {
                kind: 'optional',
                labelStringKey: 'due',
                date: signal(dayjs('2026-01-05T10:00:00Z')),
                errorStringKey: signal('timeline.externalError'),
            },
        ];
        fixture.componentRef.setInput('timelineItems', timelineItems);

        expect(component.internalTimelineItems()[1]).toMatchObject({
            hasInvalidDateOrder: true,
            hasExternalError: true,
            tooltip: 'artemisApp.exercise.timelineDateOrderTooltip',
        });
    });
});
