import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-landing-footer',
    standalone: true,
    imports: [TranslateDirective, RouterLink],
    template: `
        <footer class="landing-footer">
            <div class="footer-links">
                <a routerLink="/about" class="footer-link" jhiTranslate="aboutUs"></a>
                <a href="https://github.com/ls1intum/Artemis/issues/new/choose" target="_blank" rel="noopener noreferrer" class="footer-link" jhiTranslate="feedback"></a>
                <a href="https://github.com/ls1intum/Artemis/releases" target="_blank" rel="noopener noreferrer" class="footer-link" jhiTranslate="releases"></a>
                <a routerLink="/privacy" class="footer-link" jhiTranslate="artemisApp.legal.privacy"></a>
                <a routerLink="/imprint" class="footer-link" jhiTranslate="artemisApp.legal.imprint.title"></a>
            </div>
            <p class="copyright">
                &copy; {{ currentYear }}
                <span jhiTranslate="landing.footer.copyright"></span>
            </p>
        </footer>
    `,
    styles: `
        .landing-footer {
            margin: 5rem 0;
            text-align: center;
        }

        .footer-links {
            display: flex;
            justify-content: center;
            flex-wrap: wrap;
            gap: 1.5rem;
            margin-bottom: 1.5rem;
        }

        .footer-link {
            font-size: 0.875rem;
            color: #64748b;
            text-decoration: none;

            &:hover {
                color: #e2e8f0;
            }
        }

        .copyright {
            font-size: 0.875rem;
            line-height: 1.25rem;
            color: #64748b;
            margin: 0;
        }
    `,
})
export class LandingFooterComponent {
    currentYear = new Date().getFullYear();
}
