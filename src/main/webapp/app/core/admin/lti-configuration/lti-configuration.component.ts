import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Course } from 'app/core/course/shared/entities/course.model';
import { faExclamationTriangle, faPencilAlt, faPlus, faSort, faTrash, faWrench } from '@fortawesome/free-solid-svg-icons';
import { LtiPlatformConfiguration } from 'app/lti/shared/entities/lti-configuration.model';
import { LtiConfigurationService } from 'app/core/admin/lti-configuration/lti-configuration.service';
import { SortService } from 'app/shared/service/sort.service';
import { Subject } from 'rxjs';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/shared/service/alert.service';
import { LTI_URLS } from 'app/core/admin/lti-configuration/lti-configuration.urls';
import { ITEMS_PER_PAGE } from 'app/shared/constants/pagination.constants';
import { combineLatest } from 'rxjs';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbNav, NgbNavContent, NgbNavItem, NgbNavLink, NgbNavLinkBase, NgbNavOutlet, NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { CopyToClipboardButtonComponent } from 'app/shared/components/buttons/copy-to-clipboard-button/copy-to-clipboard-button.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { SortByDirective } from 'app/shared/sort/directive/sort-by.directive';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { ItemCountComponent } from 'app/shared/pagination/item-count.component';

@Component({
    selector: 'jhi-lti-configuration',
    templateUrl: './lti-configuration.component.html',
    imports: [
        FormsModule,
        TranslateDirective,
        NgbNav,
        NgbNavItem,
        NgbNavLink,
        NgbNavLinkBase,
        NgbNavContent,
        HelpIconComponent,
        CopyToClipboardButtonComponent,
        RouterLink,
        FaIconComponent,
        SortDirective,
        SortByDirective,
        DeleteButtonDirective,
        ItemCountComponent,
        NgbPagination,
        NgbNavOutlet,
    ],
})
export class LtiConfigurationComponent implements OnInit {
    private router = inject(Router);
    private ltiConfigurationService = inject(LtiConfigurationService);
    private sortService = inject(SortService);
    private alertService = inject(AlertService);
    private activatedRoute = inject(ActivatedRoute);

    course: Course;
    platforms: LtiPlatformConfiguration[];
    ascending!: boolean;
    activeTab = 1;
    predicate = 'id';
    reverse = false;

    // page information
    page = 1;
    itemsPerPage = ITEMS_PER_PAGE;
    totalItems = 0;

    // Icons
    faSort = faSort;
    faExclamationTriangle = faExclamationTriangle;
    faWrench = faWrench;
    faPencilAlt = faPencilAlt;
    faTrash = faTrash;
    faPlus = faPlus;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    /**
     * Gets the configuration for the course encoded in the route and fetches the exercises
     */
    ngOnInit(): void {
        combineLatest({ data: this.activatedRoute.data, params: this.activatedRoute.queryParamMap }).subscribe(({ data, params }) => {
            const page = params.get('page');
            this.page = page !== null ? +page : 1;
            const sort = (params.get('sort') ?? data['defaultSort']).split(',');
            this.predicate = sort[0];
            this.ascending = sort[1] === 'asc';
            this.loadData();
        });
    }

    loadData(): void {
        this.ltiConfigurationService
            .query({
                page: this.page - 1,
                size: this.itemsPerPage,
                sort: this.sort(),
            })
            .subscribe((res: HttpResponse<LtiPlatformConfiguration[]>) => this.onSuccess(res.body, res.headers));
    }

    transition(): void {
        this.router.navigate(['/admin/lti-configuration'], {
            queryParams: {
                page: this.page,
                sort: this.predicate + ',' + (this.ascending ? 'asc' : 'desc'),
            },
        });
    }

    /**
     * Gets the dynamic registration url
     */
    getDynamicRegistrationUrl(): string {
        return LTI_URLS.LTI13_DYNAMIC_REGISTRATION_URL; // Needs to match url in lti.route
    }

    /**
     * Gets the deep linking url
     */
    getDeepLinkingUrl(): string {
        return LTI_URLS.LTI13_DEEPLINK_REDIRECT_PATH; // Needs to match url in CustomLti13Configurer
    }

    /**
     * Gets the tool url
     */
    getToolUrl(): string {
        return LTI_URLS.TOOL_URL; // Needs to match url in CustomLti13Configurer
    }

    /**
     * Gets the keyset url
     */
    getKeysetUrl(): string {
        return LTI_URLS.KEYSET_URI; // Needs to match url in CustomLti13Configurer
    }

    /**
     * Gets the initiate login url
     */
    getInitiateLoginUrl(): string {
        return LTI_URLS.LTI13_LOGIN_INITIATION_PATH; // Needs to match uri in CustomLti13Configurer
    }

    /**
     * Gets the redirect uri
     */
    getRedirectUri(): string {
        return LTI_URLS.LTI13_LOGIN_REDIRECT_PROXY_PATH; // Needs to match uri in CustomLti13Configurer
    }

    /**
     * Sorts the `platforms` array by the current `predicate` in `reverse` order.
     */
    sortRows() {
        this.sortService.sortByProperty(this.platforms, this.predicate, this.reverse);
    }

    /**
     * Initiates the deletion of an LTI platform configuration.
     * Upon successful deletion, navigates to the LTI configuration admin page.
     * If an error occurs, emits the error message to `dialogErrorSource`.
     *
     * @param platformId The unique identifier of the LTI platform to be deleted.
     */
    deleteLtiPlatform(platformId: number): void {
        this.ltiConfigurationService.deleteLtiPlatform(platformId).subscribe({
            next: () => {
                this.dialogErrorSource.next('');
                this.platforms = this.platforms.filter((platform) => platform.id !== platformId);
            },
            error: (error: HttpErrorResponse) => {
                this.dialogErrorSource.next(error.message);
                this.alertService.error('artemisApp.lti13.deletePlatformError');
            },
        });
    }

    private sort(): string[] {
        const result = [this.predicate + ',' + (this.ascending ? 'asc' : 'desc')];
        if (this.predicate !== 'id') {
            result.push('id');
        }
        return result;
    }

    private onSuccess(platforms: LtiPlatformConfiguration[] | null, headers: HttpHeaders): void {
        this.totalItems = Number(headers.get('X-Total-Count'));
        this.platforms = platforms || [];
    }
}
