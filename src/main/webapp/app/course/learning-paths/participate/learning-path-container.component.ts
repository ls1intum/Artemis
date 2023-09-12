import { Component, Input, OnInit, ViewChild, ViewEncapsulation } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { faChevronLeft, faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { Exercise } from 'app/entities/exercise.model';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';
import { Lecture } from 'app/entities/lecture.model';
import { LearningPathService } from 'app/course/learning-paths/learning-path.service';
import { NgxLearningPathNode, NodeType } from 'app/entities/competency/learning-path.model';
import { LectureService } from 'app/lecture/lecture.service';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { LearningPathLectureUnitViewComponent } from 'app/course/learning-paths/participate/lecture-unit/learning-path-lecture-unit-view.component';
import { CourseExerciseDetailsComponent } from 'app/overview/exercise-details/course-exercise-details.component';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ExerciseEntry, LearningPathStorageService, LectureUnitEntry, StorageEntry } from 'app/course/learning-paths/participate/learning-path-storage.service';
import { LearningPathGraphSidebarComponent } from 'app/course/learning-paths/participate/learning-path-graph-sidebar.component';

@Component({
    selector: 'jhi-learning-path-container',
    styleUrls: ['./learning-path-container.component.scss'],
    templateUrl: './learning-path-container.component.html',
    encapsulation: ViewEncapsulation.None,
})
export class LearningPathContainerComponent implements OnInit {
    @ViewChild('graphSidebar') graphSidebar: LearningPathGraphSidebarComponent;

    @Input() courseId: number;
    learningPathId: number;

    learningObjectId?: number;
    lectureId?: number;
    lecture: Lecture | undefined;
    lectureUnit: LectureUnit | undefined;
    exercise: Exercise | undefined;

    // icons
    faChevronLeft = faChevronLeft;
    faChevronRight = faChevronRight;

    constructor(
        private router: Router,
        private activatedRoute: ActivatedRoute,
        private alertService: AlertService,
        private learningPathService: LearningPathService,
        private lectureService: LectureService,
        private exerciseService: ExerciseService,
        public learningPathStorageService: LearningPathStorageService,
    ) {}

    ngOnInit() {
        if (!this.courseId) {
            this.activatedRoute.parent!.parent!.params.subscribe((params) => {
                this.courseId = params['courseId'];
            });
        }
        this.learningPathService.getLearningPathId(this.courseId).subscribe((learningPathIdResponse) => {
            this.learningPathId = learningPathIdResponse.body!;

            // load latest lecture unit or exercise that was accessed
            this.onPrevTask();
        });
    }

    onNextTask() {
        let entry: LectureUnitEntry | ExerciseEntry | undefined;
        if (this.lectureUnit?.id) {
            entry = this.learningPathStorageService.storeLectureUnit(this.learningPathId, this.lectureId!, this.lectureUnit.id);
        } else if (this.exercise?.id) {
            entry = this.learningPathStorageService.storeExercise(this.learningPathId, this.exercise.id);
        }
        // reset state to avoid invalid states
        this.undefineAll();
        const recommendation = this.learningPathStorageService.getNextRecommendation(this.learningPathId, entry);
        this.loadEntry(recommendation);
    }

    undefineAll() {
        // reset ids
        this.lectureId = undefined;
        this.learningObjectId = undefined;
        // reset models
        this.lecture = undefined;
        this.lectureUnit = undefined;
        this.exercise = undefined;
    }

    onPrevTask() {
        // reset interaction with current learning object
        if (this.learningObjectId && this.lectureId) {
            this.learningPathStorageService.setInteraction(this.learningPathId, new LectureUnitEntry(this.lectureId, this.learningObjectId), false);
        } else if (this.learningObjectId) {
            this.learningPathStorageService.setInteraction(this.learningPathId, new ExerciseEntry(this.learningObjectId), false);
        }
        // reset state to avoid invalid states
        this.undefineAll();
        if (this.learningPathStorageService.hasPrevious(this.learningPathId)) {
            this.loadEntry(this.learningPathStorageService.getPrevious(this.learningPathId));
        }
    }

    private loadEntry(entry: StorageEntry | undefined) {
        if (entry instanceof LectureUnitEntry) {
            this.learningObjectId = entry.lectureUnitId;
            this.lectureId = entry.lectureId;
            this.loadLectureUnit();
        } else if (entry instanceof ExerciseEntry) {
            this.learningObjectId = entry.exerciseId;
            this.loadExercise();
        } else {
            this.graphSidebar.learningPathGraphComponent.clearHighlighting();
            return;
        }
        this.graphSidebar.learningPathGraphComponent.highlightNode(entry);
    }

    loadLectureUnit() {
        this.lectureService.findWithDetails(this.lectureId!).subscribe({
            next: (findLectureResult) => {
                this.lecture = findLectureResult.body!;
                if (this.lecture?.lectureUnits) {
                    this.lectureUnit = this.lecture.lectureUnits.find((lectureUnit) => lectureUnit.id === this.learningObjectId);
                }
            },
            error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
        });
        this.router.navigate(['lecture-unit'], { relativeTo: this.activatedRoute });
    }

    loadExercise() {
        this.exerciseService.getExerciseDetails(this.learningObjectId!).subscribe({
            next: (exerciseResponse) => {
                this.exercise = exerciseResponse.body!;
            },
            error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
        });
        this.router.navigate(['exercise'], { relativeTo: this.activatedRoute });
    }

    /**
     * This function gets called if the router outlet gets activated. This is
     * used only for the LearningPathLectureUnitViewComponent
     * @param instance The component instance
     */
    onChildActivate(instance: LearningPathLectureUnitViewComponent | CourseExerciseDetailsComponent) {
        if (instance instanceof LearningPathLectureUnitViewComponent) {
            this.setupLectureUnitView(instance);
        } else {
            this.setupExerciseView(instance);
        }
    }

    setupLectureUnitView(instance: LearningPathLectureUnitViewComponent) {
        if (this.lecture && this.lectureUnit) {
            instance.lecture = this.lecture;
            instance.lectureUnit = this.lectureUnit!;
        }
    }

    setupExerciseView(instance: CourseExerciseDetailsComponent) {
        if (this.exercise) {
            instance.learningPathMode = true;
            instance.courseId = this.courseId;
            instance.exerciseId = this.learningObjectId!;
        }
    }

    onNodeClicked(node: NgxLearningPathNode) {
        if (node.type === NodeType.LECTURE_UNIT || node.type === NodeType.EXERCISE) {
            if (this.lectureUnit?.id) {
                this.learningPathStorageService.storeLectureUnit(this.learningPathId, this.lectureId!, this.lectureUnit.id);
            } else if (this.exercise?.id) {
                this.learningPathStorageService.storeExercise(this.learningPathId, this.exercise.id);
            }
            // reset state to avoid invalid states
            this.undefineAll();
            this.learningObjectId = node.linkedResource!;
            this.lectureId = node.linkedResourceParent;
            if (node.type === NodeType.LECTURE_UNIT) {
                this.loadLectureUnit();
                this.graphSidebar.learningPathGraphComponent.highlightNode(new LectureUnitEntry(this.lectureId!, this.learningObjectId));
            } else if (node.type === NodeType.EXERCISE) {
                this.loadExercise();
                this.graphSidebar.learningPathGraphComponent.highlightNode(new ExerciseEntry(this.learningObjectId));
            }
        }
    }
}
