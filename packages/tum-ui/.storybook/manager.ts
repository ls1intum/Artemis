import { useEffect } from 'react';
import { addons, types, useGlobals } from 'storybook/manager-api';
import { themes } from 'storybook/theming';

const tumUiGuideUrl = process.env.STORYBOOK_TUM_UI_GUIDE_URL;

function managerTheme(theme: unknown) {
    const baseTheme = theme === 'dark' ? themes.dark : theme === 'light' ? themes.light : themes.normal;
    return {
        ...baseTheme,
        brandTitle: tumUiGuideUrl ? 'Back to TUM UI package guide' : 'TUM UI component reference',
        ...(tumUiGuideUrl ? { brandUrl: tumUiGuideUrl, brandTarget: '_self' } : {}),
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

addons.register('tum-ui/theme-synchronizer', () => {
    addons.add('tum-ui/theme-synchronizer', {
        type: types.TOOL,
        title: 'Synchronize the manager theme',
        render: ThemeSynchronizer,
    });
});
