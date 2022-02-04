import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CategorySelectorComponent } from 'app/shared/category-selector/category-selector.component';
import { MockComponent, MockModule, MockPipe } from 'ng-mocks';
import { ColorSelectorComponent } from 'app/shared/color-selector/color-selector.component';
import { MatAutocompleteModule, MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatChipInput, MatChipInputEvent, MatChipsModule } from '@angular/material/chips';
import { MatSelectModule } from '@angular/material/select';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { ArtemisColorSelectorModule } from 'app/shared/color-selector/color-selector.module';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';

describe('Category Selector Component', () => {
    let comp: CategorySelectorComponent;
    let fixture: ComponentFixture<CategorySelectorComponent>;
    let emitSpy: jest.SpyInstance;

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
        category: 'category4',
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

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                FontAwesomeTestingModule,
                MockModule(MatAutocompleteModule),
                MockModule(MatFormFieldModule),
                MockModule(MatChipsModule),
                MockModule(MatSelectModule),
                MockModule(ArtemisColorSelectorModule),
                MockModule(ReactiveFormsModule),
                MockModule(FormsModule),
            ],
            declarations: [CategorySelectorComponent, MockComponent(ColorSelectorComponent), MockPipe(ArtemisTranslatePipe)],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CategorySelectorComponent);
                comp = fixture.componentInstance;

                emitSpy = jest.spyOn(comp.selectedCategories, 'emit');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(comp).not.toBeUndefined();
    });

    it('should choose random color', () => {
        const result = comp.chooseRandomColor();

        expect(comp.categoryColors).toContain(result);
    });

    it('should find existing category', () => {
        comp.existingCategories = [category3, category4];
        const result = comp.findExistingCategory('category3');

        expect(result).toEqual(category3);
    });

    it('should not find existing category', () => {
        comp.existingCategories = [category3, category4];
        const result = comp.findExistingCategory('category5');

        expect(result).toEqual(undefined);
    });

    it('should convert categories to string array', () => {
        comp.categories = [category1, category2];
        const result = comp.categoriesAsStringArray();

        expect(result).toEqual(['category1', 'category2']);
    });

    it('should convert categories to empty string array', () => {
        const result = comp.categoriesAsStringArray();

        expect(result).toEqual([]);
    });

    it('should existing convert categories to string array', () => {
        comp.existingCategories = [category1, category2];
        const result = comp.existingCategoriesAsStringArray();

        expect(result).toEqual(['category1', 'category2']);
    });

    it('should existing convert categories to empty string array', () => {
        const result = comp.existingCategoriesAsStringArray();

        expect(result).toEqual([]);
    });

    it('should filter categories with result', () => {
        comp.existingCategories = [category1, category2];
        const result = comp.filterCategories('Gory1');

        expect(result).toEqual([category1.category]);
    });

    it('should filter categories without result', () => {
        comp.existingCategories = [category1, category2];

        const result = comp.filterCategories('caTcaT');

        expect(result).toEqual([]);
    });

    it('should create an exercise category', () => {
        const result = comp.createCategory('Test');

        expect(comp.categoryColors).toContain(result.color);
        expect(result.category).toEqual('Test');
    });

    it('should remove category', () => {
        fixture.detectChanges();
        comp.categories = [category1, category2, category3];
        const cancelColorSelectorSpy = jest.spyOn(comp.colorSelector, 'cancelColorSelector');
        comp.onItemRemove(category2);

        expect(comp.categories).toEqual([category1, category3]);
        expect(cancelColorSelectorSpy).toHaveBeenCalledOnce();
        expect(emitSpy).toHaveBeenCalledWith([category1, category3]);
    });

    it('should open color selector', () => {
        fixture.detectChanges();
        const mouseEvent = { x: 1, y: 2 } as MouseEvent;
        const openColorSelectorSpy = jest.spyOn(comp.colorSelector, 'openColorSelector');
        comp.openColorSelector(mouseEvent, category5);

        expect(comp.selectedCategory).toEqual(category5);
        expect(openColorSelectorSpy).toHaveBeenCalledWith(mouseEvent, undefined, 150);
    });

    it('should select color for category', () => {
        comp.categories = [category1, category2, category3];
        comp.selectedCategory = category2;
        comp.onSelectedColor('#fff');
        const expected = { category: 'category2', color: '#fff' };

        expect(comp.selectedCategory).toEqual(expected);
        expect(comp.categories).toEqual([category1, expected, category3]);
        expect(emitSpy).toHaveBeenCalledWith([category1, expected, category3]);
    });

    it('should create new item on select', () => {
        comp.categories = [category6];
        comp.existingCategories = [category6, category7, category8];
        const event = { option: { value: 'category9' } } as MatAutocompleteSelectedEvent;
        fixture.detectChanges();
        comp.onItemSelect(event);

        const categoryColor = comp.categories[1].color;
        expect(comp.categories).toEqual([category6, { category: 'category9', color: categoryColor }]);
        expect(emitSpy).toHaveBeenCalledWith([category6, { category: 'category9', color: categoryColor }]);
        expect(comp.categoryInput.nativeElement.value).toBe('');
        expect(comp.categoryCtrl.value).toBe(null);
    });

    it('should not create new item on select', () => {
        comp.categories = [category6];
        comp.existingCategories = [category7, category8];
        const event = { option: { value: 'category7' } } as MatAutocompleteSelectedEvent;
        fixture.detectChanges();
        comp.onItemSelect(event);

        expect(comp.categories).toEqual([category6, category7]);
        expect(emitSpy).toHaveBeenCalledWith([category6, category7]);
        expect(comp.categoryInput.nativeElement.value).toBe('');
        expect(comp.categoryCtrl.value).toBe(null);
    });

    it('should not create duplicate item on add', () => {
        comp.categories = [category6];
        const event = { value: 'category6', chipInput: { clear: () => {} } as MatChipInput } as MatChipInputEvent;
        fixture.detectChanges();
        comp.onItemAdd(event);

        expect(comp.categories).toEqual([category6]);
        expect(emitSpy).not.toHaveBeenCalled();
        expect(comp.categoryCtrl.value).toBe(null);
    });

    it('should save exiting category on add', () => {
        comp.categories = [category6];
        comp.existingCategories = [category7, category8];
        const event = { value: 'category8', chipInput: { clear: () => {} } as MatChipInput } as MatChipInputEvent;
        fixture.detectChanges();
        comp.onItemAdd(event);

        const categoryColor = comp.categories[1].color;
        expect(comp.categories).toEqual([category6, category8]);
        expect(emitSpy).toHaveBeenCalledWith([category6, category8]);
        expect(comp.categoryCtrl.value).toBe(null);
    });

    it('should create new item on add for existing categories', () => {
        comp.categories = [category6];
        comp.existingCategories = [];
        const event = { value: 'category9', chipInput: { clear: () => {} } as MatChipInput } as MatChipInputEvent;
        fixture.detectChanges();
        comp.onItemAdd(event);

        const categoryColor = comp.categories[1].color;
        expect(comp.categories).toEqual([category6, { category: 'category9', color: categoryColor }]);
        expect(emitSpy).toHaveBeenCalledWith([category6, { category: 'category9', color: categoryColor }]);
        expect(comp.categoryCtrl.value).toBe(null);
    });

    it('should create new item on add for empty categories', () => {
        comp.categories = [];
        comp.existingCategories = [];
        const event = { value: 'category6', chipInput: { clear: () => {} } as MatChipInput } as MatChipInputEvent;
        fixture.detectChanges();
        comp.onItemAdd(event);

        const categoryColor = comp.categories[0].color;
        expect(comp.categories).toEqual([{ category: 'category6', color: categoryColor }]);
        expect(emitSpy).toHaveBeenCalledWith([{ category: 'category6', color: categoryColor }]);
        expect(comp.categoryCtrl.value).toBe(null);
    });
});
