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
import { StudentMetrics } from '../../../../../../main/webapp/app/entities/student-metrics.model';
import { ActivatedRoute } from '@angular/router';

describe('CoursePerformanceSectionComponent', () => {
    let component: CoursePerformanceSectionComponent;
    let fixture: ComponentFixture<CoursePerformanceSectionComponent>;

    const courseId = 1;

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
                        getCourseMetricsForUser: () => {
                            return of({});
                        },
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

        fixture = TestBed.createComponent(CoursePerformanceSectionComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('courseId', courseId);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).toBeDefined();
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
    readonly studentMetrics = input.required<StudentMetrics>();
}
