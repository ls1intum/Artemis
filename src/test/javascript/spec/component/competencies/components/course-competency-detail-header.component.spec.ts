import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { CourseCompetencyDetailHeaderComponent } from 'app/course/competencies/components/course-competency-detail-header/course-competency-detail-header.component';
import { CourseCompetency, CourseCompetencyType } from 'app/entities/competency.model';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { By } from '@angular/platform-browser';

describe('CourseCompetencyDetailHeaderComponent', () => {
    let component: CourseCompetencyDetailHeaderComponent;
    let fixture: ComponentFixture<CourseCompetencyDetailHeaderComponent>;
    let router: Router;

    const courseId = 1;
    let courseCompetency: CourseCompetencyType;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CourseCompetencyDetailHeaderComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                {
                    provide: Router,
                    useClass: MockRouter,
                },
            ],
        }).compileComponents();

        courseCompetency = <CourseCompetency>{
            id: 1,
            title: 'Competency 1',
            description: 'Competency 1 description',
            taxonomy: 'ANALYZE',
            masteryThreshold: 100,
            userProgress: [
                {
                    progress: 100.0,
                    confidence: 1.0,
                    confidenceReason: 'NO_REASON',
                },
            ],
        };

        router = TestBed.inject(Router);

        fixture = TestBed.createComponent(CourseCompetencyDetailHeaderComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('courseCompetency', courseCompetency);
        fixture.componentRef.setInput('courseId', courseId);
        fixture.componentRef.setInput('isMastered', true);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).toBeTruthy();
        expect(component.courseCompetency()).toEqual(courseCompetency);
        expect(component.courseId()).toEqual(courseId);
        expect(component.isMastered()).toBeTrue();
    });

    it('should display title', async () => {
        fixture.detectChanges();

        const title = fixture.debugElement.query(By.css('#course-competency-title'));

        expect(title.nativeElement.textContent).toContain(courseCompetency.title);
    });

    it('should display batch when mastered', () => {
        fixture.componentRef.setInput('isMastered', true);
        fixture.detectChanges();
        const batch = fixture.debugElement.query(By.css('#course-competency-mastered-batch'));
        expect(batch).toBeTruthy();
    });

    it('should not display batch when not mastered', () => {
        fixture.componentRef.setInput('isMastered', false);
        fixture.detectChanges();
        const batch = fixture.debugElement.query(By.css('#course-competency-mastered-batch'));
        expect(batch).toBeFalsy();
    });

    it('should display batch when competency is optional', () => {
        courseCompetency.optional = true;
        fixture.detectChanges();
        const batch = fixture.debugElement.query(By.css('#course-competency-optional-batch'));
        expect(batch).toBeTruthy();
    });

    it('should not display batch when competency is not optional', () => {
        courseCompetency.optional = false;
        fixture.detectChanges();
        const batch = fixture.debugElement.query(By.css('#course-competency-optional-batch'));
        expect(batch).toBeFalsy();
    });

    it('should display course competency description', () => {
        fixture.detectChanges();
        const description = fixture.debugElement.query(By.css('.markdown-preview'));
        expect(description.nativeElement.textContent).toContain(courseCompetency.description);
    });

    it('should not display course competency description when it is empty', () => {
        courseCompetency.description = '';
        fixture.detectChanges();
        const description = fixture.debugElement.query(By.css('.markdown-preview'));
        expect(description).toBeFalsy();
    });

    it('should navigate to edit page on edit button click', () => {
        const navigateSpy = jest.spyOn(router, 'navigate');

        fixture.detectChanges();

        const editButton = fixture.debugElement.query(By.css('#edit-course-competency-button'));
        editButton.nativeElement.click();

        fixture.detectChanges();

        expect(navigateSpy).toHaveBeenCalledWith(['/course-management', courseId, 'competency-management', courseCompetency.id, 'edit']);
    });
});
