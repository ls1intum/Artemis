import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { PlagiarismCaseInstructorDetailViewComponent } from 'app/course/plagiarism-cases/instructor-view/detail-view/plagiarism-case-instructor-detail-view.component';
import { PlagiarismCasesInstructorViewComponent } from 'app/course/plagiarism-cases/instructor-view/plagiarism-cases-instructor-view.component';
import { ArtemisPlagiarismCasesSharedModule } from 'app/course/plagiarism-cases/shared/plagiarism-cases-shared.module';
import { plagiarismInstructorRoutes } from './plagiarism-instructor-view.route';

@NgModule({
    imports: [
        RouterModule.forChild(plagiarismInstructorRoutes),
        ArtemisPlagiarismCasesSharedModule,
        PlagiarismCasesInstructorViewComponent,
        PlagiarismCaseInstructorDetailViewComponent,
    ],
})
export class ArtemisPlagiarismCasesInstructorViewModule {}
