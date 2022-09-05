import { Component, OnInit, OnDestroy } from '@angular/core';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/shared/plagiarism-cases.service';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { HttpResponse } from '@angular/common/http';
import { getExerciseUrlSegment, getIcon } from 'app/entities/exercise.model';
import { PlagiarismVerdict } from 'app/exercises/shared/plagiarism/types/PlagiarismVerdict';
import { MetisService } from 'app/shared/metis/metis.service';
import { PageType } from 'app/shared/metis/metis.util';
import { Post } from 'app/entities/metis/post.model';
import { Subscription } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';
import { faCheck, faInfo, faPrint, faUser } from '@fortawesome/free-solid-svg-icons';
import { ThemeService } from 'app/core/theme/theme.service';
import { abbreviateString } from 'app/utils/text.utils';

@Component({
    selector: 'jhi-plagiarism-case-instructor-detail-view',
    templateUrl: './plagiarism-case-instructor-detail-view.component.html',
    styleUrls: ['./plagiarism-case-instructor-detail-view.component.scss'],
    providers: [MetisService],
})
export class PlagiarismCaseInstructorDetailViewComponent implements OnInit, OnDestroy {
    courseId: number;
    plagiarismCaseId: number;
    plagiarismCase: PlagiarismCase;

    verdictPointDeduction = 0;
    verdictMessage = '';
    createdPost: Post;

    activeTab = 1;

    getExerciseUrlSegment = getExerciseUrlSegment;
    getIcon = getIcon;
    faUser = faUser;
    faPrint = faPrint;
    faInfo = faInfo;
    faCheck = faCheck;

    readonly pageType = PageType.PLAGIARISM_CASE;
    private postsSubscription: Subscription;
    posts: Post[];

    constructor(
        protected metisService: MetisService,
        private plagiarismCasesService: PlagiarismCasesService,
        private route: ActivatedRoute,
        private alertService: AlertService,
        private translateService: TranslateService,
        private themeService: ThemeService,
    ) {}

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
                this.metisService.getFilteredPosts({
                    plagiarismCaseId: this.plagiarismCase!.id,
                });
                this.createEmptyPost();
            },
        });
        this.postsSubscription = this.metisService.posts.subscribe((posts: Post[]) => {
            const filteredPosts = posts.filter((post) => post.plagiarismCase?.id === this.plagiarismCaseId);

            // Handle post deletion case by checking if unfiltered posts are empty.
            if (filteredPosts.length > 0 || posts.length === 0) {
                // Note: "filteredPosts.length > 0 || posts.length === 0" behaves differently than filteredPosts.length >= 0
                // when "posts.length > 0 && filteredPosts.length === 0".
                this.posts = filteredPosts;
            }
        });
    }

    ngOnDestroy(): void {
        this.postsSubscription?.unsubscribe();
    }

    /**
     * saves the verdict of the plagiarism case as POINT_DEDUCTION
     * and saves the point deduction in percent
     */
    savePointDeductionVerdict(): void {
        if (!this.isStudentNotified()) {
            throw new Error('Cannot call savePointDeductionVerdict before student is notified');
        }
        this.plagiarismCasesService
            .saveVerdict(this.courseId, this.plagiarismCaseId, {
                verdict: PlagiarismVerdict.POINT_DEDUCTION,
                verdictPointDeduction: this.verdictPointDeduction,
            })
            .subscribe({
                next: (res: HttpResponse<PlagiarismCase>) => {
                    this.plagiarismCase.verdict = res.body!.verdict;
                    this.plagiarismCase.verdictPointDeduction = res.body!.verdictPointDeduction!;
                    this.plagiarismCase.verdictBy = res.body!.verdictBy;
                    this.plagiarismCase.verdictDate = res.body!.verdictDate;
                },
            });
    }

    /**
     * saves the verdict of the plagiarism case as WARNING
     * and saves the warning message
     */
    saveWarningVerdict(): void {
        if (!this.isStudentNotified()) {
            throw new Error('Cannot call saveWarningVerdict before student is notified');
        }
        this.plagiarismCasesService
            .saveVerdict(this.courseId, this.plagiarismCaseId, {
                verdict: PlagiarismVerdict.WARNING,
                verdictMessage: this.verdictMessage,
            })
            .subscribe({
                next: (res: HttpResponse<PlagiarismCase>) => {
                    this.plagiarismCase.verdict = res.body!.verdict;
                    this.plagiarismCase.verdictMessage = res.body!.verdictMessage!;
                    this.plagiarismCase.verdictBy = res.body!.verdictBy;
                    this.plagiarismCase.verdictDate = res.body!.verdictDate;
                },
            });
    }

    /**
     * saves the verdict of the plagiarism case as PLAGIARISM
     */
    saveVerdict(): void {
        if (!this.isStudentNotified()) {
            throw new Error('Cannot call saveVerdict before student is notified');
        }
        this.plagiarismCasesService.saveVerdict(this.courseId, this.plagiarismCaseId, { verdict: PlagiarismVerdict.PLAGIARISM }).subscribe({
            next: (res: HttpResponse<PlagiarismCase>) => {
                this.plagiarismCase.verdict = res.body!.verdict;
                this.plagiarismCase.verdictBy = res.body!.verdictBy;
                this.plagiarismCase.verdictDate = res.body!.verdictDate;
            },
        });
    }

    /**
     * saves the verdict of the plagiarism case as NO_PLAGIARISM
     */
    saveNoPlagiarismVerdict(): void {
        if (!this.isStudentNotified()) {
            throw new Error('Cannot call saveNoPlagiarismVerdict before student is notified');
        }
        this.plagiarismCasesService.saveVerdict(this.courseId, this.plagiarismCaseId, { verdict: PlagiarismVerdict.NO_PLAGIARISM }).subscribe({
            next: (res: HttpResponse<PlagiarismCase>) => {
                this.plagiarismCase.verdict = res.body!.verdict;
                this.plagiarismCase.verdictBy = res.body!.verdictBy;
                this.plagiarismCase.verdictDate = res.body!.verdictDate;
            },
        });
    }

    isStudentNotified() {
        return this.posts?.length > 0;
    }

    onStudentNotified(post: Post) {
        if (!this.posts) {
            this.posts = [];
        }
        this.posts.push(post);
        this.alertService.success('artemisApp.plagiarism.plagiarismCases.studentNotified');
    }

    /**
     * Creates a post for the student notification.
     * This method invokes the metis service to create an empty default post (without course-wide context) that is needed for initialization of the modal.
     * The plagiarism case is set as context and an example title and body for the instructor is generated.
     **/
    createEmptyPost(): void {
        const studentName = abbreviateString(this.plagiarismCase.student?.name ?? '', 70);
        const exerciseTitle = abbreviateString(this.plagiarismCase.exercise?.title ?? '', 70);
        const courseTitle = abbreviateString(this.plagiarismCase.exercise?.course?.title ?? '', 70);

        this.createdPost = this.metisService.createEmptyPostForContext(undefined, undefined, undefined, this.plagiarismCase);
        // Note the limit of 1.000 characters for the post's content
        this.createdPost.title = this.translateService.instant('artemisApp.plagiarism.plagiarismCases.notification.title', {
            exercise: exerciseTitle,
        });
        this.createdPost.content = this.translateService.instant('artemisApp.plagiarism.plagiarismCases.notification.body', {
            student: studentName,
            exercise: exerciseTitle,
            course: courseTitle,
            cocLink: 'https://www.in.tum.de/fileadmin/w00bws/in/2.Fur_Studierende/Pruefungen_und_Formalitaeten/1.Gute_studentische_Praxis/englisch/leitfaden-en_2016Jun22.pdf',
            aspoLink: 'https://www.tum.de/studium/im-studium/das-studium-organisieren/satzungen-ordnungen#statute;t:Allgemeine%20Prüfungs-%20und%20Studienordnung;sort:106;page:1',
        });
    }

    /**
     * Prints the whole page using the theme service
     */
    printPlagiarismCase(): void {
        this.themeService.print();
    }
}
