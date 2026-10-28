import { OnDestroy, Service, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, Subscription, finalize, of, shareReplay, tap } from 'rxjs';
import { CourseAvailableTabs } from 'app/course/shared/entities/course-available-tabs.model';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { AccountService } from 'app/core/auth/account.service';
import { currentNavigationId } from 'app/course/overview/services/navigation-scope';

/**
 * Holds which course overview tabs are available for the course the user is currently in.
 *
 * Both the {@link CourseOverviewGuard} (which decides before a tab route activates) and the course container (which
 * renders the sidebar from it) read the same value, so entering a course costs exactly one `available-tabs` request and
 * selecting a tab therefore costs one request no matter which of them asks first.
 *
 * What is held is scoped to a single router navigation, so the guard and the sidebar of one tab selection share one
 * response while the next selection asks again. A course whose content changed — a lecture published, an exam made
 * visible — therefore shows the new tab on the next click rather than only after a page reload.
 */
@Service()
export class CourseAvailableTabsService implements OnDestroy {
    private readonly courseManagementService = inject(CourseManagementService);
    private readonly accountService = inject(AccountService);
    private readonly router = inject(Router);

    private readonly state = signal<{ courseId: number; navigationId: number; tabs: CourseAvailableTabs } | undefined>(undefined);
    private inFlightRequest?: { courseId: number; observable: Observable<CourseAvailableTabs> };

    private currentUserId?: number;
    private readonly authenticationStateSubscription: Subscription;

    constructor() {
        this.currentUserId = this.accountService.userIdentity()?.id;
        this.authenticationStateSubscription = this.accountService.getAuthenticationState().subscribe((user) => {
            if (this.currentUserId !== user?.id) {
                this.currentUserId = user?.id;
                this.clear();
            }
        });
    }

    ngOnDestroy(): void {
        this.clear();
        this.authenticationStateSubscription?.unsubscribe();
    }

    /**
     * The available tabs for the given course, or undefined when they are not currently held for it.
     * @param courseId the course to read the tabs for
     */
    tabsFor(courseId: number): CourseAvailableTabs | undefined {
        const current = this.state();
        // Guard on `current` itself: with optional chaining alone, an undefined courseId would compare equal to the
        // undefined of an empty state and wrongly report a hit
        return current && current.courseId === courseId && current.navigationId === currentNavigationId(this.router) ? current.tabs : undefined;
    }

    /**
     * Fetches the available tabs from the server and holds them as the current course's tabs, replacing whatever was
     * held before.
     * @param courseId the course to fetch the tabs for
     */
    load(courseId: number): Observable<CourseAvailableTabs> {
        const observable = this.courseManagementService.getCourseAvailableTabs(courseId).pipe(
            tap((tabs) => {
                if (this.inFlightRequest?.observable === observable) {
                    this.state.set({ courseId, navigationId: currentNavigationId(this.router), tabs });
                }
            }),
            finalize(() => {
                if (this.inFlightRequest?.observable === observable) {
                    this.inFlightRequest = undefined;
                }
            }),
            shareReplay({ bufferSize: 1, refCount: true }),
        );
        this.inFlightRequest = { courseId, observable };
        return observable;
    }

    /**
     * Returns this navigation's held tabs or shares an unfinished request for the course. Concurrent readers therefore
     * cost one request, while a later navigation still fetches fresh availability.
     * @param courseId the course to get the tabs for
     */
    loadIfNeeded(courseId: number): Observable<CourseAvailableTabs> {
        const tabs = this.tabsFor(courseId);
        if (tabs) {
            return of(tabs);
        }
        return this.inFlightRequest?.courseId === courseId ? this.inFlightRequest.observable : this.load(courseId);
    }

    /**
     * Drops the held tabs and prevents an older request from replacing them, so the next visit fetches again.
     */
    clear(): void {
        this.state.set(undefined);
        this.inFlightRequest = undefined;
    }
}
