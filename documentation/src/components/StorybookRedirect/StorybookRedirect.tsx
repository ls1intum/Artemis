import { useColorMode } from '@docusaurus/theme-common';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import { useEffect } from 'react';

export default function StorybookRedirect() {
    const { siteConfig } = useDocusaurusContext();
    const { colorMode } = useColorMode();
    const storybookIncluded = siteConfig.customFields?.tumUiStorybookIncluded === true;
    const storybookUrl = `/developer/tum-ui/?path=/docs/introduction--docs&globals=theme:${colorMode}`;

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
