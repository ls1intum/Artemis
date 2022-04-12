import { HttpResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Params } from '@angular/router';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/shared/plagiarism-cases.service';
import { getIcon } from 'app/entities/exercise.model';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';
import { combineLatest, Subscription } from 'rxjs';

@Component({
    selector: 'jhi-plagiarism-cases-student-view',
    templateUrl: './plagiarism-cases-student-view.component.html',
})
export class PlagiarismCasesStudentViewComponent implements OnInit, OnDestroy {
    courseId: number;
    plagiarismCases: PlagiarismCase[] = [];
    private paramSubscription: Subscription;
    getIcon = getIcon;

    constructor(private plagiarismCasesService: PlagiarismCasesService, private activatedRoute: ActivatedRoute) {}

    ngOnInit(): void {
        this.paramSubscription = combineLatest({ params: this.activatedRoute.parent!.parent!.params }).subscribe((routeParams: { params: Params }) => {
            const { params } = routeParams;
            this.courseId = params.courseId;
            this.plagiarismCasesService.getPlagiarismCasesForStudent(this.courseId).subscribe({
                next: (res: HttpResponse<PlagiarismCase[]>) => {
                    this.plagiarismCases = res.body!;
                },
            });
        });
    }

    ngOnDestroy(): void {
        this.paramSubscription?.unsubscribe();
    }
}
