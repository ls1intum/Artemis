import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { CourseManagementStatisticsComponent } from 'app/course/manage/course-management-statistics.component';
import { ExerciseType } from 'app/entities/exercise.model';
import { SpanType } from 'app/entities/statistics.model';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { StatisticsAverageScoreGraphComponent } from 'app/shared/statistics-graph/statistics-average-score-graph.component';
import { StatisticsGraphComponent } from 'app/shared/statistics-graph/statistics-graph.component';
import { StatisticsService } from 'app/shared/statistics-graph/statistics.service';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of } from 'rxjs';
import { MockHasAnyAuthorityDirective } from '../../../helpers/mocks/directive/mock-has-any-authority.directive';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { ArtemisTestModule } from '../../../test.module';

describe('CourseManagementStatisticsComponent', () => {
    let fixture: ComponentFixture<CourseManagementStatisticsComponent>;
    let component: CourseManagementStatisticsComponent;
    let service: StatisticsService;

    const returnValue = {
        averageScoreOfCourse: 75,
        averageScoresOfExercises: [
            { exerciseId: 1, exerciseName: 'PatternsExercise', averageScore: 50, exerciseType: ExerciseType.PROGRAMMING },
            { exerciseId: 2, exerciseName: 'MorePatterns', averageScore: 50, exerciseType: ExerciseType.MODELING },
        ],
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([])],
            declarations: [
                CourseManagementStatisticsComponent,
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
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        const statisticsService = jest.spyOn(service, 'getCourseStatistics').mockReturnValue(of(returnValue));
        fixture.detectChanges();
        expect(statisticsService).toHaveBeenCalledOnce();
    });

    it('should trigger when tab changed', fakeAsync(() => {
        jest.spyOn(service, 'getCourseStatistics').mockReturnValue(of(returnValue));
        const tabSpy = jest.spyOn(component, 'onTabChanged');
        fixture.detectChanges();

        const button = fixture.debugElement.nativeElement.querySelector('#option3');
        button.click();

        tick();
        expect(tabSpy).toHaveBeenCalledOnce();
        expect(component.currentSpan).toBe(SpanType.MONTH);
    }));
});
