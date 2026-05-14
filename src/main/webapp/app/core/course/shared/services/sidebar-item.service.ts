import { Injectable } from '@angular/core';
import { SidebarItem } from 'app/core/course/shared/course-sidebar/course-sidebar.component';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import {
    faBullseye,
    faCalendarDays,
    faChalkboardTeacher,
    faChartBar,
    faChartColumn,
    faCode,
    faCog,
    faComments,
    faDumbbell,
    faFileAlt,
    faList,
    faNetworkWired,
    faPuzzlePiece,
    faQuestion,
    faRobot,
    faTable,
    faTableCells,
    faUserCheck,
    faUsers,
} from '@fortawesome/free-solid-svg-icons';

/**
 * Service for creating common sidebar items used in both course overview and course management components
 */
@Injectable({
    providedIn: 'root',
})
export class CourseSidebarItemService {
    getManagementDefaultItems(courseId: number): SidebarItem[] {
        return [
            {
                routerLink: courseId ? `${courseId}` : '',
                icon: faTableCells,
                title: 'Overview',
                translation: 'artemisApp.course.overview',
                hidden: false,
                isPrefix: true,
            },
            this.getExamsItem(courseId),
            this.getExercisesItem(courseId),
            this.getStatisticsItem(courseId),
        ];
    }

    getExercisesItem(courseId?: number): SidebarItem {
        return {
            routerLink: courseId ? `${courseId}/exercises` : 'exercises',
            icon: faCode,
            iconColor: '#6366f1',
            title: 'Exercises',
            translation: 'artemisApp.courseOverview.menu.exercises',
            hidden: false,
        };
    }

    getStatisticsItem(courseId?: number): SidebarItem {
        return {
            routerLink: courseId ? `${courseId}/course-statistics` : 'course-statistics',
            icon: faChartColumn,
            title: 'Statistics',
            translation: 'artemisApp.courseOverview.menu.statistics',
            hidden: false,
        };
    }

    getStudentDefaultItems(hasDashboard = false, questionsAvailable = false): SidebarItem[] {
        const items = [];
        const training = [];

        if (hasDashboard) {
            items.push(this.getDashboardItem());
        }

        const exercisesItem: SidebarItem = {
            routerLink: 'exercises',
            icon: faCode,
            iconColor: '#6366f1',
            title: 'Exercises',
            translation: 'artemisApp.courseOverview.menu.exercises',
            hidden: false,
        };

        if (questionsAvailable) {
            training.push(this.getTrainingItem());
        }

        const statisticsItem: SidebarItem = {
            routerLink: 'statistics',
            icon: faChartColumn,
            title: 'Statistics',
            translation: 'artemisApp.courseOverview.menu.statistics',
            hidden: false,
        };

        const calendarItem: SidebarItem = {
            routerLink: 'calendar',
            icon: faCalendarDays,
            title: 'Calendar',
            translation: 'artemisApp.courseOverview.menu.calendar',
            hidden: false,
        };

        return [...items, exercisesItem, ...training, statisticsItem, calendarItem];
    }

    getTrainingItem(): SidebarItem {
        return {
            routerLink: 'training',
            icon: faDumbbell,
            title: 'Training',
            translation: 'overview.training',
            hidden: false,
        };
    }

    getExamsItem(courseId?: number): SidebarItem {
        return {
            routerLink: courseId ? `${courseId}/exams` : 'exams',
            icon: faFileAlt,
            iconColor: '#f59e0b',
            title: 'Exams',
            testId: 'exam-tab',
            translation: 'artemisApp.courseOverview.menu.exams',
            hidden: false,
        };
    }

    getLecturesItem(courseId?: number): SidebarItem {
        return {
            routerLink: courseId ? `${courseId}/lectures` : 'lectures',
            icon: faChalkboardTeacher,
            iconColor: '#0ea5e9',
            title: 'Lectures',
            translation: 'artemisApp.courseOverview.menu.lectures',
            testId: 'lectures',
            hidden: false,
        };
    }

    getCommunicationsItem(courseId?: number): SidebarItem {
        return {
            routerLink: courseId ? `${courseId}/communication` : 'communication',
            icon: faComments,
            title: 'Communication',
            translation: 'artemisApp.courseOverview.menu.communication',
            hidden: false,
        };
    }

    getTutorialGroupsItem(courseId?: number): SidebarItem {
        return {
            routerLink: courseId ? `${courseId}/tutorial-groups` : 'tutorial-groups',
            icon: faUsers,
            iconColor: '#8b5cf6',
            title: 'Tutorials',
            translation: 'artemisApp.courseOverview.menu.tutorialGroups',
            hidden: false,
        };
    }

    getCompetenciesManagementItem(courseId: number): SidebarItem {
        return {
            routerLink: courseId ? `${courseId}/competency-management` : 'competency-management',
            icon: faBullseye,
            iconColor: '#10b981',
            title: 'Competency Management',
            translation: 'artemisApp.courseOverview.menu.competencies',
            hidden: false,
        };
    }
    getCompetenciesItem(): SidebarItem {
        return {
            routerLink: 'competencies',
            icon: faBullseye,
            iconColor: '#10b981',
            title: 'Competencies',
            translation: 'artemisApp.courseOverview.menu.competencies',
            hidden: false,
        };
    }
    getLearningPathItem(): SidebarItem {
        return {
            routerLink: 'learning-path',
            icon: faNetworkWired,
            title: 'Learning Path',
            translation: 'artemisApp.courseOverview.menu.learningPath',
            featureToggle: FeatureToggle.LearningPaths,
            hidden: false,
        };
    }

    getLearningPathManagementItem(courseId: number): SidebarItem {
        return {
            routerLink: `${courseId}/learning-path-management`,
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

    getIrisItem(): SidebarItem {
        return {
            routerLink: 'iris',
            icon: faRobot,
            title: 'Iris',
            translation: 'artemisApp.courseOverview.menu.iris',
            hidden: false,
        };
    }

    getFaqManagementItem(courseId: number): SidebarItem {
        return {
            routerLink: `${courseId}/faqs`,
            icon: faQuestion,
            iconColor: '#ec4899',
            title: 'FAQs',
            translation: 'artemisApp.courseOverview.menu.faq',
            hidden: false,
        };
    }

    getFaqItem(): SidebarItem {
        return {
            routerLink: 'faq',
            icon: faQuestion,
            iconColor: '#ec4899',
            title: 'FAQs',
            translation: 'artemisApp.courseOverview.menu.faq',
            hidden: false,
        };
    }

    getIrisSettingsItem(courseId: number): SidebarItem {
        return {
            routerLink: `${courseId}/iris-settings`,
            icon: faRobot,
            title: 'IRIS Settings',
            translation: 'artemisApp.iris.settings.button.course.title',
            testId: 'iris-settings',
            hidden: false,
        };
    }

    getAssessmentDashboardItem(courseId: number): SidebarItem {
        return {
            routerLink: `${courseId}/assessment-dashboard`,
            icon: faUserCheck,
            title: 'Assessment Dashboard',
            translation: 'entity.action.assessmentDashboard',
            hidden: false,
        };
    }

    getScoresItem(courseId: number): SidebarItem {
        return {
            routerLink: `${courseId}/scores`,
            icon: faTable,
            title: 'Scores',
            translation: 'entity.action.scores',
            hidden: false,
        };
    }

    getBuildQueueItem(courseId: number): SidebarItem {
        return {
            routerLink: `${courseId}/build-overview`,
            icon: faList,
            title: 'Build Overview',
            translation: 'artemisApp.buildQueue.title',
            hidden: false,
        };
    }

    getLtiConfigurationItem(courseId: number): SidebarItem {
        return {
            routerLink: `${courseId}/lti-configuration`,
            icon: faPuzzlePiece,
            title: 'LTI Configuration',
            translation: 'global.menu.admin.lti',
            testId: 'lti-settings',
            hidden: false,
        };
    }
    getNotificationSettingsItem(): SidebarItem {
        return {
            routerLink: 'settings',
            icon: faCog,
            title: 'Notification Settings',
            translation: 'artemisApp.courseOverview.menu.settings',
            hidden: false,
            bottom: true,
        };
    }
    getCourseSettingsItem(courseId: number): SidebarItem {
        return {
            routerLink: `${courseId}/settings`,
            icon: faCog,
            title: 'Settings',
            translation: 'artemisApp.courseOverview.menu.settings',
            hidden: false,
            bottom: true,
            testId: 'course-settings',
        };
    }
}
