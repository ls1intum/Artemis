import * as chai from 'chai';
import { ChartsModule } from 'ng2-charts';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { DoughnutChartType } from 'app/course/manage/detail/course-detail.component';
import { DoughnutChartComponent } from 'app/exercises/shared/statistics/doughnut-chart.component';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { ExerciseType } from 'app/entities/exercise.model';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { Router } from '@angular/router';
import { stub } from 'sinon';
import { MockRouterLinkDirective } from '../lecture-unit/lecture-unit-management.component.spec';

chai.use(sinonChai);
const expect = chai.expect;

describe('DoughnutChartComponent', () => {
    let fixture: ComponentFixture<DoughnutChartComponent>;
    let component: DoughnutChartComponent;
    let router: Router;

    const absolute = 80;
    const percentage = 80;
    const max = 100;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ChartsModule],
            declarations: [DoughnutChartComponent, MockPipe(ArtemisTranslatePipe), MockRouterLinkDirective],
            providers: [
                { provide: Router, useClass: MockRouter },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(DoughnutChartComponent);
                component = fixture.componentInstance;
                router = TestBed.inject(Router);
            });
    });

    beforeEach(() => {
        component.course = { id: 1 };
        component.exerciseId = 2;
        component.exerciseType = ExerciseType.TEXT;
        component.currentPercentage = absolute;
        component.currentAbsolute = percentage;
        component.currentMax = max;
    });

    it('should initialize', () => {
        component.contentType = DoughnutChartType.AVERAGE_EXERCISE_SCORE;
        component.ngOnChanges();
        const expected = [absolute, max - absolute];
        expect(component.stats).to.deep.equal(expected);
        expect(component.doughnutChartData[0].data).to.deep.equal(expected);

        component.currentMax = 0;
        component.ngOnChanges();
        expect(component.doughnutChartData[0].data).to.deep.equal([-1, 0]);
    });
    describe('setting titles for different chart types', () => {
        it('should set title for average exercise score', () => {
            component.contentType = DoughnutChartType.AVERAGE_EXERCISE_SCORE;
            component.ngOnInit();
            expect(component.doughnutChartTitle).to.deep.equal('averageScore');
            expect(component.titleLink).to.deep.equal([`/course-management/${component.course.id}/${component.exerciseType}-exercises/${component.exerciseId}/scores`]);
        });

        it('should set title for participations', () => {
            component.contentType = DoughnutChartType.PARTICIPATIONS;
            component.ngOnInit();
            expect(component.doughnutChartTitle).to.deep.equal('participationRate');
            expect(component.titleLink).to.deep.equal([`/course-management/${component.course.id}/${component.exerciseType}-exercises/${component.exerciseId}/participations`]);
        });

        it('should set title for question chart', () => {
            component.contentType = DoughnutChartType.QUESTIONS;
            component.ngOnInit();
            expect(component.doughnutChartTitle).to.deep.equal('answered_posts');
            expect(component.titleLink).to.deep.equal([`/courses/${component.course.id}/exercises/${component.exerciseId}`]);
        });
    });

    it('should open corresponding page', () => {
        const navigateSpy = stub(router, 'navigate');
        component.contentType = DoughnutChartType.AVERAGE_EXERCISE_SCORE;
        component.ngOnInit();
        component.openCorrespondingPage();

        expect(navigateSpy).to.have.been.calledOnce;
    });
});
