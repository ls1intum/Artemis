import { Component, OnInit, OnDestroy } from '@angular/core';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/shared/plagiarism-cases.service';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { ExerciseType, getCourseFromExercise, getIcon } from 'app/entities/exercise.model';
import { PlagiarismVerdict } from 'app/exercises/shared/plagiarism/types/PlagiarismVerdict';
import { MetisService } from 'app/shared/metis/metis.service';
import { PageType } from 'app/shared/metis/metis.util';
import { Post } from 'app/entities/metis/post.model';
import { Subscription } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';

@Component({
    selector: 'jhi-plagiarism-case-instructor-detail-view',
    templateUrl: './plagiarism-case-instructor-detail-view.component.html',
    providers: [MetisService],
})
export class PlagiarismCaseInstructorDetailViewComponent implements OnInit, OnDestroy {
    courseId: number;
    plagiarismCaseId: number;
    plagiarismCase: PlagiarismCase;
    verdictPointDeduction = 0;
    verdictMessage = '';
    createdPost: Post;
    getIcon = getIcon;

    readonly pageType = PageType.PLAGIARISM_CASE;
    private postsSubscription: Subscription;
    posts: Post[];

    constructor(protected metisService: MetisService, private plagiarismCasesService: PlagiarismCasesService, private route: ActivatedRoute, private alertService: AlertService) {}

    ngOnInit(): void {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.plagiarismCaseId = Number(this.route.snapshot.paramMap.get('plagiarismCaseId'));
        this.plagiarismCasesService.getPlagiarismCaseDetailForInstructor(this.courseId, this.plagiarismCaseId).subscribe({
            next: (res: HttpResponse<PlagiarismCase>) => {
                this.plagiarismCase = res.body!;
                this.verdictMessage = this.plagiarismCase.verdictMessage ?? '';
                this.verdictPointDeduction = this.plagiarismCase.verdictPointDeduction ?? 0;
                this.metisService.setCourse(getCourseFromExercise(this.plagiarismCase.exercise!)!);
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
     * invoke metis service to create an empty default post that is needed on initialization of a modal to create a post,
     * this empty post has no course-wide context as well as the plagiarism case set as context
     * it has an example text for the instructor and a default title containing the exercise title
     **/
    createEmptyPost(): void {
        this.createdPost = this.metisService.createEmptyPostForContext(undefined, undefined, undefined, this.plagiarismCase);
        this.createdPost.content = `Dear ${this.plagiarismCase.student!.name},\nAfter a meticulous review of your final submission for the ${
            this.plagiarismCase.exercise!.title
        } in the ${
            this.plagiarismCase.exercise!.exerciseGroup
                ? `exam ${this.plagiarismCase.exercise!.exerciseGroup.exam!.title}`
                : `course ${this.plagiarismCase.exercise!.course!.title}`
        }, we have concluded that you have committed plagiarism.\nThis is not only a violation of principles of good student practice but also of the “Student Code of Conduct” of the faculty of computer science that you have agreed upon. You can check the Student conduct code [here](https://www.in.tum.de/fileadmin/w00bws/in/2.Fur_Studierende/Pruefungen_und_Formalitaeten/1.Gute_studentische_Praxis/englisch/leitfaden-en_2016Jun22.pdf) §22.1 of the “Allgemeine Studien- und Prüfungsordnung (APSO)” [“General Examination and Study Regulations”] regulates consequences for such cases. You can find the APSO [here](https://www.tum.de/studium/im-studium/das-studium-organisieren/satzungen-ordnungen#statute;t:Allgemeine%20Prüfungs-%20und%20Studienordnung;sort:106;page:1).\nYou have one week to provide a statement about this situation.`;
        this.createdPost.title = `Plagiarism Case ${this.plagiarismCase.exercise!.title}`;
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

    /**
     * exports the plagiarism case with all relevant information as PDF
     */
    exportPlagiarismCase(): void {
        // TODO: export the plagiarism case as a PDF with all relevant information
    }
}
