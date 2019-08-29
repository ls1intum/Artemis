import { Component, HostBinding, Input, OnInit } from '@angular/core';
import { Exercise, ParticipationStatus } from 'app/entities/exercise';
import { InitializationState, Participation, ProgrammingExerciseStudentParticipation } from 'app/entities/participation';
import { CourseExerciseService } from 'app/entities/course';
import { Router } from '@angular/router';
import { JhiAlertService } from 'ng-jhipster';
import { HttpClient } from '@angular/common/http';
import { AccountService } from 'app/core';
import { SourceTreeService } from 'app/components/util/sourceTree.service';
import { IntelliJState } from 'app/intellij/intellij';
import { JavaBridgeService } from 'app/intellij/java-bridge.service';

@Component({
    selector: 'jhi-programming-exercise-student-ide-actions',
    templateUrl: './programming-exercise-student-ide-actions.component.html',
    styleUrls: ['../../overview/course-overview.scss'],
    providers: [JhiAlertService, SourceTreeService],
})
export class ProgrammingExerciseStudentIdeActionsComponent implements OnInit {
    readonly UNINITIALIZED = ParticipationStatus.UNINITIALIZED;
    readonly INITIALIZED = ParticipationStatus.INITIALIZED;
    readonly INACTIVE = ParticipationStatus.INACTIVE;
    isOpenedInIntelliJ = false;

    @Input() @HostBinding('class.col') equalColumns = true;
    @Input() @HostBinding('class.col-auto') smallColumns = false;

    @Input() exercise: Exercise;
    @Input() courseId: number;

    @Input() smallButtons: boolean;

    constructor(
        private jhiAlertService: JhiAlertService,
        private courseExerciseService: CourseExerciseService,
        private httpClient: HttpClient,
        private accountService: AccountService,
        private sourceTreeService: SourceTreeService,
        private javaBridge: JavaBridgeService,
        private router: Router,
    ) {}

    ngOnInit(): void {
        this.javaBridge.state.subscribe((ideState: IntelliJState) => (this.isOpenedInIntelliJ = ideState.opened === this.exercise.id));
    }

    participationStatus(): ParticipationStatus {
        if (!this.hasParticipations(this.exercise)) {
            return ParticipationStatus.UNINITIALIZED;
        } else if (this.exercise.participations[0].initializationState === InitializationState.INITIALIZED) {
            return ParticipationStatus.INITIALIZED;
        }
        return ParticipationStatus.INACTIVE;
    }

    hasParticipations(exercise: Exercise): boolean {
        return exercise.participations && exercise.participations.length > 0;
    }

    hasResults(participation: Participation): boolean {
        return participation.results && participation.results.length > 0;
    }

    repositoryUrl(participation: Participation) {
        return (participation as ProgrammingExerciseStudentParticipation).repositoryUrl;
    }

    startExercise() {
        this.exercise.loading = true;

        this.courseExerciseService
            .startExercise(this.courseId, this.exercise.id)
            .finally(() => (this.exercise.loading = false))
            .subscribe(
                participation => {
                    if (participation) {
                        this.exercise.participations = [participation];
                        this.exercise.participationStatus = this.participationStatus();
                    }
                    this.jhiAlertService.success('artemisApp.exercise.personalRepository');
                },
                error => {
                    console.log('Error: ' + error);
                    this.jhiAlertService.warning('artemisApp.exercise.startError');
                },
            );
    }

    buildSourceTreeUrl(cloneUrl: string): string {
        return this.sourceTreeService.buildSourceTreeUrl(cloneUrl);
    }

    importIntoIntelliJ() {
        const title = this.exercise.title;
        const id = this.exercise.id;
        const repo = this.repositoryUrl(this.exercise.participations[0]);
        this.javaBridge.clone(repo, title, id, this.courseId);
    }

    submitChanges() {
        this.javaBridge.submit();
    }
}
