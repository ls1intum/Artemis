import { Component, HostBinding, Input, OnChanges, OnDestroy, OnInit } from '@angular/core';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import dayjs from 'dayjs/esm';
import { Subscription } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { HttpClient } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { Exercise, ExerciseType, getIcon, getIconTooltip, IncludedInOverallScore } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { getExerciseDueDate, participationStatus } from 'app/exercises/shared/exercise/exercise.utils';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { setBuildPlanUrlForProgrammingParticipations } from 'app/exercises/shared/participation/participation.utils';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { HttpResponse } from '@angular/common/http';

@Component({
    selector: 'jhi-course-exercise-row',
    templateUrl: './course-exercise-row.component.html',
    styleUrls: ['./course-exercise-row.scss'],
})
export class CourseExerciseRowComponent implements OnInit, OnDestroy, OnChanges {
    readonly QUIZ = ExerciseType.QUIZ;
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly MODELING = ExerciseType.MODELING;
    readonly TEXT = ExerciseType.TEXT;
    readonly FILE_UPLOAD = ExerciseType.FILE_UPLOAD;
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

    participationUpdateListener: Subscription;

    constructor(
        private accountService: AccountService,
        private participationService: ParticipationService,
        private exerciseService: ExerciseService,
        private httpClient: HttpClient,
        private participationWebsocketService: ParticipationWebsocketService,
        private profileService: ProfileService,
    ) {}

    ngOnInit() {
        if (this.exercise.id) {
            this.exerciseService.getExerciseDetails(this.exercise.id).subscribe((exerciseResponse: HttpResponse<Exercise>) => {
                if (exerciseResponse.body) {
                    this.exercise = exerciseResponse.body!;
                    this.exercise.participationStatus = participationStatus(this.exercise);
                    if (this.exercise.studentParticipations?.length) {
                        this.gradedStudentParticipation = this.participationService.getSpecificStudentParticipation(this.exercise.studentParticipations, false);
                    }
                }
            });
        }

        this.participationUpdateListener = this.participationWebsocketService.subscribeForParticipationChanges().subscribe((changedParticipation: StudentParticipation) => {
            if (changedParticipation && this.exercise && changedParticipation.exercise?.id === this.exercise.id) {
                this.exercise.studentParticipations = this.exercise.studentParticipations?.length
                    ? this.exercise.studentParticipations.map((el) => {
                          return el.id === changedParticipation.id ? changedParticipation : el;
                      })
                    : [changedParticipation];
                this.exercise.participationStatus = participationStatus(this.exercise);
                this.gradedStudentParticipation = this.participationService.getSpecificStudentParticipation(this.exercise.studentParticipations, false);
                this.dueDate = getExerciseDueDate(this.exercise, this.gradedStudentParticipation);
            }
        });
        if (this.exercise.type === ExerciseType.PROGRAMMING) {
            this.profileService.getProfileInfo().subscribe((profileInfo) => {
                const programmingParticipations = this.exercise.studentParticipations as ProgrammingExerciseStudentParticipation[];
                setBuildPlanUrlForProgrammingParticipations(profileInfo, programmingParticipations, (this.exercise as ProgrammingExercise).projectKey);
            });
        }
    }

    ngOnChanges(): void {
        const cachedParticipations = this.participationWebsocketService.getParticipationsForExercise(this.exercise.id!);
        if (cachedParticipations) {
            this.exercise.studentParticipations = cachedParticipations;
            this.gradedStudentParticipation = this.participationService.getSpecificStudentParticipation(this.exercise.studentParticipations, false);
        }
        this.dueDate = getExerciseDueDate(this.exercise, this.gradedStudentParticipation);
        this.exercise.participationStatus = participationStatus(this.exercise, false);
        this.exercise.isAtLeastTutor = this.accountService.isAtLeastTutorInCourse(this.course || this.exercise.exerciseGroup!.exam!.course);
        this.exercise.isAtLeastEditor = this.accountService.isAtLeastEditorInCourse(this.course || this.exercise.exerciseGroup!.exam!.course);
        this.exercise.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(this.course || this.exercise.exerciseGroup!.exam!.course);
        this.isAfterAssessmentDueDate = !this.exercise.assessmentDueDate || dayjs().isAfter(this.exercise.assessmentDueDate);
        if (this.exercise.type === ExerciseType.QUIZ) {
            const quizExercise = this.exercise as QuizExercise;
            quizExercise.isActiveQuiz = this.exerciseService.isActiveQuiz(this.exercise);
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
