import { addons } from 'storybook/manager-api';
import { themes } from 'storybook/theming';

const artemisClientGuideUrl = process.env.STORYBOOK_ARTEMIS_CLIENT_GUIDE_URL;

addons.setConfig({
    theme: {
        ...themes.normal,
        brandTitle: artemisClientGuideUrl ? 'Back to Artemis client guide' : 'TUM UI component reference',
        ...(artemisClientGuideUrl ? { brandUrl: artemisClientGuideUrl, brandTarget: '_self' } : {}),
    },
});
