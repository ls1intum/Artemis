import Link from '@docusaurus/Link';
import { useLocation } from '@docusaurus/router';
import { useColorMode } from '@docusaurus/theme-common';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import { useEffect } from 'react';

const DEFAULT_STORY = 'introduction--docs';
const STORY_ANCHOR_PATTERN = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

function storyFromHash(hash: string) {
    try {
        const anchor = decodeURIComponent(hash.replace(/^#/, ''));
        return STORY_ANCHOR_PATTERN.test(anchor) ? `${anchor}--docs` : DEFAULT_STORY;
    } catch {
        return DEFAULT_STORY;
    }
}

export default function StorybookRedirect() {
    const { siteConfig } = useDocusaurusContext();
    const { colorMode } = useColorMode();
    const { hash } = useLocation();
    const storybookIncluded = siteConfig.customFields?.tumUiStorybookIncluded === true;
    const story = storyFromHash(hash);
    const storybookUrl = `/developer/tum-ui/?path=/docs/${story}&globals=theme:${colorMode}`;

    useEffect(() => {
        if (storybookIncluded) {
            window.location.replace(storybookUrl);
        }
    }, [storybookIncluded, storybookUrl]);

    return storybookIncluded ? (
        <p>
            Opening the TUM UI component reference… <Link to={`pathname://${storybookUrl}`}>Continue to the reference</Link>
        </p>
    ) : null;
}
