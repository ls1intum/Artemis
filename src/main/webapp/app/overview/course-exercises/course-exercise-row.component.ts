import { Component, HostBinding, Input, OnChanges, OnDestroy, OnInit, inject } from '@angular/core';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import dayjs from 'dayjs/esm';
import { Subscription } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { AccountService } from 'app/core/auth/account.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { Exercise, ExerciseType, IncludedInOverallScore, getIcon, getIconTooltip } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { getExerciseDueDate } from 'app/exercises/shared/exercise/exercise.utils';
import { ExerciseCategory } from 'app/entities/exercise-category.model';

@Component({
    selector: 'jhi-course-exercise-row',
    templateUrl: './course-exercise-row.component.html',
    styleUrls: ['./course-exercise-row.scss'],
})
export class CourseExerciseRowComponent implements OnInit, OnDestroy, OnChanges {
    private accountService = inject(AccountService);
    private participationService = inject(ParticipationService);
    private exerciseService = inject(ExerciseService);
    private participationWebsocketService = inject(ParticipationWebsocketService);

    readonly IncludedInOverallScore = IncludedInOverallScore;
    readonly dayjs = dayjs;
    @HostBinding('class') classes = 'exercise-row';
    @Input() exercise: Exercise;
    @Input() course: Course;
    @Input() hasGuidedTour: boolean;
    /**
     * PresentationMode deactivates the interactivity of the component
     */
    @Input() isPresentationMode = false;

    getIcon = getIcon;
    getIconTooltip = getIconTooltip;
    public exerciseCategories: ExerciseCategory[];
    isAfterAssessmentDueDate: boolean;
    dueDate?: dayjs.Dayjs;
    gradedStudentParticipation?: StudentParticipation;

    routerLink: string[];

    participationUpdateListener: Subscription;

    ngOnInit() {
        if (this.exercise?.studentParticipations?.length) {
            this.gradedStudentParticipation = this.participationService.getSpecificStudentParticipation(this.exercise.studentParticipations, false);
        }

        this.participationUpdateListener = this.participationWebsocketService.subscribeForParticipationChanges().subscribe((changedParticipation: StudentParticipation) => {
            if (changedParticipation && this.exercise && changedParticipation.exercise?.id === this.exercise.id) {
                this.exercise.studentParticipations = this.exercise.studentParticipations?.length
                    ? this.exercise.studentParticipations.map((el) => {
                          return el.id === changedParticipation.id ? changedParticipation : el;
                      })
                    : [changedParticipation];
                this.gradedStudentParticipation = this.participationService.getSpecificStudentParticipation(this.exercise.studentParticipations, false);
                this.dueDate = getExerciseDueDate(this.exercise, this.gradedStudentParticipation);
            }
        });

        this.routerLink = ['/courses', this.course.id!.toString(), 'exercises', this.exercise.id!.toString()];
    }

    ngOnChanges(): void {
        const cachedParticipations = this.participationWebsocketService.getParticipationsForExercise(this.exercise.id!);
        if (cachedParticipations?.length) {
            this.exercise.studentParticipations = cachedParticipations;
            this.gradedStudentParticipation = this.participationService.getSpecificStudentParticipation(this.exercise.studentParticipations, false);
        }
        this.dueDate = getExerciseDueDate(this.exercise, this.gradedStudentParticipation);
        this.exercise.isAtLeastTutor = this.accountService.isAtLeastTutorInCourse(this.course || this.exercise.exerciseGroup!.exam!.course);
        this.exercise.isAtLeastEditor = this.accountService.isAtLeastEditorInCourse(this.course || this.exercise.exerciseGroup!.exam!.course);
        this.exercise.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(this.course || this.exercise.exerciseGroup!.exam!.course);
        this.isAfterAssessmentDueDate = !this.exercise.assessmentDueDate || dayjs().isAfter(this.exercise.assessmentDueDate);
        if (this.exercise.type === ExerciseType.QUIZ) {
            const quizExercise = this.exercise as QuizExercise;
            quizExercise.isActiveQuiz = this.exerciseService.isActiveQuiz(quizExercise);
            quizExercise.isPracticeModeAvailable = quizExercise.isOpenForPractice && quizExercise.quizEnded;
            this.exercise = quizExercise;
        }
        this.exerciseCategories = this.exercise.categories || [];
        this.exercise.course = this.course;
    }

    ngOnDestroy() {
        if (this.participationUpdateListener) {
            this.participationUpdateListener.unsubscribe();
        }
    }

    getUrgentClass(date?: dayjs.Dayjs) {
        if (!date) {
            return undefined;
        }
        const remainingDays = date.diff(dayjs(), 'days');
        if (0 <= remainingDays && remainingDays < 7) {
            return 'text-danger';
        }
    }

    asQuizExercise(exercise: Exercise): QuizExercise {
        return exercise as QuizExercise;
    }
}
