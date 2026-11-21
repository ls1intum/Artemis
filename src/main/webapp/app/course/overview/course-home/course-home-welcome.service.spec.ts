import { CourseHomeWelcomeService } from './course-home-welcome.service';
import { COURSE_HOME_SITUATIONS, createCourseHomeFixture } from './test/course-home.fixtures';
import en from '../../../../i18n/en/courseHome.json';
import de from '../../../../i18n/de/courseHome.json';

describe('deterministic Home welcomes', () => {
    it.each(COURSE_HOME_SITUATIONS)('provides three paired variants for %s', (situation) => {
        const home = createCourseHomeFixture(situation);
        const service = new CourseHomeWelcomeService();
        for (let variantIndex = 0; variantIndex < 3; variantIndex++) {
            home.welcome.variantIndex = variantIndex;
            const key = service.keys(home).body.split('.').slice(3);
            for (const catalog of [en, de]) {
                let value: unknown = catalog.artemisApp.courseHome.welcome;
                for (const part of key) value = (value as Record<string, unknown>)[part];
                expect(typeof value).toBe('string');
                expect((value as string).length).toBeGreaterThan(30);
            }
        }
    });
    it('keeps all 45 welcome variants paired with identical parameters in both languages', () => {
        expect(Object.keys(en.artemisApp.courseHome.welcome)).toHaveLength(15);
        expect(Object.keys(de.artemisApp.courseHome.welcome)).toEqual(Object.keys(en.artemisApp.courseHome.welcome));
        for (const situation of Object.keys(en.artemisApp.courseHome.welcome)) {
            const english = en.artemisApp.courseHome.welcome[situation as keyof typeof en.artemisApp.courseHome.welcome];
            const german = de.artemisApp.courseHome.welcome[situation as keyof typeof de.artemisApp.courseHome.welcome];
            expect(Object.keys(english)).toHaveLength(3);
            for (const variant of ['v0', 'v1', 'v2'] as const) {
                for (const part of ['title', 'body'] as const) {
                    expect(english[variant][part].match(/{{\w+}}/g) ?? []).toEqual(german[variant][part].match(/{{\w+}}/g) ?? []);
                }
            }
        }
    });
});
