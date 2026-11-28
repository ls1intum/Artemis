import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';

export interface SharingCourse {
    id?: number;
    title?: string;
    shortName?: string;
    color?: string;
}

export const LOAD_SHARING_COURSES = new InjectionToken<() => Observable<SharingCourse[]>>('Load courses for sharing');
