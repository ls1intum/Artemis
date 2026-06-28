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
 * Returns true if the component exposes both {@code toggleSidebar} (a function) and
 * {@code isCollapsed} (a boolean or zero-argument function / Signal) — the full
 * shape of the {@link SidebarView} contract.
 */
export function hasSidebar(component: unknown): component is SidebarView {
    const c = component as SidebarView;
    return !!component && typeof c.toggleSidebar === 'function' && (typeof c.isCollapsed === 'boolean' || typeof c.isCollapsed === 'function');
}
