import { ComponentFixture, TestBed, fakeAsync, tick, waitForAsync } from '@angular/core/testing';
import { Component, DebugElement, ViewChild } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { NgbTypeaheadModule } from '@ng-bootstrap/ng-bootstrap';
import { By } from '@angular/platform-browser';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { CourseUsersSelectorComponent, SearchRoleGroup } from 'app/communication/course-users-selector/course-users-selector.component';
import { UserPublicInfoDTO } from 'app/core/user/user.model';

import { TranslateModule } from '@ngx-translate/core';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';

@Component({
    template: `
        <jhi-course-users-selector
            [disabled]="disabled"
            [courseId]="courseId"
            [rolesToAllowSearchingIn]="rolesToAllowSearchingIn"
            [multiSelect]="multiSelect"
            [showUserList]="showUserList"
        />
    `,
    imports: [CourseUsersSelectorComponent],
})
class WrapperComponent {
    @ViewChild(CourseUsersSelectorComponent)
    courseUsersSelectorComponent: CourseUsersSelectorComponent;
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
    // let searchUsersSpy: jest.SpyInstance;
    const courseManagementServiceMock = { searchUsers: jest.fn() } as unknown as CourseManagementService;

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({
            imports: [CommonModule, FormsModule, ReactiveFormsModule, NgbTypeaheadModule, TranslateModule.forRoot()],
            providers: [{ provide: CourseManagementService, useValue: courseManagementServiceMock }],
        }).compileComponents();
        fixture = TestBed.createComponent(WrapperComponent);
        wrapperComponent = fixture.componentInstance;
        fixture.detectChanges();

        userSelectorDebugElement = fixture.debugElement.query(By.directive(CourseUsersSelectorComponent));
        userSelectorComponent = userSelectorDebugElement.componentInstance;
    }));

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
        it('changing connected wrapper should update the component property', fakeAsync(() => {
            const exampleUserPublicInfoDTO = generateExampleUserPublicInfoDTO({});
            userSelectorComponent.selectedUsers = [exampleUserPublicInfoDTO];
            wrapperComponent.multiSelect = testCase.multiSelect;
            fixture.changeDetectorRef.detectChanges();
            tick();
            expect(userSelectorComponent.selectedUsers).toEqual([exampleUserPublicInfoDTO]);
            expect(fixture.debugElement.queryAll(By.css('.selected-user'))).toHaveLength(1);
        }));

        it('should convert undefined to empty array', fakeAsync(() => {
            userSelectorComponent.selectedUsers = [];
            wrapperComponent.multiSelect = testCase.multiSelect;
            fixture.changeDetectorRef.detectChanges();
            tick();
            expect(userSelectorComponent.selectedUsers).toEqual([]);
        }));

        it('searching, selecting and deleting a user should update the selectedUsers property', fakeAsync(() => {
            userSelectorComponent.selectedUsers = [];
            wrapperComponent.multiSelect = testCase.multiSelect;
            const user = generateExampleUserPublicInfoDTO({});
            const searchResponse: HttpResponse<UserPublicInfoDTO[]> = new HttpResponse({
                body: [user],
                status: 200,
            });
            const searchStub = jest.spyOn(courseManagementServiceMock, 'searchUsers').mockReturnValue(of(searchResponse));

            // searching for a user
            changeInput(fixture.debugElement.nativeElement, 'test');
            tick(1000);
            fixture.changeDetectorRef.detectChanges();
            expect(searchStub).toHaveBeenCalledOnce();
            expect(searchStub).toHaveBeenCalledWith(1, 'test', ['students', 'tutors', 'instructors']);
            expectDropdownItems(fixture.nativeElement, ['MHMortimer of Sto Helit (mort)']);
            // selecting the user in the dropdown
            getDropdownButtons(fixture.debugElement)[0].triggerEventHandler('click', {});
            fixture.changeDetectorRef.detectChanges();
            tick();
            expect(userSelectorComponent.selectedUsers).toEqual([user]);

            // now we delete the user again from the selected users
            expect(fixture.debugElement.queryAll(By.css('.selected-user'))).toHaveLength(1);
            const deleteButton = fixture.debugElement.query(By.css('.delete-user'));
            deleteButton.triggerEventHandler('click', {});
            fixture.changeDetectorRef.detectChanges();
            tick();
            expect(userSelectorComponent.selectedUsers).toEqual([]);
            expect(wrapperComponent.selectedUsers).toEqual([]);
        }));

        it('should block the input field and not show delete button', fakeAsync(() => {
            wrapperComponent.multiSelect = testCase.multiSelect;
            const exampleUserPublicInfoDTO = generateExampleUserPublicInfoDTO({});
            userSelectorComponent.selectedUsers = [exampleUserPublicInfoDTO];
            wrapperComponent.disabled = true;
            fixture.changeDetectorRef.detectChanges();
            tick(1000);
            expect(userSelectorComponent.selectedUsers).toEqual([exampleUserPublicInfoDTO]);
            expect(fixture.debugElement.query(By.css('.delete-user'))).toBeFalsy();
        }));

        it('should render profile picture for users in dropdown', fakeAsync(() => {
            const user = generateExampleUserPublicInfoDTO({});
            const searchResponse = new HttpResponse({ body: [user], status: 200 });
            const searchStub = jest.spyOn(courseManagementServiceMock, 'searchUsers').mockReturnValue(of(searchResponse));

            changeInput(fixture.debugElement.nativeElement, 'test');
            tick(1000);
            fixture.changeDetectorRef.detectChanges();
            expect(searchStub).toHaveBeenCalledOnce();

            const profilePicture = fixture.debugElement.query(By.directive(ProfilePictureComponent));
            expect(profilePicture).not.toBeNull();
        }));
    });

    function getNativeInput(element: HTMLElement): HTMLInputElement {
        return <HTMLInputElement>element.querySelector('input');
    }

    function changeInput(element: any, value: string) {
        const input = getNativeInput(element);
        input.value = value;
        const evt = new Event('input', { bubbles: true, cancelable: false });
        input.dispatchEvent(evt);
    }

    function getDropdownButtons(element: DebugElement): DebugElement[] {
        return Array.from(element.queryAll(By.css('button.dropdown-item')));
    }

    function expectDropdownItems(nativeEl: HTMLElement, dropdownEntries: string[]): void {
        const pages = nativeEl.querySelectorAll('button.dropdown-item');
        expect(pages).toHaveLength(dropdownEntries.length);
        for (let i = 0; i < dropdownEntries.length; i++) {
            const resultDef = dropdownEntries[i];
            expect(normalizeText(pages[i].textContent)).toEqual(resultDef);
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

    afterEach(() => {
        jest.clearAllMocks();
    });
});
