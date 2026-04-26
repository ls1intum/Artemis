import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { CourseGroup } from 'app/core/course/shared/entities/course.model';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { StudentDTO } from 'app/core/shared/entities/student-dto.model';
import { User } from 'app/core/user/user.model';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { ExamAddStudentsDialogComponent } from 'app/exam/manage/students/add-students-dialog/exam-add-students-dialog.component';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamUser } from 'app/exam/shared/entities/exam-user.model';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateService } from '@ngx-translate/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { of, throwError } from 'rxjs';
import { MockProvider } from 'ng-mocks';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ExamAddStudentsDialogComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ExamAddStudentsDialogComponent;
    let fixture: ComponentFixture<ExamAddStudentsDialogComponent>;
    let courseManagementService: CourseManagementService;
    let examManagementService: ExamManagementService;
    let alertService: AlertService;

    const courseId = 42;
    const examId = 21;

    const studentAlice = {
        id: 1,
        login: 'alice',
        name: 'Alice Doe',
        visibleRegistrationNumber: 'M123',
        email: 'alice@tum.de',
    } as User;
    const studentBob = {
        id: 2,
        login: 'bob',
        name: 'Bob Roe',
        visibleRegistrationNumber: 'M456',
        email: 'bob@tum.de',
    } as User;
    const studentWithoutLogin = {
        id: 3,
        name: 'No Login',
    } as User;

    async function openDialogAndRender() {
        component.openDialog();
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    }

    async function typeInSearch(value: string) {
        const searchInput = document.body.querySelector('input[type="text"]') as HTMLInputElement;
        searchInput.value = value;
        searchInput.dispatchEvent(new Event('input'));
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    }

    function getRegisterButtonForLogin(login: string): HTMLButtonElement {
        const rows = Array.from(document.body.querySelectorAll('tbody tr'));
        const matchingRow = rows.find((row) => row.textContent?.includes(login));
        return matchingRow?.querySelector('button') as HTMLButtonElement;
    }

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExamAddStudentsDialogComponent],
            providers: [
                MockProvider(CourseManagementService),
                MockProvider(ExamManagementService),
                MockProvider(AlertService),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamAddStudentsDialogComponent);
        component = fixture.componentInstance;
        courseManagementService = TestBed.inject(CourseManagementService);
        examManagementService = TestBed.inject(ExamManagementService);
        alertService = TestBed.inject(AlertService);
        fixture.componentRef.setInput('courseId', courseId);
        fixture.componentRef.setInput('exam', {
            id: examId,
            examUsers: [],
        } as Exam);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('openDialog should load students', () => {
        vi.spyOn(courseManagementService, 'getAllUsersInCourseGroup').mockReturnValue(
            of(
                new HttpResponse({
                    body: [studentBob, studentWithoutLogin, studentAlice],
                }),
            ),
        );

        component.searchText.set('will-be-reset');

        component.openDialog();

        expect(component.dialogVisible()).toBe(true);
        expect(component.searchText()).toBe('');
        expect(courseManagementService.getAllUsersInCourseGroup).toHaveBeenCalledWith(courseId, CourseGroup.STUDENTS);
        expect(component.isLoading()).toBe(false);
        expect(component.allCourseStudents()).toEqual([studentAlice, studentBob]);
    });

    it('registerStudent should call service and update localRegisteredLogins', async () => {
        vi.spyOn(courseManagementService, 'getAllUsersInCourseGroup').mockReturnValue(of(new HttpResponse({ body: [studentAlice] })));
        const addStudentToExamSpy = vi.spyOn(examManagementService, 'addStudentToExam').mockReturnValue(of(new HttpResponse<StudentDTO>({ body: new StudentDTO() })));
        const emitSpy = vi.spyOn(component.studentsChanged, 'emit');

        await openDialogAndRender();
        const registerButton = getRegisterButtonForLogin(studentAlice.login!);
        registerButton.click();

        expect(addStudentToExamSpy).toHaveBeenCalledOnce();
        expect(addStudentToExamSpy).toHaveBeenCalledWith(courseId, examId, studentAlice.login);
        expect(component.isAlreadyRegistered(studentAlice)).toBe(true);
        expect(emitSpy).toHaveBeenCalledOnce();
    });

    it('registerStudent should show alert on error', async () => {
        vi.spyOn(courseManagementService, 'getAllUsersInCourseGroup').mockReturnValue(of(new HttpResponse({ body: [studentAlice] })));
        const alertErrorSpy = vi.spyOn(alertService, 'error');
        vi.spyOn(examManagementService, 'addStudentToExam').mockReturnValue(throwError(() => new Error('failed')));

        await openDialogAndRender();
        const registerButton = getRegisterButtonForLogin(studentAlice.login!);
        registerButton.click();

        expect(alertErrorSpy).toHaveBeenCalledOnce();
        expect(alertErrorSpy).toHaveBeenCalledWith('artemisApp.examManagement.examStudents.addDialog.errorRegisterStudent');
        expect(component.isAlreadyRegistered(studentAlice)).toBe(false);
    });

    it('isAlreadyRegistered should return true for registered logins', () => {
        const examUser = { user: { login: studentBob.login } as User } as ExamUser;
        fixture.componentRef.setInput('exam', {
            id: examId,
            examUsers: [examUser],
        } as Exam);

        expect(component.isAlreadyRegistered(studentBob)).toBe(true);
        expect(component.isAlreadyRegistered(studentAlice)).toBe(false);
    });

    it('search filtering should work correctly', async () => {
        vi.spyOn(courseManagementService, 'getAllUsersInCourseGroup').mockReturnValue(
            of(
                new HttpResponse({
                    body: [studentAlice, studentBob],
                }),
            ),
        );

        await openDialogAndRender();

        expect(component.userSearchResults()).toEqual([studentAlice, studentBob]);

        await typeInSearch('alice');
        expect(component.userSearchResults()).toEqual([studentAlice]);

        await typeInSearch('m456');
        expect(component.userSearchResults()).toEqual([studentBob]);

        await typeInSearch('TUM.DE');
        expect(component.userSearchResults()).toEqual([studentAlice, studentBob]);

        await typeInSearch('unknown');
        expect(component.userSearchResults()).toEqual([]);
        expect(component.noSearchResults()).toBe(true);
    });
});
