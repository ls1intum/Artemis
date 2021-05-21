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
import { MockComponent, MockProvider } from 'ng-mocks';
import { NonProgrammingExerciseDetailCommonActionsComponent } from 'app/exercises/shared/exercise-detail-common-actions/non-programming-exercise-detail-common-actions.component';
import { StatisticsService } from 'app/shared/statistics-graph/statistics.service';
import { ExerciseManagementStatisticsDto } from 'app/exercises/shared/statistics/exercise-management-statistics-dto';

describe('TextExercise Management Detail Component', () => {
    let comp: TextExerciseDetailComponent;
    let fixture: ComponentFixture<TextExerciseDetailComponent>;
    let exerciseService: TextExerciseService;
    let statisticsService: StatisticsService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [TextExerciseDetailComponent, MockComponent(NonProgrammingExerciseDetailCommonActionsComponent)],
            providers: [{ provide: ActivatedRoute, useValue: new MockActivatedRoute() }, MockProvider(TranslateService)],
        })
            .overrideTemplate(TextExerciseDetailComponent, '')
            .compileComponents();
        fixture = TestBed.createComponent(TextExerciseDetailComponent);
        comp = fixture.componentInstance;
        exerciseService = fixture.debugElement.injector.get(TextExerciseService);
        statisticsService = fixture.debugElement.injector.get(StatisticsService);
    });

    describe('OnInit with course exercise', () => {
        const course: Course = { id: 123 } as Course;
        const textExerciseWithCourse: TextExercise = new TextExercise(course, undefined);
        textExerciseWithCourse.id = 123;
        const textExerciseStatistics = {
            averageScoreOfExercise: 4,
            maxPointsOfExercise: 4,
            scoreDistribution: [4],
            numberOfExerciseScores: 4,
            numberOfParticipations: 4,
            numberOfStudentsInCourse: 4,
            numberOfQuestions: 4,
            numberOfAnsweredQuestions: 4,
        } as ExerciseManagementStatisticsDto;

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ exerciseId: textExerciseWithCourse.id });
        });

        it('Should call load on init and be not in exam mode', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(exerciseService, 'find').and.returnValue(
                of(
                    new HttpResponse({
                        body: textExerciseWithCourse,
                        headers,
                    }),
                ),
            );
            spyOn(statisticsService, 'getExerciseStatistics').and.returnValue(of(textExerciseStatistics));
            // WHEN
            fixture.detectChanges();
            comp.ngOnInit();

            // THEN
            expect(exerciseService.find).toHaveBeenCalled();
            expect(comp.isExamExercise).toBeFalsy();
            expect(comp.textExercise).toEqual(textExerciseWithCourse);
        });
    });

    describe('OnInit with exam exercise', () => {
        const exerciseGroup: ExerciseGroup = new ExerciseGroup();
        const textExerciseWithExerciseGroup: TextExercise = new TextExercise(undefined, exerciseGroup);
        textExerciseWithExerciseGroup.id = 123;

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ exerciseId: textExerciseWithExerciseGroup.id });
        });

        it('Should call load on init and be in exam mode', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(exerciseService, 'find').and.returnValue(
                of(
                    new HttpResponse({
                        body: textExerciseWithExerciseGroup,
                        headers,
                    }),
                ),
            );
            // WHEN
            fixture.detectChanges();
            comp.ngOnInit();

            // THEN
            expect(exerciseService.find).toHaveBeenCalled();
            expect(comp.isExamExercise).toBeTruthy();
            expect(comp.textExercise).toEqual(textExerciseWithExerciseGroup);
        });
    });
});
