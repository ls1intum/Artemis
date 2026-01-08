import React, { useEffect } from 'react';
import ExecutionEnvironment from '@docusaurus/ExecutionEnvironment';

/**
 * Root component that wraps the entire Docusaurus application.
 * Used to add global functionality like auto-expanding details elements when navigating via anchor links.
 */
export default function Root({ children }) {
  useEffect(() => {
    if (!ExecutionEnvironment.canUseDOM) {
      return;
    }

    /**
     * Opens a details element with the given ID.
     */
    const openDetailsById = (elementId) => {
      const attempts = [0, 100, 300, 600];

      attempts.forEach((delay) => {
        setTimeout(() => {
          try {
            const element = document.getElementById(elementId);
            if (element && element.tagName === 'DETAILS') {
              element.setAttribute('open', '');
              element.setAttribute('data-collapsed', 'false');
              element.classList.add('open');
              element.open = true;

              // Find the child div and change its display style
              const childDiv = element.querySelector(':scope > div');
              if (childDiv) {
                childDiv.style.display = 'block';
                childDiv.style.height = 'auto';
                childDiv.style.overflow = 'visible';
              }
            }
          } catch (error) {
            console.error('Error opening details:', error);
          }
        }, delay);
      });
    };

    /**
     * Opens a details element if the current URL hash matches its ID.
     */
    const openDetailsOnHash = () => {
      const hash = window.location.hash;

      if (hash && hash.length > 1) {
        // Remove the # from the hash
        const elementId = hash.substring(1);
        openDetailsById(elementId);
      }
    };

    /**
     * Intercept clicks on anchor links and open details elements if needed.
     */
    const handleLinkClick = (event) => {
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
    };

    // Run on initial load
    openDetailsOnHash();

    // Listen to various navigation events
    window.addEventListener('hashchange', openDetailsOnHash);
    window.addEventListener('popstate', openDetailsOnHash);

    // Intercept all link clicks
    document.addEventListener('click', handleLinkClick, true);

    // Cleanup event listeners on unmount
    return () => {
      window.removeEventListener('hashchange', openDetailsOnHash);
      window.removeEventListener('popstate', openDetailsOnHash);
      document.removeEventListener('click', handleLinkClick, true);
    };
  }, []);

  return <>{children}</>;
}
