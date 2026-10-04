import { Signal, isSignal } from '@angular/core';

/**
 * Contract for child route components that own a collapsible inner sidebar panel.
 * Implemented by tab components of {@link CourseOverviewComponent} and
 * {@link CourseManagementContainerComponent} to let the parent shell delegate
 * sidebar toggle/collapse state without statically importing the component classes.
 */
export interface SidebarView {
    toggleSidebar(): void;

    /** Current collapsed state of the component's own inner sidebar. */
    readonly isCollapsed: Signal<boolean>;
}

/**
 * Contract for child route components that render the container's page title themselves — inside
 * their own sidebar header, mirroring the student overview — instead of leaving it to the container
 * title bar. Kept separate from {@link SidebarView} because a component may own a sidebar without
 * hosting the title (e.g. the Iris tab).
 */
export interface PageTitleView {
    setPageTitle(pageTitle: string): void;
}

/**
 * Runtime duck-type guard for {@link SidebarView}: the component exposes a {@code toggleSidebar}
 * method and an {@code isCollapsed} signal. Used instead of {@code instanceof} so the container
 * shells do not need a static import of every tab component, which would pull all of them into the
 * shell's chunk and defeat the router's lazy {@code loadComponent}.
 */
export function isSidebarView(component: unknown): component is SidebarView {
    return (
        typeof component === 'object' &&
        component !== null &&
        'toggleSidebar' in component &&
        typeof component.toggleSidebar === 'function' &&
        'isCollapsed' in component &&
        isSignal(component.isCollapsed)
    );
}

/**
 * Runtime duck-type guard for {@link PageTitleView}. See {@link isSidebarView} for why the check is
 * structural rather than an {@code instanceof}.
 */
export function isPageTitleView(component: unknown): component is PageTitleView {
    return typeof component === 'object' && component !== null && 'setPageTitle' in component && typeof component.setPageTitle === 'function';
}
