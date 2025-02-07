import { Component, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { PlagiarismCaseReviewComponent } from 'app/course/plagiarism-cases/shared/review/plagiarism-case-review.component';
import { PlagiarismCaseVerdictComponent } from 'app/course/plagiarism-cases/shared/verdict/plagiarism-case-verdict.component';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/shared/plagiarism-cases.service';
import { ActivatedRoute, Params, RouterLink } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { getCourseFromExercise, getIcon } from 'app/entities/exercise.model';
import { Subscription, combineLatest } from 'rxjs';
import { MetisService } from 'app/shared/metis/metis.service';
import { Post } from 'app/entities/metis/post.model';
import { PageType } from 'app/shared/metis/metis.util';
import { faUser } from '@fortawesome/free-solid-svg-icons';
import { PlagiarismVerdict } from 'app/exercises/shared/plagiarism/types/PlagiarismVerdict';
import { PostComponent } from 'app/shared/metis/post/post.component';
import { ButtonType } from 'app/shared/components/button.component';
import dayjs from 'dayjs/esm';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ButtonComponent } from 'app/shared/components/button.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-plagiarism-case-student-detail-view',
    templateUrl: './plagiarism-case-student-detail-view.component.html',
    styleUrls: ['./plagiarism-case-student-detail-view.component.scss'],
    providers: [MetisService],
    imports: [TranslateDirective, PlagiarismCaseVerdictComponent, FaIconComponent, RouterLink, PostComponent, ButtonComponent, PlagiarismCaseReviewComponent, ArtemisTranslatePipe],
})
export class PlagiarismCaseStudentDetailViewComponent implements OnInit, OnDestroy {
    protected metisService = inject(MetisService);
    private plagiarismCasesService = inject(PlagiarismCasesService);
    private activatedRoute = inject(ActivatedRoute);

    @ViewChild('post') postComponent: PostComponent;
    readonly ButtonType = ButtonType;

    courseId: number;
    plagiarismCaseId: number;
    plagiarismCase: PlagiarismCase;

    private paramSubscription: Subscription;
    readonly plagiarismVerdict = PlagiarismVerdict;

    getIcon = getIcon;
    faUser = faUser;

    readonly pageType = PageType.PLAGIARISM_CASE_STUDENT;
    private postsSubscription: Subscription;
    posts: Post[];

    affectedExerciseRouterLink: (string | number)[];

    isAfterDueDate: boolean;

    readonly dayjs = dayjs;

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

                    const now = dayjs();
                    this.isAfterDueDate = now.isAfter(this.plagiarismCase.exercise?.dueDate);
                },
            });
        });
        this.postsSubscription = this.metisService.posts.pipe().subscribe((posts: Post[]) => {
            this.posts = posts;
        });
    }

    async handleStudentReply() {
        this.postComponent.openCreateAnswerPostModal();
        await this.informInstructor();
    }

    async informInstructor() {
        await this.plagiarismCasesService.informInstructorAboutPostReply(this.posts[0].id!);
    }

    ngOnDestroy(): void {
        this.paramSubscription?.unsubscribe();
        this.postsSubscription?.unsubscribe();
    }
}
