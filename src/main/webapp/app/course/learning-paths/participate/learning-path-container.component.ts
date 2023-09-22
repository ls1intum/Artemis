import { Component, Input, OnInit } from '@angular/core';
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
import { ExerciseEntry, LearningPathHistoryStorageService, LectureUnitEntry } from 'app/course/learning-paths/participate/learning-path-history-storage.service';

@Component({
    selector: 'jhi-learning-path-container',
    styleUrls: ['./learning-path-container.component.scss'],
    templateUrl: './learning-path-container.component.html',
})
export class LearningPathContainerComponent implements OnInit {
    @Input() courseId: number;
    learningPathId: number;

    learningObjectId: number;
    lectureId?: number;
    lecture?: Lecture;
    lectureUnit?: LectureUnit;
    exercise?: Exercise;

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
        public learningPathHistoryStorageService: LearningPathHistoryStorageService,
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
        if (this.lectureUnit?.id) {
            this.learningPathHistoryStorageService.storeLectureUnit(this.learningPathId, this.lectureId!, this.lectureUnit.id);
        } else if (this.exercise?.id) {
            this.learningPathHistoryStorageService.storeExercise(this.learningPathId, this.exercise.id);
        }
        // reset state to avoid invalid states
        this.undefineAll();
        // todo: load recommendation, part of next pr
    }

    undefineAll() {
        this.lecture = undefined;
        this.lectureUnit = undefined;
        this.exercise = undefined;
    }

    onPrevTask() {
        // reset state to avoid invalid states
        this.undefineAll();
        if (this.learningPathHistoryStorageService.hasPrevious(this.learningPathId)) {
            const entry = this.learningPathHistoryStorageService.getPrevious(this.learningPathId);
            if (entry instanceof LectureUnitEntry) {
                this.learningObjectId = entry.lectureUnitId;
                this.lectureId = entry.lectureId;
                this.loadLectureUnit();
            } else if (entry instanceof ExerciseEntry) {
                this.learningObjectId = entry.exerciseId;
                this.loadExercise();
            }
        }
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
        this.exerciseService.getExerciseDetails(this.learningObjectId).subscribe({
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
        if (this.lecture) {
            instance.lecture = this.lecture;
            instance.lectureUnit = this.lectureUnit!;
        }
    }

    setupExerciseView(instance: CourseExerciseDetailsComponent) {
        if (this.exercise) {
            instance.learningPathMode = true;
            instance.courseId = this.courseId;
            instance.exerciseId = this.learningObjectId;
        }
    }

    onNodeClicked(node: NgxLearningPathNode) {
        if (node.type !== NodeType.LECTURE_UNIT && node.type !== NodeType.EXERCISE) {
            return;
        }
        if (this.lectureUnit?.id) {
            this.learningPathHistoryStorageService.storeLectureUnit(this.learningPathId, this.lectureId!, this.lectureUnit.id);
        } else if (this.exercise?.id) {
            this.learningPathHistoryStorageService.storeExercise(this.learningPathId, this.exercise.id);
        }
        // reset state to avoid invalid states
        this.undefineAll();
        this.learningObjectId = node.linkedResource!;
        this.lectureId = node.linkedResourceParent;
        if (node.type === NodeType.LECTURE_UNIT) {
            this.loadLectureUnit();
        } else if (node.type === NodeType.EXERCISE) {
            this.loadExercise();
        }
    }
}
