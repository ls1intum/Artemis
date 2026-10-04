import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { LectureTimelineComponent } from 'app/lecture/manage/lecture-period/lecture-timeline.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';

describe('LectureTimelineComponent', () => {
    let fixture: ComponentFixture<LectureTimelineComponent>;
    let component: LectureTimelineComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LectureTimelineComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(LectureTimelineComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    function orderError(): HTMLElement | null {
        return fixture.nativeElement.querySelector('[data-testid="lecture-period-order-error"]');
    }

    it('should label both date fields', () => {
        const labels = Array.from(fixture.nativeElement.querySelectorAll('label')).map((label) => (label as HTMLLabelElement).htmlFor);

        expect(labels).toEqual(['lecture-start-date', 'lecture-end-date']);
        expect(fixture.nativeElement.querySelector('#lecture-start-date')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('#lecture-end-date')).not.toBeNull();
    });

    it('should accept an empty period and a period whose end follows its start', () => {
        expect(component.isValid()).toBe(true);

        component.startDate.set(dayjs('2026-10-01T10:00'));
        component.endDate.set(dayjs('2026-10-01T12:00'));
        fixture.detectChanges();

        expect(component.isValid()).toBe(true);
        expect(orderError()).toBeNull();
    });

    it.each([
        ['before', dayjs('2026-10-01T08:00')],
        ['equal to', dayjs('2026-10-01T10:00')],
    ])('should reject an end %s the start and say why', (_, endDate) => {
        const validity: boolean[] = [];
        component.periodValidChange.subscribe((valid) => validity.push(valid));

        component.startDate.set(dayjs('2026-10-01T10:00'));
        component.endDate.set(endDate);
        fixture.detectChanges();

        expect(component.isEndBeforeStart()).toBe(true);
        expect(component.isValid()).toBe(false);
        expect(validity.at(-1)).toBe(false);
        expect(orderError()).not.toBeNull();
    });

    it('should report typed text that is not a date yet as invalid', () => {
        const startField: HTMLInputElement = fixture.nativeElement.querySelector('#lecture-start-date');

        startField.value = '01.10.';
        startField.dispatchEvent(new Event('input'));
        startField.dispatchEvent(new Event('blur'));
        fixture.detectChanges();

        expect(component.isValid()).toBe(false);
    });

    it('should emit when a lecture date changes', () => {
        const emitSpy = vi.spyOn(component.datesChanged, 'emit');

        component.startDate.set(dayjs());
        fixture.detectChanges();

        expect(emitSpy).toHaveBeenCalledOnce();
    });
});
