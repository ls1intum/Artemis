import { inputBinding, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { of } from 'rxjs';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('TranslateDirective', () => {
    let translateService: TranslateService;
    let spy: ReturnType<typeof vi.spyOn>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        });

        translateService = TestBed.inject(TranslateService);
        spy = vi.spyOn(translateService, 'get');
    });

    // Create the fixture inside each test: Angular flushes all pending effects application-wide on a single
    // detectChanges(), so a fixture created in beforeEach would leak its translation effect into the other tests.
    function createWithKey(key: () => string | undefined) {
        return TestBed.createDirective(TranslateDirective, { tagName: 'div', bindings: [inputBinding('jhiTranslate', key)] });
    }

    it('should change HTML', () => {
        const fixture = createWithKey(() => 'test');
        fixture.detectChanges();

        expect(spy).toHaveBeenCalledWith('test', undefined);
    });

    it.each([undefined, ''])('should not call translateService.get for an empty key (%p) and should clear the element', (key) => {
        const dynamicFixture = createWithKey(() => key);
        const element = dynamicFixture.nativeElement;
        element.textContent = 'stale';

        // ngx-translate's get() throws synchronously on an empty key; the guard must prevent the call entirely
        expect(() => dynamicFixture.detectChanges()).not.toThrow();
        expect(spy).not.toHaveBeenCalled();
        expect(element.textContent).toBe('');
    });

    it('should translate once a previously empty key becomes non-empty', () => {
        const key = signal<string | undefined>(undefined);
        const dynamicFixture = createWithKey(key);
        dynamicFixture.detectChanges();
        expect(spy).not.toHaveBeenCalled();

        key.set('test');
        dynamicFixture.detectChanges();
        expect(spy).toHaveBeenCalledWith('test', undefined);
    });

    it('sanitizes the translated value before assigning to innerHTML (XSS via translateValues)', () => {
        // Simulate a translation whose interpolated translateValues carry user-controlled markup (e.g. a course
        // title). ngx-translate does not HTML-escape interpolation params, so the directive must sanitize.
        spy.mockReturnValue(of('Course <img src="x" onerror="alert(1)"><script>alert(2)</script> title') as ReturnType<typeof translateService.get>);

        const fixture = createWithKey(() => 'test');
        fixture.detectChanges();
        const element = fixture.nativeElement;

        expect(element.innerHTML).not.toContain('onerror');
        expect(element.innerHTML).not.toContain('<script');
        expect(element.innerHTML).not.toContain('alert(2)');
        // benign text around the stripped payload is preserved
        expect(element.textContent).toContain('Course');
    });

    it('preserves benign inline markup in the translated value', () => {
        spy.mockReturnValue(of('Click <a href="/x"><strong>here</strong></a>') as ReturnType<typeof translateService.get>);

        const fixture = createWithKey(() => 'test');
        fixture.detectChanges();
        const element = fixture.nativeElement;

        expect(element.innerHTML).toContain('<strong>here</strong>');
        expect(element.innerHTML).toContain('href="/x"');
    });
});
