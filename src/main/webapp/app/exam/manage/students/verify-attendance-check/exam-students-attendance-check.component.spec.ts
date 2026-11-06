import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, UrlSegment, convertToParamMap, provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { User } from 'app/account/user/user.model';
import { Course } from 'app/course/shared/entities/course.model';
import { ExamUserAttendanceCheckDTO } from 'app/exam/shared/entities/exam-users-attendance-check-dto.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { ExamStudentsAttendanceCheckComponent } from 'app/exam/manage/students/verify-attendance-check/exam-students-attendance-check.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { SortService } from 'app/foundation/service/sort.service';
import { SortDirective } from 'app/foundation/sort/directive/sort.directive';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { Observable, of, throwError } from 'rxjs';
import { MockDirective, MockPipe } from 'ng-mocks';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import dayjs from 'dayjs/esm';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';

describe('ExamStudentsAttendanceCheckComponent', () => {
    const course = { id: 1 } as Course;
    const user1 = { id: 1, name: 'name', login: 'login' } as User;
    const user2 = { id: 2, login: 'user2' } as User;
    const dateTime = dayjs().subtract(1, 'hour');
    const examWithCourse: Exam = { course, id: 2, examUsers: [{ user: user1 }, { user: user2 }], startDate: dateTime } as Exam;

    const route = {
        snapshot: { paramMap: convertToParamMap({ courseId: course.id }) },
        url: new Observable<UrlSegment[]>(),
        data: of({ exam: examWithCourse }),
    } as any as ActivatedRoute;

    let component: ExamStudentsAttendanceCheckComponent;
    let fixture: ComponentFixture<ExamStudentsAttendanceCheckComponent>;
    let examManagementService: ExamManagementService;
    let sortService: SortService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExamStudentsAttendanceCheckComponent, MockDirective(TranslateDirective), MockDirective(SortDirective), MockPipe(ArtemisTranslatePipe)],
            providers: [
                provideRouter([]),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                { provide: ActivatedRoute, useValue: route },
                { provide: AccountService, useClass: MockAccountService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamStudentsAttendanceCheckComponent);
        component = fixture.componentInstance;
        examManagementService = TestBed.inject(ExamManagementService);
        sortService = TestBed.inject(SortService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
        fixture.destroy();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
        expect(component.courseId).toEqual(course.id);
        expect(component.exam).toEqual(examWithCourse);
        expect(component.hasExamStarted()).toBe(true);
    });

    it('should not load and not wait forever when the exam has not started', () => {
        const startDate = examWithCourse.startDate;
        examWithCourse.startDate = dayjs().add(1, 'hour');
        try {
            const examServiceSpy = vi.spyOn(examManagementService, 'verifyExamUserAttendance');

            fixture.detectChanges();

            expect(component.hasExamStarted()).toBe(false);
            expect(examServiceSpy).not.toHaveBeenCalled();
            expect(component.isLoading()).toBe(false);
        } finally {
            examWithCourse.startDate = startDate;
        }
    });

    it('should distinguish an automatic decision from signed attendance for an unchecked student', async () => {
        const unsigned = { id: 5, login: 'student1', started: true, attendanceChecked: false } as ExamUserAttendanceCheckDTO;
        vi.spyOn(examManagementService, 'verifyExamUserAttendance').mockReturnValue(of(new HttpResponse({ body: [unsigned] })));
        fixture.detectChanges();
        await fixture.whenStable();
        const select = fixture.nativeElement.querySelector('[data-testid="manual-attendance"]') as HTMLSelectElement;
        expect(select.value).toBe('');
        expect(select.selectedOptions[0].getAttribute('jhiTranslate')).toBe('artemisApp.examManagement.examStudents.manualAttendance.automatic');
        expect(select.parentElement?.querySelector('span')).toBeNull();
        expect(component.uncheckedCount()).toBe(1);
    });

    it('should save a manual absence after conduction and retain the signature', () => {
        const signed = { id: 5, login: 'student1', signingImagePath: 'signature.png', attendanceOverride: false } as ExamUserAttendanceCheckDTO;
        const save = vi.spyOn(examManagementService, 'updateAttendance').mockReturnValue(of(undefined));
        vi.spyOn(examManagementService, 'verifyExamUserAttendance').mockReturnValue(of(new HttpResponse({ body: [signed] })));
        fixture.detectChanges();
        component.hasExamEnded.set(true);
        component.setAttendance(signed, 'false');
        expect(save).toHaveBeenCalledWith(course.id, examWithCourse.id, 5, false);
        expect(component.allExamUsersAttendanceCheck()[0].signingImagePath).toBe('signature.png');
        expect(component.savingAttendance()).toBeUndefined();
    });

    it('should identify signed students with incomplete identity checks', () => {
        const users = [
            { id: 5, login: 'incomplete', started: true, signingImagePath: 'signature.png', attendanceChecked: false },
            { id: 6, login: 'complete', started: true, signingImagePath: 'signature.png', attendanceChecked: true },
        ] as ExamUserAttendanceCheckDTO[];
        vi.spyOn(examManagementService, 'verifyExamUserAttendance').mockReturnValue(of(new HttpResponse({ body: users })));
        fixture.detectChanges();
        const rows = fixture.nativeElement.querySelectorAll('[data-testid="attendance-check-row"]');
        expect(rows[0].querySelector('[data-testid="attendance-check-status"]').getAttribute('data-checked')).toBe('false');
        expect(rows[1].querySelector('[data-testid="attendance-check-status"]').getAttribute('data-checked')).toBe('true');
        expect(rows[0].querySelectorAll('img')).toHaveLength(2);
        expect(component.uncheckedCount()).toBe(1);
    });

    it('should block further edits after a failed refresh and recover through retry', async () => {
        const original = { id: 5, login: 'student1', started: true, attendanceChecked: false } as ExamUserAttendanceCheckDTO;
        const updated = { ...original, attendanceOverride: false, attendanceChecked: true };
        vi.spyOn(examManagementService, 'verifyExamUserAttendance')
            .mockReturnValueOnce(of(new HttpResponse({ body: [original] })))
            .mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })))
            .mockReturnValueOnce(of(new HttpResponse({ body: [updated] })));
        const save = vi.spyOn(examManagementService, 'updateAttendance').mockReturnValue(of(undefined));
        fixture.detectChanges();
        component.hasExamEnded.set(true);
        component.setAttendance(original, 'false');
        fixture.detectChanges();
        await fixture.whenStable();
        expect(fixture.nativeElement.querySelector('[data-testid="manual-attendance"]').disabled).toBe(true);
        component.setAttendance(original, 'true');
        expect(save).toHaveBeenCalledOnce();
        const retry = fixture.nativeElement.querySelector('[data-testid="retry-attendance"]') as HTMLButtonElement;
        expect(retry).toBeTruthy();
        retry.click();
        fixture.detectChanges();
        await fixture.whenStable();
        expect(component.allExamUsersAttendanceCheck()).toEqual([updated]);
        expect(component.uncheckedCount()).toBe(0);
        expect(fixture.nativeElement.querySelector('[data-testid="manual-attendance"]').disabled).toBe(false);
    });

    it('should restore the persisted selection after a failed save and permit retry', async () => {
        const original = { id: 5, login: 'student1', started: true, signingImagePath: 'signature.png', attendanceChecked: false } as ExamUserAttendanceCheckDTO;
        const reload = vi.spyOn(examManagementService, 'verifyExamUserAttendance').mockReturnValue(of(new HttpResponse({ body: [original] })));
        const save = vi
            .spyOn(examManagementService, 'updateAttendance')
            .mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })))
            .mockReturnValue(of(undefined));
        fixture.detectChanges();
        component.hasExamEnded.set(true);
        fixture.detectChanges();
        await fixture.whenStable();
        const select = fixture.nativeElement.querySelector('[data-testid="manual-attendance"]') as HTMLSelectElement;
        select.value = 'true';
        select.dispatchEvent(new Event('change'));
        fixture.detectChanges();
        await fixture.whenStable();
        expect(fixture.nativeElement.querySelector('[data-testid="manual-attendance"]').value).toBe('');
        expect(component.allExamUsersAttendanceCheck()[0].signingImagePath).toBe('signature.png');
        expect(component.savingAttendance()).toBeUndefined();
        expect(reload).toHaveBeenCalledOnce();
        component.setAttendance(original, 'false');
        expect(save).toHaveBeenCalledTimes(2);
    });

    it('should not edit attendance during the exam', () => {
        fixture.detectChanges();
        const save = vi.spyOn(examManagementService, 'updateAttendance');
        component.hasExamEnded.set(false);
        component.setAttendance({ id: 5 } as ExamUserAttendanceCheckDTO, 'true');
        expect(save).not.toHaveBeenCalled();
    });

    it('should unlock attendance correction at the synchronized exam end and clean up its timer', () => {
        vi.useFakeTimers();
        const oldEndDate = examWithCourse.endDate;
        try {
            examWithCourse.endDate = dayjs().add(1, 'second');
            vi.spyOn(examManagementService, 'verifyExamUserAttendance').mockReturnValue(of(new HttpResponse({ body: [] })));
            fixture.detectChanges();
            expect(component.hasExamEnded()).toBe(false);
            vi.advanceTimersByTime(1000);
            expect(component.hasExamEnded()).toBe(true);
            examWithCourse.endDate = dayjs().add(1, 'hour');
            component['scheduleAttendanceDeadline']();
            expect(vi.getTimerCount()).toBeGreaterThan(0);
            component.ngOnDestroy();
            expect(vi.getTimerCount()).toBe(0);
        } finally {
            examWithCourse.endDate = oldEndDate;
            vi.useRealTimers();
        }
    });

    it('should use the server clock when opening attendance after the exam', () => {
        const oldEndDate = examWithCourse.endDate;
        try {
            examWithCourse.endDate = dayjs().add(1, 'hour');
            vi.spyOn(TestBed.inject(ArtemisServerDateService), 'now').mockImplementation(() => dayjs().add(2, 'hours'));
            vi.spyOn(examManagementService, 'verifyExamUserAttendance').mockReturnValue(of(new HttpResponse({ body: [] })));
            fixture.detectChanges();
            expect(component.hasExamEnded()).toBe(true);
        } finally {
            examWithCourse.endDate = oldEndDate;
        }
    });

    it('should test on error', () => {
        component.onError('ErrorString');
        expect(component.isTransitioning()).toBe(false);
        expect(component.isLoading()).toBe(false);
    });

    it('should test on sort', () => {
        const sortServiceSpy = vi.spyOn(sortService, 'sortByProperty');

        fixture.detectChanges();
        component.sortRows();
        expect(sortServiceSpy).toHaveBeenCalledOnce();
    });

    it('should call exam management service', () => {
        const examUserAttendanceCheckDTO = new ExamUserAttendanceCheckDTO();
        examUserAttendanceCheckDTO.id = 1;
        examUserAttendanceCheckDTO.studentImagePath = 'studentImagePath';
        examUserAttendanceCheckDTO.login = 'student1';
        examUserAttendanceCheckDTO.registrationNumber = '12345678';
        examUserAttendanceCheckDTO.signingImagePath = 'signingImagePath';
        examUserAttendanceCheckDTO.started = true;
        examUserAttendanceCheckDTO.submitted = false;
        const response: ExamUserAttendanceCheckDTO[] = [examUserAttendanceCheckDTO];
        const examServiceStub = vi.spyOn(examManagementService, 'verifyExamUserAttendance').mockReturnValue(of(new HttpResponse({ body: response })));

        fixture.detectChanges();

        expect(examServiceStub).toHaveBeenCalledOnce();
        expect(examServiceStub).toHaveBeenCalledWith(course.id, examWithCourse.id, true);
        expect(component.allExamUsersAttendanceCheck()).toEqual(response);
        expect(component.allExamUsersAttendanceCheck()).toHaveLength(1);
        expect(component.isLoading()).toBe(false);
    });
});
