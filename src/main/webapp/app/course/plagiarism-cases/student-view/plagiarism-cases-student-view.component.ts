import { HttpResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/plagiarism-cases.service';
import { getIcon } from 'app/entities/exercise.model';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';
import { Subscription } from 'rxjs';

@Component({
    selector: 'jhi-plagiarism-cases-student-view',
    templateUrl: './plagiarism-cases-student-view.component.html',
})
export class PlagiarismCasesStudentViewComponent implements OnInit, OnDestroy {
    courseId: number;
    plagiarismCases: PlagiarismCase[] = [];
    private paramSubscription: Subscription;
    getIcon = getIcon;

    constructor(private plagiarismCasesService: PlagiarismCasesService, private route: ActivatedRoute) {}

    ngOnInit(): void {
        this.paramSubscription = this.route.parent!.params.subscribe((params) => {
            this.courseId = parseInt(params['courseId'], 10);
        });
        this.plagiarismCasesService.getPlagiarismCasesForStudent(this.courseId).subscribe({
            next: (res: HttpResponse<PlagiarismCase[]>) => {
                this.plagiarismCases = res.body!;
            },
        });
    }

    ngOnDestroy(): void {
        this.paramSubscription?.unsubscribe();
    }
}
