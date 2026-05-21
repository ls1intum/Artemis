import { useEffect } from 'react';
import ExecutionEnvironment from '@docusaurus/ExecutionEnvironment';
import { openDetailsOnHash, handleLinkClick } from '../utils/detailsScrollHelper';

/**
 * Root component that wraps the entire Docusaurus application.
 * Sets up global event listeners for handling collapsed details elements during navigation.
 */
export default function Root({ children }) {
  useEffect(() => {
    if (!ExecutionEnvironment.canUseDOM) {
      return;
    }

    // Run on initial load
    openDetailsOnHash();

    // Listen to navigation events
    window.addEventListener('hashchange', openDetailsOnHash);
    window.addEventListener('popstate', openDetailsOnHash);
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
