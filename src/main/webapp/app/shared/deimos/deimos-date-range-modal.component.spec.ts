import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';

import { DeimosDateRangeModalComponent } from 'app/shared/deimos/deimos-date-range-modal.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('DeimosDateRangeModalComponent', () => {
    setupTestBed({ zoneless: true });

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
