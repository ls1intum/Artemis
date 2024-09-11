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

describe('LearningPathStudentPageComponent', () => {
    let component: LearningPathStudentPageComponent;
    let fixture: ComponentFixture<LearningPathStudentPageComponent>;
    let learningPathApiService: LearningPathApiService;
    let alertService: AlertService;

    const learningPathId = 1;
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

    it('should initialize', () => {
        expect(component).toBeTruthy();
        expect(component.courseId()).toBe(courseId);
    });

    it('should get learning path id', async () => {
        const getLearningPathIdSpy = jest.spyOn(learningPathApiService, 'getLearningPathId').mockResolvedValue(learningPathId);

        fixture.detectChanges();
        await fixture.whenStable();

        expect(getLearningPathIdSpy).toHaveBeenCalledWith(courseId);
        expect(component.learningPathId()).toEqual(learningPathId);
    });

    it('should show navigation on successful load', async () => {
        jest.spyOn(learningPathApiService, 'getLearningPathId').mockResolvedValue(learningPathId);

        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const navComponent = fixture.debugElement.query(By.directive(LearningPathNavComponent));
        expect(navComponent).toBeTruthy();
    });

    it('should show error when learning path id could not be loaded', async () => {
        const getLearningPathIdSpy = jest.spyOn(learningPathApiService, 'getLearningPathId').mockRejectedValue(new Error());
        const alertServiceErrorSpy = jest.spyOn(alertService, 'addAlert');

        fixture.detectChanges();
        await fixture.whenStable();

        expect(getLearningPathIdSpy).toHaveBeenCalledWith(courseId);
        expect(alertServiceErrorSpy).toHaveBeenCalledOnce();
    });

    it('should set isLoading correctly during learning path load', async () => {
        jest.spyOn(learningPathApiService, 'getLearningPathId').mockResolvedValue(learningPathId);
        const loadingSpy = jest.spyOn(component.isLearningPathIdLoading, 'set');

        fixture.detectChanges();
        await fixture.whenStable();

        expect(loadingSpy).toHaveBeenNthCalledWith(1, true);
        expect(loadingSpy).toHaveBeenNthCalledWith(2, false);
    });

    it('should generate learning path on click', async () => {
        jest.spyOn(learningPathApiService, 'getLearningPathId').mockRejectedValue(new EntityNotFoundError());
        const generateLearningPathSpy = jest.spyOn(learningPathApiService, 'generateLearningPath').mockResolvedValue(learningPathId);

        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const generateLearningPathButton = fixture.debugElement.query(By.css('#generate-learning-path-button'));
        generateLearningPathButton.nativeElement.click();

        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.learningPathId()).toEqual(learningPathId);
        expect(generateLearningPathSpy).toHaveBeenCalledWith(courseId);
    });

    it('should set isLoading correctly during learning path generation', async () => {
        jest.spyOn(learningPathApiService, 'getLearningPathId').mockRejectedValue(new EntityNotFoundError());
        jest.spyOn(learningPathApiService, 'generateLearningPath').mockResolvedValue(learningPathId);
        const loadingSpy = jest.spyOn(component.isLearningPathIdLoading, 'set');

        fixture.detectChanges();
        await fixture.whenStable();

        expect(loadingSpy).toHaveBeenNthCalledWith(1, true);
        expect(loadingSpy).toHaveBeenNthCalledWith(2, false);
    });
});
