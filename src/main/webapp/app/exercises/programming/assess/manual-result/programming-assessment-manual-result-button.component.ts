import {Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges} from '@angular/core';
import {NgbModal} from '@ng-bootstrap/ng-bootstrap';
import {Result} from 'app/entities/result.model';
import {Subscription} from 'rxjs';
import {ParticipationWebsocketService} from 'app/overview/participation-websocket.service';
import {filter} from 'rxjs/operators';
import {User} from 'app/core/user/user.model';
import {ButtonSize, ButtonType} from 'app/shared/components/button.component';
import {AssessmentType} from 'app/entities/assessment-type.model';
import {ProgrammingExercise} from 'app/entities/programming-exercise.model';
import {Router} from '@angular/router';

@Component({
    selector: 'jhi-programming-assessment-manual-result',
    template: `
        <jhi-button
            [disabled]="!participationId"
            [btnType]="ButtonType.WARNING"
            [btnSize]="ButtonSize.SMALL"
            [icon]="'asterisk'"
            [title]="latestResult ? (latestResult.hasComplaint ? 'entity.action.viewResult' : 'entity.action.updateResult') : 'entity.action.newResult'"
            (onClick)="openCodeEditorWithStudentSubmission()"
        ></jhi-button>
    `,
})
export class ProgrammingAssessmentManualResultButtonComponent implements OnChanges, OnDestroy {
    ButtonType = ButtonType;
    ButtonSize = ButtonSize;
    @Input() participationId: number;
    @Output() onResultModified = new EventEmitter<Result>();
    @Input() latestResult?: Result | null;
    @Input() exercise: ProgrammingExercise;
    @Input() isTestRun: boolean;

    latestResultSubscription: Subscription;

    constructor(private modalService: NgbModal, private participationWebsocketService: ParticipationWebsocketService, private router: Router) {}

    /**
     * - Check that the inserted result is of type MANUAL, otherwise set it to null
     * - If the participationId changes, subscribe to the latest result from the websocket
     *
     * @param changes
     */
    ngOnChanges(changes: SimpleChanges): void {
        // Manual result can either be from type MANUAL or SEMI_AUTOMATIC
        if (
            changes.latestResult &&
            this.latestResult &&
            this.latestResult.assessmentType !== AssessmentType.MANUAL &&
            this.latestResult.assessmentType !== AssessmentType.SEMI_AUTOMATIC
        ) {
            // The assessor can't update the automatic result of the student.
            this.latestResult = null;
        }
        if (changes.participationId && this.participationId) {
            if (this.latestResultSubscription) {
                this.latestResultSubscription.unsubscribe();
            }
            // Manual result can either be from type MANUAL or SEMI_AUTOMATIC
            this.latestResultSubscription = this.participationWebsocketService
                .subscribeForLatestResultOfParticipation(this.participationId, false, this.exercise.id)
                .pipe(filter((result: Result) => result && (result.assessmentType === AssessmentType.MANUAL || result.assessmentType === AssessmentType.SEMI_AUTOMATIC)))
                .subscribe((manualResult) => {
                    let assessor: User | undefined;
                    // TODO: workaround to fix an issue when the assessor gets lost due to the websocket update
                    // we should properly fix this in the future and make sure the assessor is not cut off in the first place
                    if (this.latestResult && this.latestResult.assessor && this.latestResult.id === manualResult.id) {
                        assessor = this.latestResult.assessor;
                    }
                    this.latestResult = manualResult;
                    if (assessor && !this.latestResult.assessor) {
                        this.latestResult.assessor = assessor;
                    }
                });
        }
    }

    /**
     * Unsubscribes this instance, if it is the latest result submission
     */
    ngOnDestroy(): void {
        if (this.latestResultSubscription) {
            this.latestResultSubscription.unsubscribe();
        }
    }

    async openCodeEditorWithStudentSubmission() {
        const courseId = this.exercise.exerciseGroup?.exam?.course?.id || this.exercise.course?.id;
        const route = `/course-management/${courseId}/${this.exercise.type}-exercises/${this.exercise.id}/code-editor/${this.participationId}/assessment`;
        await this.router.navigate([route], { queryParams: { testRun: this.isTestRun } });
    }
}
