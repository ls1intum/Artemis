import { useEffect } from 'react';
import { addons, types, useGlobals } from 'storybook/manager-api';
import { themes } from 'storybook/theming';

const tumAetUiGuideUrl = process.env.STORYBOOK_TUM_AET_UI_GUIDE_URL || 'https://docs.artemis.tum.de/developer/guidelines/tum-aet-ui-kit';

function managerTheme(theme: unknown) {
    const baseTheme = theme === 'dark' ? themes.dark : theme === 'light' ? themes.light : themes.normal;
    return {
        ...baseTheme,
        brandTitle: 'TUM AET UI',
        brandUrl: tumAetUiGuideUrl,
        brandTarget: '_self',
    };
}

function ThemeSynchronizer() {
    const [globals] = useGlobals();

    useEffect(() => {
        addons.setConfig({ theme: managerTheme(globals.theme) });
    }, [globals.theme]);

    return null;
}

addons.setConfig({
    theme: managerTheme(undefined),
});

addons.register('tumaet-ui/theme-synchronizer', () => {
    addons.add('tumaet-ui/theme-synchronizer', {
        type: types.TOOL,
        title: 'Synchronize the manager theme',
        render: ThemeSynchronizer,
    });
});
