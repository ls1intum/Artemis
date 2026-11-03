import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { SpanType } from 'app/exercise/shared/entities/statistics.model';
import { StatisticsSpanSelectorComponent } from './statistics-span-selector.component';

describe('statistics span selector', () => {
    let fixture: ComponentFixture<StatisticsSpanSelectorComponent>;

    const options = (): HTMLButtonElement[] => Array.from(fixture.nativeElement.querySelectorAll('button'));
    const option = (label: string): HTMLButtonElement => options().find((button) => button.textContent?.trim() === label)!;

    const setSpan = async (span: SpanType): Promise<void> => {
        fixture.componentRef.setInput('currentSpan', span);
        await fixture.whenStable();
    };

    beforeEach(async () => {
        TestBed.configureTestingModule({ providers: [{ provide: TranslateService, useClass: MockTranslateService }] });
        fixture = TestBed.createComponent(StatisticsSpanSelectorComponent);
        await setSpan(SpanType.MONTH);
    });

    it('renders every period as a button and marks the active one', () => {
        expect(options().map((button) => button.textContent?.trim())).toEqual([
            'statistics.span.day',
            'statistics.span.week',
            'statistics.span.month',
            'statistics.span.quarter',
            'statistics.span.year',
        ]);
        expect(option('statistics.span.month').getAttribute('aria-pressed')).toBe('true');
        expect(options().filter((button) => button.getAttribute('aria-pressed') === 'true')).toHaveLength(1);
    });

    it('renders no radio input, so no misaligned radio dot can appear', () => {
        expect(fixture.nativeElement.querySelectorAll('input')).toHaveLength(0);
    });

    it('gives the group an accessible name', () => {
        expect(fixture.nativeElement.querySelector('tumaet-ui-select-button').getAttribute('aria-label')).toBe('statistics.spanLabel');
    });

    it('reports the newly selected period', async () => {
        const changed = vi.fn();
        fixture.componentInstance.spanChange.subscribe(changed);

        option('statistics.span.day').click();
        await fixture.whenStable();

        expect(changed).toHaveBeenCalledExactlyOnceWith(SpanType.DAY);
    });

    it('keeps a period selected when the active one is clicked again', async () => {
        const changed = vi.fn();
        fixture.componentInstance.spanChange.subscribe(changed);

        option('statistics.span.month').click();
        await fixture.whenStable();

        expect(changed).not.toHaveBeenCalled();
        expect(option('statistics.span.month').getAttribute('aria-pressed')).toBe('true');
    });

    it('follows the active period when the parent changes it', async () => {
        await setSpan(SpanType.YEAR);

        expect(option('statistics.span.year').getAttribute('aria-pressed')).toBe('true');
        expect(option('statistics.span.month').getAttribute('aria-pressed')).toBe('false');
    });
});
