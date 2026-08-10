import { Injectable, OnDestroy, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, Subscription, of, tap } from 'rxjs';
import { CourseAvailableTabs } from 'app/course/shared/entities/course-available-tabs.model';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { AccountService } from 'app/core/auth/account.service';
import { currentNavigationId } from 'app/course/overview/services/navigation-scope';

/**
 * Holds which course overview tabs are available for the course the user is currently in.
 *
 * Both the {@link CourseOverviewGuard} (which decides before a tab route activates) and the course container (which
 * renders the sidebar from it) read the same value, so entering a course costs exactly one `available-tabs` request and
 * switching between tabs costs none.
 *
 * What is held is scoped to a single router navigation, so the guard and the sidebar of one tab selection share one
 * response while the next selection asks again. A course whose content changed — a lecture published, an exam made
 * visible — therefore shows the new tab on the next click rather than only after a page reload.
 */
@Injectable({ providedIn: 'root' })
export class CourseAvailableTabsService implements OnDestroy {
    private readonly courseManagementService = inject(CourseManagementService);
    private readonly accountService = inject(AccountService);
    private readonly router = inject(Router);

    private readonly state = signal<{ courseId: number; navigationId: number; tabs: CourseAvailableTabs } | undefined>(undefined);

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
        return this.courseManagementService
            .getCourseAvailableTabs(courseId)
            .pipe(tap((tabs) => this.state.set({ courseId, navigationId: currentNavigationId(this.router), tabs })));
    }

    /**
     * Returns the held tabs when they belong to the given course and fetches them otherwise. This is what makes a course
     * visit cost a single request no matter which tab the user enters through.
     * @param courseId the course to get the tabs for
     */
    loadIfNeeded(courseId: number): Observable<CourseAvailableTabs> {
        const tabs = this.tabsFor(courseId);
        return tabs ? of(tabs) : this.load(courseId);
    }

    /**
     * Drops the held tabs so the next course visit fetches them again.
     */
    clear(): void {
        this.state.set(undefined);
    }
}
