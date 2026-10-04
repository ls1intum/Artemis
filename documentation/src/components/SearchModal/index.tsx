import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type MouseEvent, type ReactNode } from 'react';
import { useLocation } from '@docusaurus/router';
import SearchBar from '@theme/SearchBar';

import styles from './styles.module.css';

type SearchDialogState = 'closed' | 'opening' | 'open' | 'closing';
type SearchTriggerVariant = 'hero' | 'navbar';
type ShortcutModifier = '⌘' | 'Ctrl';

interface SearchModalContextValue {
    dialogState: SearchDialogState;
    openSearch: () => void;
    shortcutModifier: ShortcutModifier | undefined;
}

interface SearchModalProviderProps {
    children: ReactNode;
}

interface SearchModalTriggerProps {
    variant: SearchTriggerVariant;
}

const SEARCH_DIALOG_CLOSE_FALLBACK_MS = 300;
const SearchModalContext = createContext<SearchModalContextValue | undefined>(undefined);

function isAppleUserAgent(): boolean {
    return /Macintosh|Mac OS X|iPhone|iPad|iPod/.test(navigator.userAgent);
}

function SearchIcon(): ReactNode {
    return (
        <svg viewBox="0 0 20 20" aria-hidden="true" focusable="false">
            <circle cx="8.5" cy="8.5" r="5.5" />
            <path d="m12.5 12.5 4 4" />
        </svg>
    );
}

export function SearchModalProvider({ children }: SearchModalProviderProps): ReactNode {
    const location = useLocation();
    const locationKey = `${location.pathname}${location.search}${location.hash}`;
    const dialogRef = useRef<HTMLDialogElement>(null);
    const previousLocationRef = useRef(locationKey);
    const dialogStateRef = useRef<SearchDialogState>('closed');
    const openingFrameRef = useRef<number | undefined>(undefined);
    const closeTimerRef = useRef<number | undefined>(undefined);
    const [dialogState, setDialogState] = useState<SearchDialogState>('closed');
    const [shortcutModifier, setShortcutModifier] = useState<ShortcutModifier>();

    const updateDialogState = useCallback((state: SearchDialogState) => {
        dialogStateRef.current = state;
        setDialogState(state);
    }, []);

    const finishClose = useCallback(() => {
        const dialog = dialogRef.current;
        if (!dialog?.open || dialogStateRef.current !== 'closing') {
            return;
        }

        window.clearTimeout(closeTimerRef.current);
        dialog.close();
        updateDialogState('closed');
    }, [updateDialogState]);

    const openSearch = useCallback(() => {
        const dialog = dialogRef.current;
        if (!dialog) {
            return;
        }

        if (dialogStateRef.current === 'open' || dialogStateRef.current === 'opening') {
            dialog.querySelector<HTMLInputElement>('.navbar__search-input')?.focus();
            return;
        }

        window.clearTimeout(closeTimerRef.current);
        cancelAnimationFrame(openingFrameRef.current ?? 0);

        const searchInput = dialog.querySelector<HTMLInputElement>('.navbar__search-input');
        if (!dialog.open) {
            searchInput?.setAttribute('autofocus', '');
            dialog.showModal();
            searchInput?.removeAttribute('autofocus');
        } else {
            searchInput?.focus();
        }

        updateDialogState('opening');
        openingFrameRef.current = requestAnimationFrame(() => {
            openingFrameRef.current = requestAnimationFrame(() => updateDialogState('open'));
        });
    }, [updateDialogState]);

    const closeSearch = useCallback(() => {
        const dialog = dialogRef.current;
        if (!dialog?.open || dialogStateRef.current === 'closing') {
            return;
        }

        const shouldAnimate = dialogStateRef.current === 'open' && !window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        cancelAnimationFrame(openingFrameRef.current ?? 0);
        updateDialogState('closing');

        if (!shouldAnimate) {
            finishClose();
            return;
        }

        closeTimerRef.current = window.setTimeout(finishClose, SEARCH_DIALOG_CLOSE_FALLBACK_MS);
    }, [finishClose, updateDialogState]);

    useEffect(() => {
        setShortcutModifier(isAppleUserAgent() ? '⌘' : 'Ctrl');

        const handleShortcut = (event: KeyboardEvent) => {
            const platformModifierPressed = isAppleUserAgent() ? event.metaKey : event.ctrlKey;
            if (!platformModifierPressed || event.altKey || event.shiftKey || event.key.toLowerCase() !== 'k') {
                return;
            }

            event.preventDefault();
            event.stopImmediatePropagation();
            if (dialogStateRef.current === 'open' || dialogStateRef.current === 'opening') {
                closeSearch();
            } else {
                openSearch();
            }
        };

        document.addEventListener('keydown', handleShortcut, true);
        return () => document.removeEventListener('keydown', handleShortcut, true);
    }, [closeSearch, openSearch]);

    useEffect(() => {
        if (previousLocationRef.current !== locationKey) {
            previousLocationRef.current = locationKey;
            closeSearch();
        }
    }, [closeSearch, locationKey]);

    useEffect(
        () => () => {
            cancelAnimationFrame(openingFrameRef.current ?? 0);
            window.clearTimeout(closeTimerRef.current);
        },
        [],
    );

    const closeOnBackdrop = (event: MouseEvent<HTMLDialogElement>) => {
        if (event.target === dialogRef.current) {
            closeSearch();
        }
    };

    const dialogClassName = [styles.searchDialog, dialogState === 'open' && styles.searchDialogOpen, dialogState === 'closing' && styles.searchDialogClosing]
        .filter(Boolean)
        .join(' ');
    const contextValue = useMemo(() => ({ dialogState, openSearch, shortcutModifier }), [dialogState, openSearch, shortcutModifier]);

    return (
        <SearchModalContext.Provider value={contextValue}>
            {children}
            <dialog
                ref={dialogRef}
                id="documentation-search-dialog"
                className={dialogClassName}
                aria-label="Search documentation"
                onCancel={(event) => {
                    event.preventDefault();
                    closeSearch();
                }}
                onClose={() => {
                    const dialog = dialogRef.current;
                    if (!dialog || dialog.open) {
                        return;
                    }

                    cancelAnimationFrame(openingFrameRef.current ?? 0);
                    window.clearTimeout(closeTimerRef.current);
                    updateDialogState('closed');
                }}
                onClick={closeOnBackdrop}
            >
                <div
                    className={styles.searchDialogPanel}
                    onTransitionEnd={(event) => {
                        if (event.target === event.currentTarget && event.propertyName === 'opacity') {
                            finishClose();
                        }
                    }}
                >
                    <div className={styles.searchDialogHeader}>
                        <strong>Search documentation</strong>
                        <button type="button" onClick={closeSearch} aria-label="Close search">
                            ×
                        </button>
                    </div>
                    <div className={styles.modalSearch}>
                        <SearchBar key={locationKey} />
                    </div>
                </div>
            </dialog>
        </SearchModalContext.Provider>
    );
}

export function SearchModalTrigger({ variant }: SearchModalTriggerProps): ReactNode {
    const context = useContext(SearchModalContext);
    if (!context) {
        throw new Error('SearchModalTrigger must be rendered inside SearchModalProvider');
    }

    const { dialogState, openSearch, shortcutModifier } = context;
    const className = variant === 'hero' ? styles.heroTrigger : styles.navbarTrigger;

    return (
        <button
            type="button"
            className={className}
            onClick={openSearch}
            aria-label="Search documentation"
            aria-haspopup="dialog"
            aria-controls="documentation-search-dialog"
            aria-expanded={dialogState !== 'closed'}
        >
            <span className={styles.triggerLabel}>
                <SearchIcon />
                <span className={styles.triggerText}>Search</span>
            </span>
            {shortcutModifier && (
                <span className={styles.triggerShortcut} aria-hidden="true">
                    <kbd>{shortcutModifier}</kbd>
                    <kbd>K</kbd>
                </span>
            )}
        </button>
    );
}
