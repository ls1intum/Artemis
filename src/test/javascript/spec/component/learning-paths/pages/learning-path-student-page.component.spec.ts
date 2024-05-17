import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { LearningPathStudentPageComponent } from 'app/course/learning-paths/pages/learning-path-student-page/learning-path-student-page.component';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { LearningPathService } from 'app/course/learning-paths/learning-path.service';
import { LearningPathStudentNavComponent } from 'app/course/learning-paths/components/learning-path-student-nav/learning-path-student-nav.component';
import { By } from '@angular/platform-browser';
import { LearningPathExerciseComponent } from 'app/course/learning-paths/components/learning-path-exercise/learning-path-exercise.component';
import { LearningPathLectureUnitComponent } from 'app/course/learning-paths/components/learning-path-lecture-unit/learning-path-lecture-unit.component';

describe('LearningPathStudentPageComponent', () => {
    let component: LearningPathStudentPageComponent;
    let fixture: ComponentFixture<LearningPathStudentPageComponent>;
    let learningPathService: LearningPathService;
    let getLearningPathIdSpy: jest.SpyInstance;

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
                            parent: {
                                params: of({
                                    courseId: 1,
                                }),
                            },
                        },
                    },
                },

                { provide: TranslateService, useValue: jest.fn() },
            ],
        })
            .overrideComponent(LearningPathStudentPageComponent, {
                remove: {
                    imports: [LearningPathExerciseComponent, LearningPathLectureUnitComponent],
                },
                add: {
                    imports: [LearningPathStudentNavComponent],
                },
            })
            .compileComponents();

        learningPathService = TestBed.inject(LearningPathService);
        getLearningPathIdSpy = jest.spyOn(learningPathService, 'getLearningPathId');

        fixture = TestBed.createComponent(LearningPathStudentPageComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).toBeTruthy();
        expect(component.courseId()).toBe(1);
    });

    it('should get learning path id (success)', fakeAsync(() => {
        getLearningPathIdSpy = jest.spyOn(learningPathService, 'getLearningPathId').mockReturnValue(of(new HttpResponse({ body: 1 })));

        fixture.detectChanges();
        tick(); // simulate async

        expect(getLearningPathIdSpy).toHaveBeenCalledOnce();
        expect(component.learningPathId()).toBe(1);
    }));

    it('should generate learning path if not found', fakeAsync(() => {
        const generateLearningPathSpy = jest.spyOn(learningPathService, 'generateLearningPath').mockReturnValue(of(new HttpResponse({ body: 2 })));
        jest.spyOn(learningPathService, 'getLearningPathId').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));

        fixture.detectChanges();
        tick(); // Simulate async

        expect(generateLearningPathSpy).toHaveBeenCalled();
        expect(component.learningPathId()).toBe(2);
    }));

    it('should show navigation when learning path id is available', fakeAsync(() => {
        jest.spyOn(learningPathService, 'getLearningPathId').mockReturnValue(of(new HttpResponse({ body: 1 })));

        fixture.detectChanges();
        tick(); // Simulate async
        fixture.detectChanges();

        const navComponent = fixture.debugElement.query(By.directive(LearningPathStudentNavComponent));
        expect(navComponent).toBeTruthy();
    }));
});
