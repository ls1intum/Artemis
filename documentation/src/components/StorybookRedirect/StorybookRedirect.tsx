import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import { useEffect } from 'react';

export default function StorybookRedirect() {
    const { siteConfig } = useDocusaurusContext();
    const storybookIncluded = siteConfig.customFields?.tumUiStorybookIncluded === true;

    useEffect(() => {
        if (storybookIncluded) {
            window.location.reload();
        }
    }, [storybookIncluded]);

    return storybookIncluded ? <p>Opening the TUM UI component reference…</p> : null;
}
