import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { ConfirmationService } from 'primeng/api';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { EMAIL_KEY, NAME_KEY, REGISTRATION_NUMBER_KEY, USERNAME_KEY } from 'app/shared/export/export-constants';
import * as writeUsersToCsv from 'app/shared/user-import/util/write-users-to-csv';
import { MockTranslateService } from 'src/test/javascript/spec/helpers/mocks/service/mock-translate.service';
import { TutorialRegistrationsImportModalMockComponent } from 'src/test/javascript/spec/helpers/mocks/tutorialgroup/tutorial-registrations-import-modal-mock.component';
import { TutorialRegistrationsRegisterModalMockComponent } from 'src/test/javascript/spec/helpers/mocks/tutorialgroup/tutorial-registrations-register-modal-mock.component';
import { TutorialRegistrationsStudentsTableMockComponent } from 'src/test/javascript/spec/helpers/mocks/tutorialgroup/tutorial-registrations-students-table-mock.component';
import { PrimeNgConfirmDialogStubComponent } from 'src/test/javascript/spec/helpers/stubs/tutorialgroup/prime-ng-confirm-dialog-stub.component';
import { TutorialGroupRegisteredStudentsService } from 'app/tutorialgroup/manage/service/tutorial-group-registered-students.service';
import { TutorialRegistrationsImportModalComponent } from 'app/tutorialgroup/manage/tutorial-registrations-import-modal/tutorial-registrations-import-modal.component';
import { TutorialRegistrationsRegisterModalComponent } from 'app/tutorialgroup/manage/tutorial-registrations-register-modal/tutorial-registrations-register-modal.component';
import { TutorialRegistrationsStudentsTableComponent } from 'app/tutorialgroup/manage/tutorial-registrations-students-table/tutorial-registrations-students-table.component';
import { TutorialRegistrationsComponent } from './tutorial-registrations.component';
import { TutorialGroupStudent } from 'app/openapi/model/tutorialGroupStudent';

interface ConfirmationServiceMock {
    confirm: ReturnType<typeof vi.fn>;
}

interface TutorialGroupRegisteredStudentsServiceMock {
    deregisterStudent: ReturnType<typeof vi.fn>;
}

describe('TutorialRegistrationsComponent', () => {
    setupTestBed({ zoneless: true });

    let component: TutorialRegistrationsComponent;
    let fixture: ComponentFixture<TutorialRegistrationsComponent>;

    let confirmationServiceMock: ConfirmationServiceMock;
    let tutorialGroupRegisteredStudentsServiceMock: TutorialGroupRegisteredStudentsServiceMock;

    const firstStudent: TutorialGroupStudent = {
        id: 1,
        name: 'Ada Lovelace',
        login: 'ada',
        email: ' ada@tum.de ',
        registrationNumber: ' R001 ',
        profilePictureUrl: undefined,
    };

    const secondStudent: TutorialGroupStudent = {
        id: 2,
        name: 'Alan Turing',
        login: 'alan',
        email: 'alan@tum.de',
        registrationNumber: 'R002',
        profilePictureUrl: undefined,
    };

    const thirdStudent: TutorialGroupStudent = {
        id: 3,
        name: undefined,
        login: 'grace',
        email: 'grace@tum.de',
        registrationNumber: 'R003',
        profilePictureUrl: undefined,
    };

    beforeEach(async () => {
        confirmationServiceMock = {
            confirm: vi.fn(),
        };

        tutorialGroupRegisteredStudentsServiceMock = {
            deregisterStudent: vi.fn(),
        };

        await TestBed.configureTestingModule({
            imports: [TutorialRegistrationsComponent],
            providers: [
                { provide: ConfirmationService, useValue: confirmationServiceMock },
                { provide: TutorialGroupRegisteredStudentsService, useValue: tutorialGroupRegisteredStudentsServiceMock },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideComponent(TutorialRegistrationsComponent, {
                remove: {
                    imports: [
                        ConfirmDialogModule,
                        TutorialRegistrationsImportModalComponent,
                        TutorialRegistrationsRegisterModalComponent,
                        TutorialRegistrationsStudentsTableComponent,
                    ],
                    providers: [ConfirmationService],
                },
                add: {
                    imports: [
                        PrimeNgConfirmDialogStubComponent,
                        TutorialRegistrationsImportModalMockComponent,
                        TutorialRegistrationsRegisterModalMockComponent,
                        TutorialRegistrationsStudentsTableMockComponent,
                    ],
                    providers: [{ provide: ConfirmationService, useValue: confirmationServiceMock }],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(TutorialRegistrationsComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('courseId', 7);
        fixture.componentRef.setInput('tutorialGroupId', 11);
        fixture.componentRef.setInput('registeredStudents', [firstStudent, secondStudent, thirdStudent]);
        fixture.componentRef.setInput('loggedInUserIsAtLeastTutorOfGroup', false);
        fixture.componentRef.setInput('loggedInUserIsAtLeastInstructorInCourse', false);
        fixture.detectChanges();
        await fixture.whenStable();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should only render instructor actions when instructor access is granted', async () => {
        expect(fixture.nativeElement.querySelector('[data-testid="import-button"]')).toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="export-button"]')).toBeNull();

        fixture.componentRef.setInput('loggedInUserIsAtLeastInstructorInCourse', true);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(fixture.nativeElement.querySelector('[data-testid="import-button"]')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="export-button"]')).not.toBeNull();
    });

    it('should only render tutor actions and pass the remove column info when tutor access is granted', async () => {
        const studentsTable = fixture.debugElement.query(By.directive(TutorialRegistrationsStudentsTableMockComponent)).componentInstance;

        expect(fixture.nativeElement.querySelector('[data-testid="new-students-button"]')).toBeNull();
        expect(studentsTable.removeActionColumnInfo()).toBeUndefined();

        fixture.componentRef.setInput('loggedInUserIsAtLeastTutorOfGroup', true);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(fixture.nativeElement.querySelector('[data-testid="new-students-button"]')).not.toBeNull();
        expect(studentsTable.removeActionColumnInfo()).toEqual(component.studentsTableRemoveActionColumnInfo);
    });

    it('should expose filtered registered students based on the search string', async () => {
        const studentsTable = fixture.debugElement.query(By.directive(TutorialRegistrationsStudentsTableMockComponent)).componentInstance;

        expect(component.filteredRegisteredStudents()).toEqual([firstStudent, secondStudent, thirdStudent]);
        expect(studentsTable.students()).toEqual([firstStudent, secondStudent, thirdStudent]);

        component.searchString.set('ALAN');
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.filteredRegisteredStudents()).toEqual([secondStudent]);
        expect(studentsTable.students()).toEqual([secondStudent]);

        component.searchString.set('GRA');
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.filteredRegisteredStudents()).toEqual([thirdStudent]);
        expect(studentsTable.students()).toEqual([thirdStudent]);
    });

    it('should export registered students as csv with trimmed values and the expected column order', () => {
        const exportSpy = vi.spyOn(writeUsersToCsv, 'exportUserInformationAsCsv').mockImplementation(vi.fn());

        component.exportRegisteredStudents();

        expect(exportSpy).toHaveBeenCalledWith(
            [
                {
                    [NAME_KEY]: 'Ada Lovelace',
                    [USERNAME_KEY]: 'ada',
                    [EMAIL_KEY]: 'ada@tum.de',
                    [REGISTRATION_NUMBER_KEY]: 'R001',
                },
                {
                    [NAME_KEY]: 'Alan Turing',
                    [USERNAME_KEY]: 'alan',
                    [EMAIL_KEY]: 'alan@tum.de',
                    [REGISTRATION_NUMBER_KEY]: 'R002',
                },
                {
                    [NAME_KEY]: '',
                    [USERNAME_KEY]: 'grace',
                    [EMAIL_KEY]: 'grace@tum.de',
                    [REGISTRATION_NUMBER_KEY]: 'R003',
                },
            ],
            [NAME_KEY, USERNAME_KEY, EMAIL_KEY, REGISTRATION_NUMBER_KEY],
            'registrations',
        );
    });

    it('should not export a csv when no registered students exist', () => {
        const exportSpy = vi.spyOn(writeUsersToCsv, 'exportUserInformationAsCsv').mockImplementation(vi.fn());

        fixture.componentRef.setInput('registeredStudents', []);
        fixture.detectChanges();

        component.exportRegisteredStudents();

        expect(exportSpy).not.toHaveBeenCalled();
    });

    it('should deregister a student through the students table remove action after confirmation', async () => {
        confirmationServiceMock.confirm.mockImplementation((confirmation: { accept?: () => void }) => confirmation.accept?.());

        fixture.componentRef.setInput('loggedInUserIsAtLeastTutorOfGroup', true);
        fixture.detectChanges();
        await fixture.whenStable();

        const studentsTable = fixture.debugElement.query(By.directive(TutorialRegistrationsStudentsTableMockComponent)).componentInstance;

        studentsTable.triggerRemove(secondStudent);

        expect(confirmationServiceMock.confirm).toHaveBeenCalledOnce();
        expect(tutorialGroupRegisteredStudentsServiceMock.deregisterStudent).toHaveBeenCalledWith(7, 11, 'alan');
    });
});
