import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CompetencyAccordionComponent } from 'src/main/webapp/app/course/competencies/competency-accordion/competency-accordion.component';
import { By } from '@angular/platform-browser';
import { Competency, CompetencyTaxonomy } from 'app/entities/competency.model';
import { Router } from '@angular/router';
import { Course } from 'app/entities/course.model';
import { ArtemisTestModule } from '../../../test.module';
import { CourseDashboardComponent } from 'app/overview/course-dashboard/course-dashboard.component';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ProgressBarComponent } from 'app/shared/progress-bar/progress-bar.component';
import { CourseExerciseRowComponent } from 'app/overview/course-exercises/course-exercise-row.component';
import { SidePanelComponent } from 'app/shared/side-panel/side-panel.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FeatureToggleHideDirective } from 'app/shared/feature-toggle/feature-toggle-hide.directive';
import { SubmissionResultStatusComponent } from 'app/overview/submission-result-status.component';
import { MockComponent, MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { CompetencyRingsComponent } from 'app/course/competencies/competency-rings/competency-rings.component';

describe('CompetencyAccordionComponent', () => {
    let component: CompetencyAccordionComponent;
    let fixture: ComponentFixture<CompetencyAccordionComponent>;
    let router: Router;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule, MockModule(NgbTooltipModule)],
            declarations: [
                CompetencyAccordionComponent,
                MockPipe(HtmlForMarkdownPipe),
                MockPipe(ArtemisTimeAgoPipe),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockComponent(ProgressBarComponent),
                MockComponent(CourseExerciseRowComponent),
                MockComponent(SidePanelComponent),
                MockDirective(TranslateDirective),
                MockDirective(FeatureToggleHideDirective),
                MockComponent(SubmissionResultStatusComponent),
                MockComponent(CourseDashboardComponent),
                MockComponent(CompetencyRingsComponent),
            ],
            providers: [{ provide: Router, useValue: { navigate: jest.fn() } }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CompetencyAccordionComponent);
                component = fixture.componentInstance;
                router = TestBed.inject(Router);
                component.index = 1;
                component.openedIndex = 0;
            });
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should render competency title', () => {
        component.competency = { title: 'Test Competency' } as Competency;
        fixture.detectChanges();
        const titleElement = fixture.debugElement.queryAll(By.css('.competency-accordion-header h3'));
        expect(titleElement[1].nativeElement.textContent).toContain('Test Competency');
    });

    it('should render progress bar', () => {
        component.competency = { exercises: [{ completed: true }, { completed: false }] } as Competency;
        fixture.detectChanges();
        const progressBarElement = fixture.debugElement.query(By.css('jhi-progress-bar'));
        expect(progressBarElement).toBeTruthy();
    });

    it('should toggle open state when toggle is called', () => {
        component.open = false;
        component.toggle();
        expect(component.open).toBeTrue();
        component.toggle();
        expect(component.open).toBeFalse();
    });

    it('should render competency icon', () => {
        component.competency = { taxonomy: CompetencyTaxonomy.CREATE } as Competency;
        fixture.detectChanges();
        const iconElement = fixture.debugElement.query(By.css('fa-icon'));
        expect(iconElement).toBeTruthy();
    });

    it('should render competency exercises progress bar', () => {
        component.competency = { exercises: [{ completed: true }, { completed: false }] } as Competency;
        fixture.detectChanges();
        const progressBarElement = fixture.debugElement.query(By.css('jhi-progress-bar'));
        expect(progressBarElement).toBeTruthy();
    });

    it('should render competency lecture units progress bar', () => {
        component.competency = { lectureUnits: [{ completed: true }, { completed: false }] } as Competency;
        fixture.detectChanges();
        const progressBarElement = fixture.debugElement.query(By.css('jhi-progress-bar'));
        expect(progressBarElement).toBeTruthy();
    });

    it('should render competency rings', () => {
        component.competency = { userProgress: [{ progress: 50, confidence: 75 }] } as Competency;
        fixture.detectChanges();
        const competencyRingsElement = fixture.debugElement.query(By.css('jhi-competency-rings'));
        expect(competencyRingsElement).toBeTruthy();
    });

    it('should emit accordionToggle event when toggle is called', () => {
        const toggleSpy = jest.spyOn(component.accordionToggle, 'emit');
        component.toggle();
        expect(toggleSpy).toHaveBeenCalledWith({ opened: true, index: component.index });
    });

    it('should navigate to competency detail page when navigateToCompetencyDetailPage is called', () => {
        const navigateSpy = jest.spyOn(router, 'navigate');
        component.course = { id: 1 } as Course;
        component.competency = { id: 1 } as Competency;
        component.navigateToCompetencyDetailPage(new Event('click'));
        expect(navigateSpy).toHaveBeenCalledWith(['/courses', component.course.id, 'competencies', component.competency.id]);
    });
});
