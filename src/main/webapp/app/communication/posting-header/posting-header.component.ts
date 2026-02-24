import { Component, OnChanges, OnInit, computed, inject, input, output } from '@angular/core';
import { EmojiComponent } from 'app/communication/emoji/emoji.component';
import { faCheckSquare, faPencilAlt } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { Posting } from 'app/communication/shared/entities/posting.model';
import { User } from 'app/core/user/user.model';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { MetisService } from 'app/communication/service/metis.service';
import { AccountService } from 'app/core/auth/account.service';
import { tap } from 'rxjs';
import { faUser, faUserCheck, faUserGraduate } from '@fortawesome/free-solid-svg-icons';
import { DisplayPriority, UserRole } from 'app/communication/metis.util';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { Post } from 'app/communication/shared/entities/post.model';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
import { NgClass } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { addPublicFilePrefix } from 'app/app.constants';

@Component({
    selector: 'jhi-posting-header',
    templateUrl: './posting-header.component.html',
    styleUrls: ['../metis.component.scss'],
    imports: [ProfilePictureComponent, NgClass, FaIconComponent, NgbTooltip, TranslateDirective, ArtemisDatePipe, ArtemisTranslatePipe, EmojiComponent],
})
export class PostingHeaderComponent implements OnInit, OnChanges {
    lastReadDate = input<dayjs.Dayjs>();
    posting = input<Posting>();
    readOnlyMode = input<boolean>(false);
    previewMode = input<boolean>(false);
    hasChannelModerationRights = input<boolean>(false);
    isCommunicationPage = input<boolean>();
    isDeleted = input<boolean>(false);

    isModalOpen = output<void>();
    readonly onUserNameClicked = output<void>();

    isAtLeastInstructorInCourse: boolean;
    isAtLeastTutorInCourse: boolean;
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

    private metisService = inject(MetisService);
    private accountService = inject(AccountService);

    isPostResolved = computed<boolean>(() => {
        const posting = this.posting();
        return this.isPost(posting) && posting.resolved === true;
    });

    isPostPinned = computed<boolean>(() => {
        const posting = this.posting();
        return this.isPost(posting) && posting.displayPriority == DisplayPriority.PINNED;
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
     * Indicates if the posting was created after the last time the current user read the conversation
     */
    get isAfter(): boolean | undefined {
        return this.posting()?.creationDate?.isAfter(this.lastReadDate());
    }

    /**
     * Returns the author of the current posting
     */
    get authorOfPosting(): User | undefined {
        return this.posting()?.author;
    }

    /**
     * Returns the creation date of the posting
     */
    get creationDate(): dayjs.Dayjs | undefined {
        return this.posting()?.creationDate;
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

    /**
     * Emits the event to open a user profile or direct chat when the author's name is clicked,
     * unless the user is the author themself or role is missing
     */
    protected userNameClicked() {
        if (this.isAuthorOfPosting || !this.posting()?.authorRole) {
            return;
        }

        this.onUserNameClicked.emit();
    }

    protected readonly addPublicFilePrefix = addPublicFilePrefix;
}
