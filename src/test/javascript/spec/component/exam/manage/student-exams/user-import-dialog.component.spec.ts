import { HttpResponse, HttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { StudentDTO } from 'app/entities/student-dto.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { AlertErrorComponent } from 'app/shared/alert/alert-error.component';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import * as chai from 'chai';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import * as sinon from 'sinon';
import sinonChai from 'sinon-chai';
import { AlertService } from 'app/core/util/alert.service';
import { UsersImportDialogComponent } from 'app/shared/import/users-import-dialog.component';
import { TranslateService } from '@ngx-translate/core';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { Router } from '@angular/router';

chai.use(sinonChai);
const expect = chai.expect;

describe('UsersImportButtonComponent', () => {
    let fixture: ComponentFixture<UsersImportDialogComponent>;
    let component: UsersImportDialogComponent;
    let examManagementService: ExamManagementService;

    const studentCsvColumns = 'REGISTRATION_NUMBER,FIRST_NAME_OF_STUDENT,FAMILY_NAME_OF_STUDENT';

    const course: Course = { id: 1 };
    const exam: Exam = { course, id: 2, title: 'Exam Title' };

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [FormsModule],
            declarations: [
                UsersImportDialogComponent,
                MockDirective(TranslateDirective),
                MockPipe(ArtemisTranslatePipe),
                MockComponent(FaIconComponent),
                MockComponent(HelpIconComponent),
                MockComponent(AlertComponent),
                MockComponent(AlertErrorComponent),
            ],
            providers: [
                { provide: NgbActiveModal, useValue: sinon.createStubInstance(NgbActiveModal) },
                MockProvider(AlertService),
                MockProvider(ExamManagementService),
                MockProvider(HttpClient),
                MockProvider(TranslateService),
                MockProvider(SessionStorageService),
                MockProvider(LocalStorageService),
                MockProvider(Router),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(UsersImportDialogComponent);
                component = fixture.componentInstance;
                examManagementService = TestBed.inject(ExamManagementService);

                component.courseId = course.id!;
                component.exam = exam;
            });
    });

    afterEach(() => {
        sinon.restore();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).to.be.ok;
    });

    it('should reset dialog when selecting csv file', async () => {
        component.usersToImport = [{ registrationNumber: '1', lastName: 'lastName', firstName: 'firstName', login: 'login1' }];
        component.notFoundUsers = [{ registrationNumber: '2', lastName: 'lastName2', firstName: 'firstName2', login: 'login2' }];
        component.hasImported = true;

        const event = { target: { files: [studentCsvColumns] } };
        await component.onCSVFileSelect(event);

        expect(component.usersToImport).to.be.empty;
        expect(component.notFoundUsers).to.be.empty;
    });

    it('should read no students from csv file', async () => {
        const event = { target: { files: [studentCsvColumns] } };
        await component.onCSVFileSelect(event);

        expect(component.usersToImport).to.be.empty;
        expect(component.notFoundUsers).to.be.empty;
    });

    it('should read students from csv file', async () => {
        const csv = `${studentCsvColumns}\n"1","Max","Mustermann"\n"2","John","Wick"`;
        const event = { target: { files: [csv] } };
        await component.onCSVFileSelect(event);

        expect(component.usersToImport.length).to.equal(2);
        expect(component.notFoundUsers).to.be.empty;
    });

    it('should have validation error for invalid csv', async () => {
        // Csv without header
        const invalidCsv = `"1","Max","Mustermann"\n"2","John","Wick"`;

        const event = { target: { files: [invalidCsv] } };
        await component.onCSVFileSelect(event);

        expect(component.validationError).to.not.be.empty;
    });

    it('should import students', function () {
        const studentsToImport: StudentDTO[] = [
            { registrationNumber: '1', firstName: 'Max', lastName: 'Musetermann', login: 'login1' },
            { registrationNumber: '2', firstName: 'Bob', lastName: 'Ross', login: 'login2' },
        ];
        const studentsNotFound: StudentDTO[] = [{ registrationNumber: '2', firstName: 'Bob', lastName: 'Ross', login: 'login2' }];

        const fakeResponse = { body: studentsNotFound } as HttpResponse<StudentDTO[]>;
        sinon.replace(examManagementService, 'addStudentsToExam', sinon.fake.returns(of(fakeResponse)));

        component.usersToImport = studentsToImport;
        component.importUsers();

        expect(examManagementService.addStudentsToExam).to.have.been.calledOnce;
        expect(component.isImporting).to.be.false;
        expect(component.hasImported).to.be.true;
        expect(component.notFoundUsers.length).to.equal(studentsNotFound.length);
    });

    it('should compute invalid student entries', function () {
        let rowNumbersOrNull = component.computeInvalidUserEntries([{ firstnameofstudent: 'Max' }]);
        expect(rowNumbersOrNull).to.equal('2');

        rowNumbersOrNull = component.computeInvalidUserEntries([{ firstnameofstudent: 'Max' }, { registrationnumber: '1' }, { login: 'username' }]);
        expect(rowNumbersOrNull).to.equal('2');

        rowNumbersOrNull = component.computeInvalidUserEntries([{ benutzer: 'Max' }, { benutzername: '1' }, { user: 'username' }]);
        expect(rowNumbersOrNull).to.be.null;

        rowNumbersOrNull = component.computeInvalidUserEntries([{ matriculationnumber: '1' }, { matrikelnummer: '1' }]);
        expect(rowNumbersOrNull).to.be.null;

        rowNumbersOrNull = component.computeInvalidUserEntries([{ firstnameofstudent: 'Max' }, { familynameofstudent: 'Mustermann' }]);
        expect(rowNumbersOrNull).to.equal('2, 3');

        rowNumbersOrNull = component.computeInvalidUserEntries([]);
        expect(rowNumbersOrNull).to.be.null;
    });

    it('should import correctly', function () {
        const importedStudents: StudentDTO[] = [
            { registrationNumber: '1', firstName: 'Max', lastName: 'Musetermann', login: 'login1' },
            { registrationNumber: '2', firstName: 'Bob', lastName: 'Ross', login: 'login2' },
        ];
        const notImportedStudents: StudentDTO[] = [{ registrationNumber: '3', firstName: 'Some', lastName: 'Dude', login: 'login3' }];

        const fakeResponse = { body: notImportedStudents } as HttpResponse<StudentDTO[]>;
        sinon.replace(examManagementService, 'addStudentsToExam', sinon.fake.returns(of(fakeResponse)));

        component.usersToImport = importedStudents.concat(notImportedStudents);
        component.importUsers();

        importedStudents.forEach((student) => expect(component.wasImported(student)).to.be.true);
        notImportedStudents.forEach((student) => expect(component.wasImported(student)).to.be.false);

        expect(component.numberOfUsersImported).to.equal(importedStudents.length);
        expect(component.numberOfUsersNotImported).to.equal(notImportedStudents.length);
    });

    it('should invoke REST call on "Import" but not on "Finish"', function () {
        const studentsToImport: StudentDTO[] = [
            { registrationNumber: '1', firstName: 'Max', lastName: 'Mustermann', login: 'login1' },
            { registrationNumber: '2', firstName: 'Bob', lastName: 'Ross', login: 'login2' },
        ];
        const studentsNotFound: StudentDTO[] = [{ registrationNumber: '3', firstName: 'Some', lastName: 'Dude', login: 'login3' }];

        const fakeResponse = { body: studentsNotFound } as HttpResponse<StudentDTO[]>;
        sinon.replace(examManagementService, 'addStudentsToExam', sinon.fake.returns(of(fakeResponse)));

        component.usersToImport = studentsToImport;

        fixture.detectChanges();

        expect(component.hasImported).to.be.false;
        expect(component.isSubmitDisabled).to.be.false;
        const importButton = fixture.debugElement.query(By.css('#import'));
        expect(importButton).to.exist;

        importButton.nativeElement.click();

        expect(examManagementService.addStudentsToExam).to.have.been.calledOnce;
        expect(component.isImporting).to.be.false;
        expect(component.hasImported).to.be.true;
        expect(component.notFoundUsers.length).to.equal(studentsNotFound.length);

        // Reset call counter
        sinon.restore();
        sinon.replace(examManagementService, 'addStudentsToExam', sinon.fake.returns(of(fakeResponse)));

        component.hasImported = true;
        fixture.detectChanges();

        const finishButton = fixture.debugElement.query(By.css('#finish-button'));
        expect(finishButton).to.exist;

        finishButton.nativeElement.click();

        expect(examManagementService.addStudentsToExam).to.not.have.been.called;
    });
});
