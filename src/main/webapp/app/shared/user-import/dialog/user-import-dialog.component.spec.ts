import { HttpClient, HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { By } from '@angular/platform-browser';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { AlertService } from 'app/shared/service/alert.service';
import { UsersImportDialogComponent } from 'app/shared/user-import/dialog/users-import-dialog.component';
import { TranslateService } from '@ngx-translate/core';
import { Router } from '@angular/router';
import * as fs from 'fs';
import * as path from 'path';
import { ExamUserDTO } from 'app/exam/shared/entities/exam-user-dto.model';
import { DialogModule } from 'primeng/dialog';
import * as readUsersFromCsv from 'app/shared/user-import/util/read-users-from-csv';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { AdminUserService } from 'app/core/user/shared/admin-user.service';
import { User } from 'app/core/user/user.model';
import { Checkbox } from 'primeng/checkbox';
import { TutorialGroupApiService } from 'app/openapi/api/tutorialGroupApi.service';

describe('UsersImportDialogComponent', () => {
    let fixture: ComponentFixture<UsersImportDialogComponent>;
    let component: UsersImportDialogComponent;
    let examManagementService: ExamManagementService;
    let tutorialGroupApiService: TutorialGroupApiService;
    let adminUserService: AdminUserService;

    const studentCsvColumns = 'REGISTRATION_NUMBER,FIRST_NAME_OF_STUDENT,FAMILY_NAME_OF_STUDENT';

    const course: Course = { id: 1 };
    const exam: Exam = { course, id: 2, title: 'Exam Title' };

    const testDir = path.join(process.cwd(), 'src', 'test', 'javascript', 'spec', 'helpers', 'sample', 'user-import');

    beforeAll(() => {
        try {
            TestBed.initTestEnvironment(BrowserTestingModule, platformBrowserTesting());
        } catch (error) {
            // The environment is already initialized when running through the Angular CLI test harness.
        }
    });

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [FormsModule, FaIconComponent, UsersImportDialogComponent],
            providers: [
                MockProvider(AlertService),
                MockProvider(ExamManagementService),
                MockProvider(TutorialGroupApiService),
                MockProvider(AdminUserService),
                MockProvider(HttpClient),
                MockProvider(TranslateService),
                MockProvider(SessionStorageService),
                MockProvider(LocalStorageService),
                MockProvider(Router),
            ],
        })
            .overrideComponent(UsersImportDialogComponent, {
                set: {
                    imports: [
                        FormsModule,
                        MockDirective(TranslateDirective),
                        MockPipe(ArtemisTranslatePipe),
                        MockComponent(HelpIconComponent),
                        FaIconComponent,
                        DialogModule,
                        MockComponent(Checkbox),
                    ],
                },
            })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(UsersImportDialogComponent);
                component = fixture.componentInstance;
                examManagementService = TestBed.inject(ExamManagementService);
                tutorialGroupApiService = TestBed.inject(TutorialGroupApiService);
                adminUserService = TestBed.inject(AdminUserService);

                fixture.componentRef.setInput('courseId', course.id!);
                fixture.componentRef.setInput('exam', exam);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should open and close dialog', () => {
        expect(component.dialogVisible()).toBeFalse();

        component.open();
        expect(component.dialogVisible()).toBeTrue();

        component.close();
        expect(component.dialogVisible()).toBeFalse();
    });

    it('should emit importCompleted on finish', () => {
        const emitSpy = jest.spyOn(component.importCompleted, 'emit');

        component.onFinish();

        expect(emitSpy).toHaveBeenCalledOnce();
        expect(component.dialogVisible()).toBeFalse();
    });

    it('should reset dialog when selecting csv file', async () => {
        component.usersToImport = [{ registrationNumber: '1', lastName: 'lastName', firstName: 'firstName', login: 'login1', email: 'test@mail' }];
        component.notFoundUsers = [{ registrationNumber: '2', lastName: 'lastName2', firstName: 'firstName2', login: 'login2', email: 'test@mail' }];
        component.hasImported = true;

        const event = { target: { files: [studentCsvColumns] } };
        await component.onCSVFileSelect(event);

        expect(component.usersToImport).toHaveLength(0);
        expect(component.notFoundUsers).toHaveLength(0);
    });

    it('should read no students from csv file', async () => {
        const event = { target: { files: [studentCsvColumns] } };
        await component.onCSVFileSelect(event);

        expect(component.usersToImport).toHaveLength(0);
        expect(component.notFoundUsers).toHaveLength(0);
        expect(component.noUsersFoundError).toBeTrue();
    });

    it('should read students from csv file', async () => {
        const csv = `${studentCsvColumns}\n"1","Max","Mustermann"\n"2","John","Wick"`;
        const event = { target: { files: [csv] } };
        await component.onCSVFileSelect(event);

        expect(component.usersToImport).toHaveLength(2);
        expect(component.notFoundUsers).toHaveLength(0);
        expect(component.noUsersFoundError).toBeUndefined();
    });

    it('should have validation error for invalid csv', async () => {
        // Csv without header
        const invalidCsv = `"1","Max","Mustermann"\n"2","John","Wick"`;

        const event = { target: { files: [invalidCsv] } };
        await component.onCSVFileSelect(event);

        expect(component.validationError).toHaveLength(1);
    });

    it('should stop parsing and show a generic error when csv parsing fails unexpectedly', async () => {
        jest.spyOn(readUsersFromCsv, 'readStudentDTOsFromCSVFile').mockRejectedValue(new Error('parse failed'));
        const alertService = TestBed.inject(AlertService);
        const alertSpy = jest.spyOn(alertService, 'error');
        const event = { target: { files: [studentCsvColumns], value: 'students.csv' } };

        await component.onCSVFileSelect(event);

        expect(alertSpy).toHaveBeenCalledWith('artemisApp.importUsers.genericErrorMessage');
        expect(component.isParsing).toBeFalse();
        expect(event.target.value).toBe('');
    });

    it('should import students', () => {
        fixture.componentRef.setInput('examUserMode', false);
        const studentsToImport: ExamUserDTO[] = [
            { registrationNumber: '1', firstName: 'Max', lastName: 'Musetermann', login: 'login1', email: 'test@mail' },
            { registrationNumber: '2', firstName: 'Bob', lastName: 'Ross', login: 'login2', email: 'test@mail' },
        ];
        const studentsNotFound: ExamUserDTO[] = [{ registrationNumber: '2', firstName: 'Bob', lastName: 'Ross', login: 'login2', email: 'test@mail' }];

        const fakeResponse = { body: studentsNotFound } as HttpResponse<ExamUserDTO[]>;
        jest.spyOn(examManagementService, 'addStudentsToExam').mockReturnValue(of(fakeResponse));

        component.usersToImport = studentsToImport;
        component.importUsers();

        expect(examManagementService.addStudentsToExam).toHaveBeenCalledOnce();
        expect(component.isImporting).toBeFalse();
        expect(component.hasImported).toBeTrue();
        expect(component.notFoundUsers).toHaveLength(studentsNotFound.length);
    });

    describe('should read students from csv files', () => {
        const testFiles = fs.readdirSync(testDir).filter((testFile) => testFile.localeCompare('UserImportEmailOnlySampleFile.csv') !== 0);

        test.each(testFiles)('reading from %s', async (testFileName: string) => {
            const pathToTestFile = path.join(testDir, testFileName);
            const csv = fs.readFileSync(pathToTestFile, 'utf-8');
            const event = { target: { files: [csv] } };
            await component.onCSVFileSelect(event);

            expect(component.usersToImport).toHaveLength(5);

            let expectedStudentDTOs: ExamUserDTO[];
            if (testFileName.localeCompare('TUMonlineCourseExport.csv') === 0) {
                expectedStudentDTOs = [
                    { registrationNumber: '01234567', firstName: 'Max Moritz', lastName: 'Mustermann', login: '', email: 'max-moritz.mustermann@example.com' },
                    { registrationNumber: '01234568', firstName: 'John-James', lastName: 'Doe', login: '', email: 'john-james.doe@example.com' },
                    { registrationNumber: '01234569', firstName: 'Jane', lastName: 'Doe', login: '', email: 'jane.doe@example.com' },
                    { registrationNumber: '01234570', firstName: 'Alice', lastName: '-', login: '', email: 'alice@example.com' },
                    { registrationNumber: '01234571', firstName: 'Bob', lastName: 'Ross', login: '', email: 'bob.ross@example.com' },
                ];
            } else {
                expectedStudentDTOs = [
                    { registrationNumber: '01234567', firstName: 'Max Moritz', lastName: 'Mustermann', login: '', email: '' },
                    { registrationNumber: '01234568', firstName: 'John-James', lastName: 'Doe', login: '', email: '' },
                    { registrationNumber: '01234569', firstName: 'Jane', lastName: 'Doe', login: '', email: '' },
                    { registrationNumber: '01234570', firstName: 'Alice', lastName: '-', login: '', email: '' },
                    { registrationNumber: '01234571', firstName: 'Bob', lastName: 'Ross', login: '', email: '' },
                ];
            }

            expect(component.usersToImport).toEqual(expectedStudentDTOs);
        });
    });

    it('should read students from csv with email only', async () => {
        const pathToTestFile = path.join(testDir, 'UserImportEmailOnlySampleFile.csv');
        const csv = fs.readFileSync(pathToTestFile, 'utf-8');
        const event = { target: { files: [csv] } };

        await component.onCSVFileSelect(event);

        expect(component.usersToImport).toHaveLength(5);

        const expectedStudentDTOs: ExamUserDTO[] = [
            { registrationNumber: '', firstName: '', lastName: '', login: '', email: 'testuser1@mail.com' },
            { registrationNumber: '', firstName: '', lastName: '', login: '', email: 'testuser2@mail.com' },
            { registrationNumber: '', firstName: '', lastName: '', login: '', email: 'testuser3@mail.com' },
            { registrationNumber: '', firstName: '', lastName: '', login: '', email: 'testuser4@mail.com' },
            { registrationNumber: '', firstName: '', lastName: '', login: '', email: 'testuser5@mail.com' },
        ];

        expect(component.usersToImport).toEqual(expectedStudentDTOs);
    });

    it('should read students from csv with room/seat information', async () => {
        const testDir = path.join(process.cwd(), 'src', 'test', 'javascript', 'spec', 'helpers', 'sample', 'exam-user-import');
        fixture.componentRef.setInput('examUserMode', true);

        const pathToTestFile = path.join(testDir, 'UserImportWithRoomAndSeatInfo.csv');
        const csv = fs.readFileSync(pathToTestFile, 'utf-8');
        const event = { target: { files: [csv] } };

        await component.onCSVFileSelect(event);

        expect(component.examUsersToImport).toHaveLength(4);

        const expectedStudentDTOs: ExamUserDTO[] = [
            {
                room: '101.2',
                seat: '22F',
                registrationNumber: '03756882',
                firstName: 'ArTEMiS',
                lastName: 'Test User 2',
                login: 'artemis_test_user_2',
                email: 'krusche+testuser_2@in.tum.de',
            },
            {
                room: '101.2',
                seat: '28F',
                registrationNumber: '03756883',
                firstName: 'ArTEMiS',
                lastName: 'Test User 3',
                login: 'artemis_test_user_3',
                email: 'krusche+testuser_3@in.tum.de',
            },
            {
                room: '101.2',
                seat: '35F',
                registrationNumber: '03756884',
                firstName: 'ArTEMiS',
                lastName: 'Test User 4',
                login: 'artemis_test_user_4',
                email: 'krusche+testuser_4@in.tum.de',
            },
            {
                room: '101.2',
                seat: '26F',
                registrationNumber: '03756885',
                firstName: 'ArTEMiS',
                lastName: 'Test User 5',
                login: 'artemis_test_user_5',
                email: 'krusche+testuser_5@in.tum.de',
            },
        ];

        expect(component.examUsersToImport).toEqual(expectedStudentDTOs);
    });

    it('should expose multiple invalid student row numbers via validationError', async () => {
        const csv = `${studentCsvColumns}\n"","Max","Mustermann"\n"","John","Wick"`;
        const event = { target: { files: [csv], value: 'students.csv' } };

        await component.onCSVFileSelect(event);

        expect(component.validationError).toBe('2, 3');
    });

    it('should import correctly', () => {
        fixture.componentRef.setInput('examUserMode', false);
        const importedStudents: ExamUserDTO[] = [
            { registrationNumber: '1', firstName: 'Max', lastName: 'Musetermann', login: 'login1', email: '' },
            { registrationNumber: '2', firstName: 'Bob', lastName: 'Ross', login: 'login2', email: '' },
        ];
        const notImportedStudents: ExamUserDTO[] = [{ registrationNumber: '3', firstName: 'Some', lastName: 'Dude', login: 'login3', email: '' }];

        const fakeResponse = { body: notImportedStudents } as HttpResponse<ExamUserDTO[]>;
        jest.spyOn(examManagementService, 'addStudentsToExam').mockReturnValue(of(fakeResponse));

        component.usersToImport = importedStudents.concat(notImportedStudents);
        component.importUsers();

        importedStudents.forEach((student) => expect(component.wasImported(student)).toBeTrue());
        notImportedStudents.forEach((student) => expect(component.wasImported(student)).toBeFalse());
        expect(component.numberOfUsersImported).toBe(importedStudents.length);
        expect(component.numberOfUsersNotImported).toBe(notImportedStudents.length);
    });

    it('should invoke REST call on "Import" but not on "Finish"', () => {
        fixture.componentRef.setInput('examUserMode', false);
        const studentsToImport: ExamUserDTO[] = [
            { registrationNumber: '1', firstName: 'Max', lastName: 'Mustermann', login: 'login1', email: '' },
            { registrationNumber: '2', firstName: 'Bob', lastName: 'Ross', login: 'login2', email: '' },
        ];
        const studentsNotFound: ExamUserDTO[] = [{ registrationNumber: '3', firstName: 'Some', lastName: 'Dude', login: 'login3', email: '' }];

        const fakeResponse = { body: studentsNotFound } as HttpResponse<ExamUserDTO[]>;
        jest.spyOn(examManagementService, 'addStudentsToExam').mockReturnValue(of(fakeResponse));

        component.open();
        // Set usersToImport after open() since open() calls resetDialog() which clears the array
        component.usersToImport = studentsToImport;

        fixture.detectChanges();

        expect(component.hasImported).toBeFalse();
        expect(component.isSubmitDisabled).toBeFalse();
        const importButton = fixture.debugElement.query(By.css('#import'));

        expect(importButton).not.toBeNull();

        importButton.nativeElement.click();

        expect(examManagementService.addStudentsToExam).toHaveBeenCalledOnce();
        expect(component.isImporting).toBeFalse();
        expect(component.hasImported).toBeTrue();
        expect(component.notFoundUsers).toHaveLength(studentsNotFound.length);

        jest.spyOn(examManagementService, 'addStudentsToExam').mockReturnValue(of(fakeResponse));

        component.hasImported = true;
        fixture.detectChanges();

        const finishButton = fixture.debugElement.query(By.css('#finish-button'));
        expect(finishButton).not.toBeNull();

        finishButton.nativeElement.click();
        expect(examManagementService.addStudentsToExam).toHaveBeenCalledOnce();
    });

    it('should read students from csv with password column', async () => {
        const csvWithPassword = 'login,firstname,lastname,email,password\nuser1,Max,Mustermann,max@test.com,secret123\nuser2,Bob,Ross,bob@test.com,';
        const event = { target: { files: [csvWithPassword] } };
        await component.onCSVFileSelect(event);

        expect(component.usersToImport).toHaveLength(2);
        expect(component.usersToImport[0].password).toBe('secret123');
        expect(component.usersToImport[1].password).toBeUndefined();
    });

    it('should reset createInternalUsers on dialog reset', () => {
        component.createInternalUsers = true;
        component.open();
        expect(component.createInternalUsers).toBeFalse();
    });

    it('should import users in admin mode with createInternalUsers flag', () => {
        fixture.componentRef.setInput('adminUserMode', true);
        fixture.componentRef.setInput('examUserMode', false);
        fixture.componentRef.setInput('exam', undefined);
        fixture.componentRef.setInput('courseGroup', undefined);

        const failedUsers: User[] = [];
        const fakeResponse = { body: failedUsers } as HttpResponse<User[]>;
        jest.spyOn(adminUserService, 'importAll').mockReturnValue(of(fakeResponse));

        component.usersToImport = [{ registrationNumber: '', login: 'user1', email: 'user1@test.com', firstName: 'Max', lastName: 'Mustermann', password: 'secret123' }];
        component.createInternalUsers = true;
        component.importUsers();

        expect(adminUserService.importAll).toHaveBeenCalledWith(expect.arrayContaining([expect.objectContaining({ login: 'user1', password: 'secret123' })]), true);
        expect(component.isImporting).toBeFalse();
        expect(component.hasImported).toBeTrue();
    });

    it('should import users in admin mode without createInternalUsers flag by default', () => {
        fixture.componentRef.setInput('adminUserMode', true);
        fixture.componentRef.setInput('examUserMode', false);
        fixture.componentRef.setInput('exam', undefined);
        fixture.componentRef.setInput('courseGroup', undefined);

        const failedUsers: User[] = [];
        const fakeResponse = { body: failedUsers } as HttpResponse<User[]>;
        jest.spyOn(adminUserService, 'importAll').mockReturnValue(of(fakeResponse));

        component.usersToImport = [{ registrationNumber: '', login: 'user1', email: 'user1@test.com', firstName: 'Max', lastName: 'Mustermann' }];
        component.importUsers();

        expect(adminUserService.importAll).toHaveBeenCalledWith(expect.anything(), false);
    });

    it('should expose validationError for invalid exam user csv rows', async () => {
        fixture.componentRef.setInput('examUserMode', true);
        jest.spyOn(readUsersFromCsv, 'readExamUserDTOsFromCSVFile').mockResolvedValue({
            ok: false,
            invalidRowIndices: [2, 4],
        });
        const event = { target: { files: [studentCsvColumns], value: 'exam-users.csv' } };

        await component.onCSVFileSelect(event);

        expect(component.validationError).toBe('2, 4');
        expect(component.noUsersFoundError).toBeUndefined();
        expect(component.examUsersToImport).toHaveLength(0);
        expect(component.isParsing).toBeFalse();
        expect(event.target.value).toBe('');
    });

    it('should set noUsersFoundError when exam user csv contains no entries', async () => {
        fixture.componentRef.setInput('examUserMode', true);
        jest.spyOn(readUsersFromCsv, 'readExamUserDTOsFromCSVFile').mockResolvedValue({
            ok: true,
            examUsers: [],
        });
        const event = { target: { files: [studentCsvColumns], value: 'exam-users.csv' } };

        await component.onCSVFileSelect(event);

        expect(component.noUsersFoundError).toBeTrue();
        expect(component.validationError).toBeUndefined();
        expect(component.examUsersToImport).toHaveLength(0);
        expect(component.isParsing).toBeFalse();
        expect(event.target.value).toBe('');
    });

    it('should show a generic error and stop importing on save error', () => {
        const alertService = TestBed.inject(AlertService);
        const alertSpy = jest.spyOn(alertService, 'error');
        component.isImporting = true;

        component.onSaveError();

        expect(alertSpy).toHaveBeenCalledWith('artemisApp.importUsers.genericErrorMessage');
        expect(component.isImporting).toBeFalse();
    });

    it('should import tutorial group students and convert generated student dto response', () => {
        fixture.componentRef.setInput('exam', undefined);
        fixture.componentRef.setInput('tutorialGroup', { id: 5 } as TutorialGroup);
        const studentsToImport: ExamUserDTO[] = [{ registrationNumber: '1', firstName: 'Max', lastName: 'Mustermann', login: 'login1', email: 'test@mail' }];
        const generatedStudents = [
            {
                registrationNumber: '2',
                firstName: 'Ada',
                lastName: 'Lovelace',
                login: 'ada',
                email: 'ada@example.com',
            },
        ];

        const fakeResponse = { body: generatedStudents } as HttpResponse<any[]>;
        jest.spyOn(tutorialGroupApiService, 'importRegistrations').mockReturnValue(of(fakeResponse));

        component.usersToImport = studentsToImport;
        component.importUsers();

        expect(tutorialGroupApiService.importRegistrations).toHaveBeenCalledWith(course.id, 5, studentsToImport, 'response');
        expect(component.isImporting).toBeFalse();
        expect(component.hasImported).toBeTrue();
        expect(component.notFoundUsers).toEqual([
            {
                registrationNumber: '2',
                firstName: 'Ada',
                lastName: 'Lovelace',
                login: 'ada',
                email: 'ada@example.com',
            },
        ]);
    });

    it('should import admin users and map visibleRegistrationNumber in the response', () => {
        fixture.componentRef.setInput('exam', undefined);
        fixture.componentRef.setInput('adminUserMode', true);
        const usersToImport: ExamUserDTO[] = [
            { registrationNumber: '1', firstName: 'Max', lastName: 'Mustermann', login: 'login1', email: 'test@mail' },
            { registrationNumber: '2', firstName: 'Ada', lastName: 'Lovelace', login: 'ada', email: 'ada@example.com' },
        ];
        const firstImportedUser = new User(undefined, 'alan', 'Alan', 'Turing', 'alan@example.com');
        firstImportedUser.internal = true;
        firstImportedUser.visibleRegistrationNumber = '3';

        const secondImportedUser = new User(undefined, 'grace', 'Grace', 'Hopper', 'grace@example.com');
        secondImportedUser.internal = true;
        secondImportedUser.visibleRegistrationNumber = '4';

        const importedUsers: User[] = [firstImportedUser, secondImportedUser];

        jest.spyOn(adminUserService, 'importAll').mockReturnValue(of(new HttpResponse<User[]>({ body: importedUsers })));

        component.usersToImport = usersToImport;
        component.importUsers();

        expect(adminUserService.importAll).toHaveBeenCalledWith(
            [
                { registrationNumber: '1', firstName: 'Max', lastName: 'Mustermann', login: 'login1', email: 'test@mail', visibleRegistrationNumber: '1' },
                { registrationNumber: '2', firstName: 'Ada', lastName: 'Lovelace', login: 'ada', email: 'ada@example.com', visibleRegistrationNumber: '2' },
            ],
            false,
        );
        expect(component.isImporting).toBeFalse();
        expect(component.hasImported).toBeTrue();
        expect(component.notFoundUsers).toMatchObject([
            { registrationNumber: '3', firstName: 'Alan', lastName: 'Turing', login: 'alan', email: 'alan@example.com', visibleRegistrationNumber: '3' },
            { registrationNumber: '4', firstName: 'Grace', lastName: 'Hopper', login: 'grace', email: 'grace@example.com', visibleRegistrationNumber: '4' },
        ]);
    });

    it('should show a generic error when importUsers does not match any import mode', () => {
        fixture.componentRef.setInput('exam', undefined);
        fixture.componentRef.setInput('courseGroup', undefined);
        fixture.componentRef.setInput('tutorialGroup', undefined);
        fixture.componentRef.setInput('adminUserMode', false);
        const alertService = TestBed.inject(AlertService);
        const alertSpy = jest.spyOn(alertService, 'error');

        component.importUsers();

        expect(alertSpy).toHaveBeenCalledWith('artemisApp.importUsers.genericErrorMessage');
        expect(component.isImporting).toBeFalse();
        expect(component.hasImported).toBeFalse();
    });
});
