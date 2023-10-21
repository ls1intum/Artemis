import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { ArtemisTestModule } from '../../../test.module';
import { of } from 'rxjs';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { LearningPathContainerComponent } from 'app/course/learning-paths/participate/learning-path-container.component';
import { LearningPathService } from 'app/course/learning-paths/learning-path.service';
import { NgxLearningPathDTO, NgxLearningPathNode, NodeType } from 'app/entities/competency/learning-path.model';
import { LectureService } from 'app/lecture/lecture.service';
import { Lecture } from 'app/entities/lecture.model';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { LearningPathGraphSidebarComponent } from 'app/course/learning-paths/participate/learning-path-graph-sidebar.component';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { LearningPathLectureUnitViewComponent } from 'app/course/learning-paths/participate/lecture-unit/learning-path-lecture-unit-view.component';
import { CourseExerciseDetailsComponent } from 'app/overview/exercise-details/course-exercise-details.component';
import { ExerciseEntry, LearningPathStorageService, LectureUnitEntry } from 'app/course/learning-paths/participate/learning-path-storage.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { LearningPathGraphComponent } from 'app/course/learning-paths/learning-path-graph/learning-path-graph.component';

describe('LearningPathContainerComponent', () => {
    let fixture: ComponentFixture<LearningPathContainerComponent>;
    let comp: LearningPathContainerComponent;
    let learningPathService: LearningPathService;
    let getLearningPathIdStub: jest.SpyInstance;
    const learningPathId = 1337;
    let lectureService: LectureService;
    let lecture: Lecture;
    let lectureUnit: LectureUnit;
    let findWithDetailsStub: jest.SpyInstance;
    let exerciseService: ExerciseService;
    let exercise: Exercise;
    let getExerciseDetailsStub: jest.SpyInstance;
    let historyService: LearningPathStorageService;
    let getNextRecommendationStub: jest.SpyInstance;
    let hasPrevRecommendationStub: jest.SpyInstance;
    let getPrevRecommendationStub: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                ArtemisTestModule,
                MockModule(RouterModule),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(NgbTooltip),
                LearningPathGraphSidebarComponent,
                LearningPathGraphComponent,
            ],
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

                historyService = TestBed.inject(LearningPathStorageService);
                getNextRecommendationStub = jest.spyOn(historyService, 'getNextRecommendation');
                hasPrevRecommendationStub = jest.spyOn(historyService, 'hasPrevRecommendation');
                getPrevRecommendationStub = jest.spyOn(historyService, 'getPrevRecommendation');

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

    it('should retrieve next recommended entry', () => {
        comp.learningObjectId = lectureUnit.id!;
        comp.lectureUnit = lectureUnit;
        comp.lectureId = lecture.id;
        comp.lecture = lecture;
        fixture.detectChanges();
        comp.onNextTask();
        expect(getNextRecommendationStub).toHaveBeenCalledExactlyOnceWith(comp.learningPathId, new LectureUnitEntry(lecture.id!, lectureUnit.id!));
    });

    it('should not load previous task if no task selected', () => {
        comp.onPrevTask();
        expect(getPrevRecommendationStub).not.toHaveBeenCalled();
        expect(findWithDetailsStub).not.toHaveBeenCalled();
        expect(getExerciseDetailsStub).not.toHaveBeenCalled();
    });

    it('should load previous lecture unit', () => {
        hasPrevRecommendationStub.mockReturnValue(true);
        getPrevRecommendationStub.mockReturnValue(new LectureUnitEntry(lecture.id!, lectureUnit.id!));
        comp.graphSidebar.learningPathGraphComponent.ngxPath = { nodes: [], edges: [] } as NgxLearningPathDTO;
        fixture.detectChanges();
        comp.onPrevTask();
        expect(findWithDetailsStub).toHaveBeenCalledExactlyOnceWith(lecture.id);
        expect(getExerciseDetailsStub).not.toHaveBeenCalled();
    });

    it('should load previous exercise', () => {
        hasPrevRecommendationStub.mockReturnValue(true);
        getPrevRecommendationStub.mockReturnValue(new ExerciseEntry(exercise.id!));
        fixture.detectChanges();
        comp.graphSidebar.learningPathGraphComponent.ngxPath = { nodes: [], edges: [] } as NgxLearningPathDTO;
        comp.onPrevTask();
        expect(findWithDetailsStub).not.toHaveBeenCalled();
        expect(getExerciseDetailsStub).toHaveBeenCalledExactlyOnceWith(exercise.id);
    });

    it('should set properties of lecture unit view on activate', () => {
        comp.learningObjectId = lectureUnit.id!;
        comp.lectureUnit = lectureUnit;
        comp.lectureId = lecture.id;
        comp.lecture = lecture;
        fixture.detectChanges();
        const instance = { lecture: undefined, lectureUnit: undefined } as unknown as LearningPathLectureUnitViewComponent;
        comp.setupLectureUnitView(instance);
        expect(instance.lecture).toEqual(lecture);
        expect(instance.lectureUnit).toEqual(lectureUnit);
    });

    it('should set properties of exercise view on activate', () => {
        comp.exercise = exercise;
        comp.learningObjectId = exercise.id!;
        fixture.detectChanges();
        const instance = { learningPathMode: false, courseId: undefined, exerciseId: undefined } as unknown as CourseExerciseDetailsComponent;
        comp.setupExerciseView(instance);
        expect(instance.learningPathMode).toBeTruthy();
        expect(instance.courseId).toBe(1);
        expect(instance.exerciseId).toEqual(exercise.id);
    });

    it('should handle lecture unit node click', () => {
        comp.graphSidebar.learningPathGraphComponent.ngxPath = { nodes: [], edges: [] } as NgxLearningPathDTO;
        const node = { id: 'some-id', type: NodeType.LECTURE_UNIT, linkedResource: 2, linkedResourceParent: 3 } as NgxLearningPathNode;
        comp.onNodeClicked(node);
        expect(comp.learningObjectId).toBe(node.linkedResource);
        expect(comp.lectureId).toBe(node.linkedResourceParent);
        expect(findWithDetailsStub).toHaveBeenCalledWith(node.linkedResourceParent);
    });

    it('should handle exercise node click', () => {
        comp.graphSidebar.learningPathGraphComponent.ngxPath = { nodes: [], edges: [] } as NgxLearningPathDTO;
        const node = { id: 'some-id', type: NodeType.EXERCISE, linkedResource: 2 } as NgxLearningPathNode;
        comp.onNodeClicked(node);
        expect(comp.learningObjectId).toBe(node.linkedResource);
        expect(getExerciseDetailsStub).toHaveBeenCalledWith(node.linkedResource);
    });
});
