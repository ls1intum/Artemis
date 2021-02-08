import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { SafeHtml } from '@angular/platform-browser';
import { Exam } from 'app/entities/exam.model';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { AccountService } from 'app/core/auth/account.service';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { HttpResponse } from '@angular/common/http';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { ExerciseGroup } from 'app/entities/exercise-group.model';

@Component({
    selector: 'jhi-exam-detail',
    templateUrl: './exam-detail.component.html',
})
export class ExamDetailComponent implements OnInit {
    exam: Exam;
    formattedStartText?: SafeHtml;
    formattedConfirmationStartText?: SafeHtml;
    formattedEndText?: SafeHtml;
    formattedConfirmationEndText?: SafeHtml;
    isAtLeastInstructor = false;
    isLoading = false;
    pointsExercisesEqual = false;
    allExamsGenerated = false;
    allGroupsContainExercise = false;
    totalPointsMandatory = false;
    totalPointsMandatoryOptional = false;

    constructor(
        private route: ActivatedRoute,
        private artemisMarkdown: ArtemisMarkdownService,
        private accountService: AccountService,
        private examService: ExamManagementService,
        private exerciseGroupService: ExerciseGroupService,
    ) {}

    /**
     * Initialize the exam
     */
    ngOnInit(): void {
        this.isLoading = true;
        this.route.data.subscribe(({ exam }) => {
            this.exam = exam;
            this.exerciseGroupService
                .findAllForExam(this.exam!.course!.id!, this.exam.id!)
                .map((exerciseGroupArray: HttpResponse<ExerciseGroup[]>) => exerciseGroupArray.body!)
                .subscribe((exGroups) => {
                    this.exam.exerciseGroups = exGroups;
                    this.checkPointsExercisesEqual();
                    this.checkAllGroupContainsExercise();
                    this.checkTotalPointsMandatory();
                });
            this.checkAllExamsGenerated();
            this.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(this.exam.course);
            this.formattedStartText = this.artemisMarkdown.safeHtmlForMarkdown(this.exam.startText);
            this.formattedConfirmationStartText = this.artemisMarkdown.safeHtmlForMarkdown(this.exam.confirmationStartText);
            this.formattedEndText = this.artemisMarkdown.safeHtmlForMarkdown(this.exam.endText);
            this.formattedConfirmationEndText = this.artemisMarkdown.safeHtmlForMarkdown(this.exam.confirmationEndText);
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
     * Set pointsExercisesEqual to true if exercises have the same number of maxPoints within each exercise groups
     */
    checkPointsExercisesEqual() {
        this.pointsExercisesEqual = true;
        this.exam.exerciseGroups!.forEach((exerciseGroup) => {
            const points = exerciseGroup.exercises?.[0].maxPoints;
            return exerciseGroup.exercises?.forEach((exercise) => {
                if (exercise.maxPoints !== points) {
                    this.pointsExercisesEqual = false;
                    return;
                }
            });
        });
    }

    /**
     * Set pointsExercisesEqual to true if exercises have the same number of maxPoints within each exercise groups
     */
    checkAllGroupContainsExercise() {
        this.allGroupsContainExercise = true;
        this.exam.exerciseGroups!.forEach((exerciseGroup) => {
            if (!exerciseGroup.exercises || exerciseGroup.exercises.length === 0) {
                this.allGroupsContainExercise = false;
                return;
            }
        });
    }

    /**
     * Returns the route for exam components by identifier
     */
    getExamRoutesByIdentifier(identifier: string) {
        return ['/course-management', this.exam.course?.id, 'exams', this.exam.id, identifier];
    }
}
