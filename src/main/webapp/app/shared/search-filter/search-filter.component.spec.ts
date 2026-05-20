import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { vi } from 'vitest';
import { SearchFilterComponent } from 'app/shared/search-filter/search-filter.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('SearchFilterComponent', () => {
    setupTestBed({ zoneless: true });

    let component: SearchFilterComponent;
    let fixture: ComponentFixture<SearchFilterComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [SearchFilterComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(SearchFilterComponent);
                component = fixture.componentInstance;
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should set searchValue signal and emit on setSearchValue', () => {
        const emitSpy = vi.spyOn(component.newSearchEvent, 'emit');

        component.setSearchValue('hello');

        expect(component.searchValue()).toBe('hello');
        expect(emitSpy).toHaveBeenCalledOnce();
        expect(emitSpy).toHaveBeenCalledWith('hello');
    });

    it('should reset searchValue signal and emit empty string on resetSearchValue', () => {
        component.setSearchValue('some text');
        const emitSpy = vi.spyOn(component.newSearchEvent, 'emit');

        component.resetSearchValue();

        expect(component.searchValue()).toBe('');
        expect(emitSpy).toHaveBeenCalledOnce();
        expect(emitSpy).toHaveBeenCalledWith('');
    });

    it('should use default placeholder key', () => {
        expect(component.placeholderKey()).toBe('artemisApp.course.exercise.search.searchPlaceholder');
    });
});
