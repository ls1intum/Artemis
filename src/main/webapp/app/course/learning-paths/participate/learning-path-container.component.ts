import { Component, Input, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { faChevronLeft, faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { Exercise } from 'app/entities/exercise.model';
import { LectureUnit, LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { Lecture } from 'app/entities/lecture.model';
import { LearningPathService } from 'app/course/learning-paths/learning-path.service';
import { RecommendationType } from 'app/entities/learning-path.model';
import { LectureService } from 'app/lecture/lecture.service';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { isCommunicationEnabled, isMessagingEnabled } from 'app/entities/course.model';
import { DiscussionSectionComponent } from 'app/overview/discussion-section/discussion-section.component';

@Component({
    selector: 'jhi-learning-path-container',
    templateUrl: './learning-path-container.component.html',
})
export class LearningPathContainerComponent implements OnInit {
    @Input()
    courseId: number;
    learningPathId: number;

    learningObjectId: number;
    lectureId?: number;
    lecture: Lecture | undefined;
    lectureUnit: LectureUnit | undefined;
    exercise: Exercise | undefined;
    history: [number, number][] = [];

    // icons
    faChevronLeft = faChevronLeft;
    faChevronRight = faChevronRight;

    discussionComponent?: DiscussionSectionComponent;

    constructor(
        private router: Router,
        private activatedRoute: ActivatedRoute,
        private learningPathService: LearningPathService,
        private lectureService: LectureService,
        private alertService: AlertService,
    ) {}

    ngOnInit() {
        console.log('ON INIT CONTAINER');
        if (!this.courseId) {
            this.activatedRoute.parent!.parent!.params.subscribe((params) => {
                this.courseId = +params['courseId'];
            });
        }
        this.learningPathService.getLearningPathId(this.courseId).subscribe((learningPathIdResponse) => {
            this.learningPathId = learningPathIdResponse.body!;
        });
    }

    onNextTask() {
        if (this.lectureUnit?.id) {
            this.history.push([this.lectureUnit.id, this.lectureId!]);
        } else if (this.exercise?.id) {
            this.history.push([this.exercise.id, -1]);
        }
        this.undefineAll();
        console.log('request next task');
        console.log(this.courseId);
        this.learningPathService.getRecommendation(this.learningPathId).subscribe((recommendationResponse) => {
            const recommendation = recommendationResponse.body!;
            this.learningObjectId = recommendation.learningObjectId;
            this.lectureId = recommendation.lectureId;
            if (recommendation.type == RecommendationType.LECTURE_UNIT) {
                this.loadLectureUnit();
            } else if (recommendation.type === RecommendationType.EXERCISE) {
                this.loadExercise();
            }
        });
    }

    undefineAll() {
        this.lecture = undefined;
        this.lectureUnit = undefined;
        this.exercise = undefined;
    }

    onPrevTask() {
        this.undefineAll();
        const task = this.history.pop();
        if (!task) {
            return;
        } else {
            this.learningObjectId = task[0];
            this.lectureId = task[1];
            if (task[1] == -1) {
                this.loadExercise();
            } else {
                this.loadLectureUnit();
            }
        }
        console.log('request previous task');
    }

    loadLectureUnit() {
        console.log('loading lecture unit');
        this.lectureService.findWithDetails(this.lectureId!).subscribe({
            next: (findLectureResult) => {
                this.lecture = findLectureResult.body!;
                if (this.lecture?.lectureUnits) {
                    this.lectureUnit = this.lecture.lectureUnits.find((lectureUnit) => lectureUnit.id === this.learningObjectId);
                }
            },
            error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
        });
    }

    loadExercise() {
        console.log('load exercise');
    }

    /**
     * This function gets called if the router outlet gets activated. This is
     * used only for the DiscussionComponent
     * @param instance The component instance
     */
    onChildActivate(instance: DiscussionSectionComponent) {
        console.log(instance);
        this.discussionComponent = instance; // save the reference to the component instance
        if (this.lecture) {
            instance.lecture = this.lecture;
            instance.isCommunicationPage = false;
        }
        // todo exercise
    }

    protected readonly isMessagingEnabled = isMessagingEnabled;
    protected readonly isCommunicationEnabled = isCommunicationEnabled;
    protected readonly LectureUnitType = LectureUnitType;
}
