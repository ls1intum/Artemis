import { CoursePerformanceSectionComponent } from '../../../../../../main/webapp/app/overview/course-dashboard/components/course-performance-section/course-performance-section.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseExercisePerformanceComponent } from '../../../../../../main/webapp/app/overview/course-dashboard/course-exercise-performance/course-exercise-performance.component';
import { CourseCompetencyAccordionComponent } from '../../../../../../main/webapp/app/overview/course-dashboard/components/course-competency-accordion/course-competency-accordion.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { CourseDashboardService } from '../../../../../../main/webapp/app/overview/course-dashboard/course-dashboard.service';
import { AlertService } from '../../../../../../main/webapp/app/core/util/alert.service';
import { CourseStorageService } from '../../../../../../main/webapp/app/course/manage/course-storage.service';
import { of } from 'rxjs';
import { MockAlertService } from '../../../helpers/mocks/service/mock-alert.service';
import { Component, input } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { StudentMetrics } from '../../../../../../main/webapp/app/entities/student-metrics.model';
import { LectureUnitType } from '../../../../../../main/webapp/app/entities/lecture-unit/lectureUnit.model';
import { ExerciseType, IncludedInOverallScore } from '../../../../../../main/webapp/app/entities/exercise.model';
import dayjs from 'dayjs/esm';
import { HttpErrorResponse } from '@angular/common/http';

describe('CoursePerformanceSectionComponent', () => {
    let component: CoursePerformanceSectionComponent;
    let fixture: ComponentFixture<CoursePerformanceSectionComponent>;

    let courseDashboardService: CourseDashboardService;
    let alertService: AlertService;
    let getStudentMetricsSpy: jest.SpyInstance;

    const courseId = 1;

    const studentMetrics: StudentMetrics = {
        exerciseMetrics: {
            exerciseInformation: {
                3: {
                    id: 3,
                    title: 'Exercise 3',
                    shortName: 'Ex 3',
                    maxPoints: 10,
                    includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
                    startDate: dayjs('2021-01-01T00:00:00Z'),
                    type: ExerciseType.TEXT,
                },
            },
            score: {
                3: 10,
            },
            averageScore: {
                3: 6,
            },
            completed: [3],
        },
        lectureUnitStudentMetricsDTO: {
            lectureUnitInformation: {
                2: {
                    id: 2,
                    lectureId: 1,
                    lectureTitle: 'Lecture 1',
                    name: 'Lecture Unit 1',
                    type: LectureUnitType.TEXT,
                },
            },
        },
        competencyMetrics: {
            competencyInformation: {
                1: {
                    id: 1,
                    title: 'Competency 1',
                    description: 'Competency 1 description',
                    optional: false,
                    masteryThreshold: 1,
                },
            },
            exercises: {
                1: [2],
            },
            lectureUnits: {
                1: [2],
            },
            progress: {
                1: 1,
            },
            confidence: {
                1: 1,
            },
        },
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CoursePerformanceSectionComponent],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            params: of({
                                courseId: courseId,
                            }),
                        },
                    },
                },
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
                {
                    provide: CourseDashboardService,
                    useValue: {
                        getCourseMetricsForUser: () => jest.fn(),
                    },
                },
                {
                    provide: CourseStorageService,
                    useValue: {
                        getCourse: () => {
                            return { learningPathsEnabled: true };
                        },
                    },
                },
                {
                    provide: AlertService,
                    useClass: MockAlertService,
                },
            ],
        })
            .overrideComponent(CoursePerformanceSectionComponent, {
                remove: {
                    imports: [CourseExercisePerformanceComponent, CourseCompetencyAccordionComponent],
                },
                add: {
                    imports: [CourseCompetencyAccordionStubComponent],
                },
            })
            .compileComponents();

        courseDashboardService = TestBed.inject(CourseDashboardService);
        alertService = TestBed.inject(AlertService);

        getStudentMetricsSpy = jest.spyOn(courseDashboardService, 'getCourseMetricsForUser').mockResolvedValue(studentMetrics);

        fixture = TestBed.createComponent(CoursePerformanceSectionComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('courseId', courseId);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should load student metrics', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        expect(getStudentMetricsSpy).toHaveBeenCalledExactlyOnceWith(courseId);
        expect(component.studentMetrics()).toEqual(studentMetrics);
    });

    it('should set isLoading correctly', async () => {
        const isLoadingSpy = jest.spyOn(component.isLoading, 'set');

        fixture.detectChanges();
        await fixture.whenStable();

        expect(isLoadingSpy).toHaveBeenNthCalledWith(1, true);
        expect(isLoadingSpy).toHaveBeenNthCalledWith(2, false);
    });

    it('should show error when loading student metrics fails', async () => {
        const errorSpy = jest.spyOn(alertService, 'error');
        getStudentMetricsSpy.mockRejectedValue(new HttpErrorResponse({ status: 500, error: 'Error loading student metrics' }));

        fixture.detectChanges();
        await fixture.whenStable();

        expect(errorSpy).toHaveBeenCalledOnce();
    });
});

// Stub components
@Component({
    selector: 'jhi-course-competency-accordion',
    template: '',
    standalone: true,
})
class CourseCompetencyAccordionStubComponent {
    readonly courseId = input.required<number>();
}
