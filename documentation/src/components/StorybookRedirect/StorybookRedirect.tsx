import { useLocation } from '@docusaurus/router';
import { useColorMode } from '@docusaurus/theme-common';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import { useEffect } from 'react';

const DEFAULT_STORY = 'introduction--docs';
const STORY_ANCHOR = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

function storyFromHash(hash: string, storyAnchors: readonly string[]) {
    try {
        const anchor = decodeURIComponent(hash.replace(/^#/, ''));
        return STORY_ANCHOR.test(anchor) && storyAnchors.includes(anchor) ? `${anchor}--docs` : DEFAULT_STORY;
    } catch {
        return DEFAULT_STORY;
    }
}

export default function StorybookRedirect({ storyAnchors }: { storyAnchors: readonly string[] }) {
    const { siteConfig } = useDocusaurusContext();
    const { colorMode } = useColorMode();
    const { hash } = useLocation();
    const storybookIncluded = siteConfig.customFields?.tumUiStorybookIncluded === true;
    const story = storyFromHash(hash, storyAnchors);
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
