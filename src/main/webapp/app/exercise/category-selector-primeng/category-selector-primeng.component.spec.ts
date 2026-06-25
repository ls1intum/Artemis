import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { CategorySelectorPrimengComponent } from 'app/exercise/category-selector-primeng/category-selector-primeng.component';
import { AutoCompleteSelectEvent, AutoCompleteUnselectEvent } from 'primeng/autocomplete';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('CategorySelectorPrimengComponent', () => {
    setupTestBed({ zoneless: true });
    let comp: CategorySelectorPrimengComponent;
    let fixture: ComponentFixture<CategorySelectorPrimengComponent>;
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

    /** Builds an AutoComplete select event carrying the picked suggestion label. */
    const selectEvent = (value: string) => ({ value }) as AutoCompleteSelectEvent;

    /** Builds a keydown event with the typed free-text value, as the input element would emit on Enter. */
    const enterEvent = (value: string) => ({ target: { value } }) as unknown as KeyboardEvent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CategorySelectorPrimengComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(CategorySelectorPrimengComponent);
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

    it('should remove category via the chip remove (onUnselect)', () => {
        fixture.detectChanges();
        fixture.componentRef.setInput('categories', [category1, category2, category3]);
        fixture.changeDetectorRef.detectChanges();
        const cancelColorSelectorSpy = vi.spyOn(comp.colorSelector(), 'cancelColorSelector');
        comp.onItemUnselect({ value: 'category2' } as AutoCompleteUnselectEvent);

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

    it('should open color selector for a category label', () => {
        fixture.detectChanges();
        fixture.componentRef.setInput('categories', [category1, category2]);
        fixture.changeDetectorRef.detectChanges();
        const mouseEvent = new MouseEvent('click');

        const openColorSelectorSpy = vi.spyOn(comp.colorSelector(), 'openColorSelector').mockImplementation(() => undefined);
        comp.openColorSelectorForLabel(mouseEvent, 'category2');

        expect(comp.selectedCategory).toEqual(category2);
        expect(openColorSelectorSpy).toHaveBeenCalledWith(mouseEvent, undefined, 150);
    });

    it('should expose the color of a selected category label', () => {
        fixture.detectChanges();
        fixture.componentRef.setInput('categories', [category1, category2]);
        fixture.changeDetectorRef.detectChanges();

        expect(comp.colorFor('category1')).toBe('#6ae8ac');
        expect(comp.colorFor('unknown')).toBeUndefined();
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
        comp.onItemSelect(selectEvent('category9'));

        const categoryColor = comp.selectedCategoryItems()[1].color;
        expect(comp.selectedCategoryItems()).toEqual([category6, { category: 'category9', color: categoryColor }]);
        expect(emitSpy).toHaveBeenCalledWith([category6, { category: 'category9', color: categoryColor }]);
    });

    it('should not create new item on select', () => {
        fixture.componentRef.setInput('categories', [category6]);
        fixture.componentRef.setInput('existingCategories', [category7, category8]);
        fixture.changeDetectorRef.detectChanges();
        comp.onItemSelect(selectEvent('category7'));

        expect(comp.selectedCategoryItems()).toEqual([category6, category7]);
        expect(emitSpy).toHaveBeenCalledWith([category6, category7]);
    });

    it('should not create duplicate item on add', () => {
        fixture.componentRef.setInput('categories', [category6]);
        fixture.changeDetectorRef.detectChanges();
        // hide() needs the rendered AutoComplete; stub it so the free-text add path runs in isolation
        vi.spyOn(comp.autoComplete(), 'hide').mockImplementation(() => undefined);
        comp.onEnter(enterEvent('category6'));

        expect(comp.selectedCategoryItems()).toEqual([category6]);
        expect(emitSpy).not.toHaveBeenCalled();
    });

    it('should save existing category on add', () => {
        fixture.componentRef.setInput('categories', [category6]);
        fixture.componentRef.setInput('existingCategories', [category7, category8]);
        fixture.changeDetectorRef.detectChanges();
        vi.spyOn(comp.autoComplete(), 'hide').mockImplementation(() => undefined);
        comp.onEnter(enterEvent('category8'));

        expect(comp.selectedCategoryItems()).toEqual([category6, category8]);
        expect(emitSpy).toHaveBeenCalledWith([category6, category8]);
    });

    it('should create new item on add for existing categories', () => {
        fixture.componentRef.setInput('categories', [category6]);
        fixture.componentRef.setInput('existingCategories', []);
        fixture.changeDetectorRef.detectChanges();
        vi.spyOn(comp.autoComplete(), 'hide').mockImplementation(() => undefined);
        comp.onEnter(enterEvent('category9'));

        const categoryColor = comp.selectedCategoryItems()[1].color;
        expect(comp.selectedCategoryItems()).toEqual([category6, { category: 'category9', color: categoryColor }]);
        expect(emitSpy).toHaveBeenCalledWith([category6, { category: 'category9', color: categoryColor }]);
    });

    it('should create new item on add for empty categories', () => {
        fixture.componentRef.setInput('existingCategories', []);
        fixture.changeDetectorRef.detectChanges();
        vi.spyOn(comp.autoComplete(), 'hide').mockImplementation(() => undefined);
        comp.onEnter(enterEvent('category6'));

        const categoryColor = comp.selectedCategoryItems()[0].color;
        expect(comp.selectedCategoryItems()).toEqual([{ category: 'category6', color: categoryColor }]);
        expect(emitSpy).toHaveBeenCalledWith([{ category: 'category6', color: categoryColor }]);
    });

    it('should set suggestions for autocomplete on complete with empty query', () => {
        fixture.componentRef.setInput('existingCategories', [category3, category4, category5]);
        fixture.componentRef.setInput('categories', [category3]);
        fixture.detectChanges();

        comp.onComplete({ originalEvent: new Event('input'), query: '' });
        expect(comp.categorySuggestions()).toEqual(['category4', 'category5']);
    });

    it('should refresh suggestions when the inputs change after the initial render', () => {
        fixture.componentRef.setInput('existingCategories', [category3, category4, category5]);
        fixture.componentRef.setInput('categories', [category3]);
        fixture.detectChanges();

        comp.onComplete({ originalEvent: new Event('input'), query: '' });
        expect(comp.categorySuggestions()).toEqual(['category4', 'category5']);

        fixture.componentRef.setInput('categories', [category3, category4]);
        fixture.detectChanges();
        comp.onComplete({ originalEvent: new Event('input'), query: '' });
        expect(comp.categorySuggestions()).toEqual(['category5']);
    });

    it('should filter suggestions for autocomplete on query', () => {
        fixture.componentRef.setInput('existingCategories', [category3, category4, category5]);
        fixture.componentRef.setInput('categories', [category3]);
        fixture.detectChanges();

        comp.onComplete({ originalEvent: new Event('input'), query: 'category4' });
        expect(comp.categorySuggestions()).toEqual(['category4']);
    });
});
