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
 */
export function openDetailsById(elementId) {
    try {
        const element = document.getElementById(elementId);
        if (element && element.tagName === 'DETAILS') {
            const isCollapsed = element.getAttribute('data-collapsed') !== 'false';

            if (isCollapsed) {
                // Find the summary element and click it to trigger React's state update
                const summary = element.querySelector('summary');
                if (summary) {
                    summary.click();
                    element.scrollIntoView({ behavior: 'smooth', block: 'start' });
                }
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
    const hash = window.location.hash;

    if (hash && hash.length > 1) {
        // Remove the # from the hash
        const elementId = hash.substring(1);
        openDetailsById(elementId);
    }
}

/**
 * Intercepts clicks on anchor links and opens details elements if needed.
 */
export function handleLinkClick(event) {
    const target = event.target.closest('a[href*="#"]');
    if (!target) return;

    const href = target.getAttribute('href');
    if (!href) return;

    const hashIndex = href.indexOf('#');
    if (hashIndex === -1) return;

    const hash = href.substring(hashIndex + 1);

    // Small delay to allow navigation to complete
    setTimeout(() => {
        openDetailsById(hash);
    }, 50);
}
