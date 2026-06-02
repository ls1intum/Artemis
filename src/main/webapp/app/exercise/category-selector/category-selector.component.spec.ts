import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { CategorySelectorComponent } from 'app/exercise/category-selector/category-selector.component';
import { MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { MatChipInput, MatChipInputEvent } from '@angular/material/chips';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('CategorySelectorComponent', () => {
    setupTestBed({ zoneless: true });
    let comp: CategorySelectorComponent;
    let fixture: ComponentFixture<CategorySelectorComponent>;
    let emitSpy: ReturnType<typeof vi.spyOn>;

    const category1 = {
        color: '#6ae8ac',
        category: 'category1',
    } as ExerciseCategory;
    const category2 = {
        color: '#9dca53',
        category: 'category2',
    } as ExerciseCategory;
    const category3 = {
        color: '#94a11c',
        category: 'category3',
    } as ExerciseCategory;
    const category4 = {
        color: '#691b0b',
        category: 'category4',
    } as ExerciseCategory;
    const category5 = {
        color: '#ad5658',
        category: 'category5',
    } as ExerciseCategory;
    const category6 = {
        color: '#1b97ca',
        category: 'category6',
    } as ExerciseCategory;
    const category7 = {
        color: '#0d3cc2',
        category: 'category7',
    } as ExerciseCategory;
    const category8 = {
        color: '#0ab84f',
        category: 'category8',
    } as ExerciseCategory;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CategorySelectorComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(CategorySelectorComponent);
        comp = fixture.componentInstance;

        emitSpy = vi.spyOn(comp.selectedCategories, 'emit');
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should remove category', () => {
        fixture.detectChanges();
        fixture.componentRef.setInput('categories', [category1, category2, category3]);
        fixture.changeDetectorRef.detectChanges();
        const cancelColorSelectorSpy = vi.spyOn(comp.colorSelector(), 'cancelColorSelector');
        comp.onItemRemove(category2);

        expect(comp.selectedCategoryItems()).toEqual([category1, category3]);
        expect(cancelColorSelectorSpy).toHaveBeenCalledOnce();
        expect(emitSpy).toHaveBeenCalledWith([category1, category3]);
    });

    it('should open color selector', () => {
        fixture.detectChanges();
        const mouseEvent = new MouseEvent('click', {
            clientX: 1,
            clientY: 2,
        });

        const openColorSelectorSpy = vi.spyOn(comp.colorSelector(), 'openColorSelector').mockImplementation(() => undefined);
        comp.openColorSelector(mouseEvent, category5);

        expect(comp.selectedCategory).toEqual(category5);
        expect(openColorSelectorSpy).toHaveBeenCalledWith(mouseEvent, undefined, 150);
    });

    it('should select color for category', () => {
        fixture.componentRef.setInput('categories', [category1, category2, category3]);
        comp.selectedCategory = category2;
        comp.onSelectedColor('#fff');
        const expected = { category: 'category2', color: '#fff' };

        expect(comp.selectedCategory).toEqual(expected);
        expect(comp.selectedCategoryItems()).toEqual([category1, expected, category3]);
        expect(emitSpy).toHaveBeenCalledWith([category1, expected, category3]);
    });

    it('should create new item on select', () => {
        fixture.componentRef.setInput('categories', [category6]);
        fixture.componentRef.setInput('existingCategories', [category6, category7, category8]);
        fixture.changeDetectorRef.detectChanges();
        const event = { option: { value: 'category9' } } as MatAutocompleteSelectedEvent;
        comp.onItemSelect(event);

        const categoryColor = comp.selectedCategoryItems()[1].color;
        expect(comp.selectedCategoryItems()).toEqual([category6, { category: 'category9', color: categoryColor }]);
        expect(emitSpy).toHaveBeenCalledWith([category6, { category: 'category9', color: categoryColor }]);
        expect(comp.categoryInput().nativeElement.value).toBe('');
        expect(comp.categoryCtrl.value).toBeNull();
    });

    it('should not create new item on select', () => {
        fixture.componentRef.setInput('categories', [category6]);
        fixture.componentRef.setInput('existingCategories', [category7, category8]);
        fixture.changeDetectorRef.detectChanges();
        const event = { option: { value: 'category7' } } as MatAutocompleteSelectedEvent;
        comp.onItemSelect(event);

        expect(comp.selectedCategoryItems()).toEqual([category6, category7]);
        expect(emitSpy).toHaveBeenCalledWith([category6, category7]);
        expect(comp.categoryInput().nativeElement.value).toBe('');
        expect(comp.categoryCtrl.value).toBeNull();
    });

    it('should not create duplicate item on add', () => {
        fixture.componentRef.setInput('categories', [category6]);
        fixture.changeDetectorRef.detectChanges();
        const event = { value: 'category6', chipInput: { clear: () => {} } as MatChipInput } as MatChipInputEvent;
        comp.onItemAdd(event);

        expect(comp.selectedCategoryItems()).toEqual([category6]);
        expect(emitSpy).not.toHaveBeenCalled();
        expect(comp.categoryCtrl.value).toBeNull();
    });

    it('should save exiting category on add', () => {
        fixture.componentRef.setInput('categories', [category6]);
        fixture.componentRef.setInput('existingCategories', [category7, category8]);
        fixture.changeDetectorRef.detectChanges();
        const event = { value: 'category8', chipInput: { clear: () => {} } as MatChipInput } as MatChipInputEvent;
        comp.onItemAdd(event);

        expect(comp.selectedCategoryItems()).toEqual([category6, category8]);
        expect(emitSpy).toHaveBeenCalledWith([category6, category8]);
        expect(comp.categoryCtrl.value).toBeNull();
    });

    it('should create new item on add for existing categories', () => {
        fixture.componentRef.setInput('categories', [category6]);
        fixture.componentRef.setInput('existingCategories', []);
        fixture.changeDetectorRef.detectChanges();
        const event = { value: 'category9', chipInput: { clear: () => {} } as MatChipInput } as MatChipInputEvent;
        comp.onItemAdd(event);

        const categoryColor = comp.selectedCategoryItems()[1].color;
        expect(comp.selectedCategoryItems()).toEqual([category6, { category: 'category9', color: categoryColor }]);
        expect(emitSpy).toHaveBeenCalledWith([category6, { category: 'category9', color: categoryColor }]);
        expect(comp.categoryCtrl.value).toBeNull();
    });

    it('should create new item on add for empty categories', () => {
        fixture.componentRef.setInput('existingCategories', []);
        fixture.changeDetectorRef.detectChanges();
        const event = { value: 'category6', chipInput: { clear: () => {} } as MatChipInput } as MatChipInputEvent;
        comp.onItemAdd(event);

        const categoryColor = comp.selectedCategoryItems()[0].color;
        expect(comp.selectedCategoryItems()).toEqual([{ category: 'category6', color: categoryColor }]);
        expect(emitSpy).toHaveBeenCalledWith([{ category: 'category6', color: categoryColor }]);
        expect(comp.categoryCtrl.value).toBeNull();
    });

    it('should set categories for autocomplete on changes', () => {
        fixture.componentRef.setInput('existingCategories', [category3, category4, category5]);
        fixture.componentRef.setInput('categories', [category3]);
        fixture.detectChanges();

        let result;
        comp.uniqueCategoriesForAutocomplete.subscribe((value) => (result = value));
        expect(result).toEqual(['category4', 'category5']);
    });

    it('should refresh autocomplete options when the inputs change after the initial render', () => {
        fixture.componentRef.setInput('existingCategories', [category3, category4, category5]);
        fixture.componentRef.setInput('categories', [category3]);
        fixture.detectChanges();

        let result;
        comp.uniqueCategoriesForAutocomplete.subscribe((value) => (result = value));
        expect(result).toEqual(['category4', 'category5']);

        fixture.componentRef.setInput('categories', [category3, category4]);
        fixture.detectChanges();
        expect(result).toEqual(['category5']);
    });

    it('should filter categories for autocomplete on changes', () => {
        fixture.componentRef.setInput('existingCategories', [category3, category4, category5]);
        fixture.componentRef.setInput('categories', [category3]);
        fixture.detectChanges();

        let result;
        comp.uniqueCategoriesForAutocomplete.subscribe((value) => (result = value));
        comp.categoryCtrl.setValue('category4');
        comp.categoryCtrl.updateValueAndValidity();
        expect(result).toEqual(['category4']);
    });
});
