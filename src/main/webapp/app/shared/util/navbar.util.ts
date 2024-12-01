/**
 * Update the header height SCSS variable based on the navbar height.
 *
 * The navbar height can change based on the screen size and the content of the navbar
 * (e.g. long breadcrumbs due to longs exercise names)
 */
export function updateHeaderHeight() {
    setTimeout(() => {
        const navbar = document.querySelector('jhi-navbar');
        if (navbar) {
            // do not use navbar.offsetHeight, this might not be defined in Safari!
            const headerHeight = navbar.getBoundingClientRect().height;
            document.documentElement.style.setProperty('--header-height', `${headerHeight}px`);
        }
    });
}
