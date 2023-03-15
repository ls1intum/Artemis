import { HttpResponse } from '@angular/common/http';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';

import { MockHasAnyAuthorityDirective } from '../../../helpers/mocks/directive/mock-has-any-authority.directive';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { ArtemisTestModule } from '../../../test.module';
import { AlertService } from 'app/core/util/alert.service';
import { LearningGoalRingsComponent } from 'app/course/learning-goals/learning-goal-rings/learning-goal-rings.component';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { LearningGoal, LearningGoalProgress } from 'app/entities/learningGoal.model';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { CourseLearningGoalsDetailsComponent } from 'app/overview/course-learning-goals/course-learning-goals-details.component';
import { AttachmentUnitComponent } from 'app/overview/course-lectures/attachment-unit/attachment-unit.component';
import { ExerciseUnitComponent } from 'app/overview/course-lectures/exercise-unit/exercise-unit.component';
import { OnlineUnitComponent } from 'app/overview/course-lectures/online-unit/online-unit.component';
import { TextUnitComponent } from 'app/overview/course-lectures/text-unit/text-unit.component';
import { VideoUnitComponent } from 'app/overview/course-lectures/video-unit/video-unit.component';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { FireworksComponent } from 'app/shared/fireworks/fireworks.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SidePanelComponent } from 'app/shared/side-panel/side-panel.component';

describe('CourseLearningGoalsDetails', () => {
    let fixture: ComponentFixture<CourseLearningGoalsDetailsComponent>;
    let component: CourseLearningGoalsDetailsComponent;

    let learningGoalService: LearningGoalService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule, MockModule(NgbTooltipModule)],
            declarations: [
                CourseLearningGoalsDetailsComponent,
                MockPipe(ArtemisTranslatePipe),
                MockDirective(MockHasAnyAuthorityDirective),
                MockComponent(AttachmentUnitComponent),
                MockComponent(ExerciseUnitComponent),
                MockComponent(TextUnitComponent),
                MockComponent(VideoUnitComponent),
                MockComponent(OnlineUnitComponent),
                MockComponent(LearningGoalRingsComponent),
                MockComponent(SidePanelComponent),
                MockComponent(HelpIconComponent),
                MockComponent(FaIconComponent),
                MockComponent(FireworksComponent),
            ],
            providers: [
                MockProvider(LectureUnitService),
                MockProvider(AlertService),
                {
                    provide: ActivatedRoute,
                    useValue: {
                        params: of({ learningGoalId: '1', courseId: '1' }),
                    },
                },
                { provide: Router, useValue: MockRouter },
            ],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseLearningGoalsDetailsComponent);
                component = fixture.componentInstance;
                learningGoalService = TestBed.inject(LearningGoalService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
    });

    it('should load learning goal to display progress and all lecture units', () => {
        const learningGoal = {
            id: 1,
            lectureUnits: [new TextUnit()],
            exercises: [{ id: 5 } as TextExercise],
        } as LearningGoal;
        const findByIdSpy = jest.spyOn(learningGoalService, 'findById').mockReturnValue(of(new HttpResponse({ body: learningGoal })));

        fixture.detectChanges();

        const textUnit = fixture.debugElement.query(By.directive(TextUnitComponent));
        const exerciseUnit = fixture.debugElement.query(By.directive(ExerciseUnitComponent));

        expect(findByIdSpy).toHaveBeenCalledOnce();
        expect(component.learningGoal.lectureUnits).toHaveLength(2);
        expect(textUnit).not.toBeNull();
        expect(exerciseUnit).not.toBeNull();
    });

    it('should load learning goal to display progress and the exercise unit', () => {
        const learningGoal = {
            id: 1,
            exercises: [{ id: 5 } as ModelingExercise],
        } as LearningGoal;
        const findByIdSpy = jest.spyOn(learningGoalService, 'findById').mockReturnValue(of(new HttpResponse({ body: learningGoal })));

        fixture.detectChanges();

        const exerciseUnit = fixture.debugElement.query(By.directive(ExerciseUnitComponent));

        expect(findByIdSpy).toHaveBeenCalledOnce();
        expect(component.learningGoal.lectureUnits).toHaveLength(1);
        expect(exerciseUnit).not.toBeNull();
    });

    it('should show fireworks when learning goal was mastered', fakeAsync(() => {
        const learningGoal = {
            id: 1,
            userProgress: [
                {
                    progress: 100,
                    confidence: 100,
                } as LearningGoalProgress,
            ],
        } as LearningGoal;
        const findByIdSpy = jest.spyOn(learningGoalService, 'findById').mockReturnValue(of(new HttpResponse({ body: learningGoal })));

        fixture.detectChanges();
        expect(findByIdSpy).toHaveBeenCalledOnce();
        expect(component.isMastered).toBeTrue();

        component.showFireworksIfMastered();

        tick(1000);
        expect(component.showFireworks).toBeTrue();

        tick(5000);
        expect(component.showFireworks).toBeFalse();
    }));
});
