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
        component.fromDate.set(dayjs('2026-01-10T12:00:00Z').toDate());
        component.toDate.set(dayjs('2026-01-01T12:00:00Z').toDate());

        expect(component.isOrderInvalid()).toBe(true);
        expect(component.isSubmitDisabled()).toBe(true);
    });

    it('should disable submit when window exceeds maxWindowDays', () => {
        component.fromDate.set(dayjs('2026-01-01T00:00:00Z').toDate());
        component.toDate.set(dayjs('2026-02-15T00:00:00Z').toDate());

        expect(component.isWindowTooLarge()).toBe(true);
        expect(component.isSubmitDisabled()).toBe(true);
    });

    it('should allow a window of exactly maxWindowDays', () => {
        component.fromDate.set(dayjs('2026-01-01T00:00:00Z').toDate());
        component.toDate.set(dayjs('2026-02-01T00:00:00Z').toDate());

        // The server permits exactly 31 * 24h (Duration.between(from, to) > Duration.ofDays(31)), so the client must too.
        expect(component.isWindowTooLarge()).toBe(false);
        expect(component.isSubmitDisabled()).toBe(false);
    });

    it('should reject a window one hour past maxWindowDays', () => {
        component.fromDate.set(dayjs('2026-01-01T00:00:00Z').toDate());
        component.toDate.set(dayjs('2026-02-01T01:00:00Z').toDate());

        expect(component.isWindowTooLarge()).toBe(true);
    });

    it('should reject a window that only fits within maxWindowDays because of a DST transition', () => {
        // 1 October to 1 November in Europe/Berlin: the offset moves from +02:00 to +01:00, so 31 calendar days are
        // 31 days AND one hour of elapsed time. dayjs' .diff(..., 'day', true) compensates for the offset change and
        // reported exactly 31, which enabled Submit for a window the server then rejected with a 400.
        // Explicit offsets keep this independent of the machine timezone, so it cannot silently pass under UTC in CI.
        component.fromDate.set(dayjs('2026-10-01T00:00:00+02:00').toDate());
        component.toDate.set(dayjs('2026-11-01T00:00:00+01:00').toDate());

        // Asserted on elapsed time, not on dayjs' .diff(..., 'day', true): that expression is itself timezone
        // dependent (31 under Europe/Berlin, 31.0416... under UTC), so asserting on it would pass locally and fail
        // in CI. The elapsed span is the same instant arithmetic the server performs, in every timezone.
        const elapsedMs = component.toDate()!.getTime() - component.fromDate()!.getTime();
        expect(elapsedMs).toBe((31 * 24 + 1) * 60 * 60 * 1000);
        expect(component.isWindowTooLarge()).toBe(true);
        expect(component.isSubmitDisabled()).toBe(true);
    });

    it('should use the configured maxWindowDays rather than a hard-coded limit', () => {
        fixture.componentRef.setInput('maxWindowDays', 7);
        fixture.detectChanges();
        component.fromDate.set(dayjs('2026-01-01T00:00:00Z').toDate());
        component.toDate.set(dayjs('2026-01-09T00:00:00Z').toDate());

        expect(component.isWindowTooLarge()).toBe(true);
    });

    it('should disable submit while parent reports submitting', () => {
        component.fromDate.set(dayjs('2026-01-01T00:00:00Z').toDate());
        component.toDate.set(dayjs('2026-01-07T00:00:00Z').toDate());
        fixture.componentRef.setInput('isSubmitting', true);
        fixture.detectChanges();

        expect(component.isSubmitDisabled()).toBe(true);
    });

    it('should enable submit for a valid range within maxWindowDays', () => {
        component.fromDate.set(dayjs('2026-01-01T00:00:00Z').toDate());
        component.toDate.set(dayjs('2026-01-07T00:00:00Z').toDate());

        expect(component.isSubmitDisabled()).toBe(false);
    });

    it('open should set default dates and show the dialog', () => {
        const from = dayjs('2026-01-01T00:00:00Z');
        const to = dayjs('2026-01-08T00:00:00Z');

        component.open(from, to);

        expect(component.visible()).toBe(true);
        expect(dayjs(component.fromDate()!).toISOString()).toBe(from.toISOString());
        expect(dayjs(component.toDate()!).toISOString()).toBe(to.toISOString());
    });

    it('submit should emit selection and close when valid', () => {
        const emitSpy = vi.spyOn(component.confirmSelection, 'emit');
        component.fromDate.set(dayjs('2026-01-01T00:00:00Z').toDate());
        component.toDate.set(dayjs('2026-01-07T00:00:00Z').toDate());
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
});
