import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { ArtemisTestModule } from '../../test.module';
import { TextExerciseDetailComponent } from 'app/exercises/text/manage/text-exercise/text-exercise-detail.component';
import { Course } from 'app/entities/course.model';
import { TextExerciseService } from 'app/exercises/text/manage/text-exercise/text-exercise.service';
import { TextExercise } from 'app/entities/text-exercise.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { NonProgrammingExerciseDetailCommonActionsComponent } from 'app/exercises/shared/exercise-detail-common-actions/non-programming-exercise-detail-common-actions.component';
import { StatisticsService } from 'app/shared/statistics-graph/statistics.service';
import { ExerciseManagementStatisticsDto } from 'app/exercises/shared/statistics/exercise-management-statistics-dto';
import { MockRouterLinkDirective } from '../../helpers/mocks/directive/mock-router-link.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ExerciseDetailStatisticsComponent } from 'app/exercises/shared/statistics/exercise-detail-statistics.component';
import { DocumentationButtonComponent } from 'app/shared/components/documentation-button/documentation-button.component';
import { DetailOverviewListComponent } from 'app/detail-overview-list/detail-overview-list.component';

describe('TextExercise Management Detail Component', () => {
    let comp: TextExerciseDetailComponent;
    let fixture: ComponentFixture<TextExerciseDetailComponent>;
    let exerciseService: TextExerciseService;
    let statisticsService: StatisticsService;

    const textExerciseStatistics = {
        averageScoreOfExercise: 50,
        maxPointsOfExercise: 10,
        absoluteAveragePoints: 5,
        scoreDistribution: [5, 0, 0, 0, 0, 0, 0, 0, 0, 5],
        numberOfExerciseScores: 10,
        numberOfParticipations: 10,
        numberOfStudentsOrTeamsInCourse: 10,
        participationsInPercent: 100,
        numberOfPosts: 4,
        numberOfResolvedPosts: 2,
        resolvedPostsInPercent: 50,
    } as ExerciseManagementStatisticsDto;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                TextExerciseDetailComponent,
                MockComponent(NonProgrammingExerciseDetailCommonActionsComponent),
                MockComponent(ExerciseDetailStatisticsComponent),
                MockComponent(DetailOverviewListComponent),
                MockComponent(DocumentationButtonComponent),
                MockRouterLinkDirective,
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [{ provide: ActivatedRoute, useValue: new MockActivatedRoute() }, MockProvider(TranslateService)],
        }).compileComponents();
        fixture = TestBed.createComponent(TextExerciseDetailComponent);
        comp = fixture.componentInstance;
        exerciseService = fixture.debugElement.injector.get(TextExerciseService);
        statisticsService = fixture.debugElement.injector.get(StatisticsService);
    });

    describe('onInit with course exercise', () => {
        const course: Course = { id: 123 } as Course;
        const textExerciseWithCourse: TextExercise = new TextExercise(course, undefined);
        textExerciseWithCourse.id = 123;

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ exerciseId: textExerciseWithCourse.id });
        });

        it('should call load on init and be not in exam mode', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            const exerciseServiceStub = jest.spyOn(exerciseService, 'find').mockReturnValue(
                of(
                    new HttpResponse({
                        body: textExerciseWithCourse,
                        headers,
                    }),
                ),
            );
            const statisticsServiceStub = jest.spyOn(statisticsService, 'getExerciseStatistics').mockReturnValue(of(textExerciseStatistics));
            // WHEN
            fixture.detectChanges();
            comp.ngOnInit();

            // THEN
            expect(exerciseServiceStub).toHaveBeenCalledTimes(2);
            expect(statisticsServiceStub).toHaveBeenCalledTimes(2);
            expect(comp.isExamExercise).toBeFalse();
            expect(comp.textExercise).toEqual(textExerciseWithCourse);
            expect(comp.doughnutStats.participationsInPercent).toBe(100);
            expect(comp.doughnutStats.resolvedPostsInPercent).toBe(50);
            expect(comp.doughnutStats.absoluteAveragePoints).toBe(5);
        });
    });

    describe('onInit with exam exercise', () => {
        const exerciseGroup: ExerciseGroup = new ExerciseGroup();
        const textExerciseWithExerciseGroup: TextExercise = new TextExercise(undefined, exerciseGroup);
        textExerciseWithExerciseGroup.id = 123;

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ exerciseId: textExerciseWithExerciseGroup.id });
        });

        it('should call load on init and be in exam mode', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            const exerciseServiceStub = jest.spyOn(exerciseService, 'find').mockReturnValue(
                of(
                    new HttpResponse({
                        body: textExerciseWithExerciseGroup,
                        headers,
                    }),
                ),
            );
            const statisticsServiceStub = jest.spyOn(statisticsService, 'getExerciseStatistics').mockReturnValue(of(textExerciseStatistics));

            // WHEN
            fixture.detectChanges();
            comp.ngOnInit();

            // THEN
            expect(exerciseServiceStub).toHaveBeenCalledTimes(2);
            expect(statisticsServiceStub).toHaveBeenCalledTimes(2);
            expect(comp.isExamExercise).toBeTrue();
            expect(comp.textExercise).toEqual(textExerciseWithExerciseGroup);
        });
    });
});
