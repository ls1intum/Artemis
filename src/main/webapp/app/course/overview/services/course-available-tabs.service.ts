import { Injectable, OnDestroy, inject, signal } from '@angular/core';
import { Observable, Subscription, of, tap } from 'rxjs';
import { CourseAvailableTabs } from 'app/course/shared/entities/course-available-tabs.model';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { AccountService } from 'app/core/auth/account.service';

/**
 * Holds which course overview tabs are available for the course the user is currently in.
 *
 * Both the {@link CourseOverviewGuard} (which decides before a tab route activates) and the course container (which
 * renders the sidebar from it) read the same value, so entering a course costs exactly one `available-tabs` request and
 * switching between tabs costs none.
 *
 * This is per-visit state, not a cache: exactly one course is held at a time, it is keyed by course id so switching
 * courses always refetches, {@link clear} drops it when the container is destroyed, and it is dropped on logout / user
 * change. Re-entering a course therefore always asks the server again.
 */
@Injectable({ providedIn: 'root' })
export class CourseAvailableTabsService implements OnDestroy {
    private readonly courseManagementService = inject(CourseManagementService);
    private readonly accountService = inject(AccountService);

    private readonly state = signal<{ courseId: number; tabs: CourseAvailableTabs } | undefined>(undefined);

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
        return current && current.courseId === courseId ? current.tabs : undefined;
    }

    /**
     * Fetches the available tabs from the server and holds them as the current course's tabs, replacing whatever was
     * held before.
     * @param courseId the course to fetch the tabs for
     */
    load(courseId: number): Observable<CourseAvailableTabs> {
        return this.courseManagementService.getCourseAvailableTabs(courseId).pipe(tap((tabs) => this.state.set({ courseId, tabs })));
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
