import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisPlagiarismModule } from 'app/exercises/shared/plagiarism/plagiarism.module';
import { ArtemisTutorParticipationGraphModule } from 'app/shared/dashboards/tutor-participation-graph/tutor-participation-graph.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { RouterModule, Routes } from '@angular/router';
import { PlagiarismCasesInstructorViewComponent } from 'app/course/plagiarism-cases/instructor-view/plagiarism-cases-instructor-view.component';
import { PlagiarismCaseInstructorDetailViewComponent } from 'app/course/plagiarism-cases/instructor-view/detail-view/plagiarism-case-instructor-detail-view.component';
import { PlagiarismCasesStudentViewComponent } from 'app/course/plagiarism-cases/student-view/plagiarism-cases-student-view.component';
import { PlagiarismCaseStudentDetailViewComponent } from 'app/course/plagiarism-cases/student-view/detail-view/plagiarism-case-student-detail-view.component';
import { PlagiarismCaseVerdictComponent } from 'app/course/plagiarism-cases/verdict/plagiarism-case-verdict.component';
import { PlagiarismCaseReviewComponent } from 'app/course/plagiarism-cases/review/plagiarism-case-review.component';
import { MetisModule } from 'app/shared/metis/metis.module';

const routes: Routes = [];

@NgModule({
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, ArtemisPlagiarismModule, ArtemisTutorParticipationGraphModule, RouterModule.forChild(routes), MetisModule],
    declarations: [
        PlagiarismCasesInstructorViewComponent,
        PlagiarismCaseInstructorDetailViewComponent,
        PlagiarismCasesStudentViewComponent,
        PlagiarismCaseStudentDetailViewComponent,
        PlagiarismCaseVerdictComponent,
        PlagiarismCaseReviewComponent,
    ],
    exports: [PlagiarismCasesInstructorViewComponent, PlagiarismCaseInstructorDetailViewComponent],
})
export class ArtemisPlagiarismCasesModule {}
