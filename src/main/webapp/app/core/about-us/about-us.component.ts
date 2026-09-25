import { CdkCopyToClipboard } from '@angular/cdk/clipboard';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faArrowUpRightFromSquare, faCheck, faCopy } from '@fortawesome/free-solid-svg-icons';
import { TumUiButtonDirective, TumUiTagComponent } from '@tumaet/ui-angular';
import { VERSION } from 'app/app.constants';
import { BUG_REPORT_URL, CITATION, CONTRIBUTORS_URL, FEATURE_REQUEST_URL, HIGHLIGHTS, ICONS, MAX_HIGHLIGHTS, MODULES, PROJECT_LINKS } from 'app/core/about-us/about-us-data';
import { AboutUsMaintainer, AboutUsModel } from 'app/core/about-us/models/about-us-model';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { StaticContentService } from 'app/foundation/service/static-content.service';

/** Pre-filled body of the mail to the installation's contact, which routes questions about course content to the instructors. */
const CONTACT_MAIL_BODY =
    'Note: Please send only support/feature requests or bug reports regarding the Artemis Platform to this address. ' +
    'Please check our public bug tracker at https://github.com/ls1intum/Artemis for known bugs.\n' +
    'For questions regarding exercises and their content, please contact your instructors.';

/**
 * The public About page. It describes this installation first (who runs it, how to get help, what is enabled) and the
 * Artemis project second, and deliberately repeats neither the landing page nor the documentation.
 */
@Component({
    selector: 'jhi-about-us',
    templateUrl: './about-us.component.html',
    styleUrl: './about-us.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [CdkCopyToClipboard, FaIconComponent, RouterLink, TranslateDirective, ArtemisTranslatePipe, TumUiButtonDirective, TumUiTagComponent],
})
export class AboutUsComponent implements OnInit {
    private readonly profileService = inject(ProfileService);
    private readonly staticContentService = inject(StaticContentService);

    protected readonly VERSION = VERSION;
    protected readonly RELEASE_NOTES_URL = `https://github.com/ls1intum/Artemis/releases/tag/${VERSION}`;
    protected readonly BUG_REPORT_URL = BUG_REPORT_URL;
    protected readonly FEATURE_REQUEST_URL = FEATURE_REQUEST_URL;
    protected readonly CONTRIBUTORS_URL = CONTRIBUTORS_URL;
    protected readonly PROJECT_LINKS = PROJECT_LINKS;
    protected readonly CITATION = CITATION;
    protected readonly ICONS = ICONS;
    protected readonly faArrowUpRightFromSquare = faArrowUpRightFromSquare;
    protected readonly faCheck = faCheck;
    protected readonly faCopy = faCopy;

    protected readonly profileInfo = signal<ProfileInfo | undefined>(undefined);
    protected readonly maintainers = signal<AboutUsMaintainer[]>([]);
    protected readonly citationCopied = signal(false);

    private readonly activeModules = computed(() => new Set(this.profileInfo()?.activeModuleFeatures ?? []));

    protected readonly enabledModules = computed(() => MODULES.filter((module) => this.activeModules().has(module.feature)));

    protected readonly highlights = computed(() => HIGHLIGHTS.filter((highlight) => !highlight.module || this.activeModules().has(highlight.module)).slice(0, MAX_HIGHLIGHTS));

    protected readonly contactMailto = computed(() => {
        const contact = this.profileInfo()?.contact;
        return contact ? `mailto:${contact}?body=${encodeURIComponent(CONTACT_MAIL_BODY)}` : undefined;
    });

    protected readonly gitCommit = computed(() => this.profileInfo()?.git?.commit?.id?.abbrev);

    /** Only a test server can run an arbitrary branch; a production installation runs a release, which the version already names. */
    protected readonly gitBranch = computed(() => (this.profileInfo()?.testServer ? this.profileInfo()?.git?.branch : undefined));

    ngOnInit(): void {
        this.profileInfo.set(this.profileService.getProfileInfo());
        this.staticContentService.getStaticJsonFromArtemisServer('about-us.json').subscribe((data: AboutUsModel) => this.maintainers.set(data?.projectManagers ?? []));
    }
}
