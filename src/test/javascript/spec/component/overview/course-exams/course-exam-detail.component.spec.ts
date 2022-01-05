import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseExamDetailComponent } from 'app/overview/course-exams/course-exam-detail/course-exam-detail.component';
import { Exam } from 'app/entities/exam.model';
import { ArtemisTestModule } from '../../../test.module';
import dayjs from 'dayjs/esm';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { Router } from '@angular/router';

describe('CourseExamDetailComponent', () => {
    let component: CourseExamDetailComponent;
    let componentFixture: ComponentFixture<CourseExamDetailComponent>;

    const startDate = dayjs('2020-06-11 11:29:51');
    const endDate = dayjs('2020-06-11 11:59:51');

    const testExam = {
        id: 1,
        startDate,
        endDate,
    } as Exam;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [CourseExamDetailComponent, MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisDatePipe), MockPipe(ArtemisDurationFromSecondsPipe)],
            providers: [{ provide: Router, useClass: MockRouter }],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(CourseExamDetailComponent);
                component = componentFixture.componentInstance;
            });
    });

    it('should calculate exam duration', () => {
        component.exam = testExam;
        componentFixture.detectChanges();
        expect(component.examDuration).toBe(30 * 60);
    });
});
