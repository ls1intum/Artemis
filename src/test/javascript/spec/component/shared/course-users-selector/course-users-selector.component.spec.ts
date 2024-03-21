import { ComponentFixture, TestBed, fakeAsync, tick, waitForAsync } from '@angular/core/testing';
import { CourseUsersSelectorComponent, SearchRoleGroup } from 'app/shared/course-users-selector/course-users-selector.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { MockPipe, MockProvider } from 'ng-mocks';
import { NgbTypeaheadModule } from '@ng-bootstrap/ng-bootstrap';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { Component, DebugElement, ViewChild } from '@angular/core';
import { UserPublicInfoDTO } from 'app/core/user/user.model';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { By } from '@angular/platform-browser';

@Component({
    template: `
        <jhi-course-users-selector
            [(ngModel)]="selectedUsers"
            [disabled]="disabled"
            [courseId]="courseId"
            [rolesToAllowSearchingIn]="rolesToAllowSearchingIn"
            [multiSelect]="multiSelect"
            [showUserList]="showUserList"
        />
    `,
})
class WrapperComponent {
    @ViewChild(CourseUsersSelectorComponent)
    courseUsersSelectorComponent: CourseUsersSelectorComponent;

    disabled = false;
    courseId = 1;
    label = 'TestLabel';
    rolesToAllowSearchingIn: SearchRoleGroup[] = ['tutors', 'students', 'instructors'];
    multiSelect = true;
    showUserList = true;

    public selectedUsers: UserPublicInfoDTO[] | undefined | null = [];
}

describe('CourseUsersSelectorComponent', () => {
    let wrapperComponent: WrapperComponent;
    let fixture: ComponentFixture<WrapperComponent>;
    let userSelectorComponent: CourseUsersSelectorComponent;
    let searchUsersSpy: jest.SpyInstance;

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({
            imports: [CommonModule, FormsModule, ReactiveFormsModule, ArtemisSharedModule, ArtemisSharedComponentModule, NgbTypeaheadModule],
            declarations: [CourseUsersSelectorComponent, WrapperComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [MockProvider(CourseManagementService)],
        }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(WrapperComponent);
        fixture.detectChanges();
        wrapperComponent = fixture.componentInstance;
        userSelectorComponent = wrapperComponent.courseUsersSelectorComponent;
        searchUsersSpy = jest.spyOn(TestBed.inject(CourseManagementService), 'searchUsers');
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
        it('changing connected wrapper should update the component property', fakeAsync(() => {
            const exampleUserPublicInfoDTO = generateExampleUserPublicInfoDTO({});
            wrapperComponent.selectedUsers = [exampleUserPublicInfoDTO];
            wrapperComponent.multiSelect = testCase.multiSelect;
            fixture.detectChanges();
            tick();
            expect(userSelectorComponent.selectedUsers).toEqual([exampleUserPublicInfoDTO]);
            expect(fixture.debugElement.queryAll(By.css('.selected-user'))).toHaveLength(1);
        }));

        it('should convert undefined to empty array', fakeAsync(() => {
            wrapperComponent.selectedUsers = undefined;
            wrapperComponent.multiSelect = testCase.multiSelect;
            fixture.detectChanges();
            tick();
            expect(userSelectorComponent.selectedUsers).toEqual([]);
        }));

        it('searching, selecting and deleting a user should update the selectedUsers property', fakeAsync(() => {
            wrapperComponent.multiSelect = testCase.multiSelect;
            const user = generateExampleUserPublicInfoDTO({});
            const searchResponse: HttpResponse<UserPublicInfoDTO[]> = new HttpResponse({
                body: [user],
                status: 200,
            });
            const searchStub = searchUsersSpy.mockReturnValue(of(searchResponse));

            // searching for a user
            changeInput(fixture.debugElement.nativeElement, 'test');
            tick(1000);
            fixture.detectChanges();
            expect(searchStub).toHaveBeenCalledOnce();
            expect(searchStub).toHaveBeenCalledWith(1, 'test', ['students', 'tutors', 'instructors']);
            expectDropdownItems(fixture.nativeElement, ['Mortimer of Sto Helit (mort)']);
            // selecting the user in the dropdown
            getDropdownButtons(fixture.debugElement)[0].triggerEventHandler('click', {});
            fixture.detectChanges();
            tick();
            expect(userSelectorComponent.selectedUsers).toEqual([user]);
            expect(wrapperComponent.selectedUsers).toEqual([user]);

            // now we delete the user again from the selected users
            expect(fixture.debugElement.queryAll(By.css('.selected-user'))).toHaveLength(1);
            const deleteButton = fixture.debugElement.query(By.css('.delete-user'));
            deleteButton.triggerEventHandler('click', {});
            fixture.detectChanges();
            tick();
            expect(userSelectorComponent.selectedUsers).toEqual([]);
            expect(wrapperComponent.selectedUsers).toEqual([]);
        }));

        it('should block the input field and not show delete button', fakeAsync(() => {
            wrapperComponent.multiSelect = testCase.multiSelect;
            const exampleUserPublicInfoDTO = generateExampleUserPublicInfoDTO({});
            wrapperComponent.selectedUsers = [exampleUserPublicInfoDTO];
            wrapperComponent.disabled = true;
            fixture.detectChanges();
            tick(1000);
            expect(userSelectorComponent.selectedUsers).toEqual([exampleUserPublicInfoDTO]);
            expect(fixture.debugElement.query(By.css('input')).nativeElement.disabled).toBeTrue();
            expect(fixture.debugElement.queryAll(By.css('.selected-user'))).toHaveLength(1);
            expect(fixture.debugElement.query(By.css('.delete-user'))).toBeFalsy();
        }));
    });
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
}: UserPublicInfoDTO) {
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
