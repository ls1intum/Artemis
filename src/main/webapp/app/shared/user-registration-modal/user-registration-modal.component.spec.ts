import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AlertService } from 'app/shared/service/alert.service';
import { UserRegistrationModalComponent } from './user-registration-modal.component';
import { UserForRegistration } from './user-for-registration.model';

const makeUser = (id: number, isRegistered = false): UserForRegistration => ({
    id,
    login: `user${id}`,
    name: `User ${id}`,
    isRegistered,
});

const lazyEvent = { first: 0, rows: 10, filters: {}, globalFilter: null, multiSortMeta: undefined } as any;

describe('UserRegistrationModalComponent', () => {
    setupTestBed({ zoneless: true });

    let component: UserRegistrationModalComponent;
    let fixture: ComponentFixture<UserRegistrationModalComponent>;
    let mockAlertService: { error: ReturnType<typeof vi.fn> };
    let mockSearchFn: ReturnType<typeof vi.fn>;
    let mockRegisterFn: ReturnType<typeof vi.fn>;

    beforeEach(async () => {
        mockAlertService = { error: vi.fn() };
        mockSearchFn = vi.fn();
        mockRegisterFn = vi.fn();

        await TestBed.configureTestingModule({
            imports: [UserRegistrationModalComponent],
            providers: [{ provide: AlertService, useValue: mockAlertService }],
        })
            .overrideTemplate(UserRegistrationModalComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(UserRegistrationModalComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('titleKey', 'test.title');
        fixture.componentRef.setInput('searchFn', mockSearchFn);
        fixture.componentRef.setInput('registerFn', mockRegisterFn);

        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    describe('open / close', () => {
        it('should set isOpen to true', () => {
            component.open();
            expect(component.isOpen()).toBe(true);
        });

        it('should set isOpen to false and reset all state on close()', () => {
            component.isOpen.set(true);
            component.searchTerm.set('test');
            component.hasSearched.set(true);
            component.searchResults.set([makeUser(1)]);
            component.totalSearchResults.set(5);
            component.selectedUsers.set([makeUser(1)]);
            component.isViewingSelected.set(true);

            component.close();

            expect(component.isOpen()).toBe(false);
            expect(component.searchTerm()).toBe('');
            expect(component.hasSearched()).toBe(false);
            expect(component.searchResults()).toEqual([]);
            expect(component.totalSearchResults()).toBe(0);
            expect(component.selectedUsers()).toEqual([]);
            expect(component.isViewingSelected()).toBe(false);
        });
    });

    describe('onSearch', () => {
        beforeEach(() => {
            vi.useFakeTimers();
        });

        afterEach(() => {
            vi.useRealTimers();
        });

        it('should reset state when term is empty', () => {
            component.hasSearched.set(true);
            component.searchResults.set([makeUser(1)]);
            component.totalSearchResults.set(5);

            component.onSearch('');

            expect(component.hasSearched()).toBe(false);
            expect(component.searchResults()).toEqual([]);
            expect(component.totalSearchResults()).toBe(0);
        });

        it('should reset state when term is only whitespace', () => {
            component.hasSearched.set(true);
            component.onSearch('   ');
            expect(component.hasSearched()).toBe(false);
        });

        it('should trim the term and not trigger search before debounce fires', () => {
            component.onSearch('  alice  ');

            expect(component.searchTerm()).toBe('alice');
            expect(component.hasSearched()).toBe(false);
            expect(component.isLoading()).toBe(false);
        });

        it('should set hasSearched and isLoading after the debounce delay', () => {
            component.onSearch('  alice  ');

            vi.advanceTimersByTime(300);

            expect(component.hasSearched()).toBe(true);
            expect(component.isLoading()).toBe(true);
        });

        it('should debounce rapid calls and only trigger once for the last term', () => {
            component.onSearch('a');
            component.onSearch('al');
            component.onSearch('ali');
            component.onSearch('alice');

            vi.advanceTimersByTime(299);
            expect(component.isLoading()).toBe(false);

            vi.advanceTimersByTime(1);
            expect(component.isLoading()).toBe(true);
            expect(component.searchTerm()).toBe('alice');
        });
    });

    describe('onTableLazyLoad', () => {
        it('should do nothing when searchTerm is empty', () => {
            component.onTableLazyLoad(lazyEvent);
            expect(mockSearchFn).not.toHaveBeenCalled();
        });

        it('should call searchFn with correct args and update state on success', () => {
            const users = [makeUser(1), makeUser(2)];
            mockSearchFn.mockReturnValue(of({ content: users, totalElements: 2 }));
            component.searchTerm.set('alice');

            component.onTableLazyLoad(lazyEvent);

            expect(mockSearchFn).toHaveBeenCalledWith('alice', 0, 10);
            expect(component.searchResults()).toEqual(users);
            expect(component.totalSearchResults()).toBe(2);
            expect(component.isLoading()).toBe(false);
        });

        it('should show error alert and clear results on failure', () => {
            mockSearchFn.mockReturnValue(throwError(() => new Error('fail')));
            component.searchTerm.set('alice');

            component.onTableLazyLoad(lazyEvent);

            expect(mockAlertService.error).toHaveBeenCalledWith('userRegistrationModal.searchError');
            expect(component.searchResults()).toEqual([]);
            expect(component.totalSearchResults()).toBe(0);
            expect(component.isLoading()).toBe(false);
        });

        it('should discard stale responses from superseded requests', () => {
            let resolveFirst!: (v: any) => void;
            const first$ = new (require('rxjs').Observable)((obs: any) => {
                resolveFirst = (v) => {
                    obs.next(v);
                    obs.complete();
                };
            });
            const second$ = of({ content: [makeUser(2)], totalElements: 1 });

            mockSearchFn.mockReturnValueOnce(first$).mockReturnValueOnce(second$);
            component.searchTerm.set('alice');

            component.onTableLazyLoad(lazyEvent); // request 1 (pending)
            component.onTableLazyLoad(lazyEvent); // request 2 (resolves immediately)

            // Request 2 result is now live
            expect(component.searchResults()).toEqual([makeUser(2)]);

            // Resolving request 1 after request 2 should be ignored
            resolveFirst({ content: [makeUser(99)], totalElements: 1 });
            expect(component.searchResults()).toEqual([makeUser(2)]);
        });
    });

    describe('handleSelectionChange', () => {
        it('should ignore non-array input', () => {
            component.selectedUsers.set([makeUser(1)]);
            component.handleSelectionChange(makeUser(2) as any);
            expect(component.selectedUsers()).toEqual([makeUser(1)]);
        });

        it('should filter out already-registered users', () => {
            component.searchResults.set([makeUser(1), makeUser(2, true)]);
            component.handleSelectionChange([makeUser(1), makeUser(2, true)]);
            expect(component.selectedUsers()).toEqual([makeUser(1)]);
        });

        it('in search mode: preserves selections from previous pages', () => {
            const fromPreviousPage = makeUser(99);
            component.selectedUsers.set([fromPreviousPage]);
            component.searchResults.set([makeUser(1), makeUser(2)]);

            component.handleSelectionChange([makeUser(1)]);

            expect(component.selectedUsers()).toHaveLength(2);
            expect(component.selectedUsers()).toEqual(expect.arrayContaining([fromPreviousPage, makeUser(1)]));
        });

        it('in view mode: replaces selection directly', () => {
            component.isViewingSelected.set(true);
            component.selectedUsers.set([makeUser(1), makeUser(2)]);

            component.handleSelectionChange([makeUser(1)]);

            expect(component.selectedUsers()).toEqual([makeUser(1)]);
        });

        it('in view mode: exits view mode when all items are deselected', () => {
            component.isViewingSelected.set(true);
            component.selectedUsers.set([makeUser(1)]);

            component.handleSelectionChange([]);

            expect(component.selectedUsers()).toEqual([]);
            expect(component.isViewingSelected()).toBe(false);
        });
    });

    describe('toggleViewSelected', () => {
        it('should enter view mode', () => {
            component.toggleViewSelected();
            expect(component.isViewingSelected()).toBe(true);
        });

        it('should exit view mode', () => {
            component.isViewingSelected.set(true);
            component.toggleViewSelected();
            expect(component.isViewingSelected()).toBe(false);
        });
    });

    describe('clearSelection', () => {
        it('should empty selectedUsers', () => {
            component.selectedUsers.set([makeUser(1), makeUser(2)]);
            component.clearSelection();
            expect(component.selectedUsers()).toEqual([]);
        });

        it('should exit view mode', () => {
            component.isViewingSelected.set(true);
            component.clearSelection();
            expect(component.isViewingSelected()).toBe(false);
        });
    });

    describe('register', () => {
        it('should do nothing when no users are selected', () => {
            component.register();
            expect(mockRegisterFn).not.toHaveBeenCalled();
        });

        it('should call registerFn, emit registered, and close on success', () => {
            const registeredSpy = vi.fn();
            component.registered.subscribe(registeredSpy);
            mockRegisterFn.mockReturnValue(of(undefined));
            component.selectedUsers.set([makeUser(1)]);
            component.isOpen.set(true);

            component.register();

            expect(mockRegisterFn).toHaveBeenCalledWith([makeUser(1)]);
            expect(registeredSpy).toHaveBeenCalledOnce();
            expect(component.isOpen()).toBe(false);
        });

        it('should show error alert and clear loading on failure', () => {
            mockRegisterFn.mockReturnValue(throwError(() => new Error('fail')));
            component.selectedUsers.set([makeUser(1)]);

            component.register();

            expect(mockAlertService.error).toHaveBeenCalledWith('userRegistrationModal.registerError');
            expect(component.isLoading()).toBe(false);
        });
    });

    describe('computed: showTable', () => {
        it('should be false before any search', () => {
            expect(component.showTable()).toBe(false);
        });

        it('should be true when loading after a search', () => {
            component.hasSearched.set(true);
            component.isLoading.set(true);
            expect(component.showTable()).toBe(true);
        });

        it('should be true when results exist', () => {
            component.hasSearched.set(true);
            component.searchResults.set([makeUser(1)]);
            expect(component.showTable()).toBe(true);
        });

        it('should be true in view mode regardless of search state', () => {
            component.isViewingSelected.set(true);
            expect(component.showTable()).toBe(true);
        });
    });

    describe('computed: noResults', () => {
        it('should be false before any search', () => {
            expect(component.noResults()).toBe(false);
        });

        it('should be true after a search with no results', () => {
            component.hasSearched.set(true);
            expect(component.noResults()).toBe(true);
        });

        it('should be false in view mode even with empty search results', () => {
            component.hasSearched.set(true);
            component.isViewingSelected.set(true);
            expect(component.noResults()).toBe(false);
        });

        it('should be false while loading', () => {
            component.hasSearched.set(true);
            component.isLoading.set(true);
            expect(component.noResults()).toBe(false);
        });
    });

    describe('computed: tableOptions', () => {
        it('should be lazy and paginated in search mode', () => {
            const opts = component.tableOptions();
            expect(opts.lazy).toBe(true);
            expect(opts.paginated).toBe(true);
        });

        it('should be non-lazy and non-paginated in view mode', () => {
            component.isViewingSelected.set(true);
            const opts = component.tableOptions();
            expect(opts.lazy).toBe(false);
            expect(opts.paginated).toBe(false);
        });
    });

    describe('computed: selectedInCurrentResults', () => {
        it('should return only selected users present in the current search page', () => {
            component.searchResults.set([makeUser(1), makeUser(2), makeUser(3)]);
            component.selectedUsers.set([makeUser(1), makeUser(3), makeUser(99)]);

            expect(component.selectedInCurrentResults()).toEqual([makeUser(1), makeUser(3)]);
        });

        it('should return all selectedUsers in view mode', () => {
            component.isViewingSelected.set(true);
            component.selectedUsers.set([makeUser(1), makeUser(2)]);

            expect(component.selectedInCurrentResults()).toEqual([makeUser(1), makeUser(2)]);
        });
    });
});
