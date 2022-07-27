import { Posting } from 'app/entities/metis/posting.model';
import { Directive, Input, OnInit } from '@angular/core';
import dayjs from 'dayjs/esm';
import { MetisService } from 'app/shared/metis/metis.service';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faUser, faUserCheck, faUserGraduate } from '@fortawesome/free-solid-svg-icons';
import { UserRole } from 'app/shared/metis/metis.util';

@Directive()
export abstract class PostingHeaderDirective<T extends Posting> implements OnInit {
    @Input() posting: T;
    isAtLeastTutorInCourse: boolean;
    isAuthorOfPosting: boolean;
    postingIsOfToday: boolean;
    todayFlag: string | undefined;
    userAuthorityIcon: IconProp;
    userAuthorityTooltip: string;

    protected constructor(protected metisService: MetisService) {}

    /**
     * on initialization: determines if user is at least tutor in the course and if user is author of posting by invoking the metis service,
     * determines if posting is of today and sets the today flag to be shown in the header of the posting
     * determines icon and tooltip for authority type of the author
     */
    ngOnInit(): void {
        this.isAtLeastTutorInCourse = this.metisService.metisUserIsAtLeastTutorInCourse();
        this.isAuthorOfPosting = this.metisService.metisUserIsAuthorOfPosting(this.posting);
        this.postingIsOfToday = dayjs().isSame(this.posting.creationDate, 'day');
        this.todayFlag = this.getTodayFlag();
        this.setUserAuthorityIconAndTooltip();
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
     * assigns suitable icon and tooltip for the author's most privileged authority type
     */
    setUserAuthorityIconAndTooltip(): void {
        const toolTipTranslationPath = 'artemisApp.metis.userAuthorityTooltips';

        if (!this.posting.authorRole || this.posting.authorRole === UserRole.USER) {
            this.userAuthorityIcon = faUser;
            this.userAuthorityTooltip = toolTipTranslationPath + 'user';
        } else if (this.posting.authorRole === UserRole.INSTRUCTOR) {
            this.userAuthorityIcon = faUserGraduate;
            this.userAuthorityTooltip = toolTipTranslationPath + 'instructor';
        } else if (this.posting.authorRole === UserRole.TUTOR) {
            this.userAuthorityIcon = faUserCheck;
            this.userAuthorityTooltip = toolTipTranslationPath + 'ta';
        }
    }

    abstract deletePosting(): void;
}
