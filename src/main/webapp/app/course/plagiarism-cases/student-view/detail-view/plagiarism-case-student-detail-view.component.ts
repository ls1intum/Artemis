import { Component, OnInit, OnDestroy } from '@angular/core';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/shared/plagiarism-cases.service';
import { ActivatedRoute, Params } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { getCourseFromExercise, getIcon } from 'app/entities/exercise.model';
import { combineLatest, Subscription } from 'rxjs';
import { MetisService } from 'app/shared/metis/metis.service';
import { Post } from 'app/entities/metis/post.model';
import { PageType } from 'app/shared/metis/metis.util';
import { faUser } from '@fortawesome/free-solid-svg-icons';
import { PlagiarismVerdict } from 'app/exercises/shared/plagiarism/types/PlagiarismVerdict';

@Component({
    selector: 'jhi-plagiarism-case-student-detail-view',
    templateUrl: './plagiarism-case-student-detail-view.component.html',
    styleUrls: ['./plagiarism-case-student-detail-view.component.scss'],
    providers: [MetisService],
})
export class PlagiarismCaseStudentDetailViewComponent implements OnInit, OnDestroy {
    courseId: number;
    plagiarismCaseId: number;
    plagiarismCase: PlagiarismCase;

    private paramSubscription: Subscription;
    readonly plagiarismVerdict = PlagiarismVerdict;

    getIcon = getIcon;
    faUser = faUser;

    readonly pageType = PageType.PLAGIARISM_CASE;
    private postsSubscription: Subscription;
    posts: Post[];

    affectedExerciseRouterLink: (string | number)[];

    constructor(protected metisService: MetisService, private plagiarismCasesService: PlagiarismCasesService, private activatedRoute: ActivatedRoute) {}

    ngOnInit(): void {
        this.paramSubscription = combineLatest({
            ancestorParams: this.activatedRoute.parent!.parent!.params,
            params: this.activatedRoute.params,
        }).subscribe(({ ancestorParams, params }: { ancestorParams: Params; params: Params }) => {
            this.courseId = ancestorParams.courseId;
            this.plagiarismCaseId = Number(params.plagiarismCaseId);
            if (this.plagiarismCase?.id === this.plagiarismCaseId) {
                return;
            }
            this.plagiarismCasesService.getPlagiarismCaseDetailForStudent(this.courseId, this.plagiarismCaseId).subscribe({
                next: (res: HttpResponse<PlagiarismCase>) => {
                    this.plagiarismCase = res.body!;

                    const examId = this.plagiarismCase?.exercise?.exerciseGroup?.exam?.id;
                    if (examId) {
                        // Navigate to the exam result since individual exam exercises are not addressable.
                        this.affectedExerciseRouterLink = ['/courses', this.courseId, 'exams', examId];
                    } else {
                        this.affectedExerciseRouterLink = ['/courses', this.courseId, 'exercises', this.plagiarismCase.exercise!.id!];
                    }

                    this.metisService.setCourse(getCourseFromExercise(this.plagiarismCase.exercise!)!);

                    this.metisService.setPageType(this.pageType);
                    this.metisService.getFilteredPosts({
                        plagiarismCaseId: this.plagiarismCase!.id,
                    });
                },
            });
        });
        this.postsSubscription = this.metisService.posts.pipe().subscribe((posts: Post[]) => {
            this.posts = posts;
        });
    }

    ngOnDestroy(): void {
        this.paramSubscription?.unsubscribe();
        this.postsSubscription?.unsubscribe();
    }
}
