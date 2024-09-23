import { Posting } from 'app/entities/metis/posting.model';
import { Directive, EventEmitter, Input, OnInit, Output } from '@angular/core';
import dayjs from 'dayjs/esm';
import { MetisService } from 'app/shared/metis/metis.service';
import { UserRole } from 'app/shared/metis/metis.util';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faUser, faUserCheck, faUserGraduate } from '@fortawesome/free-solid-svg-icons';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { tap } from 'rxjs';

@Directive()
export abstract class PostingHeaderDirective<T extends Posting> implements OnInit {
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

    protected constructor(
        protected metisService: MetisService,
        protected accountService: AccountService,
    ) {}

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
        this.userProfilePictureInitials = this.posting.author?.name === undefined ? 'NA' : this.getInitials(this.posting.author?.name);
        this.userProfilePictureBackgroundColor = this.getBackgroundColor(this.posting.author?.id?.toString());
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

    /**
     * Returns a pseudo-random numeric value for a given string using a simple hash function.
     * @param {string} str - The string used for the hash function.
     */
    private deterministicRandomValueFromString(str: string): number {
        let seed = 0;
        for (let i = 0; i < str.length; i++) {
            seed = str.charCodeAt(i) + ((seed << 5) - seed);
        }
        const m = 0x80000000;
        const a = 1103515245;
        const c = 42718;

        seed = (a * seed + c) % m;

        return seed / (m - 1);
    }

    /**
     * Returns a background color hue for a given string.
     * @param {string | undefined} seed - The string used to determine the random value.
     */
    private getBackgroundColor(seed: string | undefined): string {
        if (seed === undefined) {
            seed = Math.random().toString();
        }
        const hue = this.deterministicRandomValueFromString(seed) * 360;
        return `hsl(${hue}, 50%, 50%)`; // Return an HSL color string
    }

    /**
     * Returns 2 capitalized initials of a given string.
     * If it has multiple names, it takes the first and last (Albert Berta Muster -> AM)
     * If it has one name, it'll return a deterministic random other string (Albert -> AB)
     * If it consists of a single letter it will return the single letter.
     * @param {string} username - The string used to generate the initials.
     */
    private getInitials(username: string): string {
        const parts = username.trim().split(/\s+/);

        let initials = '';

        if (parts.length > 1) {
            // Takes first and last word in string and returns their initials.
            initials = parts[0][0] + parts[parts.length - 1][0];
        } else {
            // If only one single word, it will take the first letter and a random second.
            initials = parts[0][0];
            const remainder = parts[0].slice(1);
            const secondInitial = remainder.match(/[a-zA-Z0-9]/);
            if (secondInitial) {
                initials += secondInitial[Math.floor(this.deterministicRandomValueFromString(username) * secondInitial.length)];
            }
        }

        return initials.toUpperCase();
    }

    abstract deletePosting(): void;
}
