import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SearchInputComponent } from './search-input.component';
import { MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ChipModule } from 'primeng/chip';

describe('SearchInputComponent', () => {
    setupTestBed({ zoneless: true });

    let component: SearchInputComponent;
    let fixture: ComponentFixture<SearchInputComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [SearchInputComponent, ChipModule, MockPipe(ArtemisTranslatePipe)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(SearchInputComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('searchQuery', '');
        fixture.componentRef.setInput('activeFilters', []);
        fixture.componentRef.setInput('isLoading', false);
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should compute hasActiveFilters correctly', () => {
        expect(component['hasActiveFilters']()).toBe(false);
        fixture.componentRef.setInput('activeFilters', ['filter1']);
        fixture.detectChanges();
        expect(component['hasActiveFilters']()).toBe(true);
    });

    it('should focus input', () => {
        vi.useFakeTimers();
        const inputElement = document.createElement('input');
        vi.spyOn(component['searchInputElement']()!, 'nativeElement', 'get').mockReturnValue(inputElement);
        const spy = vi.spyOn(inputElement, 'focus');

        component.focusInput();
        vi.runAllTimers();

        expect(spy).toHaveBeenCalled();
        vi.useRealTimers();
    });

    it('should emit searchInput on input', () => {
        const spy = vi.spyOn(component.searchInput, 'emit');
        const event = { target: { value: 'test' } } as any as Event;
        component['onInput'](event);
        expect(spy).toHaveBeenCalledWith('test');
    });

    it('should emit searchKeyDown on keydown', () => {
        const spy = vi.spyOn(component.searchKeyDown, 'emit');
        const event = new KeyboardEvent('keydown');
        component['onKeyDown'](event);
        expect(spy).toHaveBeenCalledWith(event);
    });

    it('should emit filterRemoved on filter remove', () => {
        const spy = vi.spyOn(component.filterRemoved, 'emit');
        component['onFilterRemove']('filter1');
        expect(spy).toHaveBeenCalledWith('filter1');
    });

    it('should compute hasActiveFilters true when courseFilterLabel is set', () => {
        fixture.componentRef.setInput('activeFilters', []);
        fixture.componentRef.setInput('courseFilterLabel', 'Intro to CS');
        fixture.detectChanges();
        expect(component['hasActiveFilters']()).toBe(true);
    });

    it('should emit courseFilterRemoved on course filter remove', () => {
        const spy = vi.spyOn(component.courseFilterRemoved, 'emit');
        component['onCourseFilterRemove']();
        expect(spy).toHaveBeenCalled();
    });
});
