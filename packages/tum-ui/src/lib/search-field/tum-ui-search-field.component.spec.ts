import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { TumUiSearchFieldComponent } from './tum-ui-search-field.component';

describe('TumUiSearchFieldComponent', () => {
    let fixture: ComponentFixture<TumUiSearchFieldComponent>;
    let component: TumUiSearchFieldComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TumUiSearchFieldComponent, FontAwesomeTestingModule],
        }).compileComponents();
        fixture = TestBed.createComponent(TumUiSearchFieldComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    function input(): HTMLInputElement {
        return fixture.debugElement.query(By.css('input')).nativeElement;
    }

    function clearButton(): HTMLButtonElement | undefined {
        return fixture.debugElement.query(By.css('button'))?.nativeElement;
    }

    function type(term: string): void {
        input().value = term;
        input().dispatchEvent(new Event('input'));
        fixture.detectChanges();
    }

    it('emits every keystroke as the term', () => {
        const emitted: string[] = [];
        component.value.subscribe((value) => emitted.push(value));

        type('al');
        type('alp');

        expect(emitted).toEqual(['al', 'alp']);
    });

    it('reflects a term set from outside', () => {
        fixture.componentRef.setInput('value', 'beta');
        fixture.detectChanges();
        expect(input().value).toBe('beta');
    });

    it('offers the clear control only once there is a term', () => {
        expect(clearButton()).toBeUndefined();

        type('gamma');

        expect(clearButton()).toBeDefined();
    });

    it('clears the term and returns focus to the field', () => {
        type('delta');
        const emitted: string[] = [];
        component.value.subscribe((value) => emitted.push(value));

        clearButton()!.click();
        fixture.detectChanges();

        expect(emitted).toEqual(['']);
        expect(input().value).toBe('');
        expect(document.activeElement).toBe(input());
    });

    it('names the field by its placeholder unless given an explicit label', () => {
        expect(input().getAttribute('aria-label')).toBe('Search');

        // A real key rather than free text, so the assertion fails if the name stops going through tumUiTranslate.
        fixture.componentRef.setInput('ariaLabel', 'tumUi.select.filter');
        fixture.detectChanges();
        expect(input().getAttribute('aria-label')).toBe('Filter options');
    });

    it('disables both the field and the clear control', () => {
        type('epsilon');
        fixture.componentRef.setInput('disabled', true);
        fixture.detectChanges();

        expect(input().disabled).toBe(true);
        expect(clearButton()!.disabled).toBe(true);
    });
});
