import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AboutUsComponent } from 'app/core/about-us/about-us.component';

const routes: Routes = [
    {
        path: 'about',
        children: [
            {
                path: '',
                pathMatch: 'full',
                component: AboutUsComponent,
                data: {
                    authorities: [],
                    pageTitle: 'aboutUs',
                },
            },
        ],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class AboutUsRoutingModule {}
