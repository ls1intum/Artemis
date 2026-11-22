import { TumAetUiDialogComponent, TumAetUiTableDirective, TumAetUiTableLayoutComponent } from '@tumaet/ui-angular';
import { vi } from 'vitest';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { Course } from 'app/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamApi } from 'app/openapi/api/exam-api';
import { ExamRegistrationResult } from 'app/openapi/model/exam-registration-result';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { AlertService } from 'app/foundation/service/alert.service';
import { UsersImportDialogComponent } from 'app/shared-ui/user-import/dialog/users-import-dialog.component';
import { TranslateService } from '@ngx-translate/core';
import { Router } from '@angular/router';
import * as fs from 'fs';
import * as path from 'path';
import { ExamUserDTO } from 'app/exam/shared/entities/exam-user-dto.model';
import * as readUsersFromCsv from 'app/shared-ui/user-import/util/read-users-from-csv';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { AdminUserService } from 'app/account/user/shared/admin-user.service';
import { User } from 'app/account/user/user.model';
import { TutorialGroupApi } from 'app/openapi/api/tutorial-group-api';

describe('UsersImportDialogComponent', () => {
    let fixture: ComponentFixture<UsersImportDialogComponent>;
    let component: UsersImportDialogComponent;
    let examApi: ExamApi;
    let tutorialGroupApiService: TutorialGroupApi;
    let adminUserService: AdminUserService;

    const studentCsvColumns = 'REGISTRATION_NUMBER,FIRST_NAME_OF_STUDENT,FAMILY_NAME_OF_STUDENT';

    const course: Course = { id: 1 };
    const exam: Exam = { course, id: 2, title: 'Exam Title' };

    const testDir = path.join(process.cwd(), 'src', 'test', 'javascript', 'spec', 'helpers', 'sample', 'user-import');
    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [FormsModule, FaIconComponent, UsersImportDialogComponent],
            providers: [
                MockProvider(AlertService),
                MockProvider(ExamApi),
                MockProvider(TutorialGroupApi),
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
                        MockPipe(ArtemisTranslatePipe, (value: string) => value),
                        MockComponent(HelpIconComponent),
                        FaIconComponent,
                        TumAetUiDialogComponent,
                        TumAetUiTableDirective,
                        TumAetUiTableLayoutComponent,
                    ],
                },
            })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(UsersImportDialogComponent);
                component = fixture.componentInstance;
                examApi = TestBed.inject(ExamApi);
                tutorialGroupApiService = TestBed.inject(TutorialGroupApi);
                adminUserService = TestBed.inject(AdminUserService);

                fixture.componentRef.setInput('courseId', course.id!);
                fixture.componentRef.setInput('exam', exam);
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('selects only the active import mode for the common preview', () => {
        const regularUser = { registrationNumber: '123', firstName: 'First', lastName: 'Last', login: 'regular', email: 'regular@example.org', isTestUser: true };
        const examUser = { registrationNumber: '456', firstName: 'Exam', lastName: 'User', login: 'exam', email: 'exam@example.org', room: '10.101', seat: '25F' };
        component.usersToImport.set([regularUser]);
        component.examUsersToImport.set([examUser]);
        expect(component.previewUsers()).toEqual([regularUser]);
        fixture.componentRef.setInput('examUserMode', true);
        expect(component.previewUsers()).toEqual([examUser]);
        component.examUsersToImport.set([]);
        expect(component.previewUsers()).toEqual([]);
        fixture.componentRef.setInput('examUserMode', false);
        expect(component.previewUsers()).toEqual([regularUser]);
    });

    it.each([
        { examMode: false, adminMode: false, columns: ['#', 'Matriculation number', 'Login', 'Email', 'First name', 'Last name'] },
        { examMode: false, adminMode: true, columns: ['#', 'Matriculation number', 'Login', 'Email', 'First name', 'Last name', 'Test user'] },
        { examMode: true, adminMode: false, columns: ['#', 'Matriculation number', 'Login', 'Email', 'First name', 'Last name', 'Room', 'Seat'] },
        { examMode: true, adminMode: true, columns: ['#', 'Matriculation number', 'Login', 'Email', 'First name', 'Last name', 'Room', 'Seat'] },
    ])('renders all preview values and mode-specific columns for exam=$examMode, admin=$adminMode', async ({ examMode, adminMode, columns }) => {
        const regularUser = { registrationNumber: '123', firstName: 'First', lastName: 'Last', login: 'regular', email: 'regular@example.org', isTestUser: true };
        const examUser = { registrationNumber: '456', firstName: 'Exam', lastName: 'User', login: 'exam', email: 'exam@example.org', room: '10.101', seat: '25F' };
        fixture.componentRef.setInput('examUserMode', examMode);
        fixture.componentRef.setInput('adminUserMode', adminMode);
        component.usersToImport.set([regularUser]);
        component.examUsersToImport.set([examUser]);
        component.dialogVisible.set(true);
        fixture.detectChanges();
        await fixture.whenStable();
        const table = document.querySelector<HTMLTableElement>('[data-testid="user-import-preview"]')!;
        expect(Array.from(table.querySelectorAll('th'), (cell) => cell.textContent?.trim())).toEqual(columns);
        const cells = Array.from(table.querySelectorAll('tbody tr td'), (cell) => cell.textContent?.trim());
        expect(cells.slice(0, 6)).toEqual(examMode ? ['1', '456', 'exam', 'exam@example.org', 'Exam', 'User'] : ['1', '123', 'regular', 'regular@example.org', 'First', 'Last']);
        if (examMode) {
            expect(cells.slice(6)).toEqual(['10.101', '25F']);
        } else if (adminMode) {
            expect(table.querySelector('tbody tr td:last-child fa-icon')).not.toBeNull();
        }
    });

    it('retains success, missing-user and rejected-staff row feedback in the shared preview', async () => {
        const users = ['success', 'missing', 'staff'].map((login) => ({ registrationNumber: login, firstName: 'First', lastName: 'Last', login, email: login + '@example.org' }));
        component.usersToImport.set(users);
        component.hasImported.set(true);
        component.notFoundUsers = [users[1]];
        component.rejectedStaffUsers = [users[2]];
        component.dialogVisible.set(true);
        fixture.detectChanges();
        await fixture.whenStable();
        const rows = document.querySelectorAll('[data-testid="user-import-preview"] tbody tr');
        expect(rows).toHaveLength(3);
        expect(rows[0].classList.contains('import-success')).toBe(true);
        expect(rows[1].classList.contains('import-not-found')).toBe(true);
        expect(rows[2].classList.contains('import-warning')).toBe(true);
    });

    it('should open and close dialog', () => {
        expect(component.dialogVisible()).toBe(false);

        component.open();
        expect(component.dialogVisible()).toBe(true);

        component.close();
        expect(component.dialogVisible()).toBe(false);
    });

    it('should emit importCompleted on finish', () => {
        const emitSpy = vi.spyOn(component.importCompleted, 'emit');

        component.onFinish();

        expect(emitSpy).toHaveBeenCalledOnce();
        expect(component.dialogVisible()).toBe(false);
    });

    it('should reset dialog when selecting csv file', async () => {
        component.usersToImport.set([{ registrationNumber: '1', lastName: 'lastName', firstName: 'firstName', login: 'login1', email: 'test@mail' }]);
        component.notFoundUsers = [{ registrationNumber: '2', lastName: 'lastName2', firstName: 'firstName2', login: 'login2', email: 'test@mail' }];
        component.rejectedStaffUsers = [{ registrationNumber: '3', lastName: 'lastName3', firstName: 'firstName3', login: 'login3', email: 'staff@mail' }];
        component.hasImported.set(true);

        const event = { target: { files: [studentCsvColumns] } };
        await component.onCSVFileSelect(event as unknown as Event);

        expect(component.usersToImport()).toHaveLength(0);
        expect(component.notFoundUsers).toHaveLength(0);
        expect(component.rejectedStaffUsers).toHaveLength(0);
    });

    it('should read no students from csv file', async () => {
        const event = { target: { files: [studentCsvColumns] } };
        await component.onCSVFileSelect(event as unknown as Event);

        expect(component.usersToImport()).toHaveLength(0);
        expect(component.notFoundUsers).toHaveLength(0);
        expect(component.noUsersFoundError()).toBe(true);
    });

    it('should read students from csv file', async () => {
        const csv = `${studentCsvColumns}\n"1","Max","Mustermann"\n"2","John","Wick"`;
        const event = { target: { files: [csv] } };
        await component.onCSVFileSelect(event as unknown as Event);

        expect(component.usersToImport()).toHaveLength(2);
        expect(component.notFoundUsers).toHaveLength(0);
        expect(component.noUsersFoundError()).toBeUndefined();
    });

    it('should have validation error for invalid csv', async () => {
        // Csv without header
        const invalidCsv = `"1","Max","Mustermann"\n"2","John","Wick"`;

        const event = { target: { files: [invalidCsv] } };
        await component.onCSVFileSelect(event as unknown as Event);

        expect(component.validationError()).toHaveLength(1);
    });

    it('should stop parsing and show a generic error when csv parsing fails unexpectedly', async () => {
        vi.spyOn(readUsersFromCsv, 'readStudentDTOsFromCSVFile').mockRejectedValue(new Error('parse failed'));
        const alertService = TestBed.inject(AlertService);
        const alertSpy = vi.spyOn(alertService, 'error');
        const event = { target: { files: [studentCsvColumns], value: 'students.csv' } };

        await component.onCSVFileSelect(event as unknown as Event);

        expect(alertSpy).toHaveBeenCalledWith('artemisApp.importUsers.genericErrorMessage');
        expect(component.isParsing()).toBe(false);
        expect(event.target.value).toBe('');
    });

    it('should import students', () => {
        fixture.componentRef.setInput('examUserMode', false);
        const studentsToImport: ExamUserDTO[] = [
            { registrationNumber: '1', firstName: 'Max', lastName: 'Musetermann', login: 'login1', email: 'test@mail' },
            { registrationNumber: '2', firstName: 'Bob', lastName: 'Ross', login: 'login2', email: 'test@mail' },
        ];
        const studentsNotFound: ExamUserDTO[] = [{ registrationNumber: '2', firstName: 'Bob', lastName: 'Ross', login: 'login2', email: 'test@mail' }];

        const fakeResponse: ExamRegistrationResult = { notFoundStudents: studentsNotFound, rejectedStaffUsers: [] };
        vi.spyOn(examApi, 'addStudentsToExam').mockReturnValue(of(fakeResponse));

        component.usersToImport.set(studentsToImport);
        component.importUsers();

        expect(examApi.addStudentsToExam).toHaveBeenCalledOnce();
        expect(component.isImporting()).toBe(false);
        expect(component.hasImported()).toBe(true);
        expect(component.notFoundUsers).toHaveLength(studentsNotFound.length);
    });

    describe('should read students from csv files', () => {
        const testFiles = fs.readdirSync(testDir).filter((testFile) => testFile.localeCompare('UserImportEmailOnlySampleFile.csv') !== 0);

        test.each(testFiles)('reading from %s', async (testFileName: string) => {
            const pathToTestFile = path.join(testDir, testFileName);
            const csv = fs.readFileSync(pathToTestFile, 'utf-8');
            const event = { target: { files: [csv] } };
            await component.onCSVFileSelect(event as unknown as Event);

            expect(component.usersToImport()).toHaveLength(5);

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

            expect(component.usersToImport()).toEqual(expectedStudentDTOs);
        });
    });

    it('should read students from csv with email only', async () => {
        const pathToTestFile = path.join(testDir, 'UserImportEmailOnlySampleFile.csv');
        const csv = fs.readFileSync(pathToTestFile, 'utf-8');
        const event = { target: { files: [csv] } };

        await component.onCSVFileSelect(event as unknown as Event);

        expect(component.usersToImport()).toHaveLength(5);

        const expectedStudentDTOs: ExamUserDTO[] = [
            { registrationNumber: '', firstName: '', lastName: '', login: '', email: 'testuser1@mail.com' },
            { registrationNumber: '', firstName: '', lastName: '', login: '', email: 'testuser2@mail.com' },
            { registrationNumber: '', firstName: '', lastName: '', login: '', email: 'testuser3@mail.com' },
            { registrationNumber: '', firstName: '', lastName: '', login: '', email: 'testuser4@mail.com' },
            { registrationNumber: '', firstName: '', lastName: '', login: '', email: 'testuser5@mail.com' },
        ];

        expect(component.usersToImport()).toEqual(expectedStudentDTOs);
    });

    it('should read students from csv with room/seat information', async () => {
        const testDir = path.join(process.cwd(), 'src', 'test', 'javascript', 'spec', 'helpers', 'sample', 'exam-user-import');
        fixture.componentRef.setInput('examUserMode', true);

        const pathToTestFile = path.join(testDir, 'UserImportWithRoomAndSeatInfo.csv');
        const csv = fs.readFileSync(pathToTestFile, 'utf-8');
        const event = { target: { files: [csv] } };

        await component.onCSVFileSelect(event as unknown as Event);

        expect(component.examUsersToImport()).toHaveLength(4);

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

        expect(component.examUsersToImport()).toEqual(expectedStudentDTOs);
    });

    it('should expose multiple invalid student row numbers via validationError', async () => {
        const csv = `${studentCsvColumns}\n"","Max","Mustermann"\n"","John","Wick"`;
        const event = { target: { files: [csv], value: 'students.csv' } };

        await component.onCSVFileSelect(event as unknown as Event);

        expect(component.validationError()).toBe('2, 3');
    });

    it('should import correctly', () => {
        fixture.componentRef.setInput('examUserMode', false);
        const importedStudents: ExamUserDTO[] = [
            { registrationNumber: '1', firstName: 'Max', lastName: 'Musetermann', login: 'login1', email: '' },
            { registrationNumber: '2', firstName: 'Bob', lastName: 'Ross', login: 'login2', email: '' },
        ];
        const notImportedStudents: ExamUserDTO[] = [{ registrationNumber: '3', firstName: 'Some', lastName: 'Dude', login: 'login3', email: '' }];

        const fakeResponse: ExamRegistrationResult = { notFoundStudents: notImportedStudents, rejectedStaffUsers: [] };
        vi.spyOn(examApi, 'addStudentsToExam').mockReturnValue(of(fakeResponse));

        component.usersToImport.set(importedStudents.concat(notImportedStudents));
        component.importUsers();

        importedStudents.forEach((student) => expect(component.wasImported(student)).toBe(true));
        notImportedStudents.forEach((student) => expect(component.wasImported(student)).toBe(false));
        expect(component.numberOfUsersImported).toBe(importedStudents.length);
        expect(component.numberOfUsersNotFound).toBe(notImportedStudents.length);
    });

    it('should invoke REST call on "Import" but not on "Finish"', () => {
        fixture.componentRef.setInput('examUserMode', false);
        const studentsToImport: ExamUserDTO[] = [
            { registrationNumber: '1', firstName: 'Max', lastName: 'Mustermann', login: 'login1', email: '' },
            { registrationNumber: '2', firstName: 'Bob', lastName: 'Ross', login: 'login2', email: '' },
        ];
        const studentsNotFound: ExamUserDTO[] = [{ registrationNumber: '3', firstName: 'Some', lastName: 'Dude', login: 'login3', email: '' }];

        const fakeResponse: ExamRegistrationResult = { notFoundStudents: studentsNotFound, rejectedStaffUsers: [] };
        vi.spyOn(examApi, 'addStudentsToExam').mockReturnValue(of(fakeResponse));

        component.open();
        // Set usersToImport after open() since open() calls resetDialog() which clears the array
        component.usersToImport.set(studentsToImport);

        fixture.detectChanges();

        expect(component.hasImported()).toBe(false);
        expect(component.isSubmitDisabled).toBe(false);

        // The "Import" button is wired to importUsers() via its (click) handler.
        component.importUsers();

        expect(examApi.addStudentsToExam).toHaveBeenCalledOnce();
        expect(component.isImporting()).toBe(false);
        expect(component.hasImported()).toBe(true);
        expect(component.notFoundUsers).toHaveLength(studentsNotFound.length);

        vi.spyOn(examApi, 'addStudentsToExam').mockReturnValue(of(fakeResponse));

        component.hasImported.set(true);
        fixture.detectChanges();

        // The "Finish" button is wired to onFinish() via its (click) handler and must NOT trigger another REST call.
        component.onFinish();
        expect(examApi.addStudentsToExam).toHaveBeenCalledOnce();
    });

    it('should expose validationError for invalid exam user csv rows', async () => {
        fixture.componentRef.setInput('examUserMode', true);
        vi.spyOn(readUsersFromCsv, 'readExamUserDTOsFromCSVFile').mockResolvedValue({
            ok: false,
            invalidRowIndices: [2, 4],
        });
        const event = { target: { files: [studentCsvColumns], value: 'exam-users.csv' } };

        await component.onCSVFileSelect(event as unknown as Event);

        expect(component.validationError()).toBe('2, 4');
        expect(component.noUsersFoundError()).toBeUndefined();
        expect(component.examUsersToImport()).toHaveLength(0);
        expect(component.isParsing()).toBe(false);
        expect(event.target.value).toBe('');
    });

    it('should set noUsersFoundError when exam user csv contains no entries', async () => {
        fixture.componentRef.setInput('examUserMode', true);
        vi.spyOn(readUsersFromCsv, 'readExamUserDTOsFromCSVFile').mockResolvedValue({
            ok: true,
            examUsers: [],
        });
        const event = { target: { files: [studentCsvColumns], value: 'exam-users.csv' } };

        await component.onCSVFileSelect(event as unknown as Event);

        expect(component.noUsersFoundError()).toBe(true);
        expect(component.validationError()).toBeUndefined();
        expect(component.examUsersToImport()).toHaveLength(0);
        expect(component.isParsing()).toBe(false);
        expect(event.target.value).toBe('');
    });

    it('should show a generic error and stop importing on save error', () => {
        const alertService = TestBed.inject(AlertService);
        const alertSpy = vi.spyOn(alertService, 'error');
        component.isImporting.set(true);

        component.onSaveError();

        expect(alertSpy).toHaveBeenCalledWith('artemisApp.importUsers.genericErrorMessage');
        expect(component.isImporting()).toBe(false);
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

        vi.spyOn(tutorialGroupApiService, 'importRegistrations').mockReturnValue(of(generatedStudents));

        component.usersToImport.set(studentsToImport);
        component.importUsers();

        expect(tutorialGroupApiService.importRegistrations).toHaveBeenCalledWith(course.id, 5, studentsToImport);
        expect(component.isImporting()).toBe(false);
        expect(component.hasImported()).toBe(true);
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

        vi.spyOn(adminUserService, 'importAll').mockReturnValue(of(new HttpResponse<User[]>({ body: importedUsers })));

        component.usersToImport.set(usersToImport);
        component.importUsers();

        expect(adminUserService.importAll).toHaveBeenCalledWith([
            { registrationNumber: '1', firstName: 'Max', lastName: 'Mustermann', login: 'login1', email: 'test@mail', visibleRegistrationNumber: '1' },
            { registrationNumber: '2', firstName: 'Ada', lastName: 'Lovelace', login: 'ada', email: 'ada@example.com', visibleRegistrationNumber: '2' },
        ]);
        expect(component.isImporting()).toBe(false);
        expect(component.hasImported()).toBe(true);
        expect(component.notFoundUsers).toMatchObject([
            { registrationNumber: '3', firstName: 'Alan', lastName: 'Turing', login: 'alan', email: 'alan@example.com', visibleRegistrationNumber: '3' },
            { registrationNumber: '4', firstName: 'Grace', lastName: 'Hopper', login: 'grace', email: 'grace@example.com', visibleRegistrationNumber: '4' },
        ]);
    });

    it('should show a generic error when importUsers does not match any import mode', () => {
        fixture.componentRef.setInput('exam', undefined);
        fixture.componentRef.setInput('courseRoleSlug', undefined);
        fixture.componentRef.setInput('tutorialGroup', undefined);
        fixture.componentRef.setInput('adminUserMode', false);
        const alertService = TestBed.inject(AlertService);
        const alertSpy = vi.spyOn(alertService, 'error');

        component.importUsers();

        expect(alertSpy).toHaveBeenCalledWith('artemisApp.importUsers.genericErrorMessage');
        expect(component.isImporting()).toBe(false);
        expect(component.hasImported()).toBe(false);
    });

    it('should show staff rejection alert when bulk import rejects staff', () => {
        fixture.componentRef.setInput('examUserMode', true);
        const studentsToImport: ExamUserDTO[] = [
            { registrationNumber: '1', firstName: 'Max', lastName: 'Mustermann', login: 'login1', email: '' },
            { registrationNumber: '2', firstName: 'Edith', lastName: 'Editor', login: 'editor1', email: '' },
            { registrationNumber: '3', firstName: 'Toni', lastName: 'Tutor', login: 'tutor1', email: '' },
        ];
        const rejectedStaff: ExamUserDTO[] = [
            { registrationNumber: '2', firstName: 'Edith', lastName: 'Editor', login: 'editor1', email: '' },
            { registrationNumber: '3', firstName: 'Toni', lastName: 'Tutor', login: 'tutor1', email: '' },
        ];
        const fakeResponse: ExamRegistrationResult = { notFoundStudents: [], rejectedStaffUsers: rejectedStaff };
        vi.spyOn(examApi, 'addStudentsToExam').mockReturnValue(of(fakeResponse));

        component.examUsersToImport.set(studentsToImport);
        component.importUsers();

        expect(component.notFoundUsers).toHaveLength(0);
        expect(component.rejectedStaffUsers).toHaveLength(2);
        rejectedStaff.forEach((student) => expect(component.wasNotFound(student)).toBe(false));
        rejectedStaff.forEach((student) => expect(component.wasRejectedStaff(student)).toBe(true));
        rejectedStaff.forEach((student) => expect(component.wasImported(student)).toBe(false));
        expect(component.numberOfUsersNotFound).toBe(0);
        expect(component.numberOfStaffRejected).toBe(2);
        expect(component.numberOfUsersImported).toBe(1);
    });

    it('should not show staff rejection alert when no staff is rejected', () => {
        fixture.componentRef.setInput('examUserMode', true);
        const studentsToImport: ExamUserDTO[] = [{ registrationNumber: '1', firstName: 'Max', lastName: 'Mustermann', login: 'login1', email: '' }];
        const fakeResponse: ExamRegistrationResult = { notFoundStudents: [], rejectedStaffUsers: [] };
        vi.spyOn(examApi, 'addStudentsToExam').mockReturnValue(of(fakeResponse));
        const alertService = TestBed.inject(AlertService);
        const alertSpy = vi.spyOn(alertService, 'error');

        component.examUsersToImport.set(studentsToImport);
        component.importUsers();

        expect(alertSpy).not.toHaveBeenCalled();
    });

    it('should count both not-found and rejected staff as not imported', () => {
        fixture.componentRef.setInput('examUserMode', true);
        const studentsToImport: ExamUserDTO[] = [
            { registrationNumber: '1', firstName: 'Max', lastName: 'Mustermann', login: 'login1', email: '' },
            { registrationNumber: '2', firstName: 'Edith', lastName: 'Editor', login: 'editor1', email: '' },
            { registrationNumber: '3', firstName: 'Ghost', lastName: 'User', login: 'ghost1', email: '' },
        ];
        const notFoundStudents: ExamUserDTO[] = [{ registrationNumber: '3', firstName: 'Ghost', lastName: 'User', login: 'ghost1', email: '' }];
        const rejectedStaff: ExamUserDTO[] = [{ registrationNumber: '2', firstName: 'Edith', lastName: 'Editor', login: 'editor1', email: '' }];
        const fakeResponse: ExamRegistrationResult = { notFoundStudents, rejectedStaffUsers: rejectedStaff };
        vi.spyOn(examApi, 'addStudentsToExam').mockReturnValue(of(fakeResponse));

        component.examUsersToImport.set(studentsToImport);
        component.importUsers();

        expect(component.numberOfUsersImported).toBe(1);
        expect(component.numberOfUsersNotFound).toBe(1);
        expect(component.numberOfStaffRejected).toBe(1);
        expect(component.wasImported(studentsToImport[0])).toBe(true);
        expect(component.wasNotFound(studentsToImport[0])).toBe(false);
        expect(component.wasRejectedStaff(studentsToImport[0])).toBe(false);

        // This one was rejected staff
        expect(component.wasNotFound(studentsToImport[1])).toBe(false);
        expect(component.wasRejectedStaff(studentsToImport[1])).toBe(true);
        expect(component.wasImported(studentsToImport[1])).toBe(false);

        // This one was not found
        expect(component.wasNotFound(studentsToImport[2])).toBe(true);
        expect(component.wasRejectedStaff(studentsToImport[2])).toBe(false);
        expect(component.wasImported(studentsToImport[2])).toBe(false);
    });
});
