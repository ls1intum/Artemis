import { TranslateService } from '@ngx-translate/core';
import { CourseExercisePerformanceComponent } from './course-exercise-performance.component';
import { round } from 'app/shared/util/utils';
import { GraphColors } from 'app/exercise/shared/entities/statistics.model';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { ScaleType } from '@swimlane/ngx-charts';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockDirective } from 'ng-mocks';
import { TestBed } from '@angular/core/testing';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('CourseExercisePerformanceComponent', () => {
    let component: CourseExercisePerformanceComponent;
    let fixture: ComponentFixture<CourseExercisePerformanceComponent>;
    let translateService: TranslateService;

    const mockExercisePerformance = [
        {
            exerciseId: 1,
            title: 'Exercise 1',
            shortName: 'Ex1',
            score: 85,
            averageScore: 75,
        },
        {
            exerciseId: 2,
            title: 'Exercise 2',
            shortName: 'Ex2',
            score: 90,
            averageScore: 80,
        },
    ];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [CourseExercisePerformanceComponent, BrowserAnimationsModule],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
            declarations: [MockDirective(TranslateDirective)],
        }).compileComponents();

        translateService = TestBed.inject(TranslateService);

        fixture = TestBed.createComponent(CourseExercisePerformanceComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should initialize with empty exercise performance', () => {
        expect(component.exercisePerformance).toEqual([]);
        expect(component.isDataAvailable).toBeFalsy();
    });

    it('should setup chart data correctly when exercise performance is provided', () => {
        component.exercisePerformance = mockExercisePerformance;
        (component as any).setupChart();
        component.ngOnChanges();

        expect(component.isDataAvailable).toBeTruthy();
        expect(component.ngxData).toHaveLength(2);
        expect(component.ngxData).toHaveLength(2); // Your score and average score series
        const yourScoreSeries = component.ngxData[0].series;

        // Check your score series
        expect(yourScoreSeries).toHaveLength(2);
        expect(yourScoreSeries[0].name).toBe('EX1');
        expect(yourScoreSeries[0].value).toBe(round(85, 1));
        expect(yourScoreSeries[0].extra.title).toBe('Exercise 1');

        // Check average score series
        const averageScoreSeries = component.ngxData[1].series;
        expect(averageScoreSeries).toHaveLength(2);
        expect(averageScoreSeries[0].name).toBe('EX1');
        expect(averageScoreSeries[0].value).toBe(round(75, 1));
        expect(averageScoreSeries[0].extra.title).toBe('Exercise 1');
    });

    it('should calculate yScaleMax correctly', () => {
        component.exercisePerformance = mockExercisePerformance;
        (component as any).setupChart();
        component.ngOnChanges();

        expect(component.yScaleMax).toBe(100);
        // Since the highest score is 90, yScaleMax should be 100

        component.exercisePerformance = [
            // Test with higher scores
            { exerciseId: 1, title: 'Exercise 1', score: 120, averageScore: 110 },
        ];
        (component as any).setupChart();
        component.ngOnChanges();

        expect(component.yScaleMax).toBe(120);
        // Should round up to next multiple of 10
    });

    describe('should set up correct chart color configuration', () => {
        it('should set up correct chart colors and domain', () => {
            expect(component.ngxColor.group).toBe(ScaleType.Ordinal);
            expect(component.ngxColor.domain).toEqual([GraphColors.BLUE, GraphColors.YELLOW]);
        });

        it('should handle undefined scores gracefully', () => {
            component.exercisePerformance = [
                {
                    exerciseId: 1,
                    title: 'Exercise 1',
                    shortName: 'Ex1',
                },
                // score and averageScore are undefined
            ];
            (component as any).setupChart();
            component.ngOnChanges();

            const yourScoreSeries = component.ngxData[0].series;
            const averageScoreSeries = component.ngxData[1].series;

            expect(yourScoreSeries[0].value).toBe(0);
            expect(averageScoreSeries[0].value).toBe(0);
        });
    });
});
