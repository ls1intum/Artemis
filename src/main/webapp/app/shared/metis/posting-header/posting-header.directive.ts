import { Posting } from 'app/entities/metis/posting.model';
import { Directive, Input, OnInit } from '@angular/core';
import dayjs from 'dayjs/esm';
import { MetisService } from 'app/shared/metis/metis.service';
import { Authority } from 'app/shared/constants/authority.constants';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faUser, faUserCheck, faUserGraduate } from '@fortawesome/free-solid-svg-icons';

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
     * @param authorities {[Authority]}
     */
    setUserAuthorityIconAndTooltip(): void {
        // TODO
        const authorities = this.posting.author!.authorities!.map((authority) => authority['name']);

        if (authorities.includes(Authority.INSTRUCTOR) || authorities.includes(Authority.ADMIN)) {
            this.userAuthorityIcon = faUserGraduate;
            this.userAuthorityTooltip = 'artemisApp.metis.userAuthorityTooltips.instructor';
        } else if (authorities.includes(Authority.TA) || authorities.includes(Authority.EDITOR)) {
            this.userAuthorityIcon = faUserCheck;
            this.userAuthorityTooltip = 'artemisApp.metis.userAuthorityTooltips.ta';
        } else {
            this.userAuthorityIcon = faUser;
            this.userAuthorityTooltip = 'artemisApp.metis.userAuthorityTooltips.user';
        }
    }

    abstract deletePosting(): void;
}
