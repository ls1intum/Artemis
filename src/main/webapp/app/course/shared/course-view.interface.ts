/**
 * Contract for course view components that own a collapsible inner panel.
 * Implemented by child route components of CourseOverviewComponent and
 * CourseManagementContainerComponent to let the parent shell delegate
 * sidebar toggle/collapse state.
 */
export interface CourseView {
    toggleSidebar(): void;

    /**
     * Current collapsed state. May be a plain boolean or a zero-argument function
     * (e.g. an Angular Signal): read as typeof isCollapsed === 'function' ? isCollapsed() : isCollapsed.
     */
    isCollapsed: boolean | (() => boolean);
}

/**
 * Type-guard that checks at runtime whether a component reference satisfies {@link CourseView}.
 */
export function isCourseView(component: unknown): component is CourseView {
    return !!component && typeof (component as CourseView).toggleSidebar === 'function';
}
