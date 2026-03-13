import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { HttpResponse } from '@angular/common/http';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { Subject, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { Dialog } from 'primeng/dialog';
import { PrimeNgDialogStubComponent } from 'test/helpers/stubs/tutorialgroup/prime-ng-dialog-stub.component';
import { TutorialRegistrationsRegisterModalComponent } from './tutorial-registrations-register-modal.component';
import { TutorialGroupRegisteredStudentDTO } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialRegistrationsRegisterSearchBarComponent } from 'app/tutorialgroup/manage/tutorial-registrations-register-search-bar/tutorial-registrations-register-search-bar.component';
import { TutorialRegistrationsRegisterSearchBarStubComponent } from 'test/helpers/stubs/tutorialgroup/tutorial-registrations-register-search-bar-stub.component';
import { TutorialRegistrationsStudentsTableComponent } from 'app/tutorialgroup/manage/tutorial-registrations-students-table/tutorial-registrations-students-table.component';
import { TutorialRegistrationsStudentsTableStubComponent } from 'test/helpers/stubs/tutorialgroup/tutorial-registrations-students-table-stub.component';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { AlertService } from 'app/shared/service/alert.service';
import { TutorialGroupRegisteredStudentsService } from 'app/tutorialgroup/manage/service/tutorial-group-registered-students.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

interface TutorialGroupsServiceMock {
    registerMultipleStudentsViaLogin: ReturnType<typeof vi.fn>;
}

interface AlertServiceMock {
    addErrorAlert: ReturnType<typeof vi.fn>;
}

interface TutorialGroupRegisteredStudentsServiceMock {
    addStudentsToRegisteredStudentsState: ReturnType<typeof vi.fn>;
}

describe('TutorialRegistrationsRegisterModalComponent', () => {
    setupTestBed({ zoneless: true });

    let component: TutorialRegistrationsRegisterModalComponent;
    let fixture: ComponentFixture<TutorialRegistrationsRegisterModalComponent>;

    let tutorialGroupsServiceMock: TutorialGroupsServiceMock;
    let alertServiceMock: AlertServiceMock;
    let tutorialGroupRegisteredStudentsServiceMock: TutorialGroupRegisteredStudentsServiceMock;

    const firstStudent: TutorialGroupRegisteredStudentDTO = {
        id: 1,
        name: 'Ada Lovelace',
        login: 'ada',
        email: 'ada@tum.de',
        registrationNumber: 'R001',
        profilePictureUrl: undefined,
    };

    const secondStudent: TutorialGroupRegisteredStudentDTO = {
        id: 2,
        name: 'Alan Turing',
        login: 'alan',
        email: 'alan@tum.de',
        registrationNumber: 'R002',
        profilePictureUrl: undefined,
    };

    beforeEach(async () => {
        tutorialGroupsServiceMock = {
            registerMultipleStudentsViaLogin: vi.fn(),
        };

        alertServiceMock = {
            addErrorAlert: vi.fn(),
        };

        tutorialGroupRegisteredStudentsServiceMock = {
            addStudentsToRegisteredStudentsState: vi.fn(),
        };

        await TestBed.configureTestingModule({
            imports: [TutorialRegistrationsRegisterModalComponent],
            providers: [
                { provide: TutorialGroupsService, useValue: tutorialGroupsServiceMock },
                { provide: AlertService, useValue: alertServiceMock },
                { provide: TutorialGroupRegisteredStudentsService, useValue: tutorialGroupRegisteredStudentsServiceMock },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideComponent(TutorialRegistrationsRegisterModalComponent, {
                remove: {
                    imports: [Dialog, TutorialRegistrationsRegisterSearchBarComponent, TutorialRegistrationsStudentsTableComponent],
                },
                add: {
                    imports: [PrimeNgDialogStubComponent, TutorialRegistrationsRegisterSearchBarStubComponent, TutorialRegistrationsStudentsTableStubComponent],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(TutorialRegistrationsRegisterModalComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('courseId', 7);
        fixture.componentRef.setInput('tutorialGroupId', 11);
        fixture.detectChanges();
        await fixture.whenStable();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should add selected students from the search bar output and ignore duplicates', async () => {
        component.open();
        fixture.detectChanges();
        await fixture.whenStable();

        const searchBar = fixture.debugElement.query(By.directive(TutorialRegistrationsRegisterSearchBarStubComponent)).componentInstance;
        const studentsTable = fixture.debugElement.query(By.directive(TutorialRegistrationsStudentsTableStubComponent)).componentInstance;

        searchBar.onStudentSelected.emit(firstStudent);
        searchBar.onStudentSelected.emit(firstStudent);
        searchBar.onStudentSelected.emit(secondStudent);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.selectedStudents()).toEqual([firstStudent, secondStudent]);
        expect(studentsTable.students()).toEqual([firstStudent, secondStudent]);
    });

    it('should close the modal and clear selected students when cancel is clicked', async () => {
        component.open();
        fixture.detectChanges();
        await fixture.whenStable();

        const searchBar = fixture.debugElement.query(By.directive(TutorialRegistrationsRegisterSearchBarStubComponent)).componentInstance;
        const dialog = fixture.debugElement.query(By.directive(PrimeNgDialogStubComponent)).componentInstance;
        const studentsTable = fixture.debugElement.query(By.directive(TutorialRegistrationsStudentsTableStubComponent)).componentInstance;
        const cancelButton = fixture.nativeElement.querySelector('.p-button-secondary');

        searchBar.onStudentSelected.emit(firstStudent);
        fixture.detectChanges();
        await fixture.whenStable();

        cancelButton.click();
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.isOpen()).toBe(false);
        expect(dialog.visible()).toBe(false);
        expect(component.selectedStudents()).toEqual([]);
        expect(studentsTable.students()).toEqual([]);
    });

    it('should register all selected students and close the modal on success', async () => {
        const response$ = new Subject<HttpResponse<void>>();
        tutorialGroupsServiceMock.registerMultipleStudentsViaLogin.mockReturnValue(response$.asObservable());

        component.open();
        fixture.detectChanges();
        await fixture.whenStable();

        const searchBar = fixture.debugElement.query(By.directive(TutorialRegistrationsRegisterSearchBarStubComponent)).componentInstance;
        const studentsTable = fixture.debugElement.query(By.directive(TutorialRegistrationsStudentsTableStubComponent)).componentInstance;
        const dialog = fixture.debugElement.query(By.directive(PrimeNgDialogStubComponent)).componentInstance;
        const registerButton = fixture.nativeElement.querySelector('.p-button-primary');

        searchBar.onStudentSelected.emit(firstStudent);
        searchBar.onStudentSelected.emit(secondStudent);
        fixture.detectChanges();
        await fixture.whenStable();

        registerButton.click();
        fixture.detectChanges();
        await fixture.whenStable();

        let loadingOverlay = fixture.nativeElement.querySelector('jhi-loading-indicator-overlay');

        expect(tutorialGroupsServiceMock.registerMultipleStudentsViaLogin).toHaveBeenCalledWith(7, 11, ['ada', 'alan']);
        expect(component.isLoading()).toBe(true);
        expect(loadingOverlay).not.toBeNull();

        response$.next(new HttpResponse<void>({ status: 200 }));
        response$.complete();
        fixture.detectChanges();
        await fixture.whenStable();

        loadingOverlay = fixture.nativeElement.querySelector('jhi-loading-indicator-overlay');

        expect(tutorialGroupRegisteredStudentsServiceMock.addStudentsToRegisteredStudentsState).toHaveBeenCalledWith([firstStudent, secondStudent]);
        expect(component.isLoading()).toBe(false);
        expect(component.isOpen()).toBe(false);
        expect(dialog.visible()).toBe(false);
        expect(component.selectedStudents()).toEqual([]);
        expect(studentsTable.students()).toEqual([]);
        expect(loadingOverlay).toBeNull();
    });

    it('should keep the modal open and show an error alert when register all fails', async () => {
        tutorialGroupsServiceMock.registerMultipleStudentsViaLogin.mockReturnValue(throwError(() => new Error('register failed')));

        component.open();
        fixture.detectChanges();
        await fixture.whenStable();

        const searchBar = fixture.debugElement.query(By.directive(TutorialRegistrationsRegisterSearchBarStubComponent)).componentInstance;
        const registerButton = fixture.nativeElement.querySelector('.p-button-primary');
        const studentsTable = fixture.debugElement.query(By.directive(TutorialRegistrationsStudentsTableStubComponent)).componentInstance;
        const dialog = fixture.debugElement.query(By.directive(PrimeNgDialogStubComponent)).componentInstance;

        searchBar.onStudentSelected.emit(firstStudent);
        fixture.detectChanges();
        await fixture.whenStable();

        registerButton.click();
        fixture.detectChanges();
        await fixture.whenStable();

        const loadingOverlay = fixture.nativeElement.querySelector('jhi-loading-indicator-overlay');

        expect(tutorialGroupsServiceMock.registerMultipleStudentsViaLogin).toHaveBeenCalledWith(7, 11, ['ada']);
        expect(alertServiceMock.addErrorAlert).toHaveBeenCalledWith('artemisApp.pages.tutorialGroupRegistrations.registerModal.registerErrorAlert');
        expect(component.isLoading()).toBe(false);
        expect(component.isOpen()).toBe(true);
        expect(dialog.visible()).toBe(true);
        expect(component.selectedStudents()).toEqual([firstStudent]);
        expect(studentsTable.students()).toEqual([firstStudent]);
        expect(loadingOverlay).toBeNull();
    });

    it('should remove a selected student through the students table action', async () => {
        component.open();
        fixture.detectChanges();
        await fixture.whenStable();

        const searchBar = fixture.debugElement.query(By.directive(TutorialRegistrationsRegisterSearchBarStubComponent)).componentInstance;
        const studentsTable = fixture.debugElement.query(By.directive(TutorialRegistrationsStudentsTableStubComponent)).componentInstance;

        searchBar.onStudentSelected.emit(firstStudent);
        searchBar.onStudentSelected.emit(secondStudent);
        fixture.detectChanges();
        await fixture.whenStable();

        studentsTable.removeActionColumnInfo()?.onRemove(new Event('click'), firstStudent);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.selectedStudents()).toEqual([secondStudent]);
        expect(studentsTable.students()).toEqual([secondStudent]);
    });
});
