import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

const TRANSLATIONS: Record<string, Record<string, string>> = {
    en: { greeting: 'Hello' },
    de: { greeting: 'Hallo' },
};

@Component({
    selector: 'jhi-translate-host',
    template: `<span>{{ 'greeting' | artemisTranslate }}</span>`,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [ArtemisTranslatePipe],
})
class TranslateHostComponent {}

describe('ArtemisTranslatePipe', () => {
    setupTestBed({ zoneless: true });

    let translateService: MockTranslateService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TranslateHostComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        translateService = TestBed.inject(TranslateService) as unknown as MockTranslateService;
        vi.spyOn(translateService, 'instant').mockImplementation((key) => TRANSLATIONS[translateService.getCurrentLang()]?.[key as string] ?? (key as string));
        translateService.use('en');
    });

    it('returns an empty string for an empty or missing query', () => {
        const pipe = TestBed.runInInjectionContext(() => new ArtemisTranslatePipe());
        expect(pipe.transform('')).toBe('');
        expect(pipe.transform(undefined)).toBe('');
        expect(pipe.transform(null)).toBe('');
    });

    it('renders the translation for the active language', async () => {
        const fixture = TestBed.createComponent(TranslateHostComponent);
        fixture.autoDetectChanges();
        await fixture.whenStable();

        expect(fixture.nativeElement.querySelector('span').textContent).toBe('Hello');
    });

    it('re-renders the OnPush host when the language changes, without manual change detection', async () => {
        const fixture = TestBed.createComponent(TranslateHostComponent);
        fixture.autoDetectChanges();
        await fixture.whenStable();
        expect(fixture.nativeElement.querySelector('span').textContent).toBe('Hello');

        // Switching language emits onLangChange; the signal read in transform() must schedule a refresh on its own.
        translateService.use('de');
        await fixture.whenStable();

        expect(fixture.nativeElement.querySelector('span').textContent).toBe('Hallo');
    });
});
