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
                .subscribe((x) => {
                    this.exam.exerciseGroups = x;
                    this.checkPointsExercisesEqual();
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
     * Returns the route for exam components by identifier
     */
    getExamRoutesByIdentifier(identifier: string) {
        return ['/course-management', this.exam.course?.id, 'exams', this.exam.id, identifier];
    }
}
