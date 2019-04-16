import { Component, HostBinding, Input, OnInit } from '@angular/core';
import { Exercise, ExerciseCategory, ExerciseService, ExerciseType, ParticipationStatus, getIcon, getIconTooltip } from 'app/entities/exercise';
import { JhiAlertService } from 'ng-jhipster';
import { QuizExercise } from 'app/entities/quiz-exercise';
import { InitializationState, Participation, ParticipationService } from 'app/entities/participation';
import * as moment from 'moment';
import { Moment } from 'moment';
import { Course } from 'app/entities/course';
import { AccountService, WindowRef } from 'app/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { ProgrammingExercise } from 'app/entities/programming-exercise';
import { Lecture } from 'app/entities/lecture';

@Component({
    selector: 'jhi-course-lecture-row',
    templateUrl: './course-lecture-row.component.html',
    styleUrls: ['./course-lecture-row.scss'],
})
export class CourseLectureRowComponent implements OnInit {
    readonly QUIZ = ExerciseType.QUIZ;
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly MODELING = ExerciseType.MODELING;
    readonly TEXT = ExerciseType.TEXT;
    readonly FILE_UPLOAD = ExerciseType.FILE_UPLOAD;
    @HostBinding('class') classes = 'exercise-row';
    @Input() exercise: Exercise;
    @Input() lecture: Lecture;
    @Input() course: Course;
    @Input() extendedLink = false;

    getIcon = getIcon;
    getIconTooltip = getIconTooltip;
    public exerciseCategories: ExerciseCategory[];

    constructor(
        private accountService: AccountService,
        private jhiAlertService: JhiAlertService,
        private $window: WindowRef,
        private participationService: ParticipationService,
        private exerciseService: ExerciseService,
        private httpClient: HttpClient,
        private router: Router,
        private route: ActivatedRoute,
    ) {}

    ngOnInit() {}

    getUrgentClass(date: Moment): string {
        if (!date) {
            return;
        }
        const remainingDays = date.diff(moment(), 'days');
        if (0 <= remainingDays && remainingDays < 7) {
            return 'text-danger';
        } else {
            return;
        }
    }

    showDetails(event: any) {
        const lectureToAttach = {
            ...this.lecture,
            startDate: this.lecture.startDate.valueOf(),
            endDate: this.lecture.endDate.valueOf(),
        };
        if (this.extendedLink) {
            this.router.navigate(['overview', this.course.id, 'lectures', this.lecture.id], {
                state: {
                    lecture: lectureToAttach,
                },
            });
        } else {
            this.router.navigate([this.lecture.id], {
                relativeTo: this.route,
                state: {
                    lecture: lectureToAttach,
                },
            });
        }
    }
}
