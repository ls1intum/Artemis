import { type ReactNode, useCallback, useEffect, useRef, useState } from 'react';
import Layout from '@theme/Layout';

import { PlatformId } from '../../components/LmsComparison/data/types';
import { defaultSelections, featureCategories, highlightCards } from '../../components/LmsComparison/data/comparisonData';
import HeroSection from '../../components/LmsComparison/HeroSection';
import HighlightCards from '../../components/LmsComparison/HighlightCards';
import PlatformSelector from '../../components/LmsComparison/PlatformSelector';
import ComparisonTable from '../../components/LmsComparison/ComparisonTable';
import styles from './compare.module.css';

export default function ComparePage(): ReactNode {
    const [selected, setSelected] = useState<[PlatformId, PlatformId]>(defaultSelections);
    const sentinelRef = useRef<HTMLDivElement>(null);
    const [isSticky, setIsSticky] = useState(false);

    useEffect(() => {
        const sentinel = sentinelRef.current;
        if (!sentinel) {
            return;
        }

        const observer = new IntersectionObserver(([entry]) => setIsSticky(!entry.isIntersecting), {
            threshold: 0,
            rootMargin: '-1px 0px 0px 0px',
        });
        observer.observe(sentinel);
        return () => observer.disconnect();
    }, []);

    const handlePlatformChange = useCallback(
        (next: [PlatformId, PlatformId]) => {
            if (next[0] !== next[1]) {
                setSelected(next);
            }
        },
        [setSelected],
    );

    const visiblePlatforms: [PlatformId, PlatformId, PlatformId] = [PlatformId.Artemis, selected[0], selected[1]];

    return (
        <Layout title="Compare LMS Platforms" description="Compare Artemis with other learning management systems like Canvas, Moodle, Blackboard, ILIAS, and OpenOlat.">
            <main className={styles.compareMain}>
                <HeroSection />
                <HighlightCards cards={highlightCards} />
                <div ref={sentinelRef} aria-hidden="true" style={{ height: '1px', marginTop: '-1px' }} />
                <PlatformSelector selected={selected} onChange={handlePlatformChange} isSticky={isSticky} />
                <ComparisonTable platforms={visiblePlatforms} categories={featureCategories} />
            </main>
        </Layout>
    );
}
