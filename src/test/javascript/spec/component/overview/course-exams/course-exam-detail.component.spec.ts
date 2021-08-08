import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { CourseExamDetailComponent } from 'app/overview/course-exams/course-exam-detail/course-exam-detail.component';
import { Exam } from 'app/entities/exam.model';
import { ArtemisTestModule } from '../../../test.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import * as moment from 'moment';

chai.use(sinonChai);
const expect = chai.expect;

describe('CourseExamDetailComponent', () => {
    let component: CourseExamDetailComponent;
    let componentFixture: ComponentFixture<CourseExamDetailComponent>;

    const startDate = moment('2020-06-11 11:29:51');
    const endDate = moment('2020-06-11 11:59:51');

    const testExam = {
        id: 1,
        startDate,
        endDate,
    } as Exam;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisSharedModule],
            providers: [{ provide: AccountService, useClass: MockAccountService }],
            declarations: [CourseExamDetailComponent],
        })
            .overrideTemplate(CourseExamDetailComponent, '')
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(CourseExamDetailComponent);
                component = componentFixture.componentInstance;
            });
    });

    it('should calculate exam duration', () => {
        component.exam = testExam;
        componentFixture.detectChanges();
        expect(component.examDuration).to.deep.equal(30 * 60);
    });
});
