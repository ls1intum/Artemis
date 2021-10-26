import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { ActivatedRoute } from '@angular/router';
import { CourseExamsComponent } from 'app/overview/course-exams/course-exams.component';
import { Exam } from 'app/entities/exam.model';
import { ArtemisTestModule } from '../../../test.module';
import dayjs from 'dayjs';
import { CourseExamDetailComponent } from 'app/overview/course-exams/course-exam-detail/course-exam-detail.component';
import { MockComponent } from 'ng-mocks';
import { of } from 'rxjs';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';

describe('CourseExamsComponent', () => {
    let component: CourseExamsComponent;
    let componentFixture: ComponentFixture<CourseExamsComponent>;

    const visibleExam = {
        id: 1,
        visibleDate: dayjs().subtract(2, 'days'),
    } as Exam;

    const notVisibleExam = {
        id: 2,
        visibleDate: dayjs().add(2, 'days'),
    } as Exam;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [CourseExamsComponent, MockComponent(CourseExamDetailComponent)],
            providers: [
                { provide: ActivatedRoute, useValue: { parent: { params: of(1) } } },
                { provide: AccountService, useClass: MockAccountService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
            ],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(CourseExamsComponent);
                component = componentFixture.componentInstance;

                jest.spyOn(TestBed.inject(CourseScoreCalculationService), 'getCourse').mockReturnValue({ exams: [visibleExam, notVisibleExam] });
            });
    });

    it('exam should be visible', () => {
        componentFixture.detectChanges();
        expect(component.isVisible(visibleExam)).toBe(true);
    });

    it('exam should not be visible', () => {
        componentFixture.detectChanges();
        expect(component.isVisible(notVisibleExam)).toBe(false);
    });
});
