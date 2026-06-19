import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { User } from 'app/account/user/user.model';
import { UserService } from 'app/account/user/shared/user.service';
import { CourseGroup } from 'app/course/shared/entities/course.model';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import dayjs from 'dayjs/esm';
import { of, throwError } from 'rxjs';
import { EMAIL_KEY, NAME_KEY, REGISTRATION_NUMBER_KEY, USERNAME_KEY } from 'app/shared-ui/export/export-constants';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { CourseGroupComponent } from 'app/course/shared/course-group/course-group.component';
import { ExportUserInformationRow } from 'app/shared-ui/user-import/util/write-users-to-csv';
import * as csvUtils from 'app/shared-ui/user-import/util/write-users-to-csv';
import { AutoCompleteCompleteEvent } from 'primeng/autocomplete';

describe('CourseGroupComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: CourseGroupComponent;
    let fixture: ComponentFixture<CourseGroupComponent>;
    let userService: UserService;
    const courseGroup = CourseGroup.STUDENTS;
    const course = { id: 123, title: 'Course Title', isAtLeastInstructor: true, endDate: dayjs().subtract(5, 'minutes'), courseArchivePath: 'some-path' };
    const parentRoute = {
        data: of({ course }),
    } as any as ActivatedRoute;
    const route = { parent: parentRoute, params: of({ courseGroup }) } as any as ActivatedRoute;
    const courseGroupUser = new User(1, 'user');
    const courseGroupUser2 = new User(2, 'user2');

    beforeEach(async () => {
        TestBed.configureTestingModule({
            providers: [
                { provide: ActivatedRoute, useValue: route },
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        });
        await TestBed.compileComponents();
        fixture = TestBed.createComponent(CourseGroupComponent);
        comp = fixture.componentInstance;
        userService = TestBed.inject(UserService);
        // Set required inputs using ComponentRef
        fixture.componentRef.setInput('course', course);
        fixture.componentRef.setInput('courseGroup', courseGroup);
        fixture.componentRef.setInput('exportFileName', 'test-export');
        fixture.componentRef.setInput('userSearch', (searchTerm: string) => userService.search(searchTerm));
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(comp).not.toBeNull();
    });

    describe('onUserSearchComplete', () => {
        let searchStub: ReturnType<typeof vi.spyOn>;

        const makeEvent = (query: string): AutoCompleteCompleteEvent => ({ query, originalEvent: new Event('input') });

        beforeEach(() => {
            searchStub = vi.spyOn(userService, 'search');
        });

        it('should search users for given query and populate suggestions', () => {
            searchStub.mockReturnValue(of(new HttpResponse({ body: [courseGroupUser] })));

            comp.onUserSearchComplete(makeEvent('testLoginOrName'));

            expect(searchStub).toHaveBeenCalledWith('testLoginOrName');
            expect(searchStub).toHaveBeenCalledOnce();
            expect(comp.userSuggestions()).toEqual([courseGroupUser]);
            expect(comp.isSearching()).toBe(false);
        });

        it('should return empty suggestions when the server returns no users', () => {
            searchStub.mockReturnValue(of(new HttpResponse({ body: [] })));

            comp.onUserSearchComplete(makeEvent('testLoginOrName'));

            expect(comp.userSuggestions()).toEqual([]);
        });

        it('should return empty suggestions when query is shorter than three characters', () => {
            searchStub.mockReturnValue(of(new HttpResponse({ body: [courseGroupUser] })));

            comp.onUserSearchComplete(makeEvent('ab'));

            expect(searchStub).not.toHaveBeenCalled();
            expect(comp.userSuggestions()).toEqual([]);
        });

        it('should update filterQuery with the search query', () => {
            searchStub.mockReturnValue(of(new HttpResponse({ body: [courseGroupUser] })));

            comp.onUserSearchComplete(makeEvent('testUser'));

            expect(comp['filterQuery']()).toBe('testUser');
        });

        it('should update filterQuery even for short queries below minLength', () => {
            comp.onUserSearchComplete(makeEvent('ab'));

            expect(comp['filterQuery']()).toBe('ab');
        });

        it('should set searchFailed and return empty suggestions when the search throws', () => {
            searchStub.mockReturnValue(throwError(() => new Error('')));

            comp.onUserSearchComplete(makeEvent('testLoginOrName'));

            expect(comp.userSuggestions()).toEqual([]);
            expect(comp.searchFailed()).toBe(true);
            expect(searchStub).toHaveBeenCalledWith('testLoginOrName');
        });

        it('should reset searchFailed before each new search', () => {
            comp.searchFailed.set(true);
            searchStub.mockReturnValue(of(new HttpResponse({ body: [courseGroupUser] })));

            comp.onUserSearchComplete(makeEvent('testUser'));

            expect(comp.searchFailed()).toBe(false);
        });

        it('should set isSearching to false after the search completes', () => {
            searchStub.mockReturnValue(of(new HttpResponse({ body: [courseGroupUser] })));

            comp.onUserSearchComplete(makeEvent('testUser'));

            expect(comp.isSearching()).toBe(false);
        });
    });

    describe('onUserSelect', () => {
        beforeEach(() => {
            comp.allGroupUsers.set([]);
            fixture.componentRef.setInput('addUserToGroup', () => of(new HttpResponse<void>()));
        });

        it('should add the selected user to the course group', () => {
            comp.onUserSelect(courseGroupUser);

            expect(comp.allGroupUsers()).toEqual([courseGroupUser]);
        });

        it('should not add a user who is already in the group', () => {
            comp.allGroupUsers.set([courseGroupUser]);

            comp.onUserSelect(courseGroupUser);

            expect(comp.allGroupUsers()).toEqual([courseGroupUser]);
        });

        it('should not add a user who has no login', () => {
            const userWithoutLogin = { ...courseGroupUser };
            delete userWithoutLogin.login;

            comp.onUserSelect(userWithoutLogin);

            expect(comp.allGroupUsers()).toEqual([]);
        });

        it('should reset isTransitioning to false on error', () => {
            fixture.componentRef.setInput('addUserToGroup', () => throwError(() => new Error('Add failed')));

            comp.onUserSelect(courseGroupUser);

            expect(comp.isTransitioning()).toBe(false);
            expect(comp.allGroupUsers()).toEqual([]);
        });

        it('should set filterQuery to the selected user login so the new member stays visible', () => {
            comp.onUserSelect(courseGroupUser);

            expect(comp['filterQuery']()).toBe(courseGroupUser.login);
        });

        it('should clear userSuggestions after selection', () => {
            comp.userSuggestions.set([courseGroupUser, courseGroupUser2]);

            comp.onUserSelect(courseGroupUser);

            expect(comp.userSuggestions()).toEqual([]);
        });

        it('should set filterQuery to empty string when selected user has no login', () => {
            const userWithoutLogin = { ...courseGroupUser };
            delete userWithoutLogin.login;

            comp.onUserSelect(userWithoutLogin);

            expect(comp['filterQuery']()).toBe('');
        });
    });

    describe('isAlreadyMember', () => {
        it('should return true when the user is in the group', () => {
            comp.allGroupUsers.set([courseGroupUser]);

            expect(comp.isAlreadyMember(courseGroupUser)).toBe(true);
        });

        it('should return false when the user is not in the group', () => {
            comp.allGroupUsers.set([courseGroupUser]);

            expect(comp.isAlreadyMember(courseGroupUser2)).toBe(false);
        });
    });

    describe('filteredGroupUsers', () => {
        const alice = { ...courseGroupUser, id: 1, login: 'alice', name: 'Alice Smith', email: 'alice@example.com', visibleRegistrationNumber: '12345' };
        const bob = { ...courseGroupUser2, id: 2, login: 'bob', name: 'Bob Jones', email: 'bob@example.com', visibleRegistrationNumber: '67890' };

        beforeEach(() => {
            comp.allGroupUsers.set([alice, bob]);
        });

        it('should return all users when filterQuery is empty', () => {
            expect(comp.filteredGroupUsers()).toHaveLength(2);
        });

        it('should filter by login', () => {
            comp['filterQuery'].set('alice');

            expect(comp.filteredGroupUsers()).toHaveLength(1);
            expect(comp.filteredGroupUsers()[0].login).toBe('alice');
        });

        it('should filter by name', () => {
            comp['filterQuery'].set('jones');

            expect(comp.filteredGroupUsers()).toHaveLength(1);
            expect(comp.filteredGroupUsers()[0].login).toBe('bob');
        });

        it('should filter by email', () => {
            comp['filterQuery'].set('alice@example');

            expect(comp.filteredGroupUsers()).toHaveLength(1);
            expect(comp.filteredGroupUsers()[0].login).toBe('alice');
        });

        it('should filter by visibleRegistrationNumber', () => {
            comp['filterQuery'].set('67890');

            expect(comp.filteredGroupUsers()).toHaveLength(1);
            expect(comp.filteredGroupUsers()[0].login).toBe('bob');
        });

        it('should be case insensitive', () => {
            comp['filterQuery'].set('ALICE');

            expect(comp.filteredGroupUsers()).toHaveLength(1);
            expect(comp.filteredGroupUsers()[0].login).toBe('alice');
        });

        it('should return empty array when no user matches', () => {
            comp['filterQuery'].set('zzz');

            expect(comp.filteredGroupUsers()).toHaveLength(0);
        });
    });

    describe('onSearchKeyUp', () => {
        it('should update filterQuery from the event target value', () => {
            const input = document.createElement('input');
            input.value = 'ali';
            const event = new KeyboardEvent('keyup');
            Object.defineProperty(event, 'target', { value: input });

            comp.onSearchKeyUp(event);

            expect(comp['filterQuery']()).toBe('ali');
        });

        it('should set filterQuery to empty string when input is cleared via backspace', () => {
            comp['filterQuery'].set('alice');
            const input = document.createElement('input');
            input.value = '';
            const event = new KeyboardEvent('keyup');
            Object.defineProperty(event, 'target', { value: input });

            comp.onSearchKeyUp(event);

            expect(comp['filterQuery']()).toBe('');
        });
    });

    describe('onSearchClear', () => {
        it('should reset filterQuery to empty string', () => {
            comp['filterQuery'].set('some-query');

            comp.onSearchClear();

            expect(comp['filterQuery']()).toBe('');
        });

        it('should clear userSuggestions', () => {
            comp.userSuggestions.set([courseGroupUser]);

            comp.onSearchClear();

            expect(comp.userSuggestions()).toEqual([]);
        });
    });

    describe('removeFromGroup', () => {
        beforeEach(() => {
            comp.allGroupUsers.set([courseGroupUser, courseGroupUser2]);
            fixture.componentRef.setInput('removeUserFromGroup', () => of(new HttpResponse<void>()));
        });

        it('should remove given user from group', () => {
            comp.removeFromGroup(courseGroupUser);
            expect(comp.allGroupUsers()).toEqual([courseGroupUser2]);
        });

        it('should not do anything if users has no login', () => {
            const user = { ...courseGroupUser };
            delete user.login;
            const originalUsers = [...comp.allGroupUsers()];
            comp.removeFromGroup(user);
            expect(comp.allGroupUsers()).toEqual(originalUsers);
        });

        it('should handle error when removing user from group', () => {
            fixture.componentRef.setInput('removeUserFromGroup', () => throwError(() => new Error('Remove failed')));
            const originalUsers = [...comp.allGroupUsers()];
            comp.removeFromGroup(courseGroupUser);
            // Users should not be removed on error
            expect(comp.allGroupUsers()).toEqual(originalUsers);
        });
    });

    it('should generate csv correctly', () => {
        comp.allGroupUsers.set([courseGroupUser, courseGroupUser2]);
        const exportUserInformationAsCsvMock = vi.spyOn(csvUtils, 'exportUserInformationAsCsv').mockImplementation(() => {});

        comp.exportUserInformation();

        expect(exportUserInformationAsCsvMock).toHaveBeenCalledOnce();
        const generatedRows = exportUserInformationAsCsvMock.mock.calls[0][0];

        const expectedRow1 = {} as ExportUserInformationRow;
        expectedRow1[NAME_KEY] = '';
        expectedRow1[USERNAME_KEY] = courseGroupUser.login ?? '';
        expectedRow1[EMAIL_KEY] = '';
        expectedRow1[REGISTRATION_NUMBER_KEY] = '';

        const expectedRow2 = {} as ExportUserInformationRow;
        expectedRow2[NAME_KEY] = '';
        expectedRow2[USERNAME_KEY] = courseGroupUser2.login ?? '';
        expectedRow2[EMAIL_KEY] = '';
        expectedRow2[REGISTRATION_NUMBER_KEY] = '';

        const expectedRows = [expectedRow1, expectedRow2];

        expect(generatedRows).toEqual(expectedRows);
    });

    it('should not export csv when there are no users', () => {
        comp.allGroupUsers.set([]);
        const exportUserInformationAsCsvMock = vi.spyOn(csvUtils, 'exportUserInformationAsCsv').mockImplementation(() => {});

        comp.exportUserInformation();

        expect(exportUserInformationAsCsvMock).not.toHaveBeenCalled();
    });

    describe('dataTableRowClass', () => {
        it('should return empty string by default', () => {
            expect(comp.dataTableRowClass()).toBe('');
        });

        it('should return the current row class', () => {
            comp['rowClass'].set('test-class');
            expect(comp.dataTableRowClass()).toBe('test-class');
        });
    });

    describe('flashRowClass', () => {
        beforeEach(() => {
            vi.useFakeTimers();
        });

        afterEach(() => {
            vi.useRealTimers();
        });

        it('should set and then clear the row class', () => {
            comp.flashRowClass('flash-class');
            expect(comp['rowClass']()).toBe('flash-class');

            vi.runAllTimers();
            expect(comp['rowClass']()).toBe('');
        });
    });

    describe('exportUserInformationAsCsv', () => {
        it('should call csv export utility with rows, keys, and filename', () => {
            const rows: ExportUserInformationRow[] = [{ [NAME_KEY]: 'Test', [USERNAME_KEY]: 'user', [EMAIL_KEY]: 'test@test.com', [REGISTRATION_NUMBER_KEY]: '123' }];
            const keys = [NAME_KEY, USERNAME_KEY, EMAIL_KEY, REGISTRATION_NUMBER_KEY];
            const exportUserInformationAsCsvMock = vi.spyOn(csvUtils, 'exportUserInformationAsCsv').mockImplementation(() => {});

            comp.allGroupUsers.set([{ ...courseGroupUser, name: 'Test', email: 'test@test.com', visibleRegistrationNumber: '123' }]);
            comp.exportUserInformation();

            expect(exportUserInformationAsCsvMock).toHaveBeenCalledOnce();
            expect(exportUserInformationAsCsvMock).toHaveBeenCalledWith(rows, keys, 'test-export');
        });
    });

    describe('exportUserInformation with various user properties', () => {
        it('should handle users with all properties defined', () => {
            const userWithAllProps = {
                ...courseGroupUser,
                name: '  Test Name  ',
                email: '  test@example.com  ',
                visibleRegistrationNumber: '  12345  ',
            };
            comp.allGroupUsers.set([userWithAllProps]);
            const exportUserInformationAsCsvMock = vi.spyOn(csvUtils, 'exportUserInformationAsCsv').mockImplementation(() => {});

            comp.exportUserInformation();

            expect(exportUserInformationAsCsvMock).toHaveBeenCalledOnce();
            const generatedRows = exportUserInformationAsCsvMock.mock.calls[0][0];

            // Should trim all values
            expect(generatedRows[0][NAME_KEY]).toBe('Test Name');
            expect(generatedRows[0][EMAIL_KEY]).toBe('test@example.com');
            expect(generatedRows[0][REGISTRATION_NUMBER_KEY]).toBe('12345');
        });

        it('should handle users with undefined name', () => {
            const userWithUndefinedName = { ...courseGroupUser };
            delete (userWithUndefinedName as any).name;
            comp.allGroupUsers.set([userWithUndefinedName]);
            const exportUserInformationAsCsvMock = vi.spyOn(csvUtils, 'exportUserInformationAsCsv').mockImplementation(() => {});

            comp.exportUserInformation();

            expect(exportUserInformationAsCsvMock).toHaveBeenCalledOnce();
            const generatedRows = exportUserInformationAsCsvMock.mock.calls[0][0];

            // Should default to empty string
            expect(generatedRows[0][NAME_KEY]).toBe('');
        });

        it('should handle users with undefined email', () => {
            const userWithUndefinedEmail = { ...courseGroupUser, name: 'Test' };
            delete (userWithUndefinedEmail as any).email;
            comp.allGroupUsers.set([userWithUndefinedEmail]);
            const exportUserInformationAsCsvMock = vi.spyOn(csvUtils, 'exportUserInformationAsCsv').mockImplementation(() => {});

            comp.exportUserInformation();

            expect(exportUserInformationAsCsvMock).toHaveBeenCalledOnce();
            const generatedRows = exportUserInformationAsCsvMock.mock.calls[0][0];

            // Should default to empty string
            expect(generatedRows[0][EMAIL_KEY]).toBe('');
        });

        it('should handle users with undefined visibleRegistrationNumber', () => {
            const userWithUndefinedRegNum = { ...courseGroupUser, name: 'Test' };
            delete (userWithUndefinedRegNum as any).visibleRegistrationNumber;
            comp.allGroupUsers.set([userWithUndefinedRegNum]);
            const exportUserInformationAsCsvMock = vi.spyOn(csvUtils, 'exportUserInformationAsCsv').mockImplementation(() => {});

            comp.exportUserInformation();

            expect(exportUserInformationAsCsvMock).toHaveBeenCalledOnce();
            const generatedRows = exportUserInformationAsCsvMock.mock.calls[0][0];

            // Should default to empty string
            expect(generatedRows[0][REGISTRATION_NUMBER_KEY]).toBe('');
        });

        it('should handle users with undefined login', () => {
            const userWithUndefinedLogin = { ...courseGroupUser, name: 'Test' };
            delete (userWithUndefinedLogin as any).login;
            comp.allGroupUsers.set([userWithUndefinedLogin]);
            const exportUserInformationAsCsvMock = vi.spyOn(csvUtils, 'exportUserInformationAsCsv').mockImplementation(() => {});

            comp.exportUserInformation();

            expect(exportUserInformationAsCsvMock).toHaveBeenCalledOnce();
            const generatedRows = exportUserInformationAsCsvMock.mock.calls[0][0];

            // Should default to empty string
            expect(generatedRows[0][USERNAME_KEY]).toBe('');
        });
    });
});
