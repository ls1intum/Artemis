import { Component, OnInit, OnDestroy } from '@angular/core';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/shared/plagiarism-cases.service';
import { ActivatedRoute, Params } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { ExerciseType, getIcon } from 'app/entities/exercise.model';
import { combineLatest, Subscription } from 'rxjs';
import { MetisService } from 'app/shared/metis/metis.service';
import { Post } from 'app/entities/metis/post.model';
import { PageType } from 'app/shared/metis/metis.util';
import { faUser } from '@fortawesome/free-solid-svg-icons';

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
    exerciseTitle: string | undefined;

    private paramSubscription: Subscription;

    getIcon = getIcon;
    faUser = faUser;

    readonly pageType = PageType.PLAGIARISM_CASE;
    private postsSubscription: Subscription;
    posts: Post[];

    constructor(protected metisService: MetisService, private plagiarismCasesService: PlagiarismCasesService, private activatedRoute: ActivatedRoute) {}

    ngOnInit(): void {
        this.paramSubscription = combineLatest({ params: this.activatedRoute.parent!.parent!.params }).subscribe((routeParams: { params: Params }) => {
            const { params } = routeParams;
            this.courseId = params.courseId;
            this.plagiarismCaseId = Number(this.activatedRoute.snapshot.paramMap.get('plagiarismCaseId'));
            this.plagiarismCasesService.getPlagiarismCaseDetailForStudent(this.courseId, this.plagiarismCaseId).subscribe({
                next: (res: HttpResponse<PlagiarismCase>) => {
                    this.plagiarismCase = res.body!;
                    this.exerciseTitle =
                        this.plagiarismCase.exercise!.title!.length > 40 ? this.plagiarismCase.exercise?.title?.slice(0, 35) + '…' : this.plagiarismCase.exercise?.title;

                    this.metisService.setCourse(this.plagiarismCase.exercise!.course!);
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

    /**
     * Get the url segment for different types of exercises.
     * @param exerciseType type of exercise
     */
    getExerciseUrlSegment(exerciseType?: ExerciseType) {
        switch (exerciseType) {
            case ExerciseType.TEXT:
                return 'text-exercises';
            case ExerciseType.MODELING:
                return 'modeling-exercises';
            case ExerciseType.PROGRAMMING:
                return 'programming-exercises';
            default:
                throw Error('Unexpected exercise type ' + exerciseType);
        }
    }

    ngOnDestroy(): void {
        this.paramSubscription?.unsubscribe();
        this.postsSubscription?.unsubscribe();
    }
}
