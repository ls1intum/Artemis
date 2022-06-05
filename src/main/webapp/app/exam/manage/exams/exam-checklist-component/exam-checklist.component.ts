import { Component, Input, OnChanges } from '@angular/core';
import { Exam } from 'app/entities/exam.model';
import { ExamChecklist } from 'app/entities/exam-checklist.model';
import { faEye, faListAlt, faThList, faUser, faWrench } from '@fortawesome/free-solid-svg-icons';
import { ExamChecklistService } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist.service';

@Component({
    selector: 'jhi-exam-checklist',
    templateUrl: './exam-checklist.component.html',
})
export class ExamChecklistComponent implements OnChanges {
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

    examPreparationFinished: boolean;

    // Icons
    faEye = faEye;
    faWrench = faWrench;
    faUser = faUser;
    faListAlt = faListAlt;
    faThList = faThList;

    constructor(private examChecklistService: ExamChecklistService) {}

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
                !!this.exam.numberOfRegisteredUsers && this.exam.numberOfRegisteredUsers > 0 && this.examChecklistService.checkAllExamsGenerated(this.exam, this.examChecklist);
        });
    }
}
