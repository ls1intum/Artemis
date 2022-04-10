import { Component, OnInit, OnDestroy } from '@angular/core';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/plagiarism-cases.service';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { getIcon } from 'app/entities/exercise.model';
import { Subscription } from 'rxjs';

@Component({
    selector: 'jhi-plagiarism-case-student-detail-view',
    templateUrl: './plagiarism-case-student-detail-view.component.html',
})
export class PlagiarismCaseStudentDetailViewComponent implements OnInit, OnDestroy {
    courseId: number;
    plagiarismCaseId: number;
    plagiarismCase: PlagiarismCase;
    private paramSubscription: Subscription;
    getIcon = getIcon;

    constructor(private plagiarismCasesService: PlagiarismCasesService, private route: ActivatedRoute) {}

    ngOnInit(): void {
        this.paramSubscription = this.route.parent!.params.subscribe((params) => {
            this.courseId = parseInt(params['courseId'], 10);
        });
        this.plagiarismCaseId = Number(this.route.snapshot.paramMap.get('plagiarismCaseId'));
        this.plagiarismCasesService.getPlagiarismCaseDetailForStudent(this.courseId, this.plagiarismCaseId).subscribe({
            next: (res: HttpResponse<PlagiarismCase>) => {
                console.log(res);
                this.plagiarismCase = res.body!;
            },
        });
    }

    ngOnDestroy(): void {
        this.paramSubscription?.unsubscribe();
    }
}
