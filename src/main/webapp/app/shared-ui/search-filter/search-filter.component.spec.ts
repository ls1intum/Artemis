import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { SearchFilterComponent } from 'app/shared-ui/search-filter/search-filter.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { provideArtemisTumUiTranslator } from 'app/shared-ui/tum-ui-integration/artemis-tum-ui-translator';

describe('SearchFilterComponent', () => {
    let component: SearchFilterComponent;
    let fixture: ComponentFixture<SearchFilterComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [SearchFilterComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, provideArtemisTumUiTranslator()],
        }).compileComponents();
        fixture = TestBed.createComponent(SearchFilterComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    function input(): HTMLInputElement {
        return fixture.debugElement.query(By.css('input')).nativeElement;
    }

    function type(term: string): void {
        input().value = term;
        input().dispatchEvent(new Event('input'));
        fixture.detectChanges();
    }

    it('should emit what the reader types into the rendered field', () => {
        const emitSpy = vi.spyOn(component.newSearchEvent, 'emit');

        type('hello');

        expect(component.searchValue()).toBe('hello');
        expect(emitSpy).toHaveBeenCalledExactlyOnceWith('hello');
    });

    it('should emit an empty term when the reader clears the field', () => {
        type('some text');
        const emitSpy = vi.spyOn(component.newSearchEvent, 'emit');

        fixture.debugElement.query(By.css('button')).nativeElement.click();
        fixture.detectChanges();

        expect(component.searchValue()).toBe('');
        expect(input().value).toBe('');
        expect(emitSpy).toHaveBeenCalledExactlyOnceWith('');
    });

    it('should set searchValue signal and emit on setSearchValue', () => {
        const emitSpy = vi.spyOn(component.newSearchEvent, 'emit');

        component.setSearchValue('hello');

        expect(component.searchValue()).toBe('hello');
        expect(emitSpy).toHaveBeenCalledExactlyOnceWith('hello');
    });

    it('should reset searchValue signal and emit empty string on resetSearchValue', () => {
        component.setSearchValue('some text');
        const emitSpy = vi.spyOn(component.newSearchEvent, 'emit');

        component.resetSearchValue();

        expect(component.searchValue()).toBe('');
        expect(emitSpy).toHaveBeenCalledExactlyOnceWith('');
    });

    it('should name the field for assistive technology rather than reusing the placeholder', () => {
        // Naming a control after its placeholder makes the name change with the copy, and an E2E locator that
        // relied on the old, purpose-written name broke exactly that way.
        expect(component.ariaLabelKey()).toBe('artemisApp.course.exercise.search.searchLabel');
        expect(input().getAttribute('aria-label')).toBe('artemisApp.course.exercise.search.searchLabel');
        expect(input().getAttribute('aria-label')).not.toBe(input().getAttribute('placeholder'));

        fixture.componentRef.setInput('ariaLabelKey', 'artemisApp.exerciseManagement.searchLabel');
        fixture.detectChanges();
        expect(input().getAttribute('aria-label')).toBe('artemisApp.exerciseManagement.searchLabel');
    });

    it('should carry a stable test hook for end-to-end locators', () => {
        expect(fixture.debugElement.query(By.css('[data-testid="search-filter"]'))).not.toBeNull();
    });

    it('should pass the placeholder key through to the field', () => {
        expect(component.placeholderKey()).toBe('artemisApp.course.exercise.search.searchPlaceholder');
        expect(input().getAttribute('placeholder')).toBe('artemisApp.course.exercise.search.searchPlaceholder');

        fixture.componentRef.setInput('placeholderKey', 'artemisApp.exerciseManagement.search');
        fixture.detectChanges();
        expect(input().getAttribute('placeholder')).toBe('artemisApp.exerciseManagement.search');
    });

    it('should disable the field', () => {
        fixture.componentRef.setInput('disabled', true);
        fixture.detectChanges();

        expect(input().disabled).toBe(true);
    });
});
