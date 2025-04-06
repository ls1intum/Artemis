import { getDayTranslationKey } from 'app/tutorialgroup/shared/util/weekdays';

describe('WeekDays', () => {
    it('should return the correct translation key', () => {
        expect(getDayTranslationKey(1)).toBe('artemisApp.generic.weekdays.monday');
    });
});
