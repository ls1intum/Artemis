import { Injectable } from '@angular/core';
import { SidebarItem } from 'app/core/course/shared/course-sidebar/course-sidebar.component';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import {
    faChalkboardUser,
    faChartBar,
    faChartColumn,
    faComments,
    faFlag,
    faGraduationCap,
    faList,
    faListAlt,
    faNetworkWired,
    faPersonChalkboard,
    faPuzzlePiece,
    faQuestion,
    faRobot,
    faTable,
    faTableCells,
    faUserCheck,
} from '@fortawesome/free-solid-svg-icons';

/**
 * Service for creating common sidebar items used in both course overview and course management components
 */
@Injectable({
    providedIn: 'root',
})
export class CourseSidebarItemService {
    getManagementDefaultItems(): SidebarItem[] {
        const overviewItem: SidebarItem = {
            routerLink: '.',
            icon: faTableCells,
            title: 'Overview',
            translation: 'artemisApp.course.overview',
            hidden: false,
            isPrefix: true,
        };

        const exercisesItem: SidebarItem = {
            routerLink: 'exercises',
            icon: faListAlt,
            title: 'Exercises',
            translation: 'artemisApp.courseOverview.menu.exercises',
            hidden: false,
        };

        const statisticsItem: SidebarItem = {
            routerLink: 'course-statistics',
            icon: faChartColumn,
            title: 'Statistics',
            translation: 'artemisApp.courseOverview.menu.statistics',
            guidedTour: true,
            hidden: false,
        };

        return [overviewItem, this.getExamsItem(), exercisesItem, statisticsItem];
    }

    getStudentDefaultItems(hasDashboard = false): SidebarItem[] {
        const items = [];

        if (hasDashboard) {
            items.push(this.getDashboardItem());
        }

        const exercisesItem: SidebarItem = {
            routerLink: 'exercises',
            icon: faListAlt,
            title: 'Exercises',
            translation: 'artemisApp.courseOverview.menu.exercises',
            hidden: false,
        };

        const statisticsItem: SidebarItem = {
            routerLink: 'statistics',
            icon: faChartColumn,
            title: 'Statistics',
            translation: 'artemisApp.courseOverview.menu.statistics',
            guidedTour: true,
            hidden: false,
        };

        return [...items, exercisesItem, statisticsItem];
    }

    getExamsItem(): SidebarItem {
        return {
            routerLink: 'exams',
            icon: faGraduationCap,
            title: 'Exams',
            testId: 'exam-tab',
            translation: 'artemisApp.courseOverview.menu.exams',
            hidden: false,
        };
    }

    getLecturesItem(): SidebarItem {
        return {
            routerLink: 'lectures',
            icon: faChalkboardUser,
            title: 'Lectures',
            translation: 'artemisApp.courseOverview.menu.lectures',
            hidden: false,
        };
    }

    getCommunicationsItem(): SidebarItem {
        return {
            routerLink: 'communication',
            icon: faComments,
            title: 'Communication',
            translation: 'artemisApp.courseOverview.menu.communication',
            hidden: false,
        };
    }

    getTutorialGroupsItem(): SidebarItem {
        return {
            routerLink: 'tutorial-groups',
            icon: faPersonChalkboard,
            title: 'Tutorials',
            translation: 'artemisApp.courseOverview.menu.tutorialGroups',
            featureToggle: FeatureToggle.TutorialGroups,
            hidden: false,
        };
    }

    getCompetenciesItem(): SidebarItem {
        return {
            routerLink: 'competency-management',
            icon: faFlag,
            title: 'Competency Management',
            translation: 'artemisApp.courseOverview.menu.competencies',
            hidden: false,
        };
    }

    getLearningPathItem(): SidebarItem {
        return {
            routerLink: 'learning-path-management',
            icon: faNetworkWired,
            title: 'Learning Path',
            translation: 'artemisApp.courseOverview.menu.learningPath',
            featureToggle: FeatureToggle.LearningPaths,
            hidden: false,
        };
    }

    getDashboardItem(): SidebarItem {
        return {
            routerLink: 'dashboard',
            icon: faChartBar,
            title: 'Dashboard',
            translation: 'artemisApp.courseOverview.menu.dashboard',
            featureToggle: FeatureToggle.StudentCourseAnalyticsDashboard,
            hidden: false,
        };
    }

    getFaqItem(): SidebarItem {
        return {
            routerLink: 'faqs',
            icon: faQuestion,
            title: 'FAQs',
            translation: 'artemisApp.courseOverview.menu.faq',
            hidden: false,
        };
    }

    getIrisSettingsItem(): SidebarItem {
        return {
            routerLink: 'iris-settings',
            icon: faRobot,
            title: 'IRIS Settings',
            translation: 'artemisApp.iris.settings.button.course.title',
            testId: 'iris-settings',
            hidden: false,
        };
    }

    getAssessmentDashboardItem(): SidebarItem {
        return {
            routerLink: 'assessment-dashboard',
            icon: faUserCheck,
            title: 'Assessment Dashboard',
            translation: 'entity.action.assessmentDashboard',
            hidden: false,
        };
    }

    getScoresItem(): SidebarItem {
        return {
            routerLink: 'scores',
            icon: faTable,
            title: 'Scores',
            translation: 'entity.action.scores',
            hidden: false,
        };
    }

    getBuildQueueItem(): SidebarItem {
        return {
            routerLink: 'build-queue',
            icon: faList,
            title: 'Build Queue',
            translation: 'artemisApp.buildQueue.title',
            hidden: false,
        };
    }

    getLtiConfigurationItem(): SidebarItem {
        return {
            routerLink: 'lti-configuration',
            icon: faPuzzlePiece,
            title: 'LTI Configuration',
            translation: 'global.menu.admin.lti',
            testId: 'lti-settings',
            hidden: false,
        };
    }
}
