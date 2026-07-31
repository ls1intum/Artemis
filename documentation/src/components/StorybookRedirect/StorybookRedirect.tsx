import { useColorMode } from '@docusaurus/theme-common';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import { useEffect } from 'react';

interface StorybookRedirectProps {
    references: Record<string, string>;
}

export default function StorybookRedirect({ references }: StorybookRedirectProps) {
    const { siteConfig } = useDocusaurusContext();
    const { colorMode } = useColorMode();
    const storybookIncluded = siteConfig.customFields?.tumUiStorybookIncluded === true;
    const reference = typeof window === 'undefined' ? '' : decodeURIComponent(window.location.hash.slice(1));
    const story = references[reference] ?? 'introduction--docs';
    const storybookUrl = `/developer/tum-ui/?path=/docs/${story}&globals=theme:${colorMode}`;

    useEffect(() => {
        if (storybookIncluded) {
            window.location.replace(storybookUrl);
        }
    }, [storybookIncluded, storybookUrl]);

    return storybookIncluded ? (
        <p>
            Opening the TUM UI component reference… <a href={storybookUrl}>Continue to the reference</a>
        </p>
    ) : null;
}
