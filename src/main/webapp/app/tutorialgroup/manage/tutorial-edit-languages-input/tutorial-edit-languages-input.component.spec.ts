import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { OverlayContainer } from '@angular/cdk/overlay';
import { TranslateService } from '@ngx-translate/core';
import { TutorialEditLanguagesInputComponent } from './tutorial-edit-languages-input.component';
import { ValidationStatus } from 'app/shared/util/validation';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

function assertNonNullable<T>(value: T): asserts value is NonNullable<T> {
    expect(value).not.toBeNull();
    expect(value).not.toBeUndefined();
}

describe('TutorialEditLanguagesInputComponent', () => {
    setupTestBed({ zoneless: true });

    let component: TutorialEditLanguagesInputComponent;
    let fixture: ComponentFixture<TutorialEditLanguagesInputComponent>;
    let overlayContainer: OverlayContainer;
    let originalScrollIntoViewDescriptor: PropertyDescriptor | undefined;

    beforeEach(async () => {
        originalScrollIntoViewDescriptor = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'scrollIntoView');
        Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', {
            configurable: true,
            value: vi.fn(),
        });

        await TestBed.configureTestingModule({
            imports: [TutorialEditLanguagesInputComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        overlayContainer = TestBed.inject(OverlayContainer);
        fixture = TestBed.createComponent(TutorialEditLanguagesInputComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('alreadyUsedLanguages', ['English', 'German', 'Spanish']);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
        if (originalScrollIntoViewDescriptor) {
            Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', originalScrollIntoViewDescriptor);
        } else {
            Reflect.deleteProperty(HTMLElement.prototype, 'scrollIntoView');
        }
        overlayContainer.getContainerElement().innerHTML = '';
    });

    it('should open the suggestion panel on focus and close it on blur', async () => {
        const input = fixture.nativeElement.querySelector('input') as HTMLInputElement | null;
        assertNonNullable(input);
        input.dispatchEvent(new FocusEvent('focus'));
        fixture.detectChanges();
        await fixture.whenStable();

        const rows = Array.from(overlayContainer.getContainerElement().querySelectorAll('.language-row')) as HTMLButtonElement[];
        expect(overlayContainer.getContainerElement().querySelector('.suggestion-panel')).not.toBeNull();
        expect(rows.map((row) => row.textContent?.trim())).toEqual(['English', 'German', 'Spanish']);

        input.dispatchEvent(new FocusEvent('blur'));
        fixture.detectChanges();
        await fixture.whenStable();

        expect(overlayContainer.getContainerElement().querySelector('.suggestion-panel')).toBeNull();
    });

    it('should expose error validation after blur with an empty language', async () => {
        const input = fixture.nativeElement.querySelector('input') as HTMLInputElement | null;
        assertNonNullable(input);

        input.dispatchEvent(new FocusEvent('focus'));
        fixture.detectChanges();
        await fixture.whenStable();

        component.language.set('   ');
        input.dispatchEvent(new FocusEvent('blur'));
        fixture.detectChanges();
        await fixture.whenStable();

        expect(overlayContainer.getContainerElement().querySelector('.suggestion-panel')).toBeNull();
        expect(component.languageInputTouched()).toBe(true);
        expect(component.languageValidationResult()).toEqual({
            status: ValidationStatus.INVALID,
            message: 'artemisApp.pages.createOrEditTutorialGroup.validationError.languageRequired',
        });
    });

    it('should expose error validation after blur with too long input', async () => {
        const input = fixture.nativeElement.querySelector('input') as HTMLInputElement | null;
        assertNonNullable(input);

        input.dispatchEvent(new FocusEvent('focus'));
        fixture.detectChanges();
        await fixture.whenStable();

        component.language.set('a'.repeat(256));
        input.dispatchEvent(new FocusEvent('blur'));
        fixture.detectChanges();
        await fixture.whenStable();

        expect(overlayContainer.getContainerElement().querySelector('.suggestion-panel')).toBeNull();
        expect(component.languageInputTouched()).toBe(true);
        expect(component.languageValidationResult()).toEqual({
            status: ValidationStatus.INVALID,
            message: 'artemisApp.pages.createOrEditTutorialGroup.validationError.languageLength',
        });
    });

    it('should expose ok validation after blur with valid string', async () => {
        const input = fixture.nativeElement.querySelector('input') as HTMLInputElement | null;
        assertNonNullable(input);

        input.dispatchEvent(new FocusEvent('focus'));
        fixture.detectChanges();
        await fixture.whenStable();

        component.language.set(' English ');
        input.dispatchEvent(new FocusEvent('blur'));
        fixture.detectChanges();
        await fixture.whenStable();

        expect(overlayContainer.getContainerElement().querySelector('.suggestion-panel')).toBeNull();
        expect(component.languageInputTouched()).toBe(true);
        expect(component.languageValidationResult()).toEqual({ status: ValidationStatus.VALID });
    });

    it('should highlight suggestions with arrow keys and select the highlighted language on enter', async () => {
        const input = fixture.nativeElement.querySelector('input') as HTMLInputElement | null;
        assertNonNullable(input);

        input.dispatchEvent(new FocusEvent('focus'));
        fixture.detectChanges();
        await fixture.whenStable();

        input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
        fixture.detectChanges();
        const firstActiveRow = overlayContainer.getContainerElement().querySelector('.language-row.active') as HTMLButtonElement | null;
        expect(firstActiveRow?.textContent?.trim()).toBe('English');

        input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
        fixture.detectChanges();
        const secondActiveRow = overlayContainer.getContainerElement().querySelector('.language-row.active') as HTMLButtonElement | null;
        expect(secondActiveRow?.textContent?.trim()).toBe('German');

        input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowUp' }));
        fixture.detectChanges();
        const thirdActiveRow = overlayContainer.getContainerElement().querySelector('.language-row.active') as HTMLButtonElement | null;
        expect(thirdActiveRow?.textContent?.trim()).toBe('English');

        input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
        fixture.detectChanges();
        const fourthActiveRow = overlayContainer.getContainerElement().querySelector('.language-row.active') as HTMLButtonElement | null;
        expect(fourthActiveRow?.textContent?.trim()).toBe('German');

        input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.language()).toBe('German');
        expect(component.suggestionHighlightIndex()).toBeUndefined();
        expect(overlayContainer.getContainerElement().querySelector('.suggestion-panel')).toBeNull();
    });

    it('should select a suggestion on mouse down', async () => {
        const input = fixture.nativeElement.querySelector('input') as HTMLInputElement | null;
        assertNonNullable(input);
        input.dispatchEvent(new FocusEvent('focus'));
        fixture.detectChanges();
        await fixture.whenStable();

        const rows = Array.from(overlayContainer.getContainerElement().querySelectorAll('.language-row')) as HTMLButtonElement[];
        rows[1].dispatchEvent(new MouseEvent('mousedown'));
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.language()).toBe('German');
        expect(overlayContainer.getContainerElement().querySelector('.suggestion-panel')).toBeNull();
    });

    it('should highlight the first suggestion when pressing arrow down without a prior selection', async () => {
        const input = fixture.nativeElement.querySelector('input') as HTMLInputElement | null;
        assertNonNullable(input);
        input.dispatchEvent(new FocusEvent('focus'));
        fixture.detectChanges();
        await fixture.whenStable();

        input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
        fixture.detectChanges();

        const activeRow = overlayContainer.getContainerElement().querySelector('.language-row.active') as HTMLButtonElement | null;
        expect(activeRow?.textContent?.trim()).toBe('English');
    });

    it('should highlight the last suggestion when pressing arrow up without a prior selection', async () => {
        const input = fixture.nativeElement.querySelector('input') as HTMLInputElement | null;
        assertNonNullable(input);
        input.dispatchEvent(new FocusEvent('focus'));
        fixture.detectChanges();
        await fixture.whenStable();

        input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowUp' }));
        fixture.detectChanges();

        const activeRow = overlayContainer.getContainerElement().querySelector('.language-row.active') as HTMLButtonElement | null;
        expect(activeRow?.textContent?.trim()).toBe('Spanish');
    });

    it('should wrap around when navigating beyond the first or last suggestion', async () => {
        const input = fixture.nativeElement.querySelector('input') as HTMLInputElement | null;
        assertNonNullable(input);
        input.dispatchEvent(new FocusEvent('focus'));
        fixture.detectChanges();
        await fixture.whenStable();

        input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowUp' }));
        fixture.detectChanges();
        let activeRow = overlayContainer.getContainerElement().querySelector('.language-row.active') as HTMLButtonElement | null;
        expect(activeRow?.textContent?.trim()).toBe('Spanish');

        input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
        fixture.detectChanges();
        activeRow = overlayContainer.getContainerElement().querySelector('.language-row.active') as HTMLButtonElement | null;
        expect(activeRow?.textContent?.trim()).toBe('English');
    });
});
