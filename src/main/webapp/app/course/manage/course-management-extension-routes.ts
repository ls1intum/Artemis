import { InjectionToken } from '@angular/core';
import type { Routes } from '@angular/router';

/** Additional exercise routes inside the course management layout, ahead of the exercise detail matchers. */
export const COURSE_MANAGEMENT_EXTENSION_ROUTES = new InjectionToken<Routes>('Course management extension routes');
