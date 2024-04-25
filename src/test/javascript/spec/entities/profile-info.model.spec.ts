import { ProfileInfo, hasEditableBuildPlan } from 'app/shared/layouts/profiles/profile-info.model';

describe('Profile Info', () => {
    describe('has editable build plan', () => {
        const profileInfo = new ProfileInfo();

        it.each([
            ['jenkins', true],
            ['gitlabci', true],
            ['gitlab', false],
        ])('should have editable build plan editor for profile "%s": %s', (profile, editable) => {
            profileInfo.activeProfiles = ['artemis', 'prod', profile];
            expect(hasEditableBuildPlan(profileInfo)).toBe(editable);
        });
    });
});
