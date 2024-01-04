// import { Injectable, inject } from '@angular/core';
// import { bootstrapApplication } from '@angular/platform-browser';
// import { ActivatedRouteSnapshot, ResolveFn, RouterStateSnapshot, provideRouter } from '@angular/router';

// interface Hero {
//     name: string;
// }
// @Injectable()
// export class HeroService {
//     getHero(id: string) {
//         return { name: `Superman-${id}` };
//     }
// }

// export const heroResolver: ResolveFn<Hero> = (route: ActivatedRouteSnapshot, state: RouterStateSnapshot) => {
//     return inject(HeroService).getHero(route.paramMap.get('id')!);
// };

// bootstrapApplication(App, {
//     providers: [
//         provideRouter([
//             {
//                 path: 'detail/:id',
//                 component: HeroDetailComponent,
//                 resolve: { hero: heroResolver },
//             },
//         ]),
//     ],
// });
