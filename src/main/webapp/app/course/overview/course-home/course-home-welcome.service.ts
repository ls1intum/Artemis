import { Service } from '@angular/core';
import { CourseHome } from './course-home.model';

@Service()
export class CourseHomeWelcomeService {
    keys(home: CourseHome): { title: string; body: string } {
        const variant = Math.max(0, Math.min(2, home.welcome.variantIndex));
        const prefix = `artemisApp.courseHome.welcome.${home.welcome.welcomeCase}.v${variant}`;
        return { title: `${prefix}.title`, body: `${prefix}.body` };
    }
}
