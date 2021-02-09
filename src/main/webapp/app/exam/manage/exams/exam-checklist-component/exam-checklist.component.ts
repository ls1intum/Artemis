import { Component, OnInit, Input } from '@angular/core';
import { Exam } from 'app/entities/exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { HttpResponse } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';

@Component({
    selector: 'jhi-exam-checklist',
    templateUrl: './exam-checklist.component.html',
})
export class ExamChecklistComponent implements OnInit {
    @Input() exam: Exam;
    @Input() getExamRoutesByIdentifier: any;
    @Input() isAtLeastInstructor = false;

    isLoading = false;
    pointsExercisesEqual = false;
    allExamsGenerated = false;
    allGroupsContainExercise = false;
    totalPointsMandatory = false;
    totalPointsMandatoryOptional = false;

    constructor(private accountService: AccountService, private examService: ExamManagementService, private exerciseGroupService: ExerciseGroupService) {}

    ngOnInit() {
        this.exerciseGroupService
            .findAllForExam(this.exam!.course!.id!, this.exam.id!)
            .map((exerciseGroupArray: HttpResponse<ExerciseGroup[]>) => exerciseGroupArray.body!)
            .subscribe((exGroups) => {
                this.exam.exerciseGroups = exGroups;
                this.checkPointsExercisesEqual();
                this.checkAllGroupContainsExercise();
                this.checkTotalPointsMandatory();
                this.checkAllExamsGenerated();
            });
    }

    /**
     * Set allExamsGenerated to true if all registered students have a student exam
     */
    checkAllExamsGenerated() {
        this.allExamsGenerated = this.exam.numberOfGeneratedStudentExams === this.exam.numberOfRegisteredUsers;
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
                if (exerciseGroup.isMandatory) {
                    sumPointsExerciseGroupsMandatory += exerciseGroup!.exercises![0]!.maxPoints!;
                } else {
                    sumPointsExerciseGroupsOptional += exerciseGroup!.exercises![0]!.maxPoints!;
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
        this.exam.exerciseGroups!.forEach((exerciseGroup) => {
            const maxPoints = exerciseGroup.exercises?.[0].maxPoints;
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
