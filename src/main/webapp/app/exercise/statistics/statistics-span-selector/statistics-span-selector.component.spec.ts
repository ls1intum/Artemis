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
        fixture.nativeElement.querySelector('#option1').click();
        expect(changed).toHaveBeenCalledWith(SpanType.DAY);
        fixture.componentRef.setInput('currentSpan', SpanType.YEAR);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('label.active')?.textContent).toContain('statistics.span.year');
    });
});
