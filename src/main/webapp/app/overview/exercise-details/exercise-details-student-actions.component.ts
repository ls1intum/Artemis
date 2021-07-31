import { Component, ContentChild, HostBinding, Input, TemplateRef } from '@angular/core';
import * as moment from 'moment';
import { CourseExerciseService } from 'app/course/manage/course-management.service';
import { Router } from '@angular/router';
import { JhiAlertService } from 'ng-jhipster';
import { HttpClient } from '@angular/common/http';
import { SourceTreeService } from 'app/exercises/programming/shared/service/sourceTree.service';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { Participation } from 'app/entities/participation/participation.model';
import { Exercise, ExerciseType, ParticipationStatus } from 'app/entities/exercise.model';
import { isStartExerciseAvailable, participationStatus } from 'app/exercises/shared/exercise/exercise-utils';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { finalize } from 'rxjs/operators';

@Component({
    selector: 'jhi-exercise-details-student-actions',
    templateUrl: './exercise-details-student-actions.component.html',
    styleUrls: ['../course-overview.scss'],
    providers: [SourceTreeService],
})
export class ExerciseDetailsStudentActionsComponent {
    FeatureToggle = FeatureToggle;
    readonly ExerciseType = ExerciseType;
    readonly ParticipationStatus = ParticipationStatus;

    @Input() @HostBinding('class.col') equalColumns = true;
    @Input() @HostBinding('class.col-auto') smallColumns = false;

    @Input() exercise: Exercise;
    @Input() courseId: number;

    @Input() actionsOnly: boolean;
    @Input() smallButtons: boolean;
    @Input() showResult: boolean;

    @Input() examMode: boolean;

    // extension point, see shared/extension-point
    @ContentChild('overrideCloneOnlineEditorButton') overrideCloneOnlineEditorButton: TemplateRef<any>;

    constructor(private jhiAlertService: JhiAlertService, private courseExerciseService: CourseExerciseService, private httpClient: HttpClient, private router: Router) {}

    /**
     * check if practiceMode is available
     * @return {boolean}
     */
    isPracticeModeAvailable(): boolean {
        const quizExercise = this.exercise as QuizExercise;
        return quizExercise.isPlannedToStart! && quizExercise.isOpenForPractice! && moment(quizExercise.dueDate!).isBefore(moment());
    }

    /**
     * see exercise-utils -> isStartExerciseAvailable
     */
    isStartExerciseAvailable(): boolean {
        return isStartExerciseAvailable(this.exercise as ProgrammingExercise);
    }

    /**
     * check if onlineEditor is allowed
     * @return {boolean}
     */
    isOnlineEditorAllowed() {
        return (this.exercise as ProgrammingExercise).allowOnlineEditor;
    }

    /**
     * check if offline IDE is allowed
     * @return {boolean}
     */
    isOfflineIdeAllowed() {
        return (this.exercise as ProgrammingExercise).allowOfflineIde;
    }

    /**
     * start the exercise
     */
    startExercise() {
        if (this.exercise.type === ExerciseType.QUIZ) {
            // Start the quiz
            return this.router.navigate(['/courses', this.courseId, 'quiz-exercises', this.exercise.id, 'live']);
        }

        this.exercise.loading = true;
        this.courseExerciseService
            .startExercise(this.courseId, this.exercise.id!)
            .pipe(finalize(() => (this.exercise.loading = false)))
            .subscribe(
                (participation) => {
                    if (participation) {
                        this.exercise.studentParticipations = [participation];
                        this.exercise.participationStatus = participationStatus(this.exercise);
                    }
                    if (this.exercise.type === ExerciseType.PROGRAMMING) {
                        if ((this.exercise as ProgrammingExercise).allowOfflineIde) {
                            this.jhiAlertService.success('artemisApp.exercise.personalRepositoryClone');
                        } else {
                            this.jhiAlertService.success('artemisApp.exercise.personalRepositoryOnline');
                        }
                    }
                },
                () => {
                    this.jhiAlertService.warning('artemisApp.exercise.startError');
                },
            );
    }

    /**
     * resume the programming exercise
     */
    resumeProgrammingExercise() {
        this.exercise.loading = true;
        this.courseExerciseService
            .resumeProgrammingExercise(this.courseId, this.exercise.id!)
            .pipe(finalize(() => (this.exercise.loading = false)))
            .subscribe(
                (participation: StudentParticipation) => {
                    if (participation) {
                        // Otherwise the client would think that all results are loaded, but there would not be any (=> no graded result).
                        participation.results = this.exercise.studentParticipations![0] ? this.exercise.studentParticipations![0].results : [];
                        this.exercise.studentParticipations = [participation];
                        this.exercise.participationStatus = participationStatus(this.exercise);
                    }
                },
                (error) => {
                    this.jhiAlertService.error(`artemisApp.${error.error.entityName}.errors.${error.error.errorKey}`);
                },
            );
    }

    /**
     * Wrapper for using participationStatus() in the template
     *
     * @return {ParticipationStatus}
     */
    participationStatusWrapper(): ParticipationStatus {
        return participationStatus(this.exercise);
    }

    /**
     * Returns the id of the team that the student is assigned to (only applicable to team-based exercises)
     *
     * @return {assignedTeamId}
     */
    get assignedTeamId(): number | undefined {
        const participations = this.exercise.studentParticipations;
        return participations && participations.length > 0 ? participations[0].team?.id : this.exercise.studentAssignedTeamId;
    }

    repositoryUrl(participation: Participation) {
        const programmingParticipation = participation as ProgrammingExerciseStudentParticipation;
        return programmingParticipation.repositoryUrl;
    }
}
