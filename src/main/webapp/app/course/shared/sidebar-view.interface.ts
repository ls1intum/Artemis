/**
 * Contract for child route components that own a collapsible inner sidebar panel.
 * Implemented by tab components of {@link CourseOverviewComponent} and
 * {@link CourseManagementContainerComponent} to let the parent shell delegate
 * sidebar toggle/collapse state without statically importing the component classes.
 */
export interface SidebarView {
    toggleSidebar(): void;

    /**
     * Current collapsed state. May be a plain boolean or a zero-argument function
     * (e.g. an Angular Signal): read as typeof isCollapsed === 'function' ? isCollapsed() : isCollapsed.
     */
    isCollapsed: boolean | (() => boolean);
}

/**
 * Runtime duck-type guard for {@link SidebarView}.
 * Returns true if the component has a {@code toggleSidebar} method — sufficient to
 * distinguish sidebar-owning tab components from plain route components.
 */
export function hasSidebar(component: unknown): component is SidebarView {
    return !!component && typeof (component as SidebarView).toggleSidebar === 'function';
}
