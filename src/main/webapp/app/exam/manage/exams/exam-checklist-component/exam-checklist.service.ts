import { HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { ExamChecklist } from 'app/entities/exam-checklist.model';
import { Exam } from 'app/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { filter, map } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class ExamChecklistService {
    constructor(private examService: ExamManagementService) {}
    /**
     * Set allExamsGenerated to true if all registered students have a student exam
     */
    checkAllExamsGenerated(exam: Exam, examChecklist: ExamChecklist): boolean {
        return examChecklist.numberOfGeneratedStudentExams === exam.numberOfRegisteredUsers;
    }

    getExamStatistics(exam: Exam) {
        return this.examService.getExamStatistics(exam.course!.id!, exam.id!).pipe(
            filter((res) => !!res.body),
            map((examStatistics: HttpResponse<ExamChecklist>) => examStatistics.body!),
        );
    }

    /**
     * Set totalPointsMandatory to true if total points of exam is smaller or equal to all mandatory points
     * Set checkTotalPointsMandatoryOptional to true if total points of exam is bigger or equal to all mandatory points
     */
    checkTotalPointsMandatory(pointsExercisesEqual: boolean, exam: Exam): boolean {
        let totalPointsMandatory = false;
        let totalPointsMandatoryOptional = false;
        let sumPointsExerciseGroupsMandatory = 0;
        let sumPointsExerciseGroupsOptional = 0;

        // calculate mandatory points and optional points
        if (pointsExercisesEqual) {
            exam.exerciseGroups!.forEach((exerciseGroup) => {
                if (exerciseGroup!.exercises && exerciseGroup.exercises.length !== 0) {
                    if (exerciseGroup.isMandatory) {
                        sumPointsExerciseGroupsMandatory += exerciseGroup!.exercises![0]!.maxPoints!;
                    } else {
                        sumPointsExerciseGroupsOptional += exerciseGroup!.exercises![0]!.maxPoints!;
                    }
                }
            });

            if (sumPointsExerciseGroupsMandatory <= exam.maxPoints!) {
                totalPointsMandatory = true;
            }
            if (sumPointsExerciseGroupsMandatory + sumPointsExerciseGroupsOptional >= exam.maxPoints!) {
                totalPointsMandatoryOptional = true;
            }
        }
        return totalPointsMandatory && totalPointsMandatoryOptional;
    }

    /**
     * Set pointsExercisesEqual to true if exercises have the same number of maxPoints within each exercise group
     */
    checkPointsExercisesEqual(exam: Exam): boolean {
        let pointsExercisesEqual = true;
        if (!exam.exerciseGroups) {
            return false;
        }
        exam.exerciseGroups?.forEach((exerciseGroup) => {
            let maxPoints = 0;
            if (exerciseGroup.exercises && exerciseGroup.exercises!.length !== 0) {
                maxPoints = exerciseGroup.exercises?.[0].maxPoints!;
            }
            return exerciseGroup.exercises?.some((exercise) => {
                if (exercise.maxPoints !== maxPoints) {
                    pointsExercisesEqual = false;
                    return true;
                }
                return false;
            });
        });
        return pointsExercisesEqual;
    }

    checkAllGroupContainsExercise(exam: Exam): boolean {
        if (!exam.exerciseGroups) {
            return false;
        }
        let allGroupsContainExercise = true;
        exam.exerciseGroups!.some((exerciseGroup) => {
            if (!exerciseGroup.exercises || exerciseGroup.exercises.length === 0) {
                allGroupsContainExercise = false;
                return true;
            }
            return false;
        });
        return allGroupsContainExercise;
    }

    checkAtLeastOneExerciseGroup(exam: Exam): boolean {
        return (exam.exerciseGroups && exam.exerciseGroups.length > 0) ?? false;
    }

    checkNumberOfExerciseGroups(exam: Exam): boolean {
        const numberOfMandatoryExerciseGroups = exam.exerciseGroups?.filter((group) => group.isMandatory).length ?? 0;
        return (
            (exam.exerciseGroups && numberOfMandatoryExerciseGroups <= (exam.numberOfExercisesInExam ?? 0) && (exam.numberOfExercisesInExam ?? 0) <= exam.exerciseGroups.length) ??
            false
        );
    }

    checkAtLeastOneRegisteredStudent(exam: Exam): boolean {
        return !!exam.numberOfRegisteredUsers && exam.numberOfRegisteredUsers > 0;
    }
}
