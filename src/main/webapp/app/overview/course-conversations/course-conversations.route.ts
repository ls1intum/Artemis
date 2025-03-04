import { Routes } from '@angular/router';

const routes: Routes = [
    {
        path: '',
        pathMatch: 'full',
        data: {
            pageTitle: 'artemisApp.conversationsLayout.tabTitle',
        },
        loadComponent: () => import('app/overview/course-conversations/course-conversations.component').then((m) => m.CourseConversationsComponent),
    },
];
export { routes };
