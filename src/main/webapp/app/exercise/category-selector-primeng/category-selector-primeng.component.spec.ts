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

    /** Builds a real keydown event carrying the key, on a real input target so the onSeparatorKeydown
     * HTMLInputElement guard passes (mirrors a keydown emitted by the autocomplete input). */
    const separatorEvent = (key: string, value: string): KeyboardEvent => {
        const input = document.createElement('input');
        input.value = value;
        const event = new KeyboardEvent('keydown', { key, cancelable: true });
        Object.defineProperty(event, 'target', { value: input });
        return event;
    };

    /** Builds a plain Enter keydown event, as the input element would emit on Enter. */
    const enterEvent = (value: string) => separatorEvent('Enter', value);

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

    it('should remove the category and stop propagation via the in-chip remove icon (removeChip)', () => {
        fixture.detectChanges();
        fixture.componentRef.setInput('categories', [category1, category2, category3]);
        fixture.changeDetectorRef.detectChanges();
        const event = new MouseEvent('click');
        const stopSpy = vi.spyOn(event, 'stopPropagation');

        comp.removeChip('category2', event);

        expect(comp.selectedCategoryItems()).toEqual([category1, category3]);
        expect(stopSpy).toHaveBeenCalledOnce();
        expect(emitSpy).toHaveBeenCalledWith([category1, category3]);
    });

    it('renders the in-chip remove control as a non-submitting, labelled button inside the colored pill', async () => {
        fixture.detectChanges();
        fixture.componentRef.setInput('categories', [category1]);
        // Push the value into PrimeNG's AutoComplete so it renders the chip token via the selecteditem template.
        comp.autoComplete().writeValue([category1.category]);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const button = fixture.nativeElement.querySelector('.custom-tag button.category-chip-remove');
        expect(button).toBeTruthy();
        // type="button" so it never submits a surrounding (ngSubmit) form
        expect(button.getAttribute('type')).toBe('button');
        // accessible name carries both the action and the category (MockTranslateService returns the raw key)
        expect(button.getAttribute('aria-label')).toBe('entity.action.remove category1');
    });

    it('removes the category and emits when the rendered remove button is clicked', async () => {
        fixture.detectChanges();
        fixture.componentRef.setInput('categories', [category1, category2]);
        comp.autoComplete().writeValue([category1.category, category2.category]);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const buttons = fixture.nativeElement.querySelectorAll('.custom-tag button.category-chip-remove');
        expect(buttons.length).toBe(2);
        buttons[0].click();
        fixture.detectChanges();

        expect(comp.selectedCategoryItems()).toEqual([category2]);
        expect(emitSpy).toHaveBeenCalledWith([category2]);
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

    it('should cancel the Enter event to prevent form submission', () => {
        fixture.componentRef.setInput('existingCategories', []);
        fixture.changeDetectorRef.detectChanges();
        vi.spyOn(comp.autoComplete(), 'hide').mockImplementation(() => undefined);
        const event = enterEvent('newcat');
        const stopSpy = vi.spyOn(event, 'stopPropagation');
        comp.onEnter(event);

        expect(event.defaultPrevented).toBe(true);
        expect(stopSpy).toHaveBeenCalledOnce();
    });

    it('should commit the typed category on a comma separator key', () => {
        fixture.componentRef.setInput('existingCategories', []);
        fixture.changeDetectorRef.detectChanges();
        vi.spyOn(comp.autoComplete(), 'hide').mockImplementation(() => undefined);

        comp.onSeparatorKeydown(separatorEvent(',', 'newcat'));

        expect(comp.selectedCategoryItems().map((c) => c.category)).toContain('newcat');
    });

    it('should commit the typed category on a Tab separator key', () => {
        fixture.componentRef.setInput('existingCategories', []);
        fixture.changeDetectorRef.detectChanges();
        vi.spyOn(comp.autoComplete(), 'hide').mockImplementation(() => undefined);

        comp.onSeparatorKeydown(separatorEvent('Tab', 'newcat'));

        expect(comp.selectedCategoryItems().map((c) => c.category)).toContain('newcat');
    });

    it('should ignore a non-separator key', () => {
        fixture.componentRef.setInput('existingCategories', []);
        fixture.changeDetectorRef.detectChanges();

        comp.onSeparatorKeydown(separatorEvent('a', 'newcat'));

        expect(comp.selectedCategoryItems()).toEqual([]);
    });

    it('should let an empty field Tab away instead of committing or trapping focus', () => {
        fixture.componentRef.setInput('existingCategories', []);
        fixture.changeDetectorRef.detectChanges();

        const event = separatorEvent('Tab', '   ');
        comp.onSeparatorKeydown(event);

        expect(comp.selectedCategoryItems()).toEqual([]);
        expect(event.defaultPrevented).toBe(false);
    });

    it('ignores a keydown bubbling from a non-input target (e.g. the remove button) so it does not block activation', () => {
        const event = new KeyboardEvent('keydown', { key: 'Enter', cancelable: true });
        Object.defineProperty(event, 'target', { value: document.createElement('button') });

        comp.onSeparatorKeydown(event);

        expect(event.defaultPrevented).toBe(false);
        expect(comp.selectedCategoryItems()).toEqual([]);
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

    it('should reject free-text add via onEnter when MAX_CATEGORIES is reached', () => {
        fixture.componentRef.setInput('categories', [category1, category2, category3]);
        fixture.changeDetectorRef.detectChanges();
        // sanity check: the preset matches the component's MAX
        expect(comp.selectedCategoryItems()).toHaveLength(3);
        vi.spyOn(comp.autoComplete(), 'hide').mockImplementation(() => undefined);

        comp.onEnter(enterEvent('newCat'));

        expect(comp.selectedCategoryItems()).toEqual([category1, category2, category3]);
        expect(comp.selectedCategoryItems()).toHaveLength(3);
        expect(emitSpy).not.toHaveBeenCalled();
    });

    it('should reject suggestion add via onItemSelect when MAX_CATEGORIES is reached', () => {
        fixture.componentRef.setInput('categories', [category1, category2, category3]);
        fixture.componentRef.setInput('existingCategories', [category4]);
        fixture.changeDetectorRef.detectChanges();
        expect(comp.selectedCategoryItems()).toHaveLength(3);
        // resync target after rejection; stub so the rejection branch runs in isolation
        const writeValueSpy = vi.spyOn(comp.autoComplete(), 'writeValue').mockImplementation(() => undefined);

        comp.onItemSelect(selectEvent('newCat'));

        expect(comp.selectedCategoryItems()).toEqual([category1, category2, category3]);
        expect(comp.selectedCategoryItems()).toHaveLength(3);
        expect(emitSpy).not.toHaveBeenCalled();
        expect(writeValueSpy).toHaveBeenCalledWith(comp.selectedCategoryLabels());
    });

    it('should resync the autocomplete model when onItemSelect rejects a duplicate', () => {
        fixture.componentRef.setInput('categories', [category6, category7]);
        fixture.changeDetectorRef.detectChanges();
        const writeValueSpy = vi.spyOn(comp.autoComplete(), 'writeValue').mockImplementation(() => undefined);

        comp.onItemSelect(selectEvent('category6'));

        expect(comp.selectedCategoryItems()).toEqual([category6, category7]);
        expect(emitSpy).not.toHaveBeenCalled();
        expect(writeValueSpy).toHaveBeenCalledWith(comp.selectedCategoryLabels());
    });

    it('should reject a case-insensitive duplicate on add via onEnter', () => {
        fixture.componentRef.setInput('categories', [category6]);
        fixture.changeDetectorRef.detectChanges();
        vi.spyOn(comp.autoComplete(), 'hide').mockImplementation(() => undefined);

        comp.onEnter(enterEvent('Category6'));

        expect(comp.selectedCategoryItems()).toEqual([category6]);
        expect(emitSpy).not.toHaveBeenCalled();
    });

    it('should add the category, clear the input and hide the overlay on a valid onEnter', () => {
        fixture.componentRef.setInput('categories', [category6]);
        fixture.componentRef.setInput('existingCategories', []);
        fixture.changeDetectorRef.detectChanges();
        const hideSpy = vi.spyOn(comp.autoComplete(), 'hide').mockImplementation(() => undefined);
        const event = enterEvent('newValidCat');

        comp.onEnter(event);

        const addedColor = comp.selectedCategoryItems()[1].color;
        expect(comp.selectedCategoryItems()).toEqual([category6, { category: 'newValidCat', color: addedColor }]);
        expect(emitSpy).toHaveBeenCalledOnce();
        expect(emitSpy).toHaveBeenCalledWith([category6, { category: 'newValidCat', color: addedColor }]);
        expect((event.target as HTMLInputElement).value).toBe('');
        expect(hideSpy).toHaveBeenCalledOnce();
    });
});
