/**
 * Interactive feedback modal for the server-rendered problem statement.
 *
 * Attached to each .artemis-task[data-feedback] inside every .artemis-problem-statement
 * container on the page. Click (or Enter/Space) on a task opens a modal showing the
 * associated test feedback; the modal is appended to document.body so it is not clipped
 * by host-page overflow / transform stacking contexts, and carries a dark class when its
 * owning container is dark so CSS variables alone handle theming.
 *
 * Assumptions:
 * - katex / KaTeX is irrelevant here; feedback is plain text.
 * - The server escapes every attribute (names, messages) before it reaches this file;
 *   this file never assigns to innerHTML.
 * - __i18n is optionally injected as a literal object before this IIFE runs; fallbacks
 *   in English are used if a key is missing.
 */
(function () {
    'use strict';

    const MODAL_ID = 'artemis-feedback-modal';
    const BACKDROP_ID = 'artemis-feedback-backdrop';
    const INIT_ATTR = 'data-artemis-interactive';
    const DARK_CONTAINER_CLASS = 'artemis-problem-statement--dark';
    const FOCUSABLE_SELECTOR = [
        'a[href]',
        'button:not([disabled])',
        'input:not([disabled])',
        'select:not([disabled])',
        'textarea:not([disabled])',
        '[tabindex]:not([tabindex="-1"])',
    ].join(',');

    const i18n = typeof __i18n !== 'undefined' ? __i18n : {};
    const t = (key, fallback) => (i18n && typeof i18n[key] === 'string' && i18n[key]) || fallback;

    /** Parsed feedback kept off the DOM so the HTML stays auditable and the JSON is parsed once. */
    const feedbackByTask = new WeakMap();

    /** Maps the open modal back to the task that opened it, so focus can be restored on close. */
    let currentOpener = null;
    let previouslyFocused = null;

    function init() {
        const tasks = document.querySelectorAll('.artemis-task[data-feedback]');
        for (const task of tasks) {
            if (task.hasAttribute(INIT_ATTR)) {
                continue;
            }
            const raw = task.getAttribute('data-feedback');
            if (!raw || raw === '[]') {
                continue;
            }
            let feedback;
            try {
                feedback = JSON.parse(raw);
            } catch (e) {
                continue;
            }
            if (!Array.isArray(feedback) || feedback.length === 0) {
                continue;
            }
            feedbackByTask.set(task, feedback);
            task.setAttribute(INIT_ATTR, 'true');
            task.setAttribute('tabindex', '0');
            task.setAttribute('role', 'button');
            task.setAttribute('aria-haspopup', 'dialog');
            task.addEventListener('click', onTaskActivate);
            task.addEventListener('keydown', (event) => {
                if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault();
                    onTaskActivate.call(task, event);
                }
            });
        }
    }

    function onTaskActivate(event) {
        const task = event && event.currentTarget ? event.currentTarget : this;
        openModal(task);
    }

    /** Finds the problem-statement container the task belongs to. Falls back to document.body. */
    function containerOf(task) {
        return task.closest('.artemis-problem-statement') || document.body;
    }

    function resultSummaryOf(container) {
        const host = container && container.classList && container.classList.contains('artemis-problem-statement') ? container : null;
        if (!host) {
            return null;
        }
        const raw = host.getAttribute('data-result');
        if (!raw) {
            return null;
        }
        try {
            return JSON.parse(raw);
        } catch (e) {
            return null;
        }
    }

    function el(tag, { cls, text, attrs, children, parent } = {}) {
        const node = document.createElement(tag);
        if (cls) {
            node.className = cls;
        }
        if (text != null) {
            node.textContent = text;
        }
        if (attrs) {
            for (const [k, v] of Object.entries(attrs)) {
                node.setAttribute(k, v);
            }
        }
        if (children) {
            for (const c of children) {
                if (c) {
                    node.appendChild(c);
                }
            }
        }
        if (parent) {
            parent.appendChild(node);
        }
        return node;
    }

    function openModal(task) {
        closeModal();

        const feedback = feedbackByTask.get(task);
        if (!feedback || feedback.length === 0) {
            return;
        }

        const taskName = task.getAttribute('data-task-name') || '';
        const container = containerOf(task);
        const result = resultSummaryOf(container);
        const isDark = container && container.classList && container.classList.contains(DARK_CONTAINER_CLASS);

        const passed = feedback.filter((item) => item.passed);
        const failed = feedback.filter((item) => !item.passed);

        const backdrop = buildBackdrop(isDark);
        const { modal, body } = buildModalShell(taskName, isDark);

        const scoreSection = buildScoreSection(result);
        if (scoreSection) {
            body.appendChild(scoreSection);
            body.appendChild(el('hr', { cls: 'artemis-modal__divider' }));
        }

        const scoreBar = buildScoreBar(result);
        if (scoreBar) {
            body.appendChild(scoreBar);
        }

        if (failed.length > 0) {
            body.appendChild(buildFeedbackGroup(t('failedTests', 'Failed Tests'), failed, 'failed'));
        }
        if (passed.length > 0) {
            body.appendChild(buildFeedbackGroup(t('passedTests', 'Passed Tests'), passed, 'passed'));
        }

        previouslyFocused = document.activeElement;
        currentOpener = task;

        document.body.appendChild(backdrop);
        document.body.appendChild(modal);
        document.addEventListener('keydown', onKeydown, true);

        const firstFocusable = modal.querySelector(FOCUSABLE_SELECTOR);
        if (firstFocusable) {
            firstFocusable.focus();
        }
    }

    function buildBackdrop(isDark) {
        const backdrop = el('div', {
            cls: isDark ? 'artemis-modal-backdrop artemis-modal-backdrop--dark' : 'artemis-modal-backdrop',
            attrs: { id: BACKDROP_ID },
        });
        backdrop.addEventListener('click', closeModal);
        return backdrop;
    }

    function buildModalShell(taskName, isDark) {
        const titleId = MODAL_ID + '-title';
        const modal = el('div', {
            cls: isDark ? 'artemis-modal artemis-modal--dark' : 'artemis-modal',
            attrs: {
                id: MODAL_ID,
                role: 'dialog',
                'aria-modal': 'true',
                'aria-labelledby': titleId,
            },
        });

        const header = el('div', { cls: 'artemis-modal__header', parent: modal });
        el('h3', {
            cls: 'artemis-modal__title',
            text: `${t('feedbackTitle', 'Feedback for task:')} ${taskName}`,
            attrs: { id: titleId },
            parent: header,
        });
        const closeBtn = el('button', {
            cls: 'artemis-modal__close',
            text: '\u2715',
            attrs: { type: 'button', 'aria-label': t('close', 'Close') },
            parent: header,
        });
        closeBtn.addEventListener('click', closeModal);

        const body = el('div', { cls: 'artemis-modal__body', parent: modal });

        const footer = el('div', { cls: 'artemis-modal__footer', parent: modal });
        const footerBtn = el('button', {
            cls: 'artemis-modal__button',
            text: t('close', 'Close'),
            attrs: { type: 'button' },
            parent: footer,
        });
        footerBtn.addEventListener('click', closeModal);

        return { modal, body };
    }

    function buildScoreSection(result) {
        if (!result || result.score == null) {
            return null;
        }
        const section = el('div', { cls: 'artemis-modal__score' });
        let scoreText = `${t('score', 'Score:')} ${roundTo(result.score, 1)}%`;
        if (result.maxPoints) {
            const points = roundTo((result.score * result.maxPoints) / 100, 1);
            scoreText += ` \u00B7 ${points} ${t('of', 'of')} ${result.maxPoints} ${t('points', 'points')}`;
        }
        el('h4', { cls: 'artemis-modal__score-title', text: scoreText, parent: section });

        const metaParts = [];
        if (result.submissionDate) {
            metaParts.push(`${t('submitted', 'Submitted')} ${formatDate(result.submissionDate)}`);
        }
        if (result.commitHash) {
            metaParts.push(`${t('commit', 'Commit')} ${String(result.commitHash).substring(0, 8)}`);
        }
        if (metaParts.length > 0) {
            el('p', {
                cls: 'artemis-modal__score-meta',
                text: metaParts.join(' \u00B7 '),
                parent: section,
            });
        }
        return section;
    }

    function buildScoreBar(result) {
        if (!result || result.score == null || !result.maxPoints) {
            return null;
        }
        const track = el('div', { cls: 'artemis-score-bar' });
        const pct = Math.min(100, Math.max(0, result.score));
        const fillClass = pct >= 50 ? 'artemis-score-bar__fill artemis-score-bar__fill--pass' : 'artemis-score-bar__fill artemis-score-bar__fill--fail';
        const fill = el('div', { cls: fillClass, parent: track });
        fill.style.width = `${pct}%`;
        return track;
    }

    function buildFeedbackGroup(label, items, kind) {
        const wrapper = el('div', { cls: `artemis-feedback-group artemis-feedback-group--${kind}` });

        const body = el('div', { cls: 'artemis-feedback-group__body' });

        const header = el('button', {
            cls: 'artemis-feedback-group__header',
            attrs: { type: 'button', 'aria-expanded': 'true' },
        });
        const arrow = el('span', { cls: 'artemis-feedback-group__arrow', text: '\u25BC' });
        const labelStrong = el('strong', { text: `${label} (${items.length})` });
        el('div', { children: [arrow, labelStrong], parent: header });

        const totalCredits = items.reduce((sum, item) => (item.credits != null ? sum + item.credits : sum), 0);
        const hasCredits = items.some((item) => item.credits != null);
        if (hasCredits) {
            el('strong', {
                text: `${totalCredits >= 0 ? '+' : ''}${roundTo(totalCredits, 1)}P`,
                parent: header,
            });
        }

        header.addEventListener('click', () => {
            const expanded = header.getAttribute('aria-expanded') !== 'false';
            const next = !expanded;
            header.setAttribute('aria-expanded', String(next));
            body.style.display = next ? 'block' : 'none';
            arrow.textContent = next ? '\u25BC' : '\u25B6';
        });

        wrapper.appendChild(header);

        for (const item of items) {
            body.appendChild(buildFeedbackItem(item, kind));
        }
        wrapper.appendChild(body);
        return wrapper;
    }

    function buildFeedbackItem(item, kind) {
        const itemDiv = el('div', { cls: `artemis-feedback-item artemis-feedback-item--${kind}` });

        const headerRow = el('div', { cls: 'artemis-feedback-item__header', parent: itemDiv });

        const iconClass = kind === 'passed' ? 'fa artemis-icon-success' : 'fa artemis-icon-fail';
        const nameWrap = el('div', { cls: 'artemis-feedback-item__name' });
        el('i', { cls: iconClass, attrs: { 'aria-hidden': 'true' }, parent: nameWrap });
        el('strong', { cls: 'artemis-feedback-item__name-text', text: item.name || '', parent: nameWrap });
        headerRow.appendChild(nameWrap);

        if (item.credits != null) {
            el('span', {
                cls: 'artemis-feedback-item__credits',
                text: `${item.credits >= 0 ? '+' : ''}${roundTo(item.credits, 1)}P`,
                parent: headerRow,
            });
        }

        if (item.message) {
            el('pre', {
                cls: 'artemis-feedback-item__message',
                text: item.message,
                parent: itemDiv,
            });
        }

        return itemDiv;
    }

    function closeModal() {
        const modal = document.getElementById(MODAL_ID);
        if (modal) {
            modal.parentNode.removeChild(modal);
        }
        const backdrop = document.getElementById(BACKDROP_ID);
        if (backdrop) {
            backdrop.parentNode.removeChild(backdrop);
        }
        document.removeEventListener('keydown', onKeydown, true);
        if (previouslyFocused && typeof previouslyFocused.focus === 'function') {
            previouslyFocused.focus();
        }
        previouslyFocused = null;
        currentOpener = null;
    }

    function onKeydown(event) {
        const modal = document.getElementById(MODAL_ID);
        if (!modal) {
            return;
        }
        if (event.key === 'Escape') {
            event.preventDefault();
            closeModal();
            return;
        }
        if (event.key !== 'Tab') {
            return;
        }
        const focusables = Array.from(modal.querySelectorAll(FOCUSABLE_SELECTOR));
        if (focusables.length === 0) {
            return;
        }
        const first = focusables[0];
        const last = focusables[focusables.length - 1];
        if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first.focus();
        }
    }

    function formatDate(isoString) {
        try {
            const d = new Date(isoString);
            return d.toLocaleString();
        } catch (e) {
            return isoString;
        }
    }

    function roundTo(val, decimals) {
        const factor = Math.pow(10, decimals);
        return Math.round(val * factor) / factor;
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
