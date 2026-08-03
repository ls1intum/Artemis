import { useLocation } from '@docusaurus/router';
import { useColorMode } from '@docusaurus/theme-common';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import { useEffect } from 'react';
import { storybookStoryFromHash } from './storybook-reference.generated';

export default function StorybookRedirect() {
    const { siteConfig } = useDocusaurusContext();
    const { colorMode } = useColorMode();
    const { hash } = useLocation();
    const storybookIncluded = siteConfig.customFields?.tumUiStorybookIncluded === true;
    const story = storybookStoryFromHash(hash);
    const storybookUrl = `/developer/tum-ui/?path=/docs/${story}&globals=theme:${colorMode}`;

    useEffect(() => {
        if (storybookIncluded) {
            window.location.replace(storybookUrl);
        }
    }, [storybookIncluded, storybookUrl]);

    if (!storybookIncluded) {
        return <p>The interactive component reference is available in the combined Artemis documentation build.</p>;
    }

    return (
        <p>
            Opening the TUM UI component reference… <a href={storybookUrl}>Continue to the reference</a>
        </p>
    );
}
