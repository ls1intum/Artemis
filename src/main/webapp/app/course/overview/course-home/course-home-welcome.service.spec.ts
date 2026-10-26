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
});
