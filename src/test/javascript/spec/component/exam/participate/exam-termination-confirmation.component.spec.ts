import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ExamTerminationConfirmationComponent } from 'app/exam/participate/exam-termination-confirmation/exam-termination-confirmation.component';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe } from 'ng-mocks';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { ExamLiveEventsButtonComponent } from 'app/exam/participate/events/exam-live-events-button.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { User } from 'app/core/user/user.model';
import { FormsModule } from '@angular/forms';

describe('ExamTerminationConfirmationComponent', () => {
    let component: ExamTerminationConfirmationComponent;
    let fixture: ComponentFixture<ExamTerminationConfirmationComponent>;

    const course = { id: 456 } as Course;
    const exam: Exam = new Exam();
    exam.course = course;
    exam.id = 123;
    exam.testExam = false;
    const studentExam: StudentExam = new StudentExam();
    studentExam.testRun = false;
    studentExam.id = 1;

    let accountService: AccountService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [FormsModule],
            declarations: [ExamTerminationConfirmationComponent, MockPipe(ArtemisTranslatePipe), MockComponent(ExamLiveEventsButtonComponent), MockComponent(FaIconComponent)],
            providers: [{ provide: AccountService, useClass: MockAccountService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExamTerminationConfirmationComponent);
                component = fixture.componentInstance;
                accountService = TestBed.inject(AccountService);

                component.exam = exam;
                component.studentExam = studentExam;
            });
    });

    it('should initialize', fakeAsync(() => {
        const user = { name: 'admin' } as User;
        jest.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(user));

        component.ngOnChanges();
        tick();

        expect(component).toBeTruthy();
        expect(component.accountName).toBe(user.name);
        expect(component.confirmed).toBeFalse();
    }));

    it('should abandon exam', () => {
        const abandonExamSpy = jest.spyOn(component.onExamAbandoned, 'emit');
        component.abandonExam();
        expect(abandonExamSpy).toHaveBeenCalledOnce();
    });

    it('should continue exam', () => {
        const continueExamSpy = jest.spyOn(component.onExamContinue, 'emit');
        component.continue();
        expect(continueExamSpy).toHaveBeenCalledOnce();
    });
});
