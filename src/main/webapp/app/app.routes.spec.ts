import { Route } from '@angular/router';
import routes from 'app/app.routes';

describe('app.routes', () => {
    function findRoute(path: string): Route | undefined {
        return routes.find((route) => route.path === path);
    }

    describe('admin route layout', () => {
        it('must NOT use the global module-background wrapper', () => {
            // Regression guard (#admin sidebar/layout): the AdminContainerComponent is a self-contained layout that
            // renders its own module-bg sidebar and content cards on the plain page background (like the course
            // layouts). Wrapping it in the global `module-bg m-3 p-3` card again makes the sidebar blend into the
            // wrapper (invisible) and adds excessive left/right margin. It must stay disabled for the admin section.
            const adminRoute = findRoute('admin');
            expect(adminRoute).toBeDefined();
            expect(adminRoute?.data?.['usesModuleBackground']).toBe(false);
        });
    });
});
