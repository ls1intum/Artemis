import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CategorySelectorComponent } from 'app/shared/category-selector/category-selector.component';
import { MockComponent, MockModule } from 'ng-mocks';
import { ColorSelectorComponent } from 'app/shared/color-selector/color-selector.component';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatChipsModule } from '@angular/material/chips';
import { MatSelectModule } from '@angular/material/select';
import { ExerciseCategory } from 'app/entities/exercise-category.model';

describe('Category Selector Component', () => {
    let comp: CategorySelectorComponent;
    let fixture: ComponentFixture<CategorySelectorComponent>;

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
            imports: [MockModule(MatAutocompleteModule), MockModule(MatFormFieldModule), MockModule(MatChipsModule), MockModule(MatSelectModule)],
            declarations: [CategorySelectorComponent, MockComponent(ColorSelectorComponent)],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CategorySelectorComponent);
                comp = fixture.componentInstance;
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
        comp.categories = [];
        const result = comp.categoriesAsStringArray();

        expect(result).toEqual([]);
    });

    it('should existing convert categories to string array', () => {
        comp.existingCategories = [category1, category2];
        const result = comp.existingCategoriesAsStringArray();

        expect(result).toEqual(['category1', 'category2']);
    });

    it('should existing convert categories to empty string array', () => {
        comp.existingCategories = [];
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
});
