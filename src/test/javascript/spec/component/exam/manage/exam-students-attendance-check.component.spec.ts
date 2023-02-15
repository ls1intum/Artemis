import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, UrlSegment, convertToParamMap } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { TranslateService } from '@ngx-translate/core';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { User } from 'app/core/user/user.model';
import { UserService } from 'app/core/user/user.service';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { ExamStudentsAttendanceCheckComponent } from 'app/exam/manage/students/verify-attendance-check/exam-students-attendance-check.component';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { Observable } from 'rxjs';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { MockNgbModalService } from '../../../helpers/mocks/service/mock-ngb-modal.service';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../../test.module';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';

describe('ExamStudentsAttendanceCheckComponent', () => {
    const course = { id: 1 } as Course;
    const user1 = { id: 1, name: 'name', login: 'login' } as User;
    const user2 = { id: 2, login: 'user2' } as User;
    const examWithCourse: Exam = { course, id: 2, examUsers: [{ user: user1 }, { user: user2 }] } as Exam;

    const route = {
        snapshot: { paramMap: convertToParamMap({ courseId: course.id }) },
        url: new Observable<UrlSegment[]>(),
        data: { subscribe: (fn: (value: any) => void) => fn({ exam: examWithCourse }) },
    } as any as ActivatedRoute;

    let component: ExamStudentsAttendanceCheckComponent;
    let fixture: ComponentFixture<ExamStudentsAttendanceCheckComponent>;
    let examManagementService: ExamManagementService;
    let userService: UserService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgxDatatableModule, RouterTestingModule],
            declarations: [
                ExamStudentsAttendanceCheckComponent,
                MockComponent(DataTableComponent),
                MockDirective(TranslateDirective),
                MockDirective(DeleteButtonDirective),
                MockDirective(SortDirective),
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: Router, useClass: MockRouter },
                { provide: ActivatedRoute, useValue: route },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamStudentsAttendanceCheckComponent);
        component = fixture.componentInstance;
        examManagementService = TestBed.inject(ExamManagementService);
        userService = TestBed.inject(UserService);
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
        expect(component.isLoading).toBeFalse();
    });

    it('should test on error', () => {
        component.onError('ErrorString');
        expect(component.isTransitioning).toBeFalse();
    });

    // todo: write useful tests
});
