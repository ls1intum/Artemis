import { inject } from '@angular/core';
import type { Routes } from '@angular/router';
import { map } from 'rxjs';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { SharingComponent } from 'app/sharing/sharing.component';
import { LOAD_SHARING_COURSES } from 'app/sharing/sharing-course';

export const routes: Routes = [
    {
        path: '',
        component: SharingComponent,
        providers: [
            {
                provide: LOAD_SHARING_COURSES,
                useFactory: () => {
                    const courses = inject(CourseManagementService);
                    return () => courses.getCourseOverview().pipe(map((response) => response.body ?? []));
                },
            },
        ],
    },
];
