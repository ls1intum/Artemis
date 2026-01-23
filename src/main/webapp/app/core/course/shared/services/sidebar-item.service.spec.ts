import { TestBed } from '@angular/core/testing';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import {
    faChalkboardUser,
    faChartColumn,
    faCog,
    faComments,
    faDumbbell,
    faFlag,
    faGraduationCap,
    faList,
    faListAlt,
    faPuzzlePiece,
    faQuestion,
    faRobot,
    faTable,
    faTableCells,
    faUserCheck,
} from '@fortawesome/free-solid-svg-icons';
import { CourseSidebarItemService } from 'app/core/course/shared/services/sidebar-item.service';

describe('CourseSidebarItemService', () => {
    let service: CourseSidebarItemService;
    const courseId = 123;

    beforeEach(() => {
        TestBed.configureTestingModule({});
        service = TestBed.inject(CourseSidebarItemService);
    });

    describe('getManagementDefaultItems', () => {
        it('should return correct items with courseId', () => {
            const items = service.getManagementDefaultItems(courseId);
            const expectedItems = [
                {
                    routerLink: `${courseId}`,
                    icon: faTableCells,
                    title: 'Overview',
                    translation: 'artemisApp.course.overview',
                    hidden: false,
                    isPrefix: true,
                },
                {
                    routerLink: `${courseId}/exams`,
                    icon: faGraduationCap,
                    title: 'Exams',
                    testId: 'exam-tab',
                    translation: 'artemisApp.courseOverview.menu.exams',
                    hidden: false,
                },
                {
                    routerLink: `${courseId}/exercises`,
                    icon: faListAlt,
                    title: 'Exercises',
                    translation: 'artemisApp.courseOverview.menu.exercises',
                    hidden: false,
                },
                {
                    routerLink: `${courseId}/course-statistics`,
                    icon: faChartColumn,
                    title: 'Statistics',
                    translation: 'artemisApp.courseOverview.menu.statistics',
                    hidden: false,
                },
            ];
            expect(items).toHaveLength(4);
            expect(items).toEqual(expectedItems);
        });

        it('should return correct items without courseId', () => {
            const items = service.getManagementDefaultItems(2);

            expect(items).toHaveLength(4);

            expect(items[0]).toEqual({
                routerLink: '2',
                icon: faTableCells,
                title: 'Overview',
                translation: 'artemisApp.course.overview',
                hidden: false,
                isPrefix: true,
            });

            expect(items[2]).toEqual({
                routerLink: '2/exercises',
                icon: faListAlt,
                title: 'Exercises',
                translation: 'artemisApp.courseOverview.menu.exercises',
                hidden: false,
            });
        });
    });

    describe('getStudentDefaultItems', () => {
        it('should return items without dashboard when hasDashboard is false and questionsAvailableForTraining is false', () => {
            const items = service.getStudentDefaultItems(false, false);

            expect(items).toHaveLength(3);
            expect(items[0].title).toBe('Exercises');
            expect(items[1].title).toBe('Statistics');
            expect(items[2].title).toBe('Calendar');
        });

        it('should include dashboard item when hasDashboard is true and questionsAvailableForTraining is true', () => {
            const items = service.getStudentDefaultItems(true, true);

            expect(items).toHaveLength(5);
            expect(items[0].title).toBe('Dashboard');
            expect(items[1].title).toBe('Exercises');
            expect(items[2].title).toBe('Training');
            expect(items[3].title).toBe('Statistics');
            expect(items[4].title).toBe('Calendar');
        });
    });

    describe('Individual item methods', () => {
        it('getTrainingItem should return correct item', () => {
            const item = service.getTrainingItem();

            expect(item).toEqual({
                routerLink: 'training',
                icon: faDumbbell,
                title: 'Training',
                translation: 'overview.training',
                hidden: false,
            });
        });

        it('getExamsItem should return correct item with courseId', () => {
            const item = service.getExamsItem(courseId);

            expect(item).toEqual({
                routerLink: `${courseId}/exams`,
                icon: faGraduationCap,
                title: 'Exams',
                testId: 'exam-tab',
                translation: 'artemisApp.courseOverview.menu.exams',
                hidden: false,
            });
        });

        it('getExamsItem should return correct item without courseId', () => {
            const item = service.getExamsItem();

            expect(item).toEqual({
                routerLink: 'exams',
                icon: faGraduationCap,
                title: 'Exams',
                testId: 'exam-tab',
                translation: 'artemisApp.courseOverview.menu.exams',
                hidden: false,
            });
        });

        it('getLecturesItem should return correct item with courseId', () => {
            const item = service.getLecturesItem(courseId);

            expect(item).toEqual({
                routerLink: `${courseId}/lectures`,
                icon: faChalkboardUser,
                title: 'Lectures',
                translation: 'artemisApp.courseOverview.menu.lectures',
                testId: 'lectures',
                hidden: false,
            });
        });

        it('getLecturesItem should return correct item without courseId', () => {
            const item = service.getLecturesItem();

            expect(item).toEqual({
                routerLink: 'lectures',
                icon: faChalkboardUser,
                title: 'Lectures',
                translation: 'artemisApp.courseOverview.menu.lectures',
                testId: 'lectures',
                hidden: false,
            });
        });

        it('getCommunicationsItem should return correct item with courseId', () => {
            const item = service.getCommunicationsItem(courseId);

            expect(item).toEqual({
                routerLink: `${courseId}/communication`,
                icon: faComments,
                title: 'Communication',
                translation: 'artemisApp.courseOverview.menu.communication',
                hidden: false,
            });
        });

        it('getCompetenciesManagementItem should return correct item', () => {
            const item = service.getCompetenciesManagementItem(courseId);

            expect(item).toEqual({
                routerLink: `${courseId}/competency-management`,
                icon: faFlag,
                title: 'Competency Management',
                translation: 'artemisApp.courseOverview.menu.competencies',
                hidden: false,
            });
        });

        it('getCompetenciesItem should return correct item', () => {
            const item = service.getCompetenciesItem();

            expect(item).toEqual({
                routerLink: 'competencies',
                icon: faFlag,
                title: 'Competencies',
                translation: 'artemisApp.courseOverview.menu.competencies',
                hidden: false,
            });
        });

        it('getLearningPathItem should include feature toggle', () => {
            const item = service.getLearningPathItem();

            expect(item.featureToggle).toBe(FeatureToggle.LearningPaths);
        });

        it('getLearningPathManagementItem should include feature toggle', () => {
            const item = service.getLearningPathManagementItem(courseId);

            expect(item.featureToggle).toBe(FeatureToggle.LearningPaths);
        });

        it('getDashboardItem should include feature toggle', () => {
            const item = service.getDashboardItem();

            expect(item.featureToggle).toBe(FeatureToggle.StudentCourseAnalyticsDashboard);
        });

        it('getIrisItem should return correct item', () => {
            const item = service.getIrisItem();

            expect(item).toEqual({
                routerLink: 'iris',
                icon: faRobot,
                title: 'Iris',
                translation: 'artemisApp.courseOverview.menu.iris',
                hidden: false,
            });
        });

        it('getFaqManagementItem should return correct item', () => {
            const item = service.getFaqManagementItem(courseId);

            expect(item).toEqual({
                routerLink: `${courseId}/faqs`,
                icon: faQuestion,
                title: 'FAQs',
                translation: 'artemisApp.courseOverview.menu.faq',
                hidden: false,
            });
        });

        it('getFaqItem should return correct item', () => {
            const item = service.getFaqItem();

            expect(item).toEqual({
                routerLink: 'faq',
                icon: faQuestion,
                title: 'FAQs',
                translation: 'artemisApp.courseOverview.menu.faq',
                hidden: false,
            });
        });

        it('getIrisSettingsItem should return correct item', () => {
            const item = service.getIrisSettingsItem(courseId);

            expect(item).toEqual({
                routerLink: `${courseId}/iris-settings`,
                icon: faRobot,
                title: 'IRIS Settings',
                translation: 'artemisApp.iris.settings.button.course.title',
                testId: 'iris-settings',
                hidden: false,
            });
        });

        it('getAssessmentDashboardItem should return correct item', () => {
            const item = service.getAssessmentDashboardItem(courseId);

            expect(item).toEqual({
                routerLink: `${courseId}/assessment-dashboard`,
                icon: faUserCheck,
                title: 'Assessment Dashboard',
                translation: 'entity.action.assessmentDashboard',
                hidden: false,
            });
        });

        it('getScoresItem should return correct item', () => {
            const item = service.getScoresItem(courseId);

            expect(item).toEqual({
                routerLink: `${courseId}/scores`,
                icon: faTable,
                title: 'Scores',
                translation: 'entity.action.scores',
                hidden: false,
            });
        });

        it('getBuildQueueItem should return correct item', () => {
            const item = service.getBuildQueueItem(courseId);

            expect(item).toEqual({
                routerLink: `${courseId}/build-queue`,
                icon: faList,
                title: 'Build Queue',
                translation: 'artemisApp.buildQueue.title',
                hidden: false,
            });
        });

        it('getLtiConfigurationItem should return correct item', () => {
            const item = service.getLtiConfigurationItem(courseId);

            expect(item).toEqual({
                routerLink: `${courseId}/lti-configuration`,
                icon: faPuzzlePiece,
                title: 'LTI Configuration',
                translation: 'global.menu.admin.lti',
                testId: 'lti-settings',
                hidden: false,
            });
        });

        it('getCourseSettingsItem should return correct item', () => {
            const item = service.getCourseSettingsItem(courseId);

            expect(item).toEqual({
                routerLink: `${courseId}/settings`,
                icon: faCog,
                title: 'Settings',
                translation: 'artemisApp.courseOverview.menu.settings',
                testId: 'course-settings',
                bottom: true,
                hidden: false,
            });
        });
    });
});
