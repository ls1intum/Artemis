import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

@Component({
    template: '<div jhiTranslate="test"></div>',
    imports: [TranslateDirective],
})
class TestTranslateDirectiveComponent {}

@Component({
    template: '<div [jhiTranslate]="key()"></div>',
    imports: [TranslateDirective],
})
class TestDynamicKeyTranslateDirectiveComponent {
    key = signal<string | undefined>(undefined);
}

describe('TranslateDirective', () => {
    setupTestBed({ zoneless: true });

    let translateService: TranslateService;
    let spy: ReturnType<typeof vi.spyOn>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TestTranslateDirectiveComponent, TestDynamicKeyTranslateDirectiveComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        translateService = TestBed.inject(TranslateService);
        spy = vi.spyOn(translateService, 'get');
    });

    it('should change HTML', () => {
        // Create the fixture inside the test: Angular flushes all pending effects application-wide on a single
        // detectChanges(), so a fixture created in beforeEach would leak its translation effect into the other tests.
        const fixture = TestBed.createComponent(TestTranslateDirectiveComponent);
        fixture.detectChanges();

        expect(spy).toHaveBeenCalledWith('test', undefined);
    });

    it.each([undefined, ''])('should not call translateService.get for an empty key (%p) and should clear the element', (key) => {
        const dynamicFixture = TestBed.createComponent(TestDynamicKeyTranslateDirectiveComponent);
        dynamicFixture.componentInstance.key.set(key);
        const element: HTMLElement = dynamicFixture.nativeElement.querySelector('div');
        element.textContent = 'stale';

        // ngx-translate's get() throws synchronously on an empty key; the guard must prevent the call entirely
        expect(() => dynamicFixture.detectChanges()).not.toThrow();
        expect(spy).not.toHaveBeenCalled();
        expect(element.textContent).toBe('');
    });

    it('should translate once a previously empty key becomes non-empty', () => {
        const dynamicFixture = TestBed.createComponent(TestDynamicKeyTranslateDirectiveComponent);
        dynamicFixture.componentInstance.key.set(undefined);
        dynamicFixture.detectChanges();
        expect(spy).not.toHaveBeenCalled();

        dynamicFixture.componentInstance.key.set('test');
        dynamicFixture.detectChanges();
        expect(spy).toHaveBeenCalledWith('test', undefined);
    });
});
