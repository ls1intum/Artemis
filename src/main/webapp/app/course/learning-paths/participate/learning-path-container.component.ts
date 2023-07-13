import { Component, Input } from '@angular/core';
import { faChevronLeft, faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { Exercise } from 'app/entities/exercise.model';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';
import { Lecture } from 'app/entities/lecture.model';

@Component({
    selector: 'jhi-learning-path-container',
    templateUrl: './learning-path-container.component.html',
})
export class LearningPathContainerComponent {
    @Input()
    courseId: number;

    lecture: Lecture | undefined;
    lectureUnit: LectureUnit | undefined;
    exercise: Exercise | undefined;
    history: any[] = [];

    // icons
    faChevronLeft = faChevronLeft;
    faChevronRight = faChevronRight;

    constructor() {}

    onNextTask() {
        if (this.lectureUnit?.id) {
            this.history.push(this.lectureUnit.id);
            this.lectureUnit = undefined;
        } else if (this.exercise?.id) {
            this.history.push(this.exercise.id);
            this.lectureUnit = undefined;
        }
        console.log('request next task');
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
        } else if (task instanceof LectureUnit) {
            this.lectureUnit = task;
            this.loadLectureUnit();
        } else {
            this.exercise = task;
            this.loadExercise();
        }
        console.log('request previous task');
    }

    loadLectureUnit() {
        console.log('load lecture unit');
    }

    loadExercise() {
        console.log('load exercise');
    }
}
