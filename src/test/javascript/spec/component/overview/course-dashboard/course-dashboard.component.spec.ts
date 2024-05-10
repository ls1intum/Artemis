import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { Competency } from 'app/entities/competency.model';
import { By } from '@angular/platform-browser';
import { Exercise } from 'app/entities/exercise.model';
import { HttpResponse } from '@angular/common/http';
import { CourseDashboardComponent } from 'app/overview/course-dashboard/course-dashboard.component';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { CourseExerciseRowComponent } from 'app/overview/course-exercises/course-exercise-row.component';
import { SidePanelComponent } from 'app/shared/side-panel/side-panel.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { SubmissionResultStatusComponent } from 'app/overview/submission-result-status.component';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTestModule } from '../../../test.module';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { Course } from 'app/entities/course.model';
import { CompetencyAccordionComponent } from 'app/course/competencies/competency-accordion/competency-accordion.component';
import { StudentAnalyticsDashboardProgressBarComponent } from 'app/shared/progress-bar/student-analytics-dashboard-progress-bar.component';
import { FeatureToggleHideDirective } from 'app/shared/feature-toggle/feature-toggle-hide.directive';
import { CompetencyRingsComponent } from 'app/course/competencies/competency-rings/competency-rings.component';

describe('CourseDashboardComponent', () => {
    let component: CourseDashboardComponent;
    let fixture: ComponentFixture<CourseDashboardComponent>;
    let courseStorageService: CourseStorageService;
    let competencyService: CompetencyService;
    let getAllForCourseStudentDashboardSpy: jest.SpyInstance;
    let router: Router;
    const mockCourse = { id: 1 } as Course;
    const mockRouter = {
        navigate: jest.fn(),
    };

    const mockCompetencies = generateMockCompetenciesWithExercises(5);

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule, MockModule(NgbTooltipModule)],
            declarations: [
                CourseDashboardComponent,
                CompetencyAccordionComponent,
                MockPipe(HtmlForMarkdownPipe),
                MockPipe(ArtemisTimeAgoPipe),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockComponent(StudentAnalyticsDashboardProgressBarComponent),
                MockComponent(CourseExerciseRowComponent),
                MockComponent(SidePanelComponent),
                MockDirective(TranslateDirective),
                MockDirective(FeatureToggleHideDirective),
                MockComponent(SubmissionResultStatusComponent),
                MockComponent(CompetencyRingsComponent),
            ],
            providers: [
                MockProvider(CourseStorageService),
                MockProvider(CompetencyService),
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            parent: {
                                params: of({ competencyId: '1', courseId: '1' }),
                            },
                        },
                    },
                },
                { provide: Router, useValue: mockRouter },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseDashboardComponent);
                component = fixture.componentInstance;
                courseStorageService = TestBed.inject(CourseStorageService);
                jest.spyOn(courseStorageService, 'subscribeToCourseUpdates').mockReturnValue(of(mockCourse));
                competencyService = TestBed.inject(CompetencyService);
                const mockResponse: HttpResponse<Competency[]> = new HttpResponse({ body: mockCompetencies });
                getAllForCourseStudentDashboardSpy = jest.spyOn(competencyService, 'getAllForCourseStudentDashboard').mockReturnValue(of(mockResponse));
                router = TestBed.inject(Router);
                fixture.detectChanges();
            });
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should set courseId on initialization', () => {
        const params = { courseId: 1 };
        component.ngOnInit();
        expect(component.courseId).toEqual(params.courseId);
    });

    it('should call setCourse on initialization', () => {
        const getCourseSpy = jest.spyOn(courseStorageService, 'getCourse').mockReturnValue(mockCourse);
        component.ngOnInit();
        expect(getCourseSpy).toHaveBeenCalledWith(component.courseId);
        expect(component.course).toEqual(mockCourse);
    });

    it('should load competencies on initialization', () => {
        component.ngOnInit();
        expect(component.competencies).toEqual(mockCompetencies);
        expect(getAllForCourseStudentDashboardSpy).toHaveBeenCalledWith(component.courseId);
    });

    it('should render competency accordions when competencies are loaded', () => {
        component.ngOnInit();
        fixture.detectChanges();
        const competencyAccordions = fixture.debugElement.queryAll(By.css('jhi-competency-accordion'));
        expect(competencyAccordions).toHaveLength(mockCompetencies.length);
    });

    it('should navigate to learning paths when button is clicked and learning paths are enabled', () => {
        component.course = { learningPathsEnabled: true };
        fixture.detectChanges();
        const navigateSpy = jest.spyOn(router, 'navigate');
        const button = fixture.debugElement.query(By.css('.btn'));
        button.triggerEventHandler('click', null);
        expect(navigateSpy).toHaveBeenCalledWith(['courses', component.courseId, 'learning-path']);
    });

    it('should respond to accordionToggle event from CompetencyAccordionComponent', () => {
        component.ngOnInit();
        fixture.detectChanges();
        const competencyAccordions = fixture.debugElement.queryAll(By.css('jhi-competency-accordion'));
        const firstAccordionComponent = competencyAccordions[0].componentInstance as CompetencyAccordionComponent;
        const toggleSpy = jest.spyOn(firstAccordionComponent.accordionToggle, 'emit');
        firstAccordionComponent.toggle();
        expect(toggleSpy).toHaveBeenCalledWith({ opened: true, index: firstAccordionComponent.index });
        expect(component.openedAccordionIndex).toEqual(firstAccordionComponent.index);
    });

    it('should only allow one accordion to be open at a time', () => {
        component.ngOnInit();
        fixture.detectChanges();
        const competencyAccordions = fixture.debugElement.queryAll(By.css('jhi-competency-accordion'));
        const firstAccordionComponent = competencyAccordions[0].componentInstance as CompetencyAccordionComponent;
        const secondAccordionComponent = competencyAccordions[1].componentInstance as CompetencyAccordionComponent;

        firstAccordionComponent.toggle();
        fixture.detectChanges();
        expect(firstAccordionComponent.open).toBeTrue();
        expect(secondAccordionComponent.open).toBeFalse();

        secondAccordionComponent.toggle();
        fixture.detectChanges();
        expect(firstAccordionComponent.open).toBeFalse();
        expect(secondAccordionComponent.open).toBeTrue();
    });
});

function generateMockCompetenciesWithExercises(count: number): Competency[] {
    const competencies: Competency[] = [];
    for (let i = 1; i <= count; i++) {
        const competency: Competency = {
            id: i,
            title: `Competency ${i}`,
            description: `Description for Competency ${i}`,
            exercises: [
                {
                    id: i,
                    title: `Exercise ${i}`,
                } as Exercise,
                {
                    id: i + 1,
                    title: `Exercise ${i + 1}`,
                    // Add other properties as needed
                } as Exercise,
            ],
            // Add other properties as needed
        };
        competencies.push(competency);
    }
    return competencies;
}
