import { Component, Input, OnChanges } from '@angular/core';
import { Exam } from 'app/entities/exam.model';
import { HttpResponse } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { ExamChecklist } from 'app/entities/exam-checklist.model';
import { filter, map } from 'rxjs/operators';
import { faEye, faListAlt, faThList, faUser, faWrench } from '@fortawesome/free-solid-svg-icons';

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
    totalPointsMandatory = false;
    totalPointsMandatoryOptional = false;
    hasOptionalExercises = false;
    countMandatoryExercises = 0;

    // Icons
    faEye = faEye;
    faWrench = faWrench;
    faUser = faUser;
    faListAlt = faListAlt;
    faThList = faThList;

    constructor(private accountService: AccountService, private examService: ExamManagementService) {}

    ngOnChanges() {
        this.checkPointsExercisesEqual();
        this.checkTotalPointsMandatory();
        this.checkAllGroupContainsExercise();
        this.countMandatoryExercises = this.exam.exerciseGroups?.filter((group) => group.isMandatory)?.length ?? 0;
        this.hasOptionalExercises = this.countMandatoryExercises < (this.exam.exerciseGroups?.length ?? 0);
        this.examService
            .getExamStatistics(this.exam.course!.id!, this.exam.id!)
            .pipe(
                filter((res) => !!res.body),
                map((examStatistics: HttpResponse<ExamChecklist>) => examStatistics.body!),
            )
            .subscribe((examStats) => {
                this.examChecklist = examStats;
                this.checkAllExamsGenerated();
            });
    }

    /**
     * Set allExamsGenerated to true if all registered students have a student exam
     */
    checkAllExamsGenerated() {
        this.allExamsGenerated = this.examChecklist.numberOfGeneratedStudentExams === this.exam.numberOfRegisteredUsers;
    }

    /**
     * Set totalPointsMandatory to true if total points of exam is smaller or equal to all mandatory points
     * Set checkTotalPointsMandatoryOptional to true if total points of exam is bigger or equal to all mandatory points
     */
    checkTotalPointsMandatory() {
        this.totalPointsMandatory = false;
        this.totalPointsMandatoryOptional = false;
        let sumPointsExerciseGroupsMandatory = 0;
        let sumPointsExerciseGroupsOptional = 0;

        // calculate mandatory points and optional points
        if (this.pointsExercisesEqual) {
            this.exam.exerciseGroups!.forEach((exerciseGroup) => {
                if (exerciseGroup!.exercises && exerciseGroup.exercises.length !== 0) {
                    if (exerciseGroup.isMandatory) {
                        sumPointsExerciseGroupsMandatory += exerciseGroup!.exercises![0]!.maxPoints!;
                    } else {
                        sumPointsExerciseGroupsOptional += exerciseGroup!.exercises![0]!.maxPoints!;
                    }
                }
            });

            if (sumPointsExerciseGroupsMandatory <= this.exam.maxPoints!) {
                this.totalPointsMandatory = true;
            }
            if (sumPointsExerciseGroupsMandatory + sumPointsExerciseGroupsOptional >= this.exam.maxPoints!) {
                this.totalPointsMandatoryOptional = true;
            }
        }
    }

    /**
     * Set pointsExercisesEqual to true if exercises have the same number of maxPoints within each exercise group
     */
    checkPointsExercisesEqual() {
        this.pointsExercisesEqual = true;
        this.exam.exerciseGroups?.forEach((exerciseGroup) => {
            let maxPoints = 0;
            if (exerciseGroup.exercises && exerciseGroup.exercises!.length !== 0) {
                maxPoints = exerciseGroup.exercises?.[0].maxPoints!;
            }
            return exerciseGroup.exercises?.some((exercise) => {
                if (exercise.maxPoints !== maxPoints) {
                    this.pointsExercisesEqual = false;
                    return true;
                }
                return false;
            });
        });
    }

    /**
     * Set pointsExercisesEqual to true if exercises have the same number of maxPoints within each exercise groups
     */
    checkAllGroupContainsExercise() {
        this.allGroupsContainExercise = true;
        this.exam.exerciseGroups!.some((exerciseGroup) => {
            if (!exerciseGroup.exercises || exerciseGroup.exercises.length === 0) {
                this.allGroupsContainExercise = false;
                return true;
            }
            return false;
        });
    }
}
