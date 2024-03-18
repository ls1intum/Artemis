import { Component, Input, OnChanges, OnDestroy, OnInit } from '@angular/core';
import { Exam } from 'app/entities/exam.model';
import { ExamChecklist } from 'app/entities/exam-checklist.model';
import { faAngleDown, faAngleUp, faChartBar, faEye, faListAlt, faThList, faUser, faWrench } from '@fortawesome/free-solid-svg-icons';
import { ExamChecklistService } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { Submission } from 'app/entities/submission.model';

interface AssessmentDetails {
    title: string | undefined;
    student: string | undefined;
}

type SubmissionWithDetails = Submission & {
    details: AssessmentDetails;
};

@Component({
    selector: 'jhi-exam-checklist',
    templateUrl: './exam-checklist.component.html',
    styleUrls: ['./exam-checklist.component.scss'],
})
export class ExamChecklistComponent implements OnChanges, OnInit, OnDestroy {
    @Input() exam: Exam;
    @Input() getExamRoutesByIdentifier: any;

    examChecklist: ExamChecklist;
    isLoading = false;
    pointsExercisesEqual = false;
    allExamsGenerated = false;
    allGroupsContainExercise = false;
    totalPoints = false;
    hasOptionalExercises = false;
    countMandatoryExercises = 0;
    isTestExam: boolean;
    allAssessmentsFinished: boolean = true;
    isAssessmentsCollapsed = true;
    assessmentsWithDetails: SubmissionWithDetails[];

    numberOfSubmitted = 0;
    numberOfStarted = 0;

    examPreparationFinished: boolean;

    // Icons
    faEye = faEye;
    faWrench = faWrench;
    faUser = faUser;
    faListAlt = faListAlt;
    faThList = faThList;
    faChartBar = faChartBar;
    faAngleUp = faAngleUp;
    faAngleDown = faAngleDown;

    constructor(
        private examChecklistService: ExamChecklistService,
        private websocketService: JhiWebsocketService,
    ) {}

    ngOnInit() {
        const submittedTopic = this.examChecklistService.getSubmittedTopic(this.exam);
        this.websocketService.subscribe(submittedTopic);
        this.websocketService.receive(submittedTopic).subscribe(() => (this.numberOfSubmitted += 1));
        const startedTopic = this.examChecklistService.getStartedTopic(this.exam);
        this.websocketService.subscribe(startedTopic);
        this.websocketService.receive(startedTopic).subscribe(() => (this.numberOfStarted += 1));
    }

    ngOnChanges() {
        this.isTestExam = this.exam.testExam!;
        this.pointsExercisesEqual = this.examChecklistService.checkPointsExercisesEqual(this.exam);
        this.totalPoints = this.examChecklistService.checkTotalPointsMandatory(this.pointsExercisesEqual, this.exam);
        this.allGroupsContainExercise = this.examChecklistService.checkEachGroupContainsExercise(this.exam);
        this.countMandatoryExercises = this.exam.exerciseGroups?.filter((group) => group.isMandatory)?.length ?? 0;
        this.hasOptionalExercises = this.countMandatoryExercises < (this.exam.exerciseGroups?.length ?? 0);
        this.examChecklistService.getExamStatistics(this.exam).subscribe((examStats) => {
            this.examChecklist = examStats;
            this.allExamsGenerated =
                !!this.exam.numberOfExamUsers && this.exam.numberOfExamUsers > 0 && this.examChecklistService.checkAllExamsGenerated(this.exam, this.examChecklist);
            this.numberOfStarted = this.examChecklist.numberOfExamsStarted;
            this.numberOfSubmitted = this.examChecklist.numberOfExamsSubmitted;
            this.allAssessmentsFinished = !(this.examChecklist.unfinishedAssessments && this.examChecklist.unfinishedAssessments.length > 0);
            setTimeout(() => {
                if (!this.allAssessmentsFinished) {
                    this.assessmentsWithDetails = this.examChecklist.unfinishedAssessments.map((assessment) => ({
                        ...assessment,
                        details: this.getAssessmentDetails(assessment),
                    }));
                }
            }, 1000);
        });
    }

    ngOnDestroy(): void {
        const submittedTopic = this.examChecklistService.getSubmittedTopic(this.exam);
        this.websocketService.unsubscribe(submittedTopic);
        const startedTopic = this.examChecklistService.getStartedTopic(this.exam);
        this.websocketService.unsubscribe(startedTopic);
    }

    getAssessmentDetails(submission: Submission): {
        title: string | undefined;
        student: string | undefined;
    } {
        return {
            title: submission.participation?.exercise?.title,
            student: submission.participation?.participantName,
        };
    }
}
