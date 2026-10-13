import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { Subject, of, throwError } from 'rxjs';
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { LLMSelectionDecision } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';
import { AccountService } from 'app/core/auth/account.service';
import { SearchResultItemComponent } from 'app/core/navbar/global-search/components/modal/search-result-item/search-result-item.component';
import { GlobalSearchIrisAnswerComponent } from 'app/core/navbar/global-search/components/views/iris-answer/global-search-iris-answer.component';
import { GlobalSearchNavigationViewComponent } from 'app/core/navbar/global-search/components/views/navigation-view/global-search-navigation-view.component';
import { IrisSearchAnswerService } from 'app/core/navbar/global-search/services/iris-search-answer.service';
import { OsDetectorService } from 'app/core/navbar/global-search/services/os-detector.service';
import { SearchOverlayService } from 'app/core/navbar/global-search/services/search-overlay.service';
import { IrisSearchStatusUpdate } from 'app/core/navbar/global-search/models/iris-search-status-update.model';
import { GlobalSearchApi } from 'app/openapi/api/global-search-api';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { SEARCH_DEBOUNCE_MS } from '../views/search-result-view.directive';
import { GlobalSearchModalComponent } from './global-search-modal.component';

const originalScrollIntoView = HTMLElement.prototype.scrollIntoView;
const originalStyleDescriptor = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'style')!;

describe('GlobalSearchModalComponent keyboard integration', () => {
    let fixture: ComponentFixture<GlobalSearchModalComponent>;
    let component: GlobalSearchModalComponent;
    let ask: ReturnType<typeof vi.fn>;
    let askSubject: Subject<IrisSearchStatusUpdate>;
    const overlay = {
        isOpen: signal(true),
        open: vi.fn(),
        close: vi.fn(),
        toggle: vi.fn(),
    };

    beforeAll(() => {
        HTMLElement.prototype.scrollIntoView = vi.fn();
        Object.defineProperty(HTMLElement.prototype, 'style', {
            get() {
                const style = originalStyleDescriptor.get!.call(this) as CSSStyleDeclaration;
                return new Proxy(style, {
                    set(target, prop, value) {
                        if (typeof prop === 'string' && prop.startsWith('--')) {
                            target.setProperty(prop, String(value));
                        } else if (!(typeof prop === 'string' && /^\d+$/.test(prop))) {
                            (target as unknown as Record<string, unknown>)[prop as string] = value;
                        }
                        return true;
                    },
                });
            },
            configurable: true,
        });
    });

    afterAll(() => {
        HTMLElement.prototype.scrollIntoView = originalScrollIntoView;
        Object.defineProperty(HTMLElement.prototype, 'style', originalStyleDescriptor);
    });

    beforeEach(() => {
        vi.useFakeTimers();
        overlay.isOpen.set(true);
        askSubject = new Subject<IrisSearchStatusUpdate>();
        ask = vi.fn().mockReturnValue(askSubject.asObservable());

        TestBed.configureTestingModule({
            imports: [GlobalSearchModalComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                provideRouter([]),
                { provide: TranslateService, useClass: MockTranslateService },
                {
                    provide: AccountService,
                    useValue: {
                        userIdentity: signal({ selectedLLMUsage: LLMSelectionDecision.CLOUD_AI }),
                        isAuthenticated: vi.fn(() => true),
                        getAuthenticationState: vi.fn(() => of(undefined)),
                    },
                },
                { provide: SearchOverlayService, useValue: overlay },
                { provide: OsDetectorService, useValue: { isActionKey: vi.fn(() => false), isMac: vi.fn(() => false) } },
                { provide: GlobalSearchApi, useValue: { globalSearch: vi.fn(() => of([])) } },
                { provide: ProfileService, useValue: { isModuleFeatureActive: vi.fn(() => true) } },
                { provide: CourseStorageService, useValue: { getCourse: vi.fn(() => ({ id: 7, title: 'Algorithms' })), getCourses: vi.fn(() => []) } },
                { provide: IrisSearchAnswerService, useValue: { ask } },
            ],
        });
        TestBed.overrideComponent(GlobalSearchNavigationViewComponent, {
            remove: { imports: [SearchResultItemComponent, ArtemisTranslatePipe] },
            add: { imports: [MockComponent(SearchResultItemComponent), MockPipe(ArtemisTranslatePipe)] },
        });
        TestBed.overrideComponent(GlobalSearchIrisAnswerComponent, {
            remove: { imports: [MarkdownDirective, ArtemisTranslatePipe] },
            add: { imports: [MockDirective(MarkdownDirective), MockPipe(ArtemisTranslatePipe)] },
        });

        fixture = TestBed.createComponent(GlobalSearchModalComponent);
        component = fixture.componentInstance;
        component['tokens'].set([{ facet: 'course', value: '7' }]);
        component['filterPickerOpen'].set(false);
        component['onSearchInput']('A detailed Iris question');
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.useRealTimers();
        overlay.isOpen.set(false);
    });

    function dispatchEnter(target: EventTarget): KeyboardEvent {
        const event = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true });
        target.dispatchEvent(event);
        return event;
    }

    function showFailedIrisAnswer(): HTMLButtonElement {
        ask.mockReturnValueOnce(throwError(() => new Error('Iris unavailable'))).mockReturnValueOnce(askSubject.asObservable());
        vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS + 300);
        fixture.detectChanges();
        expect(ask).toHaveBeenCalledOnce();
        return fixture.nativeElement.querySelector('[data-testid="iris-answer-retry"]') as HTMLButtonElement;
    }

    it('keeps a selected course chip while native Iris retry handles Enter', () => {
        const retry = showFailedIrisAnswer();
        expect(retry).toBeTruthy();

        window.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true, cancelable: true }));
        expect(component['selectedChip']()).toBe(0);
        window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true }));
        expect(component['selectedChip']()).toBe(0);

        retry.focus();
        const event = dispatchEnter(retry);
        retry.click(); // jsdom does not synthesize a click for an uncancelled Enter keydown.
        fixture.detectChanges();
        vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS + 300);
        fixture.detectChanges();

        expect(event.defaultPrevented).toBe(false);
        expect(component['filterPickerOpen']()).toBe(false);
        expect(component['selectedChip']()).toBe(0);
        expect(ask).toHaveBeenCalledTimes(2);
    });

    it('does not re-pick a chip for an already prevented Enter but keeps regular chip Enter', () => {
        window.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true, cancelable: true }));
        const prevented = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true });
        prevented.preventDefault();
        window.dispatchEvent(prevented);

        expect(component['filterPickerOpen']()).toBe(false);
        expect(component['selectedChip']()).toBe(0);

        const regularEnter = dispatchEnter(document.body);

        expect(regularEnter.defaultPrevented).toBe(true);
        expect(component['editingChip']()).toBe(0);
        expect(component['selectedChip']()).toBe(-1);
    });
});
