import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LearningPathStudentPageComponent } from 'app/course/learning-paths/pages/learning-path-student-page/learning-path-student-page.component';
import { provideHttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { LearningPathNavComponent } from 'app/course/learning-paths/components/learning-path-student-nav/learning-path-student-nav.component';
import { By } from '@angular/platform-browser';
import { LearningPathExerciseComponent } from 'app/course/learning-paths/components/learning-path-exercise/learning-path-exercise.component';
import { LearningPathLectureUnitComponent } from 'app/course/learning-paths/components/learning-path-lecture-unit/learning-path-lecture-unit.component';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { LearningPathApiService } from 'app/course/learning-paths/services/learning-path-api.service';
import { AlertService } from 'app/core/util/alert.service';
import { MockAlertService } from '../../../helpers/mocks/service/mock-alert.service';
import { EntityNotFoundError } from 'app/course/learning-paths/exceptions/entity-not-found.error';
import { LearningPathDTO } from 'app/entities/competency/learning-path.model';

describe('LearningPathStudentPageComponent', () => {
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
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AlertService, useClass: MockAlertService },
                {
                    provide: LearningPathApiService,
                    useValue: {
                        getLearningPathForCurrentUser: jest.fn().mockResolvedValue(learningPath),
                        generateLearningPathForCurrentUser: jest.fn().mockResolvedValue(learningPath),
                        startLearningPathForCurrentUser: jest.fn().mockReturnValue(() => Promise.resolve()),
                    },
                },
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
        jest.restoreAllMocks();
    });

    it('should get learning path', async () => {
        const getLearningPathIdSpy = jest.spyOn(learningPathApiService, 'getLearningPathForCurrentUser').mockResolvedValue(learningPath);

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
        jest.spyOn(learningPathApiService, 'getLearningPathForCurrentUser').mockResolvedValueOnce(learningPath);

        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const navComponent = fixture.debugElement.query(By.directive(LearningPathNavComponent));
        expect(navComponent).toBeTruthy();
    });

    it('should show error when learning path could not be loaded', async () => {
        const getLearningPathIdSpy = jest.spyOn(learningPathApiService, 'getLearningPathForCurrentUser').mockRejectedValue(new Error());
        const alertServiceErrorSpy = jest.spyOn(alertService, 'addAlert');

        fixture.detectChanges();
        await fixture.whenStable();

        expect(getLearningPathIdSpy).toHaveBeenCalledExactlyOnceWith(courseId);
        expect(alertServiceErrorSpy).toHaveBeenCalledOnce();
    });

    it('should set isLoading correctly during learning path load', async () => {
        jest.spyOn(learningPathApiService, 'getLearningPathForCurrentUser').mockResolvedValue(learningPath);
        const loadingSpy = jest.spyOn(component.isLearningPathLoading, 'set');

        fixture.detectChanges();
        await fixture.whenStable();

        expect(loadingSpy).toHaveBeenNthCalledWith(1, true);
        expect(loadingSpy).toHaveBeenNthCalledWith(2, false);
    });

    it('should generate learning path on start when not found', async () => {
        jest.spyOn(learningPathApiService, 'getLearningPathForCurrentUser').mockReturnValueOnce(Promise.reject(new EntityNotFoundError()));
        const generateLearningPathSpy = jest.spyOn(learningPathApiService, 'generateLearningPathForCurrentUser').mockResolvedValue(learningPath);
        const startSpy = jest.spyOn(learningPathApiService, 'startLearningPathForCurrentUser');

        fixture.detectChanges();
        await fixture.whenStable();

        await component.startLearningPath();

        expect(component.learningPath()).toEqual({ ...learningPath, startedByStudent: true });
        expect(generateLearningPathSpy).toHaveBeenCalledExactlyOnceWith(courseId);
        expect(startSpy).toHaveBeenCalledExactlyOnceWith(learningPath.id);
    });

    it('should set learning path to started', async () => {
        const startedSpy = jest.spyOn(learningPathApiService, 'startLearningPathForCurrentUser');
        fixture.detectChanges();
        await fixture.whenStable();

        await component.startLearningPath();

        expect(component.learningPath()).toEqual({ ...learningPath, startedByStudent: true });
        expect(startedSpy).toHaveBeenCalledExactlyOnceWith(learningPath.id);
    });

    it('should set isLoading correctly during learning path start', async () => {
        const loadingSpy = jest.spyOn(component.isLearningPathLoading, 'set');

        fixture.detectChanges();
        await fixture.whenStable();

        expect(loadingSpy).toHaveBeenNthCalledWith(1, true);
        expect(loadingSpy).toHaveBeenNthCalledWith(2, false);
    });
});
