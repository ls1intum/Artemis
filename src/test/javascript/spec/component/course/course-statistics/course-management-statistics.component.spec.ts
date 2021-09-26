import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../test.module';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { RouterTestingModule } from '@angular/router/testing';
import { MockHasAnyAuthorityDirective } from '../../../helpers/mocks/directive/mock-has-any-authority.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { StatisticsGraphComponent } from 'app/shared/statistics-graph/statistics-graph.component';
import { SpanType } from 'app/entities/statistics.model';
import { CourseManagementStatisticsComponent } from 'app/course/manage/course-management-statistics.component';
import { StatisticsService } from 'app/shared/statistics-graph/statistics.service';
import { of } from 'rxjs';
import { StatisticsAverageScoreGraphComponent } from 'app/shared/statistics-graph/statistics-average-score-graph.component';

chai.use(sinonChai);
const expect = chai.expect;

describe('CourseManagementStatisticsComponent', () => {
    let fixture: ComponentFixture<CourseManagementStatisticsComponent>;
    let component: CourseManagementStatisticsComponent;
    let service: StatisticsService;

    const returnValue = {
        averageScoreOfCourse: 75,
        averageScoresOfExercises: [
            { exerciseId: 1, exerciseName: 'PatternsExercise', averageScore: 50 },
            { exerciseId: 2, exerciseName: 'MorePatterns', averageScore: 50 },
        ],
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([])],
            declarations: [
                CourseManagementStatisticsComponent,
                MockComponent(AlertComponent),
                MockComponent(StatisticsGraphComponent),
                MockComponent(StatisticsAverageScoreGraphComponent),
                MockDirective(MockHasAnyAuthorityDirective),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
            ],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseManagementStatisticsComponent);
                component = fixture.componentInstance;
                service = TestBed.inject(StatisticsService);
            });
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should initialize', () => {
        jest.spyOn(service, 'getCourseStatistics').mockReturnValue(of(returnValue));
        fixture.detectChanges();
        expect(component).to.be.ok;
    });

    it('should trigger when tab changed', fakeAsync(() => {
        jest.spyOn(service, 'getCourseStatistics').mockReturnValue(of(returnValue));
        const tabSpy = sinon.spy(component, 'onTabChanged');
        fixture.detectChanges();

        const button = fixture.debugElement.nativeElement.querySelector('#option3');
        button.click();

        tick();
        expect(tabSpy).to.have.been.calledOnce;
        expect(component.currentSpan).to.be.equal(SpanType.MONTH);
        tabSpy.restore();
    }));
});
