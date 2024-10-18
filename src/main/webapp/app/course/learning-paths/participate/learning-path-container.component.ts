import { Component, Input, OnInit, ViewChild, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { faChevronDown, faChevronUp, faEye } from '@fortawesome/free-solid-svg-icons';
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
import { LearningPathComponent } from 'app/course/learning-paths/learning-path-graph/learning-path.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { CompetencyGraphModalComponent } from 'app/course/learning-paths/components/competency-graph-modal/competency-graph-modal.component';

@Component({
    selector: 'jhi-learning-path-container',
    styleUrls: ['./learning-path-container.component.scss'],
    templateUrl: './learning-path-container.component.html',
})
export class LearningPathContainerComponent implements OnInit {
    private router = inject(Router);
    private activatedRoute = inject(ActivatedRoute);
    private alertService = inject(AlertService);
    private learningPathService = inject(LearningPathService);
    private lectureService = inject(LectureService);
    private exerciseService = inject(ExerciseService);
    private modalService = inject(NgbModal);
    learningPathStorageService = inject(LearningPathStorageService);

    @ViewChild('learningPathComponent') learningPathComponent: LearningPathComponent;

    @Input() courseId: number;
    learningPathId: number;

    learningObjectId?: number;
    lectureId?: number;
    lecture?: Lecture;
    lectureUnit?: LectureUnit;
    exercise?: Exercise;

    // icons
    faChevronUp = faChevronUp;
    faChevronDown = faChevronDown;
    faEye = faEye;

    ngOnInit() {
        if (!this.courseId) {
            this.activatedRoute.parent!.parent!.params.subscribe((params) => {
                this.courseId = params['courseId'];
            });
        }
        this.learningPathService.getLearningPathId(this.courseId).subscribe({
            next: (learningPathIdResponse) => {
                this.learningPathId = learningPathIdResponse.body!;
            },
            error: (res: HttpErrorResponse) => {
                if (res.status === 404) {
                    // if the learning path does not exist, we do need to create it
                    this.learningPathService.generateLearningPath(this.courseId).subscribe((learningPathIdResponse) => {
                        this.learningPathId = learningPathIdResponse.body!;
                    });
                } else {
                    onError(this.alertService, res);
                }
            },
        });
    }

    onNextTask() {
        const entry = this.currentStateToEntry();
        // reset state to avoid invalid states
        this.undefineAll();
        if (this.learningPathStorageService.hasNextRecommendation(this.learningPathId, entry)) {
            this.loadEntry(this.learningPathStorageService.getNextRecommendation(this.learningPathId, entry));
        }
    }

    onPrevTask() {
        const entry = this.currentStateToEntry();
        // reset state to avoid invalid states
        this.undefineAll();
        if (this.learningPathStorageService.hasPrevRecommendation(this.learningPathId, entry)) {
            this.loadEntry(this.learningPathStorageService.getPrevRecommendation(this.learningPathId, entry));
        }
    }

    currentStateToEntry() {
        if (this.lectureUnit?.id) {
            return new LectureUnitEntry(this.lectureId!, this.lectureUnit.id);
        } else if (this.exercise?.id) {
            return new ExerciseEntry(this.exercise.id);
        }
    }

    private undefineAll() {
        // reset ids
        this.lectureId = undefined;
        this.learningObjectId = undefined;
        // reset models
        this.lecture = undefined;
        this.lectureUnit = undefined;
        this.exercise = undefined;
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
            this.learningPathComponent.clearHighlighting();
            return;
        }
        this.learningPathComponent.highlightNode(entry);
        if (this.learningPathComponent.highlightedNode) {
            this.scrollTo(this.learningPathComponent.highlightedNode);
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
        this.exerciseService.getExerciseDetails(this.learningObjectId!).subscribe({
            next: (exerciseResponse) => {
                this.exercise = exerciseResponse.body!.exercise;
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
        if (node.type !== NodeType.LECTURE_UNIT && node.type !== NodeType.EXERCISE) {
            return;
        }
        // reset state to avoid invalid states
        this.undefineAll();
        this.learningObjectId = node.linkedResource!;
        this.lectureId = node.linkedResourceParent;
        if (node.type === NodeType.LECTURE_UNIT) {
            this.loadLectureUnit();
            this.learningPathComponent.highlightNode(new LectureUnitEntry(this.lectureId!, this.learningObjectId));
        } else if (node.type === NodeType.EXERCISE) {
            this.loadExercise();
            this.learningPathComponent.highlightNode(new ExerciseEntry(this.learningObjectId));
        }
        if (this.learningPathComponent.highlightedNode) {
            this.scrollTo(this.learningPathComponent.highlightedNode);
        }
    }

    scrollTo(node: NgxLearningPathNode) {
        document.getElementById(node.id)?.scrollIntoView({
            behavior: 'smooth',
        });
    }

    viewProgress() {
        const modalRef = this.modalService.open(CompetencyGraphModalComponent, {
            size: 'xl',
            backdrop: 'static',
            windowClass: 'competency-graph-modal',
        });
        modalRef.componentInstance.learningPathId = this.learningPathId;
        // modalRef.componentInstance.learningPath = learningPathResponse.body!;
        // this.learningPathService.getLearningPath(this.learningPathId).subscribe((learningPathResponse) => {
        // });
    }
}
