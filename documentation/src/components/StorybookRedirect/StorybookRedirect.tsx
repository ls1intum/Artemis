import { useLocation } from '@docusaurus/router';
import { useColorMode } from '@docusaurus/theme-common';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import { useEffect, useRef } from 'react';
import { STORYBOOK_REFERENCES } from './storybook-references';

const DEFAULT_STORY = 'introduction--docs';

function storyFromHash(hash: string) {
    try {
        const anchor = decodeURIComponent(hash.replace(/^#/, ''));
        return STORYBOOK_REFERENCES[anchor] ?? DEFAULT_STORY;
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
    const redirectLink = useRef<HTMLAnchorElement>(null);

    useEffect(() => {
        if (storybookIncluded) {
            redirectLink.current?.click();
        }
    }, [storybookIncluded, storybookUrl]);

    if (!storybookIncluded) {
        return <p>The interactive component reference is available in the combined Artemis documentation build.</p>;
    }

    return (
        <p>
            Opening the TUM UI component reference…{' '}
            <a ref={redirectLink} href={storybookUrl}>
                Continue to the reference
            </a>
        </p>
    );
}
