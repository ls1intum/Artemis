import { addons } from 'storybook/manager-api';
import { themes } from 'storybook/theming';

const tumUiGuideUrl = process.env.STORYBOOK_TUM_UI_GUIDE_URL;

addons.setConfig({
    theme: {
        ...themes.normal,
        brandTitle: tumUiGuideUrl ? 'Back to TUM UI package guide' : 'TUM UI component reference',
        ...(tumUiGuideUrl ? { brandUrl: tumUiGuideUrl, brandTarget: '_self' } : {}),
    },
});
