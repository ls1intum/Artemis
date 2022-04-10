import { Component, OnInit } from '@angular/core';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/plagiarism-cases.service';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { getIcon } from 'app/entities/exercise.model';
import { PlagiarismVerdict } from 'app/exercises/shared/plagiarism/types/PlagiarismVerdict';
import { MetisService } from 'app/shared/metis/metis.service';
import { PageType } from 'app/shared/metis/metis.util';
import { Post } from 'app/entities/metis/post.model';

@Component({
    selector: 'jhi-plagiarism-case-instructor-detail-view',
    templateUrl: './plagiarism-case-instructor-detail-view.component.html',
})
export class PlagiarismCaseInstructorDetailViewComponent implements OnInit {
    courseId: number;
    plagiarismCaseId: number;
    plagiarismCase: PlagiarismCase;
    verdictPointDeduction = 0;
    verdictMessage = '';
    createdPost: Post;
    getIcon = getIcon;

    readonly pageType = PageType.OVERVIEW;

    constructor(protected metisService: MetisService, private plagiarismCasesService: PlagiarismCasesService, private route: ActivatedRoute) {}

    ngOnInit(): void {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.plagiarismCaseId = Number(this.route.snapshot.paramMap.get('plagiarismCaseId'));
        this.plagiarismCasesService.getPlagiarismCaseDetailForInstructor(this.courseId, this.plagiarismCaseId).subscribe({
            next: (res: HttpResponse<PlagiarismCase>) => {
                this.plagiarismCase = res.body!;
                this.verdictMessage = this.plagiarismCase.verdictMessage ?? '';
                this.verdictPointDeduction = this.plagiarismCase.verdictPointDeduction ?? 0;
                this.metisService.setCourse(this.plagiarismCase.exercise!.course!);
                this.metisService.setPageType(this.pageType);
                this.createEmptyPost();
            },
        });
    }

    /**
     *
     */
    savePointDeductionVerdict(): void {
        this.plagiarismCasesService
            .savePlagiarismCaseVerdict(this.courseId, this.plagiarismCaseId, {
                verdict: PlagiarismVerdict.POINT_DEDUCTION,
                verdictPointDeduction: this.verdictPointDeduction,
            })
            .subscribe({
                next: (res: HttpResponse<PlagiarismCase>) => {
                    this.plagiarismCase.verdict = res.body!.verdict;
                    this.verdictPointDeduction = res.body!.verdictPointDeduction!;
                },
            });
    }

    /**
     *
     */
    saveWarningVerdict(): void {
        this.plagiarismCasesService
            .savePlagiarismCaseVerdict(this.courseId, this.plagiarismCaseId, {
                verdict: PlagiarismVerdict.WARNING,
                verdictMessage: this.verdictMessage,
            })
            .subscribe({
                next: (res: HttpResponse<PlagiarismCase>) => {
                    this.plagiarismCase.verdict = res.body!.verdict;
                    this.verdictMessage = res.body!.verdictMessage!;
                },
            });
    }

    /**
     *
     */
    saveVerdict(): void {
        this.plagiarismCasesService.savePlagiarismCaseVerdict(this.courseId, this.plagiarismCaseId, { verdict: PlagiarismVerdict.PLAGIARISM }).subscribe({
            next: (res: HttpResponse<PlagiarismCase>) => {
                this.plagiarismCase.verdict = res.body!.verdict;
            },
        });
    }

    /**
     * invoke metis service to create an empty default post that is needed on initialization of a modal to create a post,
     * this empty post has a default course-wide context as well as the course set as context
     **/
    createEmptyPost(): void {
        this.createdPost = this.metisService.createEmptyPostForContext(undefined, undefined, undefined, this.plagiarismCase);
    }

    exportPlagiarismCase(): void {
        // TODO: export the plagiarism case as a PDF with all relevant information
    }
}
