import { Component, DestroyRef, OnInit, ViewEncapsulation, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faArrowLeft, faScroll, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular';
import { EMPTY } from 'rxjs';
import { catchError, finalize, switchMap, tap } from 'rxjs/operators';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { AlertService } from 'app/foundation/service/alert.service';
import { ArtemisMarkdownService } from 'app/foundation/service/markdown.service';
import { ScienceService } from 'app/foundation/science/science.service';
import { ScienceEventType } from 'app/foundation/science/science.model';
import { onError } from 'app/foundation/util/global.utils';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { TextUnit } from 'app/lecture/shared/entities/lecture-unit/textUnit.model';

/**
 * Shows the content of a single text unit on its own page: `/courses/:courseId/lectures/:lectureId/text-units/:unitId`.
 *
 * The route sets `isolatedView`, which makes the course shell drop its sidebar and title bar so only the main navbar
 * and the footer remain around the text. Rendering inside the application, instead of in a blank browser window, is
 * what gives the text the application theme (including dark mode) and a URL that can be bookmarked and shared.
 */
@Component({
    selector: 'jhi-text-unit-fullscreen',
    imports: [FaIconComponent, RouterLink, TranslateDirective, TumAetUiButtonDirective],
    templateUrl: './text-unit-fullscreen.component.html',
    styleUrl: './text-unit-fullscreen.component.scss',
    encapsulation: ViewEncapsulation.None,
})
export class TextUnitFullscreenComponent implements OnInit {
    private readonly route = inject(ActivatedRoute);
    private readonly destroyRef = inject(DestroyRef);
    private readonly lectureService = inject(LectureService);
    private readonly alertService = inject(AlertService);
    private readonly artemisMarkdown = inject(ArtemisMarkdownService);
    private readonly scienceService = inject(ScienceService);

    protected readonly faArrowLeft = faArrowLeft;
    protected readonly faScroll = faScroll;
    protected readonly faSpinner = faSpinner;

    readonly courseId = signal<number | undefined>(undefined);
    readonly lectureId = signal<number | undefined>(undefined);
    readonly lectureTitle = signal<string | undefined>(undefined);
    readonly textUnit = signal<TextUnit | undefined>(undefined);
    readonly isLoading = signal(true);

    readonly formattedContent = computed(() => {
        const content = this.textUnit()?.content;
        return content ? this.artemisMarkdown.safeHtmlForMarkdown(content) : undefined;
    });

    ngOnInit(): void {
        // The course id lives on a route above this one; the lecture and unit ids are ours.
        const courseId = this.route.pathFromRoot.map((route) => route.snapshot.paramMap.get('courseId')).find((id) => id !== null);
        this.courseId.set(courseId ? Number(courseId) : undefined);

        this.route.paramMap
            .pipe(
                // The state is reset in the projection, after switchMap has cancelled the previous request: that
                // request's finalize must not end the loading state of this one.
                switchMap((params) => {
                    this.isLoading.set(true);
                    this.textUnit.set(undefined);
                    this.lectureId.set(Number(params.get('lectureId')));
                    return this.lectureService.findWithDetails(Number(params.get('lectureId'))).pipe(
                        tap((response) => {
                            const lecture = response.body;
                            const unitId = Number(params.get('unitId'));
                            const unit = lecture?.lectureUnits?.find((lectureUnit) => lectureUnit.id === unitId && lectureUnit.type === LectureUnitType.TEXT);
                            this.lectureTitle.set(lecture?.title);
                            this.textUnit.set(unit);
                            if (unit) {
                                this.scienceService.logEvent(ScienceEventType.LECTURE__OPEN_UNIT, unit.id);
                            }
                        }),
                        catchError((errorResponse: HttpErrorResponse) => {
                            onError(this.alertService, errorResponse);
                            return EMPTY;
                        }),
                        finalize(() => this.isLoading.set(false)),
                    );
                }),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe();
    }
}
