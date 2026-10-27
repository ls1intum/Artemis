import { Location } from '@angular/common';
import { provideLocationMocks } from '@angular/common/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { refreshSidebarCard } from './sidebar-card-refresh';

describe('refreshSidebarCard', () => {
    beforeEach(() => TestBed.configureTestingModule({ providers: [provideRouter([]), provideLocationMocks()] }));

    it('reads the current card selection after the temporary navigation completes', async () => {
        const router = TestBed.inject(Router);
        const route = TestBed.inject(ActivatedRoute);
        const location = TestBed.inject(Location);
        let finishNavigation!: (value: boolean) => void;
        const pendingNavigation = new Promise<boolean>((resolve) => (finishNavigation = resolve));
        const navigate = vi.spyOn(router, 'navigate').mockReturnValueOnce(pendingNavigation).mockResolvedValue(true);
        let selection = { id: 1, selected: true };

        const refreshed = refreshSidebarCard(router, route, location, () => selection);
        expect(navigate).toHaveBeenCalledExactlyOnceWith(['../'], { skipLocationChange: true, relativeTo: route.firstChild });
        selection = { id: 2, selected: true };
        finishNavigation(true);
        await refreshed;

        expect(navigate).toHaveBeenLastCalledWith(['./2'], { relativeTo: route });
    });

    it('replaces history when opening a card that was not already selected', async () => {
        const router = TestBed.inject(Router);
        const route = TestBed.inject(ActivatedRoute);
        const location = TestBed.inject(Location);
        vi.spyOn(location, 'path').mockReturnValue('/courses/7');
        const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

        await refreshSidebarCard(router, route, location, () => ({ id: 'lectures', selected: false }));

        expect(navigate).toHaveBeenCalledTimes(2);
        expect(navigate).toHaveBeenLastCalledWith(['/courses/7', 'lectures'], { replaceUrl: true });
    });
});
