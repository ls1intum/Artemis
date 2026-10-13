import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import dayjs from 'dayjs/esm';
import { TumAetUiDatePickerComponent } from '@tumaet/ui-angular';

import { TimelineComponent, TimelineItem, TimelineStatus } from './timeline.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ExerciseTimeline', () => {
    let component: TimelineComponent;
    let fixture: ComponentFixture<TimelineComponent>;

    const pickers = () => fixture.debugElement.queryAll(By.directive(TumAetUiDatePickerComponent));
    const picker = (index: number) => pickers()[index].componentInstance as TumAetUiDatePickerComponent;
    const inputOf = (index: number) => fixture.nativeElement.querySelector(`#datepicker-${index}`) as HTMLInputElement;
    const typeInto = (index: number, text: string) => {
        const input = inputOf(index);
        input.value = text;
        input.dispatchEvent(new Event('input'));
        fixture.detectChanges();
    };
    const blur = (index: number) => {
        inputOf(index).dispatchEvent(new Event('blur'));
        fixture.detectChanges();
    };

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
            hasInvalidDateOrder: false,
            isInputRequiredButUndefined: false,
            tooltip: undefined,
        });
        expect(internalTimelineItems[1]).toMatchObject({
            kind: 'required',
            labelStringKey: 'due',
            hasInvalidDateOrder: true,
            isInputRequiredButUndefined: false,
            tooltip: 'artemisApp.exercise.timelineDateStrictOrderTooltip',
        });
        expect(internalTimelineItems[2]).toMatchObject({
            kind: 'required',
            labelStringKey: 'assessment',
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
        const emittedStatuses: TimelineStatus[] = [];
        component.timelineStatusChange.subscribe((status) => emittedStatuses.push(status));

        fixture.componentRef.setInput('timelineItems', timelineItems);
        fixture.detectChanges();

        expect(component.timelineStatus()).toEqual({ valid: true, empty: false, invalidItems: [] });
        expect(emittedStatuses.at(-1)).toEqual({ valid: true, empty: false, invalidItems: [] });

        timelineItems[1].date.set(undefined);
        fixture.detectChanges();

        const dueRequired = [{ labelStringKey: 'due', reasonKey: 'artemisApp.exercise.form.timeline.required', dateName: 'due' }];
        expect(component.timelineStatus()).toEqual({ valid: false, empty: true, invalidItems: dueRequired });
        expect(emittedStatuses.at(-1)).toEqual({ valid: false, empty: true, invalidItems: dueRequired });
    });

    it('should reject equal dates', () => {
        const date = dayjs('2026-01-01T10:00:00Z');
        const timelineItems: TimelineItem[] = [
            { kind: 'optional', labelStringKey: 'release', date: signal(date) },
            { kind: 'optional', labelStringKey: 'start', date: signal(date) },
        ];
        fixture.componentRef.setInput('timelineItems', timelineItems);

        expect(component.internalTimelineItems()[1]).toMatchObject({
            hasInvalidDateOrder: true,
            tooltip: 'artemisApp.exercise.timelineDateStrictOrderTooltip',
        });
        expect(component.timelineStatus().valid).toBe(false);
    });

    it('should render one date picker per item with stable ids and labels', () => {
        fixture.componentRef.setInput('timelineItems', [
            { kind: 'optional', labelStringKey: 'release', date: signal(undefined) },
            { kind: 'required', labelStringKey: 'due', date: signal(undefined) },
        ] satisfies TimelineItem[]);
        fixture.detectChanges();

        expect(pickers().map((debugElement) => (debugElement.componentInstance as TumAetUiDatePickerComponent).inputId())).toEqual(['datepicker-0', 'datepicker-1']);
        const labels = Array.from(fixture.nativeElement.querySelectorAll('label.label')) as HTMLLabelElement[];
        expect(labels.map((label) => label.htmlFor)).toEqual(['datepicker-0', 'datepicker-1']);
        expect(inputOf(0)).not.toBeNull();
        expect(inputOf(1).getAttribute('aria-required')).toBe('true');
        expect(inputOf(0).getAttribute('aria-required')).toBeNull();
    });

    it('should show the bound date and disable the picker of a disabled item', () => {
        const date = dayjs('2026-01-01T11:11:00');
        fixture.componentRef.setInput('timelineItems', [
            { kind: 'optional', labelStringKey: 'release', date: signal(date), disabled: true },
            { kind: 'optional', labelStringKey: 'due', date: signal(undefined) },
        ] satisfies TimelineItem[]);
        fixture.detectChanges();

        expect(picker(0).value()?.isSame(date)).toBe(true);
        expect(inputOf(0).value).toBe('01.01.2026 11:11');
        expect(picker(0).disabled()).toBe(true);
        expect(inputOf(0).disabled).toBe(true);
        expect(picker(1).disabled()).toBe(false);
    });

    it('should write a picked date to the item', () => {
        const item: TimelineItem = { kind: 'optional', labelStringKey: 'release', date: signal(undefined) };
        fixture.componentRef.setInput('timelineItems', [item]);
        fixture.detectChanges();

        const newDate = dayjs('2026-01-02T12:30:00');
        picker(0).value.set(newDate);
        fixture.detectChanges();

        expect(item.date()?.isSame(newDate)).toBe(true);
    });

    it('should update the item date for complete valid manual input', () => {
        const item: TimelineItem = { kind: 'optional', labelStringKey: 'release', date: signal(dayjs('2026-01-01T11:11:00')) };
        fixture.componentRef.setInput('timelineItems', [item]);
        fixture.detectChanges();

        typeInto(0, '02.01.2026 12:30');

        expect(item.date()?.isSame(dayjs('2026-01-02T12:30:00'))).toBe(true);
        expect(component.internalTimelineItems()[0].isInvalidInput).toBe(false);
    });

    it('should clear the item date for emptied manual input', () => {
        const item: TimelineItem = { kind: 'optional', labelStringKey: 'release', date: signal(dayjs('2026-01-01T11:11:00')) };
        fixture.componentRef.setInput('timelineItems', [item]);
        fixture.detectChanges();

        typeInto(0, '');

        expect(item.date()).toBeUndefined();
        expect(component.internalTimelineItems()[0].isInvalidInput).toBe(false);
    });

    it('should keep the bound date and flag the field invalid for text that is not a date', () => {
        const initialDate = dayjs('2026-06-06T16:23:00');
        const item: TimelineItem = { kind: 'optional', labelStringKey: 'artemisApp.exercise.releaseDate', date: signal(initialDate) };
        fixture.componentRef.setInput('timelineItems', [item]);
        fixture.detectChanges();

        typeInto(0, '00.06.2026 16:23');

        // The entered text is kept (no silent revert), the bound date is untouched and saving is blocked.
        expect(inputOf(0).value).toBe('00.06.2026 16:23');
        expect(item.date()).toBe(initialDate);
        expect(component.internalTimelineItems()[0]).toMatchObject({ isInvalidInput: true, isInvalid: true, tooltip: 'artemisApp.exercise.timelineDateInvalidTooltip' });
        expect(component.timelineStatus()).toEqual({
            valid: false,
            empty: false,
            invalidItems: [
                {
                    labelStringKey: 'artemisApp.exercise.releaseDate',
                    reasonKey: 'artemisApp.exercise.form.timeline.invalidInput',
                    dateName: 'artemisApp.exercise.releaseDate',
                },
            ],
        });
        expect(fixture.nativeElement.querySelector('.timeline-datepicker-info-icon')).not.toBeNull();
        expect(inputOf(0).getAttribute('aria-invalid')).toBe('true');
    });

    it('should flag invalid text even without a bound date', () => {
        const item: TimelineItem = { kind: 'optional', labelStringKey: 'release', date: signal(undefined) };
        fixture.componentRef.setInput('timelineItems', [item]);
        fixture.detectChanges();

        typeInto(0, '0.06.2026 16:23');

        expect(item.date()).toBeUndefined();
        expect(component.internalTimelineItems()[0].isInvalidInput).toBe(true);
    });

    it('should clear the invalid flag when a valid date is typed after an invalid text', () => {
        const item: TimelineItem = { kind: 'optional', labelStringKey: 'release', date: signal(undefined) };
        fixture.componentRef.setInput('timelineItems', [item]);
        fixture.detectChanges();
        typeInto(0, 'error');
        expect(component.internalTimelineItems()[0].isInvalidInput).toBe(true);

        typeInto(0, '02.01.2026 12:30');

        expect(component.internalTimelineItems()[0].isInvalidInput).toBe(false);
        expect(item.date()?.isSame(dayjs('2026-01-02T12:30:00'))).toBe(true);
        expect(component.timelineStatus().valid).toBe(true);
    });

    it('should clear the invalid flag when the field is emptied after an invalid text', () => {
        const item: TimelineItem = { kind: 'optional', labelStringKey: 'release', date: signal(dayjs('2026-01-01T11:11:00')) };
        fixture.componentRef.setInput('timelineItems', [item]);
        fixture.detectChanges();
        typeInto(0, 'error');
        expect(component.internalTimelineItems()[0].isInvalidInput).toBe(true);

        typeInto(0, '');

        expect(component.internalTimelineItems()[0].isInvalidInput).toBe(false);
        expect(item.date()).toBeUndefined();
    });

    it('should clear the invalid flag when a date is picked after an invalid text', () => {
        const item: TimelineItem = { kind: 'optional', labelStringKey: 'release', date: signal(undefined) };
        const emittedStatuses: TimelineStatus[] = [];
        component.timelineStatusChange.subscribe((status) => emittedStatuses.push(status));
        fixture.componentRef.setInput('timelineItems', [item]);
        fixture.detectChanges();
        typeInto(0, 'error');
        expect(emittedStatuses.at(-1)?.valid).toBe(false);

        picker(0).value.set(dayjs('2026-01-02T12:30:00'));
        fixture.detectChanges();

        expect(component.internalTimelineItems()[0].isInvalidInput).toBe(false);
        expect(emittedStatuses.at(-1)).toEqual({ valid: true, empty: false, invalidItems: [] });
    });

    it('should clear the invalid flag when the clear button is used after an invalid text', () => {
        const item: TimelineItem = { kind: 'optional', labelStringKey: 'release', date: signal(dayjs('2026-01-01T11:11:00')) };
        fixture.componentRef.setInput('timelineItems', [item]);
        fixture.detectChanges();
        typeInto(0, 'error');
        expect(component.timelineStatus().valid).toBe(false);

        const clearButton = fixture.nativeElement.querySelector('.tumaet-ui-date-picker button[type="button"]') as HTMLButtonElement;
        clearButton.click();
        fixture.detectChanges();

        expect(component.internalTimelineItems()[0].isInvalidInput).toBe(false);
        expect(component.timelineStatus().valid).toBe(true);
        expect(item.date()).toBeUndefined();
    });

    it('should clear only the invalid flag of the item whose date was picked', () => {
        const release: TimelineItem = { kind: 'optional', labelStringKey: 'release', date: signal(undefined) };
        const due: TimelineItem = { kind: 'optional', labelStringKey: 'due', date: signal(undefined) };
        fixture.componentRef.setInput('timelineItems', [release, due]);
        fixture.detectChanges();
        typeInto(0, 'error');
        typeInto(1, 'oops');
        expect(component.internalTimelineItems().map((item) => item.isInvalidInput)).toEqual([true, true]);

        picker(0).value.set(dayjs('2026-01-02T12:30:00'));
        fixture.detectChanges();

        expect(component.internalTimelineItems().map((item) => item.isInvalidInput)).toEqual([false, true]);
        expect(component.timelineStatus().invalidItems.map((invalidItem) => invalidItem.labelStringKey)).toEqual(['due']);
        expect(due.date()).toBeUndefined();
    });

    it('should only paint a missing required date as an error after the field was left', () => {
        const item: TimelineItem = { kind: 'required', labelStringKey: 'due', date: signal(undefined) };
        fixture.componentRef.setInput('timelineItems', [item]);
        fixture.detectChanges();

        // Always part of the status, so that saving stays blocked ...
        expect(component.timelineStatus().valid).toBe(false);
        // ... but not painted before the user had a chance to fill it in.
        expect(component.internalTimelineItems()[0].isInvalid).toBe(false);
        expect(fixture.nativeElement.querySelector('.timeline-datepicker-info-icon')).toBeNull();
        expect(picker(0).invalid()).toBe(false);

        blur(0);

        expect(component.internalTimelineItems()[0]).toMatchObject({ isInvalid: true, tooltip: 'artemisApp.exercise.timelineDateRequiredTooltip' });
        expect(fixture.nativeElement.querySelector('.timeline-datepicker-info-icon')).not.toBeNull();
        expect(picker(0).invalid()).toBe(true);
    });

    it('should not paint an empty optional date as an error after the field was left', () => {
        fixture.componentRef.setInput('timelineItems', [{ kind: 'optional', labelStringKey: 'release', date: signal(undefined) }] satisfies TimelineItem[]);
        fixture.detectChanges();

        blur(0);

        expect(component.internalTimelineItems()[0].isInvalid).toBe(false);
        expect(component.timelineStatus().valid).toBe(true);
    });

    it('should paint a date that is not after an earlier date as an error with an accessible tooltip', () => {
        fixture.componentRef.setInput('timelineItems', [
            { kind: 'required', labelStringKey: 'release', date: signal(dayjs('2026-01-10T10:00:00Z')) },
            { kind: 'required', labelStringKey: 'due', date: signal(dayjs('2026-01-05T10:00:00Z')) },
        ] satisfies TimelineItem[]);
        fixture.detectChanges();

        expect(picker(0).invalid()).toBe(false);
        expect(picker(1).invalid()).toBe(true);
        const infoIcon = fixture.nativeElement.querySelector('#datepicker-1-info') as HTMLElement;
        expect(infoIcon.getAttribute('role')).toBe('img');
        expect(infoIcon.getAttribute('aria-label')).toBe('artemisApp.exercise.timelineDateStrictOrderTooltip');
        expect(inputOf(1).getAttribute('aria-describedby')).toContain('datepicker-1-info');
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
        expect(component.timelineStatus()).toEqual({ valid: true, empty: false, invalidItems: [] });

        expect(picker(0).invalid()).toBe(false);
        expect(fixture.nativeElement.querySelector('.timeline-datepicker.warning')).not.toBeNull();
        const infoIcon = fixture.nativeElement.querySelector('.timeline-datepicker-info-icon.warning') as HTMLElement;
        expect(infoIcon).not.toBeNull();
        expect(infoIcon.getAttribute('aria-label')).toBe('timeline.warning');

        warningStringKey.set(undefined);
        fixture.detectChanges();

        expect(component.internalTimelineItems()[0]).toMatchObject({
            hasWarning: false,
            tooltip: undefined,
        });
        expect(fixture.nativeElement.querySelector('.timeline-datepicker.warning')).toBeNull();
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
            tooltip: 'artemisApp.exercise.timelineDateStrictOrderTooltip',
        });
        expect(component.timelineStatus().valid).toBe(false);

        const secondRow = fixture.nativeElement.querySelectorAll('.timeline-item-row')[1] as HTMLElement;
        const infoIcon = secondRow.querySelector('.timeline-datepicker-info-icon') as HTMLElement;
        expect(secondRow.querySelector('.timeline-datepicker.warning')).toBeNull();
        expect(picker(1).invalid()).toBe(true);
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
        expect(component.timelineStatus()).toEqual({
            valid: false,
            empty: false,
            invalidItems: [{ labelStringKey: 'release', reasonKey: 'timeline.externalError', dateName: 'release' }],
        });
        expect(fixture.nativeElement.querySelector('.timeline-datepicker-info-icon')).not.toBeNull();

        errorStringKey.set(undefined);
        fixture.detectChanges();

        expect(component.internalTimelineItems()[0]).toMatchObject({
            hasExternalError: false,
            tooltip: undefined,
        });
        expect(component.timelineStatus()).toEqual({ valid: true, empty: false, invalidItems: [] });
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
            tooltip: 'artemisApp.exercise.timelineDateStrictOrderTooltip',
        });
    });

    it('should report which timeline items are invalid and why', () => {
        const timelineItems: TimelineItem[] = [
            { kind: 'optional', labelStringKey: 'artemisApp.exercise.releaseDate', date: signal(dayjs('2026-01-10T10:00:00Z')) },
            { kind: 'required', labelStringKey: 'artemisApp.exercise.startDate', date: signal(undefined) },
            { kind: 'optional', labelStringKey: 'artemisApp.exercise.dueDate', date: signal(dayjs('2026-01-05T10:00:00Z')) },
        ];
        fixture.componentRef.setInput('timelineItems', timelineItems);

        const status = component.timelineStatus();

        expect(status.valid).toBe(false);
        expect(status.invalidItems).toEqual([
            {
                labelStringKey: 'artemisApp.exercise.startDate',
                reasonKey: 'artemisApp.exercise.form.timeline.required',
                dateName: 'artemisApp.exercise.startDate',
            },
            {
                labelStringKey: 'artemisApp.exercise.dueDate',
                reasonKey: 'artemisApp.exercise.form.timeline.strictOrder',
                dateName: 'artemisApp.exercise.dueDate',
            },
        ]);
    });

    it('should report a malformed manual date entry as an invalid item', () => {
        const item: TimelineItem = { kind: 'optional', labelStringKey: 'artemisApp.exercise.releaseDate', date: signal(dayjs('2026-06-06T16:23:00')) };
        fixture.componentRef.setInput('timelineItems', [item]);
        fixture.detectChanges();

        typeInto(0, '00.06.2026 16:23');

        expect(component.timelineStatus().invalidItems).toEqual([
            {
                labelStringKey: 'artemisApp.exercise.releaseDate',
                reasonKey: 'artemisApp.exercise.form.timeline.invalidInput',
                dateName: 'artemisApp.exercise.releaseDate',
            },
        ]);
    });

    it('should keep an item invalid when its external error key is an empty string', () => {
        // hasExternalError is `errorStringKey !== undefined`, so an empty key still paints the field as an error.
        // The status has to agree with that, or the form reports valid while showing red.
        const item: TimelineItem = {
            kind: 'optional',
            labelStringKey: 'artemisApp.exercise.releaseDate',
            date: signal(dayjs('2026-01-01T10:00:00Z')),
            errorStringKey: signal(''),
        };
        fixture.componentRef.setInput('timelineItems', [item]);

        expect(component.internalTimelineItems()[0].hasExternalError).toBe(true);
        expect(component.timelineStatus().valid).toBe(false);
        expect(component.timelineStatus().invalidItems).toHaveLength(1);
    });

    it('should report an external error as an invalid item, using the key the item supplies', () => {
        const item: TimelineItem = {
            kind: 'optional',
            labelStringKey: 'artemisApp.exercise.releaseDate',
            date: signal(dayjs('2026-01-01T10:00:00Z')),
            errorStringKey: signal('timeline.externalError'),
        };
        fixture.componentRef.setInput('timelineItems', [item]);

        expect(component.timelineStatus().invalidItems).toEqual([
            {
                labelStringKey: 'artemisApp.exercise.releaseDate',
                reasonKey: 'timeline.externalError',
                dateName: 'artemisApp.exercise.releaseDate',
            },
        ]);
    });
});
