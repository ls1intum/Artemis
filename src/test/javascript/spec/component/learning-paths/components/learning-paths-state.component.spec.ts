import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LearningPathsStateComponent } from 'app/course/learning-paths/components/learning-paths-state/learning-paths-state.component';
import { LearningPathApiService } from 'app/course/learning-paths/services/learning-path-api.service';
import { AlertService } from 'app/core/util/alert.service';
import { MockAlertService } from '../../../helpers/mocks/service/mock-alert.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { provideHttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { HealthStatus, LearningPathHealthDTO } from 'app/entities/competency/learning-path-health.model';
import { MockRouter } from '../../../helpers/mocks/mock-router';

describe('LearningPathsStateComponent', () => {
    let component: LearningPathsStateComponent;
    let fixture: ComponentFixture<LearningPathsStateComponent>;
    let learningPathApiService: LearningPathApiService;
    let alertService: AlertService;
    let router: Router;
    let getLearningPathHealthStatusSpy: jest.SpyInstance;

    const courseId = 1;

    const learningPathHealth = <LearningPathHealthDTO>{
        missingLearningPaths: 1,
        status: [HealthStatus.MISSING, HealthStatus.NO_COMPETENCIES, HealthStatus.NO_RELATIONS],
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LearningPathsStateComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
                { provide: AlertService, useClass: MockAlertService },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            parent: {
                                params: of({
                                    courseId: courseId,
                                }),
                            },
                        },
                    },
                },
                {
                    provide: Router,
                    useClass: MockRouter,
                },
            ],
        }).compileComponents();

        learningPathApiService = TestBed.inject(LearningPathApiService);
        alertService = TestBed.inject(AlertService);
        router = TestBed.inject(Router);

        getLearningPathHealthStatusSpy = jest.spyOn(learningPathApiService, 'getLearningPathHealthStatus').mockResolvedValue(learningPathHealth);

        fixture = TestBed.createComponent(LearningPathsStateComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('courseId', courseId);
    });

    it('should initialize', () => {
        fixture.detectChanges();

        expect(component).toBeDefined();
        expect(component.courseId()).toBe(courseId);
    });

    it('should load learning path health status', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const learningPathsStateContainer = fixture.nativeElement.querySelectorAll('.learning-paths-state-container');

        expect(learningPathsStateContainer).toHaveLength(learningPathHealth.status.length);
        expect(getLearningPathHealthStatusSpy).toHaveBeenCalledWith(courseId);
        expect(component.learningPathHealthState()).toEqual(learningPathHealth.status);
    });

    it('should set isLoading correctly', async () => {
        const isLoadingSpy = jest.spyOn(component.isLoading, 'set');

        fixture.detectChanges();
        await fixture.whenStable();

        expect(isLoadingSpy).toHaveBeenNthCalledWith(1, true);
        expect(isLoadingSpy).toHaveBeenNthCalledWith(2, false);
    });

    it('should show error when loading fails', async () => {
        jest.spyOn(learningPathApiService, 'getLearningPathHealthStatus').mockRejectedValue(new Error('Error loading learning path health status'));
        const onErrorSpy = jest.spyOn(alertService, 'addAlert');

        fixture.detectChanges();
        await fixture.whenStable();

        expect(onErrorSpy).toHaveBeenCalled();
    });

    it.each([HealthStatus.NO_COMPETENCIES, HealthStatus.NO_RELATIONS])('should navigate to competencies page on %s status', async (status) => {
        const navigateSpy = jest.spyOn(router, 'navigate');
        getLearningPathHealthStatusSpy.mockResolvedValue({ ...learningPathHealth, status: [status] });

        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const healthStateButton = fixture.nativeElement.querySelector(`#health-state-button-${status}`);
        healthStateButton.click();

        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(navigateSpy).toHaveBeenCalledWith(['../competency-management'], { relativeTo: TestBed.inject(ActivatedRoute) });
    });
});
