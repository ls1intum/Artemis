import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { User } from 'app/core/user/user.model';
import { UserService } from 'app/core/user/shared/user.service';
import { CourseGroup } from 'app/core/course/shared/entities/course.model';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import dayjs from 'dayjs/esm';
import { Observable, of, throwError } from 'rxjs';
import { EMAIL_KEY, NAME_KEY, REGISTRATION_NUMBER_KEY, USERNAME_KEY } from 'app/shared/export/export-constants';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { CourseGroupComponent, GroupUserInformationRow } from 'app/core/course/shared/course-group/course-group.component';

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

    describe('searchAllUsers', () => {
        let loginOrName: string;
        let loginStream: Observable<{ text: string; entities: User[] }>;
        let searchStub: ReturnType<typeof vi.spyOn>;

        beforeEach(() => {
            loginOrName = 'testLoginOrName';
            loginStream = of({ text: loginOrName, entities: [] });
            searchStub = vi.spyOn(userService, 'search');
        });

        it('should search users for given login or name', () => {
            searchStub.mockReturnValue(of(new HttpResponse({ body: [courseGroupUser] })));
            comp.searchAllUsers(loginStream).subscribe((users: any) => {
                expect(users).toEqual([courseGroupUser]);
            });
            expect(searchStub).toHaveBeenCalledWith(loginOrName);
            expect(searchStub).toHaveBeenCalledOnce();
        });

        it('should set search no results if search returns no result', () => {
            searchStub.mockReturnValue(of(new HttpResponse({ body: [] })));
            comp.searchAllUsers(loginStream).subscribe((users: any) => {
                expect(users).toEqual([]);
            });
            expect(comp.searchNoResults()).toBe(true);
            expect(searchStub).toHaveBeenCalledWith(loginOrName);
            expect(searchStub).toHaveBeenCalledOnce();
        });

        it('should return empty if search text is shorter than three characters', () => {
            loginStream = of({ text: 'ab', entities: [] });
            searchStub.mockReturnValue(of(new HttpResponse({ body: [courseGroupUser] })));
            comp.searchAllUsers(loginStream).subscribe((users: any) => {
                expect(users).toEqual([]);
            });
            expect(searchStub).not.toHaveBeenCalled();
        });

        it('should return empty if search fails', () => {
            searchStub.mockReturnValue(throwError(() => new Error('')));
            comp.searchAllUsers(loginStream).subscribe((users: any) => {
                expect(users).toEqual([]);
            });
            expect(comp.searchFailed()).toBe(true);
            expect(searchStub).toHaveBeenCalledWith(loginOrName);
            expect(searchStub).toHaveBeenCalledOnce();
        });
    });

    describe('onAutocompleteSelect', () => {
        let user: User;

        beforeEach(() => {
            user = courseGroupUser;
            comp.allGroupUsers.set([]);
            fixture.componentRef.setInput('addUserToGroup', () => of(new HttpResponse<void>()));
        });

        it('should add the selected user to course group', () => {
            const fake = vi.fn();
            comp.onAutocompleteSelect(user, fake);
            expect(comp.allGroupUsers()).toEqual([courseGroupUser]);
            expect(fake).toHaveBeenCalledWith(user);
            expect(fake).toHaveBeenCalledOnce();
        });

        it('should call callback if user is already in the group', () => {
            const fake = vi.fn();
            comp.allGroupUsers.set([user]);
            comp.onAutocompleteSelect(user, fake);
            expect(comp.allGroupUsers()).toEqual([courseGroupUser]);
            expect(fake).toHaveBeenCalledWith(user);
            expect(fake).toHaveBeenCalledOnce();
        });

        it('should handle error when adding user to group', () => {
            fixture.componentRef.setInput('addUserToGroup', () => throwError(() => new Error('Add failed')));
            const fake = vi.fn();
            comp.onAutocompleteSelect(user, fake);
            expect(comp.isTransitioning()).toBe(false);
            expect(fake).not.toHaveBeenCalled();
        });

        it('should not add user without login', () => {
            const userWithoutLogin = { ...courseGroupUser };
            delete userWithoutLogin.login;
            const fake = vi.fn();
            comp.onAutocompleteSelect(userWithoutLogin, fake);
            expect(fake).toHaveBeenCalledWith(userWithoutLogin);
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

    describe('searchResultFormatter', () => {
        it('should format user info into appropriate format', () => {
            const name = 'testName';
            const user = { ...courseGroupUser, name };
            expect(comp.searchResultFormatter(user)).toBe(`${name} (${user.login})`);
        });
    });

    describe('searchTextFromUser', () => {
        it('converts a user to a string that can be searched for', () => {
            const user = courseGroupUser;
            expect(comp.searchTextFromUser(user)).toBe(user.login);
        });

        it('should return empty string if user does not have login', () => {
            const user = { ...courseGroupUser };
            delete user.login;
            expect(comp.searchTextFromUser(user)).toBe('');
        });
    });

    it('should generate csv correctly', () => {
        comp.allGroupUsers.set([courseGroupUser, courseGroupUser2]);
        const exportAsCsvMock = vi.spyOn(comp, 'exportAsCsv').mockImplementation(() => {});

        comp.exportUserInformation();

        expect(exportAsCsvMock).toHaveBeenCalledOnce();
        const generatedRows = exportAsCsvMock.mock.calls[0][0];

        const expectedRow1 = {} as GroupUserInformationRow;
        expectedRow1[NAME_KEY] = '';
        expectedRow1[USERNAME_KEY] = courseGroupUser.login ?? '';
        expectedRow1[EMAIL_KEY] = '';
        expectedRow1[REGISTRATION_NUMBER_KEY] = '';

        const expectedRow2 = {} as GroupUserInformationRow;
        expectedRow2[NAME_KEY] = '';
        expectedRow2[USERNAME_KEY] = courseGroupUser2.login ?? '';
        expectedRow2[EMAIL_KEY] = '';
        expectedRow2[REGISTRATION_NUMBER_KEY] = '';

        const expectedRows = [expectedRow1, expectedRow2];

        expect(generatedRows).toEqual(expectedRows);
    });

    it('should not export csv when there are no users', () => {
        comp.allGroupUsers.set([]);
        const exportAsCsvMock = vi.spyOn(comp, 'exportAsCsv').mockImplementation(() => {});

        comp.exportUserInformation();

        expect(exportAsCsvMock).not.toHaveBeenCalled();
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

    describe('exportAsCsv', () => {
        it('should call csv export functions', () => {
            const rows: GroupUserInformationRow[] = [{ [NAME_KEY]: 'Test', [USERNAME_KEY]: 'test', [EMAIL_KEY]: 'test@test.com', [REGISTRATION_NUMBER_KEY]: '123' }];
            const keys = [NAME_KEY, USERNAME_KEY, EMAIL_KEY, REGISTRATION_NUMBER_KEY];

            // Mock URL.createObjectURL since it's not available in test environment
            const createObjectURLMock = vi.fn().mockReturnValue('blob:mock-url');
            global.URL.createObjectURL = createObjectURLMock;

            // The function should not throw
            expect(() => comp.exportAsCsv(rows, keys)).not.toThrow();
        });
    });

    describe('isSearching state', () => {
        it('should set isSearching during search', () => {
            const loginOrName = 'testUser';
            const loginStream = of({ text: loginOrName, entities: [] });
            vi.spyOn(userService, 'search').mockReturnValue(of(new HttpResponse({ body: [courseGroupUser] })));

            // Subscribe to trigger the search
            comp.searchAllUsers(loginStream).subscribe();

            // After search completes, isSearching should be false
            expect(comp.isSearching()).toBe(false);
        });
    });

    describe('searchAllUsers setTimeout callback', () => {
        beforeEach(() => {
            vi.useFakeTimers();
        });

        afterEach(() => {
            vi.useRealTimers();
        });

        it('should handle when dataTable is undefined', () => {
            const loginOrName = 'testUser';
            const loginStream = of({ text: loginOrName, entities: [] });
            vi.spyOn(userService, 'search').mockReturnValue(of(new HttpResponse({ body: [courseGroupUser] })));

            // dataTable() returns undefined by default since we haven't set up the view
            comp.searchAllUsers(loginStream).subscribe();

            // Run the setTimeout callback - should not throw when dataTable is undefined
            expect(() => vi.runAllTimers()).not.toThrow();
        });

        it('should process typeahead buttons when dataTable exists', () => {
            const loginOrName = 'testUser';
            const loginStream = of({ text: loginOrName, entities: [] });
            const returnedUser = { ...courseGroupUser, id: 1 };
            vi.spyOn(userService, 'search').mockReturnValue(of(new HttpResponse({ body: [returnedUser] })));

            // Mock the dataTable with typeahead buttons
            const mockButton = document.createElement('button');
            const mockDataTable = {
                typeaheadButtons: [mockButton],
            };
            vi.spyOn(comp as any, 'dataTable').mockReturnValue(mockDataTable);

            // User is not in group - should add 'users-plus' icon
            comp.allGroupUsers.set([]);
            comp.searchAllUsers(loginStream).subscribe();
            vi.runAllTimers();

            // Button should have had HTML inserted (users-plus icon)
            expect(mockButton.innerHTML).toContain('fa-icon');
        });

        it('should add alreadyMember class when user is already in group', () => {
            const loginOrName = 'testUser';
            const loginStream = of({ text: loginOrName, entities: [] });
            const returnedUser = { ...courseGroupUser, id: 1 };
            vi.spyOn(userService, 'search').mockReturnValue(of(new HttpResponse({ body: [returnedUser] })));

            // Mock the dataTable with typeahead buttons
            const mockButton = document.createElement('button');
            const mockDataTable = {
                typeaheadButtons: [mockButton],
            };
            vi.spyOn(comp as any, 'dataTable').mockReturnValue(mockDataTable);

            // User is already in group
            comp.allGroupUsers.set([returnedUser]);
            comp.searchAllUsers(loginStream).subscribe();
            vi.runAllTimers();

            // Button should have the already-member class
            expect(mockButton.classList.contains('already-member')).toBe(true);
        });

        it('should not insert icon HTML when button already has icon', () => {
            const loginOrName = 'testUser';
            const loginStream = of({ text: loginOrName, entities: [] });
            const returnedUser = { ...courseGroupUser, id: 1 };
            vi.spyOn(userService, 'search').mockReturnValue(of(new HttpResponse({ body: [returnedUser] })));

            // Mock the dataTable with a button that already has an fa-icon
            const mockButton = document.createElement('button');
            const existingIcon = document.createElement('fa-icon');
            mockButton.appendChild(existingIcon);
            const originalHTML = mockButton.innerHTML;

            const mockDataTable = {
                typeaheadButtons: [mockButton],
            };
            vi.spyOn(comp as any, 'dataTable').mockReturnValue(mockDataTable);

            comp.allGroupUsers.set([]);
            comp.searchAllUsers(loginStream).subscribe();
            vi.runAllTimers();

            // Button should not have additional HTML inserted since it already has an icon
            expect(mockButton.innerHTML).toBe(originalHTML);
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
            const exportAsCsvMock = vi.spyOn(comp, 'exportAsCsv').mockImplementation(() => {});

            comp.exportUserInformation();

            expect(exportAsCsvMock).toHaveBeenCalledOnce();
            const generatedRows = exportAsCsvMock.mock.calls[0][0];

            // Should trim all values
            expect(generatedRows[0][NAME_KEY]).toBe('Test Name');
            expect(generatedRows[0][EMAIL_KEY]).toBe('test@example.com');
            expect(generatedRows[0][REGISTRATION_NUMBER_KEY]).toBe('12345');
        });

        it('should handle users with undefined name', () => {
            const userWithUndefinedName = { ...courseGroupUser };
            delete (userWithUndefinedName as any).name;
            comp.allGroupUsers.set([userWithUndefinedName]);
            const exportAsCsvMock = vi.spyOn(comp, 'exportAsCsv').mockImplementation(() => {});

            comp.exportUserInformation();

            expect(exportAsCsvMock).toHaveBeenCalledOnce();
            const generatedRows = exportAsCsvMock.mock.calls[0][0];

            // Should default to empty string
            expect(generatedRows[0][NAME_KEY]).toBe('');
        });

        it('should handle users with undefined email', () => {
            const userWithUndefinedEmail = { ...courseGroupUser, name: 'Test' };
            delete (userWithUndefinedEmail as any).email;
            comp.allGroupUsers.set([userWithUndefinedEmail]);
            const exportAsCsvMock = vi.spyOn(comp, 'exportAsCsv').mockImplementation(() => {});

            comp.exportUserInformation();

            expect(exportAsCsvMock).toHaveBeenCalledOnce();
            const generatedRows = exportAsCsvMock.mock.calls[0][0];

            // Should default to empty string
            expect(generatedRows[0][EMAIL_KEY]).toBe('');
        });

        it('should handle users with undefined visibleRegistrationNumber', () => {
            const userWithUndefinedRegNum = { ...courseGroupUser, name: 'Test' };
            delete (userWithUndefinedRegNum as any).visibleRegistrationNumber;
            comp.allGroupUsers.set([userWithUndefinedRegNum]);
            const exportAsCsvMock = vi.spyOn(comp, 'exportAsCsv').mockImplementation(() => {});

            comp.exportUserInformation();

            expect(exportAsCsvMock).toHaveBeenCalledOnce();
            const generatedRows = exportAsCsvMock.mock.calls[0][0];

            // Should default to empty string
            expect(generatedRows[0][REGISTRATION_NUMBER_KEY]).toBe('');
        });

        it('should handle users with undefined login', () => {
            const userWithUndefinedLogin = { ...courseGroupUser, name: 'Test' };
            delete (userWithUndefinedLogin as any).login;
            comp.allGroupUsers.set([userWithUndefinedLogin]);
            const exportAsCsvMock = vi.spyOn(comp, 'exportAsCsv').mockImplementation(() => {});

            comp.exportUserInformation();

            expect(exportAsCsvMock).toHaveBeenCalledOnce();
            const generatedRows = exportAsCsvMock.mock.calls[0][0];

            // Should default to empty string
            expect(generatedRows[0][USERNAME_KEY]).toBe('');
        });
    });
});
