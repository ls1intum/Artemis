import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { RouterTestingModule } from '@angular/router/testing';
import { ActivatedRoute } from '@angular/router';
import { CourseExamsComponent } from 'app/overview/course-exams/course-exams.component';
import { Exam } from 'app/entities/exam.model';
import { ArtemisTestModule } from '../../../test.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import dayjs from 'dayjs';

chai.use(sinonChai);
const expect = chai.expect;

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

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisSharedModule, RouterTestingModule.withRoutes([])],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            params: {
                                subscribe: (fn: (value: any) => void) => fn(1),
                            },
                        },
                    },
                },
                { provide: AccountService, useClass: MockAccountService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
            ],
            declarations: [CourseExamsComponent],
        })
            .overrideTemplate(CourseExamsComponent, '')
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(CourseExamsComponent);
                component = componentFixture.componentInstance;
            });
    });

    it('exam should be visible', () => {
        componentFixture.detectChanges();
        expect(component.isVisible(visibleExam)).to.be.true;
    });

    it('exam should not be visible', () => {
        componentFixture.detectChanges();
        expect(component.isVisible(notVisibleExam)).to.false;
    });
});
