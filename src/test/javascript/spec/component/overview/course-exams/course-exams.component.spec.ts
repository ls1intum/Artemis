import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { CourseExamsComponent } from 'app/overview/course-exams/course-exams.component';
import { Exam } from 'app/entities/exam.model';
import { ArtemisTestModule } from '../../../test.module';
import dayjs from 'dayjs/esm';
import { CourseExamDetailComponent } from 'app/overview/course-exams/course-exam-detail/course-exam-detail.component';
import { MockComponent, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ArtemisServerDateService } from 'app/shared/server-date.service';

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
                MockProvider(CourseScoreCalculationService),
                MockProvider(CourseManagementService),
                MockProvider(ArtemisServerDateService),
            ],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(CourseExamsComponent);
                component = componentFixture.componentInstance;

                jest.spyOn(TestBed.inject(CourseManagementService), 'getCourseUpdates').mockReturnValue(of());
                jest.spyOn(TestBed.inject(CourseScoreCalculationService), 'getCourse').mockReturnValue({ exams: [visibleExam, notVisibleExam] });
            });
    });

    it('exam should be visible', () => {
        componentFixture.detectChanges();
        expect(component.isVisible(visibleExam)).toBeTrue();
    });

    it('exam should not be visible', () => {
        componentFixture.detectChanges();
        expect(component.isVisible(notVisibleExam)).toBeFalse();
    });
});
