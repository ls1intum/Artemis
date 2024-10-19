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

    it('should load learning path health status', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const learningPathsStateContainer = fixture.nativeElement.querySelectorAll('.learning-paths-state-container');

        expect(learningPathsStateContainer).toHaveLength(learningPathHealth.status.length);
        expect(getLearningPathHealthStatusSpy).toHaveBeenCalledExactlyOnceWith(courseId);
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

        expect(onErrorSpy).toHaveBeenCalledOnce();
    });

    it.each([HealthStatus.NO_COMPETENCIES, HealthStatus.NO_RELATIONS])('should navigate to competencies page on %s status', async (status) => {
        const navigateSpy = jest.spyOn(router, 'navigate');
        getLearningPathHealthStatusSpy.mockResolvedValue({ ...learningPathHealth, status: [status] });

        await clickHealthStateButton(`#health-state-button-${status}`);

        expect(navigateSpy).toHaveBeenCalledExactlyOnceWith(['../competency-management'], { relativeTo: TestBed.inject(ActivatedRoute) });
    });

    it('should generate missing learning paths', async () => {
        const generateMissingLearningPathsSpy = jest.spyOn(learningPathApiService, 'generateMissingLearningPaths').mockResolvedValue();
        const successSpy = jest.spyOn(alertService, 'success');
        getLearningPathHealthStatusSpy.mockResolvedValue({ ...learningPathHealth, status: [HealthStatus.MISSING] });

        await clickHealthStateButton(`#health-state-button-${HealthStatus.MISSING}`);

        expect(generateMissingLearningPathsSpy).toHaveBeenCalledExactlyOnceWith(courseId);
        expect(successSpy).toHaveBeenCalledOnce();
        expect(getLearningPathHealthStatusSpy).toHaveBeenNthCalledWith(2, courseId);
    });

    it('should show error when generating missing learning paths fails', async () => {
        jest.spyOn(learningPathApiService, 'generateMissingLearningPaths').mockRejectedValue(new Error('Error generating missing learning paths'));
        const onErrorSpy = jest.spyOn(alertService, 'addAlert');
        getLearningPathHealthStatusSpy.mockResolvedValue({ ...learningPathHealth, status: [HealthStatus.MISSING] });

        await clickHealthStateButton(`#health-state-button-${HealthStatus.MISSING}`);

        expect(onErrorSpy).toHaveBeenCalledOnce();
    });

    async function clickHealthStateButton(selector: string) {
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const healthStateButton = fixture.nativeElement.querySelector(selector);
        healthStateButton.click();

        fixture.detectChanges();
        await fixture.whenStable();
    }
});
