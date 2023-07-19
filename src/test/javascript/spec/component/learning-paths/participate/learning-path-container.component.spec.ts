import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockModule } from 'ng-mocks';
import { By } from '@angular/platform-browser';
import { ArtemisTestModule } from '../../../test.module';
import { of } from 'rxjs';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { LearningPathContainerComponent } from 'app/course/learning-paths/participate/learning-path-container.component';
import { LearningPathService } from 'app/course/learning-paths/learning-path.service';
import { LearningPathRecommendation, RecommendationType } from 'app/entities/learning-path.model';
import { LectureService } from 'app/lecture/lecture.service';
import { Lecture } from 'app/entities/lecture.model';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { LearningPathGraphSidebarComponent } from 'app/course/learning-paths/participate/learning-path-graph-sidebar.component';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { TextExercise } from 'app/entities/text-exercise.model';

describe('LearningPathContainerComponent', () => {
    let fixture: ComponentFixture<LearningPathContainerComponent>;
    let comp: LearningPathContainerComponent;
    let learningPathService: LearningPathService;
    let getLearningPathIdStub: jest.SpyInstance;
    const learningPathId = 1337;
    let getRecommendationStub: jest.SpyInstance;
    let lectureService: LectureService;
    let lecture: Lecture;
    let lectureUnit: LectureUnit;
    let findWithDetailsStub: jest.SpyInstance;
    let exerciseService: ExerciseService;
    let exercise: Exercise;
    let getExerciseDetailsStub: jest.SpyInstance;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockComponent(LearningPathGraphSidebarComponent), MockModule(RouterModule)],
            declarations: [LearningPathContainerComponent],
            providers: [
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
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(LearningPathContainerComponent);
                comp = fixture.componentInstance;
                learningPathService = TestBed.inject(LearningPathService);
                getLearningPathIdStub = jest.spyOn(learningPathService, 'getLearningPathId').mockReturnValue(of(new HttpResponse({ body: learningPathId })));
                getRecommendationStub = jest.spyOn(learningPathService, 'getRecommendation');

                lectureUnit = new AttachmentUnit();
                lectureUnit.id = 3;
                lecture = new Lecture();
                lecture.id = 2;
                lecture.lectureUnits = [lectureUnit];
                lectureService = TestBed.inject(LectureService);
                findWithDetailsStub = jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(of(new HttpResponse({ body: lecture })));

                exercise = new TextExercise(undefined, undefined);
                exercise.id = 4;
                exerciseService = TestBed.inject(ExerciseService);
                getExerciseDetailsStub = jest.spyOn(exerciseService, 'getExerciseDetails').mockReturnValue(of(new HttpResponse({ body: exercise })));

                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(comp.courseId).toBe(1);
        expect(getLearningPathIdStub).toHaveBeenCalled();
        expect(getLearningPathIdStub).toHaveBeenCalledWith(1);
    });

    it('should request recommendation on next button click', () => {
        const button = fixture.debugElement.query(By.css('.next-button'));
        expect(button).not.toBeNull();
        button.nativeElement.click();
        expect(getRecommendationStub).toHaveBeenCalledWith(learningPathId);
    });

    it('should load lecture unit on recommendation', () => {
        const recommendation = new LearningPathRecommendation();
        recommendation.learningObjectId = lectureUnit.id!;
        recommendation.lectureId = lecture.id;
        recommendation.type = RecommendationType.LECTURE_UNIT;
        getRecommendationStub.mockReturnValue(of(new HttpResponse({ body: recommendation })));
        comp.onNextTask();
        expect(findWithDetailsStub).toHaveBeenCalled();
        expect(findWithDetailsStub).toHaveBeenCalledWith(lecture.id);
        expect(getExerciseDetailsStub).not.toHaveBeenCalled();
    });

    it('should load exercise on recommendation', () => {
        const recommendation = new LearningPathRecommendation();
        recommendation.learningObjectId = exercise.id!;
        recommendation.type = RecommendationType.EXERCISE;
        getRecommendationStub.mockReturnValue(of(new HttpResponse({ body: recommendation })));
        comp.onNextTask();
        expect(findWithDetailsStub).not.toHaveBeenCalled();
        expect(getExerciseDetailsStub).toHaveBeenCalled();
        expect(getExerciseDetailsStub).toHaveBeenCalledWith(exercise.id);
    });

    it('should store current lecture unit in history', () => {
        comp.learningObjectId = lectureUnit.id!;
        comp.lectureUnit = lectureUnit;
        comp.lectureId = lecture.id;
        comp.lecture = lecture;
        fixture.detectChanges();
        comp.onNextTask();
        expect(comp.history).toEqual([[lectureUnit.id!, lecture.id!]]);
    });

    it('should store current exercise in history', () => {
        comp.learningObjectId = exercise.id!;
        comp.exercise = exercise;
        fixture.detectChanges();
        comp.onNextTask();
        expect(comp.history).toEqual([[exercise.id!, -1]]);
    });

    it('should load no previous task if history is empty', () => {
        comp.onPrevTask();
        expect(findWithDetailsStub).not.toHaveBeenCalled();
        expect(getExerciseDetailsStub).not.toHaveBeenCalled();
    });

    it('should load previous lecture unit', () => {
        comp.history = [[lectureUnit.id!, lecture.id!]];
        fixture.detectChanges();
        comp.onPrevTask();
        expect(findWithDetailsStub).toHaveBeenCalled();
        expect(findWithDetailsStub).toHaveBeenCalledWith(lecture.id);
        expect(getExerciseDetailsStub).not.toHaveBeenCalled();
    });

    it('should load previous exercise', () => {
        comp.history = [[exercise.id!, -1]];
        fixture.detectChanges();
        comp.onPrevTask();
        expect(findWithDetailsStub).not.toHaveBeenCalled();
        expect(getExerciseDetailsStub).toHaveBeenCalled();
        expect(getExerciseDetailsStub).toHaveBeenCalledWith(exercise.id);
    });
});
