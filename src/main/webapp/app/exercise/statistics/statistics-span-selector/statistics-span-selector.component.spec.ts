import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { SpanType } from 'app/exercise/shared/entities/statistics.model';
import { StatisticsSpanSelectorComponent } from './statistics-span-selector.component';

describe('statistics span selector', () => {
    beforeEach(() => TestBed.configureTestingModule({ providers: [{ provide: TranslateService, useClass: MockTranslateService }] }));
    it('renders the active period and reports the selected period', () => {
        const fixture = TestBed.createComponent(StatisticsSpanSelectorComponent);
        const changed = vi.fn();
        fixture.componentRef.setInput('currentSpan', SpanType.MONTH);
        fixture.componentInstance.spanChange.subscribe(changed);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('label.active')?.textContent).toContain('statistics.span.month');
        expect(fixture.nativeElement.querySelector('input:checked')?.id).toBe('option3');
        const dayLabel: HTMLLabelElement = fixture.nativeElement.querySelector('label');
        expect(dayLabel.control?.id).toBe('option1');
        fixture.nativeElement.querySelector('#option1').click();
        expect(changed).toHaveBeenCalledWith(SpanType.DAY);
        fixture.componentRef.setInput('currentSpan', SpanType.YEAR);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('label.active')?.textContent).toContain('statistics.span.year');
        expect(fixture.nativeElement.querySelectorAll('input:checked')).toHaveLength(1);
        expect(fixture.nativeElement.querySelector('input:checked')?.id).toBe('option5');
        changed.mockClear();
        dayLabel.click();
        expect(changed).toHaveBeenCalledExactlyOnceWith(SpanType.DAY);
    });
});
