import { Posting } from 'app/entities/metis/posting.model';
import { Directive, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import dayjs from 'dayjs/esm';
import { MetisService } from 'app/shared/metis/metis.service';
import { UserRole } from 'app/shared/metis/metis.util';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faUser, faUserCheck, faUserGraduate } from '@fortawesome/free-solid-svg-icons';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { tap } from 'rxjs';
import { getBackgroundColorHue } from 'app/utils/color.utils';
import { getInitialsFromString } from 'app/utils/text.utils';

@Directive()
export abstract class PostingHeaderDirective<T extends Posting> implements OnInit {
    protected metisService = inject(MetisService);
    protected accountService = inject(AccountService);

    @Input() posting: T;
    @Input() isCommunicationPage: boolean;

    @Input() hasChannelModerationRights = false;
    @Output() isModalOpen = new EventEmitter<void>();
    isAtLeastTutorInCourse: boolean;
    isAuthorOfPosting: boolean;
    postingIsOfToday: boolean;
    todayFlag?: string;
    userAuthorityIcon: IconProp;
    userAuthority: string;
    userRoleBadge: string;
    userAuthorityTooltip: string;
    userProfilePictureBackgroundColor: string;
    userProfilePictureInitials: string;
    currentUser?: User;

    /**
     * on initialization: determines if user is at least tutor in the course and if user is author of posting by invoking the metis service,
     * determines if posting is of today and sets the today flag to be shown in the header of the posting
     * determines icon and tooltip for authority type of the author
     */
    ngOnInit(): void {
        this.accountService
            .getAuthenticationState()
            .pipe(tap((user: User) => (this.currentUser = user)))
            .subscribe();
        this.postingIsOfToday = dayjs().isSame(this.posting.creationDate, 'day');
        this.todayFlag = this.getTodayFlag();
        this.setUserProperties();
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
     * Checks if the current user has at least a tutor role in the course and sets the `isAtLeastTutorInCourse` flag accordingly.
     * Calls `setUserAuthorityIconAndTooltip()` to set the user's authority icon and tooltip based on their role.
     *
     * @returns {void}
     */
    setUserProperties(): void {
        this.isAuthorOfPosting = this.metisService.metisUserIsAuthorOfPosting(this.posting);
        this.isAtLeastTutorInCourse = this.metisService.metisUserIsAtLeastTutorInCourse();
        this.setUserAuthorityIconAndTooltip();
    }

    /**
     * assigns suitable icon and tooltip for the author's authority type
     */
    setUserAuthorityIconAndTooltip(): void {
        const toolTipTranslationPath = 'artemisApp.metis.userAuthorityTooltips.';
        const roleBadgeTranslationPath = 'artemisApp.metis.userRoles.';
        this.userProfilePictureInitials = this.posting.author?.name === undefined ? 'NA' : getInitialsFromString(this.posting.author?.name);
        this.userProfilePictureBackgroundColor = getBackgroundColorHue(this.posting.author?.id?.toString());
        this.userAuthorityIcon = faUser;
        if (this.posting.authorRole === UserRole.USER) {
            this.userAuthority = 'student';
            this.userRoleBadge = roleBadgeTranslationPath + this.userAuthority;
            this.userAuthorityTooltip = toolTipTranslationPath + this.userAuthority;
        } else if (this.posting.authorRole === UserRole.INSTRUCTOR) {
            this.userAuthorityIcon = faUserGraduate;
            this.userAuthority = 'instructor';
            this.userRoleBadge = roleBadgeTranslationPath + this.userAuthority;
            this.userAuthorityTooltip = toolTipTranslationPath + this.userAuthority;
        } else if (this.posting.authorRole === UserRole.TUTOR) {
            this.userAuthorityIcon = faUserCheck;
            this.userAuthority = 'tutor';
            this.userRoleBadge = roleBadgeTranslationPath + this.userAuthority;
            this.userAuthorityTooltip = toolTipTranslationPath + this.userAuthority;
        }
    }

    abstract deletePosting(): void;
}
