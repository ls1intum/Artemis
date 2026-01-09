/**
 * Helper utilities for handling scrolling to collapsed details elements.
 * This ensures that when navigating to a hash that points to a details element,
 * the element is automatically expanded.
 *
 * E.g. see https://github.com/ls1intum/Artemis/pull/11851#pullrequestreview-3639428261
 */

/**
 * Opens a details element with the given ID by triggering a click on the summary.
 * This ensures React's internal state is properly updated.
 * If the element is not a details element itself, it will search for the nearest parent details element.
 */
export function openDetailsById(elementId) {
    try {
        const element = document.getElementById(elementId);
        if (!element) {
            return;
        }

        // Find the nearest details element (either the element itself or its parent)
        const detailsElement = element.tagName === 'DETAILS'
            ? element
            : element.closest('details');

        if (detailsElement && !detailsElement.open) {
            // Find the summary element and click it to trigger React's state update
            const summary = detailsElement.querySelector('summary');
            if (summary) {
                summary.click();
                element.scrollIntoView({ behavior: 'smooth', block: 'start' });
            }
        }
    } catch (error) {
        console.error('Error opening details:', error);
    }
}

/**
 * Opens a details element if the current URL hash matches its ID.
 */
export function openDetailsOnHash() {
    const isServerSideRendering = typeof window === 'undefined';
    if (isServerSideRendering) {
        return;
    }

    const hash = window.location.hash;

    if (hash && hash.length > 1) {
        // Remove the # from the hash and decode it
        const elementId = decodeURIComponent(hash.substring(1));
        openDetailsById(elementId);
    }
}

/**
 * Intercepts clicks on anchor links and opens details elements if needed.
 */
export function handleLinkClick(event) {
    // Safely get the closest anchor element (event.target might be a text node)
    const target = event.target instanceof Element
        ? event.target.closest('a[href*="#"]')
        : null;

    if (!target) {
        return;
    }

    const href = target.getAttribute('href');
    if (!href) {
        return;
    }

    try {
        // Parse the href using URL API for more robust handling
        const url = new URL(href, window.location.href);
        const hash = url.hash;

        if (hash && hash.length > 1) {
            // Remove the # and decode the hash
            const elementId = decodeURIComponent(hash.substring(1));
            openDetailsById(elementId);
        }
    } catch (error) {
        // Fallback for invalid URLs
        const hashIndex = href.indexOf('#');
        if (hashIndex !== -1) {
            const hash = decodeURIComponent(href.substring(hashIndex + 1));
            openDetailsById(hash);
        }
    }
}
