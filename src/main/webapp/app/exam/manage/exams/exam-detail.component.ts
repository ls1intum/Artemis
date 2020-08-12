import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { SafeHtml } from '@angular/platform-browser';
import { Exam } from 'app/entities/exam.model';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';

@Component({
    selector: 'jhi-exam-detail',
    templateUrl: './exam-detail.component.html',
})
export class ExamDetailComponent implements OnInit {
    exam: Exam;
    formattedStartText: SafeHtml | null;
    formattedConfirmationStartText: SafeHtml | null;
    formattedEndText: SafeHtml | null;
    formattedConfirmationEndText: SafeHtml | null;

    constructor(private route: ActivatedRoute, private artemisMarkdown: ArtemisMarkdownService) {}

    /**
     * Initialize the exam
     */
    ngOnInit(): void {
        this.route.data.subscribe(({ exam }) => {
            this.exam = exam;
            this.formattedStartText = this.artemisMarkdown.safeHtmlForMarkdown(this.exam.startText);
            this.formattedConfirmationStartText = this.artemisMarkdown.safeHtmlForMarkdown(this.exam.confirmationStartText);
            this.formattedEndText = this.artemisMarkdown.safeHtmlForMarkdown(this.exam.endText);
            this.formattedConfirmationEndText = this.artemisMarkdown.safeHtmlForMarkdown(this.exam.confirmationEndText);
        });
    }

    /**
     * Go back.
     */
    previousState() {
        window.history.back();
    }

    /**
     * Returns the route for editing the exam.
     */
    getEditRoute() {
        return ['/course-management', this.exam.course.id, 'exams', this.exam.id, 'edit'];
    }
}
