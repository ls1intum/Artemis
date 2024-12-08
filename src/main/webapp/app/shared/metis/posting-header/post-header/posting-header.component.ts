import { Component, OnChanges, OnDestroy, OnInit, ViewChild, computed, inject, input, output } from '@angular/core';
import { PostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { faCheckSquare, faPencilAlt } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { CachingStrategy } from 'app/shared/image/secured-image.component';
import { Posting } from 'app/entities/metis/posting.model';
import { User } from 'app/core/user/user.model';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { MetisService } from 'app/shared/metis/metis.service';
import { AccountService } from 'app/core/auth/account.service';
import { tap } from 'rxjs';
import { faUser, faUserCheck, faUserGraduate } from '@fortawesome/free-solid-svg-icons';
import { UserRole } from 'app/shared/metis/metis.util';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { Post } from 'app/entities/metis/post.model';

@Component({
    selector: 'jhi-posting-header',
    templateUrl: './posting-header.component.html',
    styleUrls: ['../../metis.component.scss'],
})
export class PostingHeaderComponent implements OnInit, OnDestroy, OnChanges {
    readOnlyMode = input<boolean>(false);
    lastReadDate = input<dayjs.Dayjs>();
    previewMode = input<boolean>(false);
    @ViewChild(PostCreateEditModalComponent) postCreateEditModal?: PostCreateEditModalComponent;
    isAtLeastInstructorInCourse: boolean;
    posting = input<Posting>();
    isCommunicationPage = input<boolean>();
    hasChannelModerationRights = input<boolean>(false);
    isModalOpen = output<void>();

    isDeleted = input<boolean>(false);

    isAuthorOfPosting: boolean;
    postingIsOfToday: boolean;
    todayFlag?: string;
    userAuthorityIcon: IconProp;
    userAuthority: string;
    userRoleBadge: string;
    userAuthorityTooltip: string;
    currentUser?: User;

    // Icons
    readonly faPencilAlt = faPencilAlt;
    readonly faCheckSquare = faCheckSquare;

    protected metisService: MetisService = inject(MetisService);
    protected accountService: AccountService = inject(AccountService);

    isPostResolved = computed<boolean>(() => {
        const p = this.posting();
        return this.isPost(p) && p.resolved === true;
    });

    /**
     * on initialization: determines if user is author of posting by invoking the metis service,
     * determines if posting is of today and sets the today flag to be shown in the header of the posting
     * determines icon and tooltip for authority type of the author
     */
    ngOnInit(): void {
        this.accountService
            .getAuthenticationState()
            .pipe(
                tap((user: User) => {
                    this.currentUser = user;
                    this.setUserProperties();
                }),
            )
            .subscribe();
        this.postingIsOfToday = dayjs().isSame(this.posting()?.creationDate, 'day');
        this.todayFlag = this.getTodayFlag();
    }

    private isPost(posting: Posting | AnswerPost | undefined): posting is Post {
        return posting !== undefined && 'resolved' in posting;
    }

    /**
     * on changes: re-evaluates authority roles
     */
    ngOnChanges() {
        this.setUserProperties();
        this.setUserAuthorityIconAndTooltip();
    }

    /**
     * on leaving the page, the modal should be closed
     */
    ngOnDestroy(): void {
        this.postCreateEditModal?.modalRef?.close();
    }

    /**
     * sets a flag that replaces the date by "Today" in the posting's header if applicable
     */
    getTodayFlag(): string | undefined {
        if (this.postingIsOfToday) {
            return 'artemisApp.metis.today';
        } else {
            return undefined;
        }
    }

    /**
     * Sets various user properties related to the posting and course.
     * Checks if the current user is the author of the posting and sets the `isAuthorOfPosting` flag accordingly.
     * Calls `setUserAuthorityIconAndTooltip()` to set the user's authority icon and tooltip based on their role.
     *
     * @returns {void}
     */
    setUserProperties(): void {
        this.isAuthorOfPosting = this.metisService.metisUserIsAuthorOfPosting(this.posting()!);
        this.setUserAuthorityIconAndTooltip();
    }

    /**
     * assigns suitable icon and tooltip for the author's authority type
     */
    setUserAuthorityIconAndTooltip(): void {
        const toolTipTranslationPath = 'artemisApp.metis.userAuthorityTooltips.';
        const roleBadgeTranslationPath = 'artemisApp.metis.userRoles.';
        this.userAuthorityIcon = faUser;
        if (this.posting()?.authorRole === UserRole.USER) {
            this.userAuthority = 'student';
            this.userRoleBadge = roleBadgeTranslationPath + this.userAuthority;
            this.userAuthorityTooltip = toolTipTranslationPath + this.userAuthority;
        } else if (this.posting()?.authorRole === UserRole.INSTRUCTOR) {
            this.userAuthorityIcon = faUserGraduate;
            this.userAuthority = 'instructor';
            this.userRoleBadge = roleBadgeTranslationPath + this.userAuthority;
            this.userAuthorityTooltip = toolTipTranslationPath + this.userAuthority;
        } else if (this.posting()?.authorRole === UserRole.TUTOR) {
            this.userAuthorityIcon = faUserCheck;
            this.userAuthority = 'tutor';
            this.userRoleBadge = roleBadgeTranslationPath + this.userAuthority;
            this.userAuthorityTooltip = toolTipTranslationPath + this.userAuthority;
        } else {
            this.userAuthority = 'student';
            this.userRoleBadge = 'artemisApp.metis.userRoles.deleted';
            this.userAuthorityTooltip = 'artemisApp.metis.userAuthorityTooltips.deleted';
        }
    }

    protected readonly CachingStrategy = CachingStrategy;
}
