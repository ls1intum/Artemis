import { Component, OnDestroy, OnInit, inject, viewChild } from '@angular/core';
import { Observable } from 'rxjs';
import { PlagiarismCaseReviewComponent } from 'app/plagiarism/shared/review/plagiarism-case-review.component';
import { PlagiarismCaseVerdictComponent } from 'app/plagiarism/shared/verdict/plagiarism-case-verdict.component';
import { PlagiarismCase } from 'app/plagiarism/shared/entities/PlagiarismCase';
import { PlagiarismCasesService } from 'app/plagiarism/shared/services/plagiarism-cases.service';
import { ActivatedRoute, Params, RouterLink } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { getCourseFromExercise, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Subscription, combineLatest } from 'rxjs';
import { MetisService } from 'app/communication/service/metis.service';
import { Post } from 'app/communication/shared/entities/post.model';
import { PageType } from 'app/communication/metis.util';
import { faUser } from '@fortawesome/free-solid-svg-icons';
import { PlagiarismVerdict } from 'app/plagiarism/shared/entities/PlagiarismVerdict';
import { ButtonType } from 'app/shared/components/buttons/button/button.component';
import dayjs from 'dayjs/esm';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { PostComponent } from 'app/communication/post/post.component';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { PlagiarismAnswerPostCreationDTO } from 'app/plagiarism/shared/entities/PlagiarismAnswerPostCreationDTO';

@Component({
    selector: 'jhi-plagiarism-case-student-detail-view',
    templateUrl: './plagiarism-case-student-detail-view.component.html',
    styleUrls: ['./plagiarism-case-student-detail-view.component.scss'],
    providers: [MetisService],
    imports: [TranslateDirective, PlagiarismCaseVerdictComponent, FaIconComponent, RouterLink, PostComponent, ButtonComponent, PlagiarismCaseReviewComponent, ArtemisTranslatePipe],
})
export class PlagiarismCaseStudentDetailViewComponent implements OnInit, OnDestroy {
    private metisService = inject(MetisService);
    private plagiarismCasesService = inject(PlagiarismCasesService);
    private activatedRoute = inject(ActivatedRoute);
    readonly postComponent = viewChild.required<PostComponent>('post');
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
            ancestorParams: this.activatedRoute.parent!.params,
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

    ngOnDestroy(): void {
        this.paramSubscription?.unsubscribe();
        this.postsSubscription?.unsubscribe();
    }

    createPlagiarismAnswerPost = (answerPost: AnswerPost): Observable<AnswerPost> => {
        const dto = PlagiarismAnswerPostCreationDTO.of(answerPost);
        return this.metisService.createAnswerPost(dto);
    };
}
