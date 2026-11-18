import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, DebugElement, viewChild } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { By } from '@angular/platform-browser';
import { Subject, of, throwError } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { CourseUsersSelectorComponent, SearchRoleGroup } from 'app/communication/course-users-selector/course-users-selector.component';
import { UserPublicInfoDTO } from 'app/account/user/user.model';

import { provideTranslateService } from '@ngx-translate/core';
import { ProfilePictureComponent } from 'app/shared-ui/profile-picture/profile-picture.component';

@Component({
    template: ` <jhi-course-users-selector [courseId]="courseId" [rolesToAllowSearchingIn]="rolesToAllowSearchingIn" [multiSelect]="multiSelect" [showUserList]="showUserList" />`,
    imports: [CourseUsersSelectorComponent],
})
class WrapperComponent {
    readonly courseUsersSelectorComponent = viewChild(CourseUsersSelectorComponent);
    searchInput = '';
    disabled = false;
    courseId = 1;
    rolesToAllowSearchingIn: SearchRoleGroup[] = ['tutors', 'students', 'instructors'];
    multiSelect = true;
    showUserList = true;
    public selectedUsers: UserPublicInfoDTO[] | undefined | null = [];
}

describe('CourseUsersSelectorComponent', () => {
    let wrapperComponent: WrapperComponent;
    let fixture: ComponentFixture<WrapperComponent>;
    let userSelectorComponent: CourseUsersSelectorComponent;
    let userSelectorDebugElement: DebugElement;
    const courseManagementServiceMock = { searchUsers: vi.fn() } as unknown as CourseManagementService;

    afterEach(() => {
        vi.useRealTimers();
        vi.clearAllMocks();
        fixture.destroy();
        document.querySelectorAll('.cdk-overlay-container').forEach((element) => element.remove());
    });

    beforeEach(async () => {
        vi.useFakeTimers();
        TestBed.configureTestingModule({
            imports: [CommonModule, FormsModule, ReactiveFormsModule],
            providers: [{ provide: CourseManagementService, useValue: courseManagementServiceMock }, provideTranslateService()],
        });
        fixture = TestBed.createComponent(WrapperComponent);
        wrapperComponent = fixture.componentInstance;
        fixture.detectChanges();

        userSelectorDebugElement = fixture.debugElement.query(By.directive(CourseUsersSelectorComponent));
        userSelectorComponent = userSelectorDebugElement.componentInstance;
    });

    it('should create', () => {
        expect(wrapperComponent).toBeTruthy();
    });

    it('should have host class', () => {
        const courseUsersSelector = fixture.debugElement.query(By.css('jhi-course-users-selector'));
        expect(courseUsersSelector).toBeTruthy();
        expect(courseUsersSelector.nativeElement.classList).toContain('course-users-selector');
    });

    const testCases = [
        {
            multiSelect: true,
        },
        {
            multiSelect: false,
        },
    ];

    testCases.forEach((testCase) => {
        it('changing connected wrapper should update the component property', () => {
            const exampleUserPublicInfoDTO = generateExampleUserPublicInfoDTO({});
            userSelectorComponent.selectedUsers.set([exampleUserPublicInfoDTO]);
            wrapperComponent.multiSelect = testCase.multiSelect;
            fixture.changeDetectorRef.detectChanges();
            vi.advanceTimersByTime(0);
            expect(userSelectorComponent.selectedUsers()).toEqual([exampleUserPublicInfoDTO]);
            expect(fixture.debugElement.queryAll(By.css('.selected-user'))).toHaveLength(1);
        });

        it('should convert undefined to empty array', () => {
            userSelectorComponent.selectedUsers.set([]);
            wrapperComponent.multiSelect = testCase.multiSelect;
            fixture.changeDetectorRef.detectChanges();
            vi.advanceTimersByTime(0);
            expect(userSelectorComponent.selectedUsers()).toEqual([]);
        });

        it('searching, selecting and deleting a user should update the selectedUsers property', () => {
            userSelectorComponent.selectedUsers.set([]);
            wrapperComponent.multiSelect = testCase.multiSelect;
            const user = generateExampleUserPublicInfoDTO({});
            const searchResponse: HttpResponse<UserPublicInfoDTO[]> = new HttpResponse({
                body: [user],
                status: 200,
            });
            const searchStub = vi.spyOn(courseManagementServiceMock, 'searchUsers').mockReturnValue(of(searchResponse));

            // searching for a user
            changeInput(fixture.debugElement.nativeElement, 'test');
            vi.advanceTimersByTime(1000);
            fixture.changeDetectorRef.detectChanges();
            expect(searchStub).toHaveBeenCalledOnce();
            expect(searchStub).toHaveBeenCalledWith(1, 'test', ['students', 'tutors', 'instructors']);
            expectDropdownItems(['MHMortimer of Sto Helit (mort)']);
            // selecting the user in the dropdown
            getDropdownOptions()[0].click();
            fixture.changeDetectorRef.detectChanges();
            vi.advanceTimersByTime(0);
            expect(userSelectorComponent.selectedUsers()).toEqual([user]);
            // the search input is empty again and the dropdown is closed
            expect(getNativeInput(fixture.debugElement.nativeElement).value).toBe('');
            expect(getDropdownOptions()).toHaveLength(0);

            // now we delete the user again from the selected users
            expect(fixture.debugElement.queryAll(By.css('.selected-user'))).toHaveLength(1);
            const deleteButton = fixture.debugElement.query(By.css('.delete-user'));
            deleteButton.triggerEventHandler('click', {});
            fixture.changeDetectorRef.detectChanges();
            vi.advanceTimersByTime(0);
            expect(userSelectorComponent.selectedUsers()).toEqual([]);
            expect(wrapperComponent.selectedUsers).toEqual([]);
        });

        it('should block the input field and not show delete button', () => {
            wrapperComponent.multiSelect = testCase.multiSelect;
            const exampleUserPublicInfoDTO = generateExampleUserPublicInfoDTO({});
            userSelectorComponent.selectedUsers.set([exampleUserPublicInfoDTO]);
            userSelectorComponent.disabled.set(true);
            fixture.changeDetectorRef.detectChanges();
            vi.advanceTimersByTime(1000);
            expect(userSelectorComponent.selectedUsers()).toEqual([exampleUserPublicInfoDTO]);
            expect(fixture.debugElement.query(By.css('.delete-user'))).toBeFalsy();
        });

        it('should render profile picture for users in dropdown', () => {
            const user = generateExampleUserPublicInfoDTO({});
            const searchResponse = new HttpResponse({ body: [user], status: 200 });
            const searchStub = vi.spyOn(courseManagementServiceMock, 'searchUsers').mockReturnValue(of(searchResponse));

            changeInput(fixture.debugElement.nativeElement, 'test');
            vi.advanceTimersByTime(1000);
            fixture.changeDetectorRef.detectChanges();
            expect(searchStub).toHaveBeenCalledOnce();

            const profilePicture = fixture.debugElement.query(By.directive(ProfilePictureComponent));
            expect(profilePicture).not.toBeNull();
        });
    });

    describe('search input', () => {
        const user = generateExampleUserPublicInfoDTO({});
        let searchStub: ReturnType<typeof vi.spyOn>;

        beforeEach(() => {
            searchStub = vi.spyOn(courseManagementServiceMock, 'searchUsers').mockReturnValue(of(new HttpResponse({ body: [user], status: 200 })));
        });

        function search(term: string) {
            changeInput(fixture.debugElement.nativeElement, term);
            vi.advanceTimersByTime(1000);
            fixture.changeDetectorRef.detectChanges();
        }

        it('should give the input an id derived from the host id and mark the results for end-to-end tests', () => {
            const host: HTMLElement = fixture.debugElement.query(By.directive(CourseUsersSelectorComponent)).nativeElement;
            expect(host.id).toMatch(/^users-selector\d+$/);
            expect(getNativeInput(host).id).toBe(`${host.id}-search-input`);

            search('test');

            expect(document.querySelectorAll('.cdk-overlay-container [data-testid="user-search-result"]')).toHaveLength(1);
        });

        it('should say that it is searching instead of finding nothing while the request is pending', () => {
            const response = new Subject<HttpResponse<UserPublicInfoDTO[]>>();
            searchStub.mockReturnValue(response);

            search('test');

            expect(userSelectorComponent.isSearching()).toBe(true);
            expectDropdownItems(['artemisApp.userSelector.searchingText']);

            response.next(new HttpResponse({ body: [user], status: 200 }));
            response.complete();
            fixture.changeDetectorRef.detectChanges();

            expect(userSelectorComponent.isSearching()).toBe(false);
            expectDropdownItems(['MHMortimer of Sto Helit (mort)']);
        });

        it('should not search for fewer than three characters while students are included', () => {
            search('te');

            expect(searchStub).not.toHaveBeenCalled();
            expect(getDropdownOptions()).toHaveLength(0);
        });

        it('should search for a short term once students are excluded and offer the users on focus', () => {
            fixture.debugElement.nativeElement.querySelector('#students').click();
            fixture.changeDetectorRef.detectChanges();
            expect(userSelectorComponent.searchStudents()).toBe(false);

            search('te');

            // focusing the input already offers the tutors and instructors, typing narrows them down
            expect(searchStub).toHaveBeenCalledTimes(2);
            expect(searchStub).toHaveBeenNthCalledWith(1, 1, '', ['tutors', 'instructors']);
            expect(searchStub).toHaveBeenNthCalledWith(2, 1, 'te', ['tutors', 'instructors']);
            expectDropdownItems(['MHMortimer of Sto Helit (mort)']);
        });

        it('should repeat the search with the remaining roles when a filter changes', () => {
            search('test');
            expect(searchStub).toHaveBeenLastCalledWith(1, 'test', ['students', 'tutors', 'instructors']);

            fixture.debugElement.nativeElement.querySelector('#tutors').click();
            vi.advanceTimersByTime(0);

            expect(searchStub).toHaveBeenCalledTimes(2);
            expect(searchStub).toHaveBeenLastCalledWith(1, 'test', ['students', 'instructors']);
        });

        it('should not search when a filter changes while the input is empty', () => {
            fixture.debugElement.nativeElement.querySelector('#tutors').click();
            vi.advanceTimersByTime(0);

            expect(searchStub).not.toHaveBeenCalled();
        });

        it('should not offer any user when no role is searched', () => {
            fixture.debugElement.nativeElement.querySelector('#students').click();
            fixture.debugElement.nativeElement.querySelector('#tutors').click();
            fixture.debugElement.nativeElement.querySelector('#instructors').click();
            fixture.changeDetectorRef.detectChanges();

            search('test');

            expect(searchStub).not.toHaveBeenCalled();
            expect(userSelectorComponent.suggestions()).toEqual([]);
        });

        it('should not offer users that are already selected', () => {
            userSelectorComponent.selectedUsers.set([user]);

            search('test');

            expect(searchStub).toHaveBeenCalledOnce();
            expect(userSelectorComponent.suggestions()).toEqual([]);
        });

        it('should tell the user when the search failed', () => {
            searchStub.mockReturnValue(throwError(() => new Error('failed')));

            search('test');

            expect(userSelectorComponent.searchFailed()).toBe(true);
            expect(userSelectorComponent.isSearching()).toBe(false);
            expect(fixture.debugElement.nativeElement.textContent).toContain('artemisApp.userSelector.searchFailedText');
        });
    });

    function getNativeInput(element: HTMLElement): HTMLInputElement {
        return <HTMLInputElement>element.querySelector('input[type="text"]');
    }

    function changeInput(element: any, value: string) {
        const input = getNativeInput(element);
        input.focus();
        input.value = value;
        const evt = new Event('input', { bubbles: true, cancelable: false });
        input.dispatchEvent(evt);
    }

    function getDropdownOptions(): HTMLElement[] {
        return Array.from(document.querySelectorAll<HTMLElement>('.cdk-overlay-container li[role="option"]'));
    }

    function expectDropdownItems(dropdownEntries: string[]): void {
        const options = getDropdownOptions();
        expect(options).toHaveLength(dropdownEntries.length);
        for (let i = 0; i < dropdownEntries.length; i++) {
            const resultDef = dropdownEntries[i];
            expect(normalizeText(options[i].textContent)).toEqual(resultDef);
        }
    }

    function normalizeText(txt: string | null): string {
        return txt ? txt.trim().replace(/\s+/g, ' ') : '';
    }

    function generateExampleUserPublicInfoDTO({
        id = 3,
        login = 'mort',
        name = 'Mortimer of Sto Helit',
        firstName = 'Mortimer',
        lastName = 'of Sto Helit',
        isInstructor = false,
        isEditor = false,
        isTeachingAssistant = false,
        isStudent = true,
    }: Partial<UserPublicInfoDTO> = {}): UserPublicInfoDTO {
        const exampleUserPublicInfoDTO = new UserPublicInfoDTO();
        exampleUserPublicInfoDTO.id = id;
        exampleUserPublicInfoDTO.login = login;
        exampleUserPublicInfoDTO.name = name;
        exampleUserPublicInfoDTO.firstName = firstName;
        exampleUserPublicInfoDTO.lastName = lastName;
        exampleUserPublicInfoDTO.isInstructor = isInstructor;
        exampleUserPublicInfoDTO.isEditor = isEditor;
        exampleUserPublicInfoDTO.isTeachingAssistant = isTeachingAssistant;
        exampleUserPublicInfoDTO.isStudent = isStudent;
        return exampleUserPublicInfoDTO;
    }
});
