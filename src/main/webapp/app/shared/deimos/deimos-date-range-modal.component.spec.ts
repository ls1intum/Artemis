import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';

import { DeimosDateRangeModalComponent } from 'app/shared/deimos/deimos-date-range-modal.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('DeimosDateRangeModalComponent', () => {
    let fixture: ComponentFixture<DeimosDateRangeModalComponent>;
    let component: DeimosDateRangeModalComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [DeimosDateRangeModalComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(DeimosDateRangeModalComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('titleTranslationKey', 'artemisApp.deimos.modal.title.course');
        fixture.componentRef.setInput('maxWindowDays', 31);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should disable submit when dates are missing', () => {
        expect(component.isSubmitDisabled()).toBe(true);
    });

    it('should disable submit when from is after to', () => {
        component.fromDate.set(dayjs('2026-01-10T12:00:00Z'));
        component.toDate.set(dayjs('2026-01-01T12:00:00Z'));

        expect(component.isOrderInvalid()).toBe(true);
        expect(component.isSubmitDisabled()).toBe(true);
    });

    it('should disable submit when window exceeds maxWindowDays', () => {
        component.fromDate.set(dayjs('2026-01-01T00:00:00Z'));
        component.toDate.set(dayjs('2026-02-15T00:00:00Z'));

        expect(component.isWindowTooLarge()).toBe(true);
        expect(component.isSubmitDisabled()).toBe(true);
    });

    it('should disable submit while parent reports submitting', () => {
        component.fromDate.set(dayjs('2026-01-01T00:00:00Z'));
        component.toDate.set(dayjs('2026-01-07T00:00:00Z'));
        fixture.componentRef.setInput('isSubmitting', true);
        fixture.detectChanges();

        expect(component.isSubmitDisabled()).toBe(true);
    });

    it('should enable submit for a valid range within maxWindowDays', () => {
        component.fromDate.set(dayjs('2026-01-01T00:00:00Z'));
        component.toDate.set(dayjs('2026-01-07T00:00:00Z'));

        expect(component.isSubmitDisabled()).toBe(false);
    });

    it('open should set default dates and show the dialog', () => {
        const from = dayjs('2026-01-01T00:00:00Z');
        const to = dayjs('2026-01-08T00:00:00Z');

        component.open(from, to);

        expect(component.visible()).toBe(true);
        expect(component.fromDate()!.toISOString()).toBe(from.toISOString());
        expect(component.toDate()!.toISOString()).toBe(to.toISOString());
    });

    it('submit should emit selection and close when valid', () => {
        const emitSpy = vi.spyOn(component.confirmSelection, 'emit');
        component.fromDate.set(dayjs('2026-01-01T00:00:00Z'));
        component.toDate.set(dayjs('2026-01-07T00:00:00Z'));
        component.visible.set(true);

        component.submit();

        expect(emitSpy).toHaveBeenCalledOnce();
        expect(emitSpy.mock.calls[0][0].from.toISOString()).toBe(dayjs('2026-01-01T00:00:00Z').toISOString());
        expect(emitSpy.mock.calls[0][0].to.toISOString()).toBe(dayjs('2026-01-07T00:00:00Z').toISOString());
        expect(component.visible()).toBe(false);
    });

    it('submit should no-op when disabled', () => {
        const emitSpy = vi.spyOn(component.confirmSelection, 'emit');

        component.submit();

        expect(emitSpy).not.toHaveBeenCalled();
    });

    it('cancel should hide the dialog', () => {
        component.visible.set(true);

        component.cancel();

        expect(component.visible()).toBe(false);
    });
    describe('with the dialog open', () => {
        function field(id: string): HTMLInputElement | null {
            return document.querySelector(`#${id}`);
        }

        function type(input: HTMLInputElement, text: string) {
            input.value = text;
            input.dispatchEvent(new Event('input'));
            fixture.detectChanges();
        }

        async function openDialog(from?: dayjs.Dayjs, to?: dayjs.Dayjs) {
            component.open(from, to);
            fixture.detectChanges();
            await fixture.whenStable();
            fixture.detectChanges();
        }

        afterEach(() => {
            component.cancel();
            fixture.detectChanges();
        });

        it('should render both pickers filled with the default range', async () => {
            await openDialog(dayjs('2026-01-01T08:30'), dayjs('2026-01-08T17:45'));

            expect(field('deimos-from')?.value).toBe('01.01.2026 08:30');
            expect(field('deimos-to')?.value).toBe('08.01.2026 17:45');
        });

        it('should write typed dates into the signals and enable submit', async () => {
            await openDialog();

            type(field('deimos-from')!, '01.01.2026 08:00');
            type(field('deimos-to')!, '03.01.2026 08:00');

            expect(component.fromDate()?.format('YYYY-MM-DD HH:mm')).toBe('2026-01-01 08:00');
            expect(component.toDate()?.format('YYYY-MM-DD HH:mm')).toBe('2026-01-03 08:00');
            expect(component.isSubmitDisabled()).toBe(false);
        });

        it('should flag both pickers and explain the order error when from is after to', async () => {
            await openDialog(dayjs('2026-01-10T12:00'), dayjs('2026-01-01T12:00'));

            expect(field('deimos-from')?.getAttribute('aria-invalid')).toBe('true');
            expect(field('deimos-to')?.getAttribute('aria-describedby')).toBe('deimos-order-error');
            expect(document.querySelector('#deimos-order-error')).not.toBeNull();
        });

        it('should keep submit disabled while a typed date does not parse', async () => {
            await openDialog(dayjs('2026-01-01T08:00'), dayjs('2026-01-03T08:00'));
            expect(component.isSubmitDisabled()).toBe(false);

            type(field('deimos-to')!, '03.01.');

            expect(component.toTextValid()).toBe(false);
            expect(component.isSubmitDisabled()).toBe(true);
        });
    });
});
