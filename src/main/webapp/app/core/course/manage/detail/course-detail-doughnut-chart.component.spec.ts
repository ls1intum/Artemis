import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseDetailDoughnutChartComponent } from 'app/core/course/manage/detail/course-detail-doughnut-chart.component';
import { DoughnutChartType } from 'app/core/course/manage/detail/course-detail.component';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ComponentRef } from '@angular/core';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute, RouterLink, RouterModule } from '@angular/router';
import { NgClass } from '@angular/common';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockModule, MockPipe } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { PieChartModule } from '@swimlane/ngx-charts';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

describe('CourseDetailDoughnutChartComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<CourseDetailDoughnutChartComponent>;
    let component: CourseDetailDoughnutChartComponent;
    let componentRef: ComponentRef<CourseDetailDoughnutChartComponent>;

    const course = { id: 1, isAtLeastInstructor: true } as Course;
    const absolute = 80;
    const percentage = 80;
    const max = 100;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CourseDetailDoughnutChartComponent, RouterModule.forRoot([])],
            providers: [
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideComponent(CourseDetailDoughnutChartComponent, {
                set: {
                    imports: [RouterLink, NgClass, MockComponent(FaIconComponent), MockModule(PieChartModule), MockPipe(ArtemisTranslatePipe)],
                },
            })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseDetailDoughnutChartComponent);
                component = fixture.componentInstance;
                componentRef = fixture.componentRef;
            });
    });

    beforeEach(() => {
        componentRef.setInput('course', course);
        componentRef.setInput('contentType', DoughnutChartType.ASSESSMENT);
        componentRef.setInput('currentPercentage', percentage);
        componentRef.setInput('currentAbsolute', absolute);
        componentRef.setInput('currentMax', max);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();

        const expected = [absolute, max - absolute, 0];
        expect(component.stats()).toEqual(expected);
        expect(component.ngxData()[0].value).toBe(expected[0]);
        expect(component.ngxData()[1].value).toBe(expected[1]);
        expect(component.ngxData()[2].value).toBe(expected[2]);

        componentRef.setInput('currentMax', 0);
        fixture.detectChanges();

        // display grey color when currentMax = 0
        expect(component.ngxData()[0].value).toBe(0);
        expect(component.ngxData()[1].value).toBe(0);
        expect(component.ngxData()[2].value).toBe(1);
    });

    it('should set the right title and link', () => {
        fixture.detectChanges();
        expect(component.doughnutChartTitle()).toBe('assessments');
        expect(component.titleLink()).toBe('assessment-dashboard');

        componentRef.setInput('contentType', DoughnutChartType.COMPLAINTS);
        fixture.detectChanges();
        expect(component.doughnutChartTitle()).toBe('complaints');
        expect(component.titleLink()).toBe('complaints');

        componentRef.setInput('contentType', DoughnutChartType.FEEDBACK);
        fixture.detectChanges();
        expect(component.doughnutChartTitle()).toBe('moreFeedback');
        expect(component.titleLink()).toBe('more-feedback-requests');

        componentRef.setInput('contentType', DoughnutChartType.AVERAGE_COURSE_SCORE);
        fixture.detectChanges();
        expect(component.doughnutChartTitle()).toBe('averageStudentScore');
        expect(component.titleLink()).toBe('scores');

        componentRef.setInput('contentType', DoughnutChartType.CURRENT_LLM_COST);
        fixture.detectChanges();
        expect(component.doughnutChartTitle()).toBe('currentTotalLLMCost');
        expect(component.titleLink()).toBeUndefined();

        componentRef.setInput('contentType', DoughnutChartType.AVERAGE_EXERCISE_SCORE);
        fixture.detectChanges();
        expect(component.doughnutChartTitle()).toBe('');
        expect(component.titleLink()).toBeUndefined();
    });
});
