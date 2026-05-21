import { vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LearningPathStudentPageComponent } from 'app/atlas/overview/learning-path-student-page/learning-path-student-page.component';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { LearningPathNavComponent } from 'app/atlas/overview/learning-path-student-nav/learning-path-student-nav.component';
import { By } from '@angular/platform-browser';
import { LearningPathExerciseComponent } from 'app/atlas/overview/learning-path-exercise/learning-path-exercise.component';
import { LearningPathLectureUnitComponent } from 'app/atlas/overview/learning-path-lecture-unit/learning-path-lecture-unit.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { LearningPathApiService } from 'app/atlas/shared/services/learning-path-api.service';
import { AlertService } from 'app/shared/service/alert.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { LearningPathDTO } from 'app/atlas/shared/entities/learning-path.model';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ScienceService } from 'app/shared/science/science.service';
import { MockProvider } from 'ng-mocks';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

describe('LearningPathStudentPageComponent', () => {
    setupTestBed({ zoneless: true });
    let component: LearningPathStudentPageComponent;
    let fixture: ComponentFixture<LearningPathStudentPageComponent>;
    let learningPathApiService: LearningPathApiService;
    let alertService: AlertService;

    const learningPath: LearningPathDTO = {
        id: 1,
        progress: 0,
        startedByStudent: false,
    };
    const courseId = 2;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LearningPathStudentPageComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
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
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AlertService, useClass: MockAlertService },
                {
                    provide: LearningPathApiService,
                    useValue: {
                        getLearningPathForCurrentUser: vi.fn().mockResolvedValue(learningPath),
                        generateLearningPathForCurrentUser: vi.fn().mockResolvedValue(learningPath),
                        startLearningPathForCurrentUser: vi.fn().mockReturnValue(() => Promise.resolve()),
                    },
                },
                MockProvider(ScienceService),
            ],
        })
            .overrideComponent(LearningPathStudentPageComponent, {
                remove: {
                    imports: [LearningPathExerciseComponent, LearningPathLectureUnitComponent],
                },
                add: {
                    imports: [LearningPathNavComponent],
                },
            })
            .compileComponents();

        learningPathApiService = TestBed.inject(LearningPathApiService);
        alertService = TestBed.inject(AlertService);

        fixture = TestBed.createComponent(LearningPathStudentPageComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should get learning path', async () => {
        const getLearningPathIdSpy = vi.spyOn(learningPathApiService, 'getLearningPathForCurrentUser').mockResolvedValue(learningPath);

        fixture.detectChanges();
        await fixture.whenStable();

        expect(getLearningPathIdSpy).toHaveBeenCalledExactlyOnceWith(courseId);
        expect(component.learningPath()).toEqual(learningPath);
    });

    it('should show navigation when learning path has been started', async () => {
        const learningPath: LearningPathDTO = {
            id: 1,
            progress: 0,
            startedByStudent: true,
        };
        vi.spyOn(learningPathApiService, 'getLearningPathForCurrentUser').mockResolvedValueOnce(learningPath);

        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const navComponent = fixture.debugElement.query(By.directive(LearningPathNavComponent));
        expect(navComponent).toBeTruthy();
    });

    it('should show error when learning path could not be loaded', async () => {
        const getLearningPathIdSpy = vi.spyOn(learningPathApiService, 'getLearningPathForCurrentUser').mockRejectedValue(new Error());
        const alertServiceErrorSpy = vi.spyOn(alertService, 'addAlert');

        fixture.detectChanges();
        await fixture.whenStable();

        expect(getLearningPathIdSpy).toHaveBeenCalledExactlyOnceWith(courseId);
        expect(alertServiceErrorSpy).toHaveBeenCalledOnce();
    });

    it('should set isLoading correctly during learning path load', async () => {
        vi.spyOn(learningPathApiService, 'getLearningPathForCurrentUser').mockResolvedValue(learningPath);
        const loadingSpy = vi.spyOn(component.isLearningPathLoading, 'set');

        fixture.detectChanges();
        await fixture.whenStable();

        expect(loadingSpy).toHaveBeenNthCalledWith(1, true);
        expect(loadingSpy).toHaveBeenNthCalledWith(2, false);
    });

    it('should generate learning path on start when not found', async () => {
        vi.spyOn(learningPathApiService, 'getLearningPathForCurrentUser').mockReturnValueOnce(Promise.reject(new HttpErrorResponse({ status: 404 })));
        const generateLearningPathSpy = vi.spyOn(learningPathApiService, 'generateLearningPathForCurrentUser').mockResolvedValue(learningPath);
        const startSpy = vi.spyOn(learningPathApiService, 'startLearningPathForCurrentUser');

        fixture.detectChanges();
        await fixture.whenStable();

        await component.startLearningPath();

        expect(component.learningPath()).toEqual({ ...learningPath, startedByStudent: true });
        expect(generateLearningPathSpy).toHaveBeenCalledExactlyOnceWith(courseId);
        expect(startSpy).toHaveBeenCalledExactlyOnceWith(learningPath.id);
    });

    it('should set learning path to started', async () => {
        const startedSpy = vi.spyOn(learningPathApiService, 'startLearningPathForCurrentUser');
        fixture.detectChanges();
        await fixture.whenStable();

        await component.startLearningPath();

        expect(component.learningPath()).toEqual({ ...learningPath, startedByStudent: true });
        expect(startedSpy).toHaveBeenCalledExactlyOnceWith(learningPath.id);
    });

    it('should set isLoading correctly during learning path start', async () => {
        const loadingSpy = vi.spyOn(component.isLearningPathLoading, 'set');

        fixture.detectChanges();
        await fixture.whenStable();

        expect(loadingSpy).toHaveBeenNthCalledWith(1, true);
        expect(loadingSpy).toHaveBeenNthCalledWith(2, false);
    });
});
