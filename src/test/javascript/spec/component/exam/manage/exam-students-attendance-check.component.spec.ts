import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, UrlSegment, convertToParamMap } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { TranslateService } from '@ngx-translate/core';
import { User } from 'app/core/user/user.model';
import { Course } from 'app/entities/course.model';
import { ExamUserAttendanceCheckDTO } from 'app/entities/exam-users-attendance-check-dto.model';
import { Exam } from 'app/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { ExamStudentsAttendanceCheckComponent } from 'app/exam/manage/students/verify-attendance-check/exam-students-attendance-check.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SortService } from 'app/shared/service/sort.service';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { MockDirective, MockPipe } from 'ng-mocks';
import { Observable } from 'rxjs';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { MockNgbModalService } from '../../../helpers/mocks/service/mock-ngb-modal.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../../test.module';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import dayjs from 'dayjs/esm';

describe('ExamStudentsAttendanceCheckComponent', () => {
    const course = { id: 1 } as Course;
    const user1 = { id: 1, name: 'name', login: 'login' } as User;
    const user2 = { id: 2, login: 'user2' } as User;
    const dateTime = dayjs().subtract(1, 'hour');
    const examWithCourse: Exam = { course, id: 2, examUsers: [{ user: user1 }, { user: user2 }], startDate: dateTime } as Exam;

    const route = {
        snapshot: { paramMap: convertToParamMap({ courseId: course.id }) },
        url: new Observable<UrlSegment[]>(),
        data: { subscribe: (fn: (value: any) => void) => fn({ exam: examWithCourse }) },
    } as any as ActivatedRoute;

    let component: ExamStudentsAttendanceCheckComponent;
    let fixture: ComponentFixture<ExamStudentsAttendanceCheckComponent>;
    let examManagementService: ExamManagementService;
    let sortService: SortService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule],
            declarations: [ExamStudentsAttendanceCheckComponent, MockDirective(TranslateDirective), MockDirective(SortDirective), MockPipe(ArtemisTranslatePipe)],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: Router, useClass: MockRouter },
                { provide: ActivatedRoute, useValue: route },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamStudentsAttendanceCheckComponent);
        component = fixture.componentInstance;
        examManagementService = TestBed.inject(ExamManagementService);
        sortService = TestBed.inject(SortService);
    });

    afterEach(() => {
        jest.restoreAllMocks();
        fixture.destroy();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
        expect(component.courseId).toEqual(course.id);
        expect(component.exam).toEqual(examWithCourse);
        expect(component.hasExamStarted).toBeTrue();
    });

    it('should test on error', () => {
        component.onError('ErrorString');
        expect(component.isTransitioning).toBeFalse();
        expect(component.isLoading).toBeFalse();
    });

    it('should test on sort', () => {
        const sortServiceSpy = jest.spyOn(sortService, 'sortByProperty');

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
        const examServiceStub = jest.spyOn(examManagementService, 'verifyExamUserAttendance').mockReturnValue(of(new HttpResponse({ body: response })));

        fixture.detectChanges();

        expect(examServiceStub).toHaveBeenCalledOnce();
        expect(examServiceStub).toHaveBeenCalledWith(course.id, examWithCourse.id);
        expect(component.allExamUsersAttendanceCheck).toEqual(response);
        expect(component.allExamUsersAttendanceCheck).toHaveLength(1);
        expect(component.isLoading).toBeFalse();
    });
});
