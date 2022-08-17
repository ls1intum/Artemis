import { HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { ExamChecklist } from 'app/entities/exam-checklist.model';
import { Exam } from 'app/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { Observable } from 'rxjs';
import { filter, map } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class ExamChecklistService {
    constructor(private examService: ExamManagementService) {}

    /**
     * indicates whether all student exams are generated
     * @param exam the corresponding exam
     * @param examChecklist the examChecklist for the exam
     */
    checkAllExamsGenerated(exam: Exam, examChecklist: ExamChecklist): boolean {
        return examChecklist.numberOfGeneratedStudentExams === exam.numberOfRegisteredUsers;
    }

    /**
     * Fetches examChecklist from the Server
     * @param exam exam the checklist should be fetched
     * @returns examChecklist as Observable
     */
    getExamStatistics(exam: Exam): Observable<ExamChecklist> {
        return this.examService.getExamStatistics(exam.course!.id!, exam.id!).pipe(
            filter((res) => !!res.body),
            map((examStatistics: HttpResponse<ExamChecklist>) => examStatistics.body!),
        );
    }

    /**
     * indicates whether the max points of the exam can be reached by the current configuration
     * @param pointsExercisesEqual flag indicating whether within every exercise groups the max points equal
     * @param exam the corresponding exam
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
     * indicates whether the max points within every exercise group equal
     * @param exam the corresponding exam
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

    /**
     * Method to calculate the number of Points in an exam
     * @param pointsExercisesEqual flag indicating whether within every exercise groups the max points equal
     * @param exam the corresponding exam
     */
    calculateExercisePoints(pointsExercisesEqual: boolean, exam: Exam): number {
        let sumPointsExerciseGroups = 0;
        if (pointsExercisesEqual) {
            exam.exerciseGroups!.forEach((exerciseGroup) => {
                if (exerciseGroup!.exercises && exerciseGroup.exercises.length !== 0) {
                    sumPointsExerciseGroups += exerciseGroup!.exercises![0]!.maxPoints!;
                }
            });
        }
        return sumPointsExerciseGroups;
    }

    /**
     * indicates whether every exercise group of an exam contains at least one exercise
     * @param exam the corresponding exam
     */
    checkEachGroupContainsExercise(exam: Exam): boolean {
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

    /**
     * indicates whether an exam has at least one exercise group
     * @param exam the corresponding exam
     */
    checkAtLeastOneExerciseGroup(exam: Exam): boolean {
        return (exam.exerciseGroups && exam.exerciseGroups.length > 0) ?? false;
    }

    /**
     * indicates whether the number of exam exercises is in range between the number of mandatory exercise groups and the number of all exercise groups
     * @param exam the corresponding exam
     */
    checkNumberOfExerciseGroups(exam: Exam): boolean {
        const numberOfMandatoryExerciseGroups = exam.exerciseGroups?.filter((group) => group.isMandatory).length ?? 0;
        return (
            (exam.exerciseGroups && numberOfMandatoryExerciseGroups <= (exam.numberOfExercisesInExam ?? 0) && (exam.numberOfExercisesInExam ?? 0) <= exam.exerciseGroups.length) ??
            false
        );
    }

    /**
     * indicates whether at least one student is registered for an exam
     * @param exam the corresponding exam
     */
    checkAtLeastOneRegisteredStudent(exam: Exam): boolean {
        return !!exam.numberOfRegisteredUsers && exam.numberOfRegisteredUsers > 0;
    }

    getSubmittedTopic(exam: Exam) {
        return `/topic/exam/${exam.id}/submitted`;
    }

    getStartedTopic(exam: Exam) {
        return `/topic/exam/${exam.id}/started`;
    }
}
