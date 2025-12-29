import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { SimpleChange } from '@angular/core';
import { DataTableComponent } from './data-table.component';
import { MockModule, MockProvider } from 'ng-mocks';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { NgbTypeaheadModule } from '@ng-bootstrap/ng-bootstrap';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SortService } from 'app/shared/service/sort.service';
import { Subject } from 'rxjs';
import { faSort, faSortDown, faSortUp } from '@fortawesome/free-solid-svg-icons';
import { BaseEntity, StringBaseEntity } from 'app/shared/model/base-entity';

// Test entity interfaces extending BaseEntity for type safety
interface TestUserEntity extends BaseEntity {
    name?: string;
    login?: string;
    active?: boolean;
}

interface TestNestedEntity extends BaseEntity {
    student?: { name: string; login: string };
}

interface TestArrayEntity extends BaseEntity {
    students?: { name: string }[];
}

describe('DataTableComponent', () => {
    let component: DataTableComponent;
    let fixture: ComponentFixture<DataTableComponent>;
    let localStorageService: LocalStorageService;
    let sortService: SortService;

    const mockLocalStorage: { [key: string]: string } = {};

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockModule(FontAwesomeModule), MockModule(NgbTypeaheadModule), DataTableComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, MockProvider(LocalStorageService), SortService],
        }).compileComponents();

        fixture = TestBed.createComponent(DataTableComponent);
        component = fixture.componentInstance;
        localStorageService = TestBed.inject(LocalStorageService);
        sortService = TestBed.inject(SortService);

        // Setup mock localStorage behavior
        jest.spyOn(localStorageService, 'retrieve').mockImplementation(<T>(key: string): T | undefined => {
            const value = mockLocalStorage[key];
            if (value === undefined) return undefined;
            // Parse numeric values
            const numValue = Number(value);
            if (!isNaN(numValue)) return numValue as T;
            return value as T;
        });
        jest.spyOn(localStorageService, 'store').mockImplementation(<T>(key: string, value: T) => {
            mockLocalStorage[key] = String(value);
        });

        // Clear mock storage before each test
        Object.keys(mockLocalStorage).forEach((key) => delete mockLocalStorage[key]);

        fixture.detectChanges();
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    // Helper function to set entities with proper typing
    const setTestEntities = <T extends BaseEntity | StringBaseEntity>(entities: T[]): void => {
        component.allEntities = entities;
    };

    describe('Component initialization', () => {
        it('should create', () => {
            expect(component).toBeTruthy();
        });

        it('should initialize with default values', () => {
            expect(component.entities).toEqual([]);
            expect(component.entityCriteria.textSearch).toEqual([]);
            expect(component.entityCriteria.sortProp).toEqual({ field: 'id', order: 'asc' });
            expect(component.pagingValue).toBe(50);
        });

        it('should initialize with default paging value when no cached value exists', () => {
            expect(component.pagingValue).toBe(component.DEFAULT_PAGING_VALUE);
        });

        it('should initialize with cached paging value from local storage', () => {
            mockLocalStorage['entity-items-per-page'] = '100';

            // Re-create component to test initialization
            fixture = TestBed.createComponent(DataTableComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();

            expect(component.pagingValue).toBe(100);
        });

        it('should use default paging value when cached value is invalid', () => {
            mockLocalStorage['entity-items-per-page'] = '999'; // Invalid value not in PAGING_VALUES

            fixture = TestBed.createComponent(DataTableComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();

            expect(component.pagingValue).toBe(component.DEFAULT_PAGING_VALUE);
        });

        it('should handle "all" as cached paging value', () => {
            mockLocalStorage['entity-items-per-page'] = 'all';

            fixture = TestBed.createComponent(DataTableComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();

            expect(component.pagingValue).toBe('all');
        });
    });

    describe('ngOnChanges', () => {
        it('should update entities when allEntities changes', () => {
            const updateEntitiesSpy = jest.spyOn(component as any, 'updateEntities');

            component.ngOnChanges({
                allEntities: new SimpleChange([], [{ id: 1 }], false),
            });

            expect(updateEntitiesSpy).toHaveBeenCalled();
        });

        it('should update entities when customFilterKey changes', () => {
            const updateEntitiesSpy = jest.spyOn(component as any, 'updateEntities');

            component.ngOnChanges({
                customFilterKey: new SimpleChange({}, { filter: true }, false),
            });

            expect(updateEntitiesSpy).toHaveBeenCalled();
        });

        it('should not update entities when other inputs change', () => {
            const updateEntitiesSpy = jest.spyOn(component as any, 'updateEntities');

            component.ngOnChanges({
                isLoading: new SimpleChange(false, true, false),
            });

            expect(updateEntitiesSpy).not.toHaveBeenCalled();
        });
    });

    describe('Context getter', () => {
        it('should return correct context structure', () => {
            setTestEntities([{ id: 1 }, { id: 2 }]);
            component.ngOnChanges({ allEntities: new SimpleChange([], component.allEntities, false) });

            const context = component.context;

            expect(context.settings).toBeDefined();
            expect(context.settings.limit).toBe(50);
            expect(context.settings.rows).toEqual(component.entities);
            expect(context.settings.headerHeight).toBe(50);
            expect(context.settings.footerHeight).toBe(50);
            expect(context.settings.rowHeight).toBe('auto');
            expect(context.settings.scrollbarH).toBeTrue();

            expect(context.controls).toBeDefined();
            expect(context.controls.onSort).toBeDefined();
            expect(context.controls.iconForSortPropField).toBeDefined();
        });

        it('should return undefined limit when pagingValue is "all"', () => {
            component.pagingValue = 'all';

            expect(component.context.settings.limit).toBeUndefined();
        });
    });

    describe('isPreparing getter', () => {
        it('should return true when isLoading is true', () => {
            component.isLoading = true;
            component.isRendering = false;

            expect(component.isPreparing).toBeTrue();
        });

        it('should return true when isRendering is true', () => {
            component.isLoading = false;
            component.isRendering = true;

            expect(component.isPreparing).toBeTrue();
        });

        it('should return false when neither isLoading nor isRendering is true', () => {
            component.isLoading = false;
            component.isRendering = false;

            expect(component.isPreparing).toBeFalse();
        });
    });

    describe('pageLimit getter', () => {
        it('should return numeric value when pagingValue is a number', () => {
            component.pagingValue = 100;

            expect(component.pageLimit).toBe(100);
        });

        it('should return undefined when pagingValue is "all"', () => {
            component.pagingValue = 'all';

            expect(component.pageLimit).toBeUndefined();
        });
    });

    describe('perPageTranslation', () => {
        beforeEach(() => {
            component.entitiesPerPageTranslation = 'items.perPage';
            component.showAllEntitiesTranslation = 'items.showAll';
        });

        it('should return entitiesPerPageTranslation for numeric values', () => {
            expect(component.perPageTranslation(50)).toBe('items.perPage');
            expect(component.perPageTranslation(100)).toBe('items.perPage');
        });

        it('should return showAllEntitiesTranslation for "all"', () => {
            expect(component.perPageTranslation('all')).toBe('items.showAll');
        });
    });

    describe('setEntitiesPerPage', () => {
        it('should set isRendering to true initially', () => {
            component.setEntitiesPerPage(100);

            expect(component.isRendering).toBeTrue();
        });

        it('should update pagingValue after timeout', fakeAsync(() => {
            component.setEntitiesPerPage(100);

            tick(500);

            expect(component.pagingValue).toBe(100);
            expect(component.isRendering).toBeFalse();
        }));

        it('should store value in local storage', () => {
            const storeSpy = jest.spyOn(localStorageService, 'store');

            component.setEntitiesPerPage(200);

            expect(storeSpy).toHaveBeenCalledWith('entity-items-per-page', '200');
        });

        it('should handle "all" value', fakeAsync(() => {
            const storeSpy = jest.spyOn(localStorageService, 'store');

            component.setEntitiesPerPage('all');
            tick(500);

            expect(component.pagingValue).toBe('all');
            expect(storeSpy).toHaveBeenCalledWith('entity-items-per-page', 'all');
        }));
    });

    describe('Sorting functionality', () => {
        beforeEach(() => {
            const entities: TestUserEntity[] = [
                { id: 3, name: 'Charlie' },
                { id: 1, name: 'Alice' },
                { id: 2, name: 'Bob' },
            ];
            setTestEntities(entities);
            component.ngOnChanges({ allEntities: new SimpleChange([], component.allEntities, false) });
        });

        it('should sort entities by field in ascending order by default', () => {
            component.onSort('name');

            expect(component.entityCriteria.sortProp).toEqual({ field: 'name', order: 'asc' });
        });

        it('should toggle sort order when sorting by same field', () => {
            component.onSort('name');
            expect(component.entityCriteria.sortProp.order).toBe('asc');

            component.onSort('name');
            expect(component.entityCriteria.sortProp.order).toBe('desc');

            component.onSort('name');
            expect(component.entityCriteria.sortProp.order).toBe('asc');
        });

        it('should reset to ascending when sorting by different field', () => {
            component.onSort('name');
            component.onSort('name'); // Now desc

            component.onSort('id');

            expect(component.entityCriteria.sortProp).toEqual({ field: 'id', order: 'asc' });
        });

        it('should call sortService.sortByProperty', () => {
            const sortSpy = jest.spyOn(sortService, 'sortByProperty');

            component.onSort('name');

            expect(sortSpy).toHaveBeenCalledWith(expect.any(Array), 'name', true);
        });
    });

    describe('iconForSortPropField', () => {
        it('should return NONE icon for non-sorted field', () => {
            component.entityCriteria.sortProp = { field: 'name', order: 'asc' as any };

            expect(component.iconForSortPropField('id')).toEqual(faSort);
        });

        it('should return ASC icon for ascending sorted field', () => {
            component.entityCriteria.sortProp = { field: 'name', order: 'asc' as any };

            expect(component.iconForSortPropField('name')).toEqual(faSortUp);
        });

        it('should return DESC icon for descending sorted field', () => {
            component.entityCriteria.sortProp = { field: 'name', order: 'desc' as any };

            expect(component.iconForSortPropField('name')).toEqual(faSortDown);
        });
    });

    describe('Custom filter functionality', () => {
        beforeEach(() => {
            const entities: TestUserEntity[] = [
                { id: 1, name: 'Alice', active: true },
                { id: 2, name: 'Bob', active: false },
                { id: 3, name: 'Charlie', active: true },
            ];
            setTestEntities(entities);
        });

        it('should apply custom filter', () => {
            component.customFilter = (entity) => (entity as TestUserEntity).active === true;
            component.ngOnChanges({ allEntities: new SimpleChange([], component.allEntities, false) });

            expect(component.entities).toHaveLength(2);
            expect(component.entities.map((e) => (e as TestUserEntity).name)).toEqual(['Alice', 'Charlie']);
        });

        it('should combine custom filter with text search', fakeAsync(() => {
            component.searchFields = ['name'];
            component.searchEntityFilterEnabled = true;
            component.customFilter = (entity) => (entity as TestUserEntity).active === true;
            component.ngOnChanges({ allEntities: new SimpleChange([], component.allEntities, false) });

            const text$ = new Subject<string>();
            component.onSearch(text$).subscribe();

            text$.next('Alice');
            tick(250);

            expect(component.entities).toHaveLength(1);
            expect((component.entities[0] as TestUserEntity).name).toBe('Alice');
        }));
    });

    describe('Text search functionality', () => {
        beforeEach(() => {
            component.searchFields = ['name', 'login'];
            component.searchEntityFilterEnabled = true;
        });

        it('should return all entities when search is empty', fakeAsync(() => {
            const entities: TestUserEntity[] = [
                { id: 1, name: 'Alice', login: 'alice' },
                { id: 2, name: 'Bob', login: 'bob' },
            ];
            setTestEntities(entities);

            const text$ = new Subject<string>();
            component.onSearch(text$).subscribe();

            text$.next('');
            tick(250);

            expect(component.entities).toHaveLength(2);
        }));

        it('should filter entities by search term', fakeAsync(() => {
            const entities: TestUserEntity[] = [
                { id: 1, name: 'Alice', login: 'alice' },
                { id: 2, name: 'Bob', login: 'bob' },
                { id: 3, name: 'Charlie', login: 'charlie' },
            ];
            setTestEntities(entities);

            const text$ = new Subject<string>();
            component.onSearch(text$).subscribe();

            text$.next('Alice');
            tick(250);

            expect(component.entities).toHaveLength(1);
            expect(component.entities[0].id).toBe(1);
        }));

        it('should support comma-separated search terms', fakeAsync(() => {
            const entities: TestUserEntity[] = [
                { id: 1, name: 'Alice', login: 'alice' },
                { id: 2, name: 'Bob', login: 'bob' },
                { id: 3, name: 'Charlie', login: 'charlie' },
            ];
            setTestEntities(entities);

            const text$ = new Subject<string>();
            component.onSearch(text$).subscribe();

            text$.next('Alice, Bob');
            tick(250);

            // Both Alice and Bob should match (OR logic for comma-separated terms)
            expect(component.entities).toHaveLength(2);
        }));

        it('should search in login field as well', fakeAsync(() => {
            const entities: TestUserEntity[] = [
                { id: 1, name: 'Alice Smith', login: 'asmith' },
                { id: 2, name: 'Bob Jones', login: 'bjones' },
            ];
            setTestEntities(entities);

            const text$ = new Subject<string>();
            component.onSearch(text$).subscribe();

            text$.next('bjones');
            tick(250);

            expect(component.entities).toHaveLength(1);
            expect(component.entities[0].id).toBe(2);
        }));

        it('should support wildcard * for multiple characters', fakeAsync(() => {
            const entities: TestUserEntity[] = [
                { id: 1, name: 'Alexander', login: 'alex' },
                { id: 2, name: 'Alexandra', login: 'alexa' },
                { id: 3, name: 'Bob', login: 'bob' },
            ];
            setTestEntities(entities);

            const text$ = new Subject<string>();
            component.onSearch(text$).subscribe();

            text$.next('Alex*');
            tick(250);

            expect(component.entities).toHaveLength(2);
        }));

        it('should support wildcard ? for single character', fakeAsync(() => {
            const entities: TestUserEntity[] = [
                { id: 1, name: 'A1B', login: 'a1b' },
                { id: 2, name: 'A2B', login: 'a2b' },
                { id: 3, name: 'AB', login: 'ab' }, // Should not match A?B (needs exactly one char between)
                { id: 4, name: 'A12B', login: 'a12b' }, // Should not match A?B (has two chars between)
            ];
            setTestEntities(entities);

            const text$ = new Subject<string>();
            component.onSearch(text$).subscribe();

            // A?B should match A1B and A2B (? matches exactly one character)
            text$.next('A?B');
            tick(250);

            expect(component.entities).toHaveLength(2);
            expect(component.entities.map((e) => e.id)).toEqual(expect.arrayContaining([1, 2]));
        }));

        it('should support space-separated search terms (AND logic)', fakeAsync(() => {
            const entities: TestUserEntity[] = [
                { id: 1, name: 'Max Mustermann', login: 'mmuster' },
                { id: 2, name: 'Max Gregor Mustermann', login: 'mgmuster' },
                { id: 3, name: 'Max Schmidt', login: 'mschmidt' },
            ];
            setTestEntities(entities);

            const text$ = new Subject<string>();
            component.onSearch(text$).subscribe();

            text$.next('Max Mustermann');
            tick(250);

            expect(component.entities).toHaveLength(2);
            expect(component.entities.map((e) => e.id)).toEqual(expect.arrayContaining([1, 2]));
        }));

        it('should set searchQueryTooShort when query is less than MIN_SEARCH_QUERY_LENGTH', fakeAsync(() => {
            const entities: TestUserEntity[] = [{ id: 1, name: 'Alice', login: 'alice' }];
            setTestEntities(entities);

            const text$ = new Subject<string>();
            component.onSearch(text$).subscribe();

            text$.next('Al');
            tick(250);

            expect(component.searchQueryTooShort).toBeTrue();
        }));

        it('should not set searchQueryTooShort when query meets MIN_SEARCH_QUERY_LENGTH', fakeAsync(() => {
            const entities: TestUserEntity[] = [{ id: 1, name: 'Alice', login: 'alice' }];
            setTestEntities(entities);

            const text$ = new Subject<string>();
            component.onSearch(text$).subscribe();

            text$.next('Ali');
            tick(250);

            expect(component.searchQueryTooShort).toBeFalse();
        }));

        it('should debounce search input', fakeAsync(() => {
            const entities: TestUserEntity[] = [
                { id: 1, name: 'Alice', login: 'alice' },
                { id: 2, name: 'Bob', login: 'bob' },
            ];
            setTestEntities(entities);
            // Initialize entities first
            component.ngOnChanges({ allEntities: new SimpleChange([], component.allEntities, false) });
            tick(0);

            expect(component.entities).toHaveLength(2);

            const text$ = new Subject<string>();
            component.onSearch(text$).subscribe();

            text$.next('Alice');
            tick(100); // Before debounce time (200ms)

            // Should still have all entities since debounce hasn't fired yet
            expect(component.entities).toHaveLength(2);

            tick(150); // After debounce time (total 250ms > 200ms)

            expect(component.entities).toHaveLength(1);
        }));

        it('should not filter when searchEntityFilterEnabled is false', fakeAsync(() => {
            component.searchEntityFilterEnabled = false;
            const entities: TestUserEntity[] = [
                { id: 1, name: 'Alice', login: 'alice' },
                { id: 2, name: 'Bob', login: 'bob' },
            ];
            setTestEntities(entities);
            component.ngOnChanges({ allEntities: new SimpleChange([], component.allEntities, false) });

            const text$ = new Subject<string>();
            component.onSearch(text$).subscribe();

            text$.next('Alice');
            tick(250);

            // All entities should still be present
            expect(component.entities).toHaveLength(2);
        }));
    });

    describe('Nested field search', () => {
        beforeEach(() => {
            component.searchFields = ['student.name', 'student.login'];
            component.searchEntityFilterEnabled = true;
        });

        it('should search in nested fields', fakeAsync(() => {
            const entities: TestNestedEntity[] = [
                { id: 1, student: { name: 'Alice', login: 'alice' } },
                { id: 2, student: { name: 'Bob', login: 'bob' } },
            ];
            setTestEntities(entities);

            const text$ = new Subject<string>();
            component.onSearch(text$).subscribe();

            text$.next('Alice');
            tick(250);

            expect(component.entities).toHaveLength(1);
            expect(component.entities[0].id).toBe(1);
        }));

        it('should search in array fields', fakeAsync(() => {
            component.searchFields = ['students.name'];
            const entities: TestArrayEntity[] = [
                { id: 1, students: [{ name: 'Alice' }, { name: 'Bob' }] },
                { id: 2, students: [{ name: 'Charlie' }, { name: 'David' }] },
            ];
            setTestEntities(entities);

            const text$ = new Subject<string>();
            component.onSearch(text$).subscribe();

            text$.next('Alice');
            tick(250);

            expect(component.entities).toHaveLength(1);
            expect(component.entities[0].id).toBe(1);
        }));

        it('should handle missing nested fields gracefully', fakeAsync(() => {
            const entities: TestNestedEntity[] = [
                { id: 1, student: { name: 'Alice', login: 'alice' } },
                { id: 2, student: undefined },
                { id: 3 }, // No student field at all
            ];
            setTestEntities(entities);

            const text$ = new Subject<string>();
            component.onSearch(text$).subscribe();

            text$.next('Alice');
            tick(250);

            expect(component.entities).toHaveLength(1);
            expect(component.entities[0].id).toBe(1);
        }));
    });

    describe('Unicode normalization in search', () => {
        beforeEach(() => {
            component.searchFields = ['name', 'login'];
            component.searchEntityFilterEnabled = true;
        });

        it('should find users with Turkish special characters when searching without diacritics', fakeAsync(() => {
            const entities: TestUserEntity[] = [
                { id: 1, name: 'Doğan Yılmaz', login: 'dyilmaz' },
                { id: 2, name: 'Max Müller', login: 'mmueller' },
                { id: 3, name: 'John Smith', login: 'jsmith' },
            ];
            setTestEntities(entities);

            const text$ = new Subject<string>();
            component.onSearch(text$).subscribe();

            text$.next('Dogan');
            tick(250);

            expect(component.entities).toHaveLength(1);
            expect(component.entities[0].id).toBe(1);
        }));

        it('should find users with German umlauts when searching without diacritics', fakeAsync(() => {
            const entities: TestUserEntity[] = [
                { id: 1, name: 'Max Müller', login: 'mmueller' },
                { id: 2, name: 'Hans Schröder', login: 'hschroeder' },
                { id: 3, name: 'John Smith', login: 'jsmith' },
            ];
            setTestEntities(entities);

            const text$ = new Subject<string>();
            component.onSearch(text$).subscribe();

            text$.next('Muller');
            tick(250);

            expect(component.entities).toHaveLength(1);
            expect(component.entities[0].id).toBe(1);
        }));

        it('should find users when searching with diacritics for non-diacritic names', fakeAsync(() => {
            const entities: TestUserEntity[] = [
                { id: 1, name: 'Dogan Yilmaz', login: 'dyilmaz' },
                { id: 2, name: 'Muller Hans', login: 'mhans' },
                { id: 3, name: 'John Smith', login: 'jsmith' },
            ];
            setTestEntities(entities);

            const text$ = new Subject<string>();
            component.onSearch(text$).subscribe();

            text$.next('Doğan');
            tick(250);

            expect(component.entities).toHaveLength(1);
            expect(component.entities[0].id).toBe(1);
        }));

        it('should find users with French accents when searching without diacritics', fakeAsync(() => {
            const entities: TestUserEntity[] = [
                { id: 1, name: 'François Lefèvre', login: 'flefevre' },
                { id: 2, name: 'John Smith', login: 'jsmith' },
            ];
            setTestEntities(entities);

            const text$ = new Subject<string>();
            component.onSearch(text$).subscribe();

            text$.next('Francois');
            tick(250);

            expect(component.entities).toHaveLength(1);
            expect(component.entities[0].id).toBe(1);
        }));

        it('should still support case-insensitive search', fakeAsync(() => {
            const entities: TestUserEntity[] = [
                { id: 1, name: 'Max Müller', login: 'mmueller' },
                { id: 2, name: 'John Smith', login: 'jsmith' },
            ];
            setTestEntities(entities);

            const text$ = new Subject<string>();
            component.onSearch(text$).subscribe();

            text$.next('MULLER');
            tick(250);

            expect(component.entities).toHaveLength(1);
            expect(component.entities[0].id).toBe(1);
        }));

        it('should still support wildcard search with diacritics normalization', fakeAsync(() => {
            const entities: TestUserEntity[] = [
                { id: 1, name: 'Doğan Yılmaz', login: 'dyilmaz' },
                { id: 2, name: 'Dogan Smith', login: 'dsmith' },
                { id: 3, name: 'John Smith', login: 'jsmith' },
            ];
            setTestEntities(entities);

            const text$ = new Subject<string>();
            component.onSearch(text$).subscribe();

            text$.next('Dog*');
            tick(250);

            expect(component.entities).toHaveLength(2);
        }));

        it('should find users with Spanish characters', fakeAsync(() => {
            const entities: TestUserEntity[] = [
                { id: 1, name: 'José García', login: 'jgarcia' },
                { id: 2, name: 'María Núñez', login: 'mnunez' },
                { id: 3, name: 'John Smith', login: 'jsmith' },
            ];
            setTestEntities(entities);

            const text$ = new Subject<string>();
            component.onSearch(text$).subscribe();

            text$.next('Jose');
            tick(250);

            expect(component.entities).toHaveLength(1);
            expect(component.entities[0].id).toBe(1);
        }));

        it('should find users with Nordic characters', fakeAsync(() => {
            const entities: TestUserEntity[] = [
                { id: 1, name: 'Björk Guðmundsdóttir', login: 'bguom' },
                { id: 2, name: 'Søren Kierkegaard', login: 'skierk' },
                { id: 3, name: 'John Smith', login: 'jsmith' },
            ];
            setTestEntities(entities);

            const text$ = new Subject<string>();
            component.onSearch(text$).subscribe();

            text$.next('Bjork');
            tick(250);

            expect(component.entities).toHaveLength(1);
            expect(component.entities[0].id).toBe(1);
        }));
    });

    describe('Autocomplete functionality', () => {
        beforeEach(() => {
            component.searchFields = ['name', 'login'];
            component.searchEntityFilterEnabled = true;
        });

        it('should call onAutocompleteSelect and update search text', () => {
            component.entityCriteria.textSearch = ['Ali'];
            const entity: BaseEntity = { id: 1 };

            component.onAutocompleteSelect(entity);

            expect(component.entityCriteria.textSearch).toEqual(['1']);
        });

        it('should use custom searchTextFromEntity function', () => {
            component.searchTextFromEntity = (entity) => (entity as TestUserEntity).name ?? '';
            component.entityCriteria.textSearch = ['Ali'];
            const entity: TestUserEntity = { id: 1, name: 'Alice' };

            component.onAutocompleteSelect(entity);

            expect(component.entityCriteria.textSearch).toEqual(['Alice']);
        });

        it('should call filterAfterAutocompleteSelect through wrapper', () => {
            const filterSpy = jest.spyOn(component, 'filterAfterAutocompleteSelect');
            const entity: BaseEntity = { id: 1 };

            component.onAutocompleteSelect(entity);

            expect(filterSpy).toHaveBeenCalled();
        });

        it('should use custom onAutocompleteSelectWrapper', () => {
            let wrapperCalled = false;
            component.onAutocompleteSelectWrapper = (entity, callback) => {
                wrapperCalled = true;
                callback(entity);
            };
            const entity: BaseEntity = { id: 1 };

            component.onAutocompleteSelect(entity);

            expect(wrapperCalled).toBeTrue();
        });
    });

    describe('searchInputFormatter', () => {
        it('should format search criteria as comma-separated string', () => {
            component.entityCriteria.textSearch = ['Alice', 'Bob', 'Charlie'];

            expect(component.searchInputFormatter()).toBe('Alice, Bob, Charlie');
        });

        it('should return empty string for empty search criteria', () => {
            component.entityCriteria.textSearch = [];

            expect(component.searchInputFormatter()).toBe('');
        });

        it('should return single term without comma', () => {
            component.entityCriteria.textSearch = ['Alice'];

            expect(component.searchInputFormatter()).toBe('Alice');
        });
    });

    describe('onSearchInputBlur', () => {
        it('should set searchQueryTooShort to false', () => {
            component.searchQueryTooShort = true;

            component.onSearchInputBlur();

            expect(component.searchQueryTooShort).toBeFalse();
        });
    });

    describe('entitiesSizeChange event', () => {
        it('should emit entities size when entities are updated', fakeAsync(() => {
            const emitSpy = jest.spyOn(component.entitiesSizeChange, 'emit');
            setTestEntities([{ id: 1 }, { id: 2 }, { id: 3 }]);

            component.ngOnChanges({ allEntities: new SimpleChange([], component.allEntities, false) });
            tick(0); // Allow setTimeout to execute

            expect(emitSpy).toHaveBeenCalledWith(3);
        }));

        it('should emit updated size after filtering', fakeAsync(() => {
            const emitSpy = jest.spyOn(component.entitiesSizeChange, 'emit');
            component.searchFields = ['name'];
            component.searchEntityFilterEnabled = true;
            const entities: TestUserEntity[] = [
                { id: 1, name: 'Alice' },
                { id: 2, name: 'Bob' },
                { id: 3, name: 'Charlie' },
            ];
            setTestEntities(entities);
            component.ngOnChanges({ allEntities: new SimpleChange([], component.allEntities, false) });
            tick(0);

            emitSpy.mockClear();

            const text$ = new Subject<string>();
            component.onSearch(text$).subscribe();

            text$.next('Alice');
            tick(250);

            expect(emitSpy).toHaveBeenCalledWith(1);
        }));
    });

    describe('Edge cases', () => {
        it('should handle empty allEntities', () => {
            setTestEntities([]);
            component.ngOnChanges({ allEntities: new SimpleChange([], [], false) });

            expect(component.entities).toEqual([]);
        });

        it('should handle entities with undefined fields', fakeAsync(() => {
            component.searchFields = ['name'];
            component.searchEntityFilterEnabled = true;
            const entities: TestUserEntity[] = [
                { id: 1, name: undefined },
                { id: 2 }, // No name field at all
                { id: 3, name: 'Alice' },
            ];
            setTestEntities(entities);

            const text$ = new Subject<string>();
            component.onSearch(text$).subscribe();

            text$.next('Alice');
            tick(250);

            expect(component.entities).toHaveLength(1);
            expect(component.entities[0].id).toBe(3);
        }));

        it('should handle special regex characters in search', fakeAsync(() => {
            component.searchFields = ['name'];
            component.searchEntityFilterEnabled = true;
            const entities: TestUserEntity[] = [
                { id: 1, name: 'Test (1)' },
                { id: 2, name: 'Test [2]' },
                { id: 3, name: 'Test.3' },
            ];
            setTestEntities(entities);

            const text$ = new Subject<string>();
            component.onSearch(text$).subscribe();

            // Search with special characters that need escaping
            text$.next('Test (1)');
            tick(250);

            expect(component.entities).toHaveLength(1);
            expect(component.entities[0].id).toBe(1);
        }));

        it('should handle very long search terms', fakeAsync(() => {
            component.searchFields = ['name'];
            component.searchEntityFilterEnabled = true;
            const longName = 'A'.repeat(1000);
            const entities: TestUserEntity[] = [{ id: 1, name: longName }];
            setTestEntities(entities);

            const text$ = new Subject<string>();
            component.onSearch(text$).subscribe();

            text$.next(longName);
            tick(250);

            expect(component.entities).toHaveLength(1);
        }));

        it('should handle entities with string IDs', () => {
            const entities: StringBaseEntity[] = [{ id: 'abc-123' }, { id: 'def-456' }];
            setTestEntities(entities);
            component.ngOnChanges({ allEntities: new SimpleChange([], component.allEntities, false) });

            expect(component.entities).toHaveLength(2);
        });
    });
});
