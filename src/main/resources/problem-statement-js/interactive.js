(function () {
    'use strict';

    var MODAL_ID = 'artemis-feedback-modal';
    var BACKDROP_ID = 'artemis-feedback-backdrop';
    var INIT_ATTR = 'data-artemis-interactive';

    var ICON_CHECK = '<svg width="16" height="16" viewBox="0 0 16 16" fill="none" style="vertical-align:middle">'
        + '<circle cx="8" cy="8" r="7.5" fill="#28a745"/>'
        + '<path d="M5 8l2 2 4-4" stroke="#fff" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>';
    var ICON_FAIL = '<svg width="16" height="16" viewBox="0 0 16 16" fill="none" style="vertical-align:middle">'
        + '<circle cx="8" cy="8" r="7.5" fill="#dc3545"/>'
        + '<path d="M5.5 5.5l5 5M10.5 5.5l-5 5" stroke="#fff" stroke-width="1.5" stroke-linecap="round"/></svg>';

    // Read CSS variable or return fallback
    function cssVar(name, fallback) {
        var val = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
        return val || fallback;
    }

    function init() {
        var tasks = document.querySelectorAll('.artemis-task[data-feedback]');
        for (var i = 0; i < tasks.length; i++) {
            if (tasks[i].hasAttribute(INIT_ATTR)) {
                continue;
            }
            var raw = tasks[i].getAttribute('data-feedback');
            if (!raw || raw === '[]') {
                continue;
            }
            var feedback;
            try {
                feedback = JSON.parse(raw);
            }
            catch (e) {
                continue;
            }
            if (!Array.isArray(feedback) || feedback.length === 0) {
                continue;
            }
            tasks[i].setAttribute(INIT_ATTR, 'true');
            tasks[i].setAttribute('tabindex', '0');
            tasks[i].setAttribute('role', 'button');
            tasks[i].setAttribute('aria-haspopup', 'dialog');
            tasks[i].addEventListener('click', openModal);
            tasks[i].addEventListener('keydown', function (e) {
                if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    openModal.call(this, e);
                }
            });
        }
    }

    function getResultSummary() {
        var container = document.querySelector('.artemis-problem-statement[data-result]');
        if (!container) {
            return null;
        }
        try {
            return JSON.parse(container.getAttribute('data-result'));
        }
        catch (e) {
            return null;
        }
    }

    function openModal(e) {
        closeModal();
        var raw = this.getAttribute('data-feedback');
        if (!raw) {
            return;
        }
        var feedback;
        try {
            feedback = JSON.parse(raw);
        }
        catch (err) {
            return;
        }
        if (!Array.isArray(feedback) || feedback.length === 0) {
            return;
        }

        var taskName = this.getAttribute('data-task-name') || 'Task';
        var result = getResultSummary();

        // Theme colors from CSS variables
        var bodyBg = cssVar('--body-bg', '#fff');
        var bodyColor = cssVar('--body-color', '#212529');
        var borderColor = cssVar('--border-color', '#dee2e6');
        var secondaryColor = cssVar('--secondary', '#6c757d');
        var successColor = cssVar('--success', '#28a745');
        var dangerColor = cssVar('--danger', '#dc3545');

        // Detect dark mode: if body background is dark, adjust feedback colors
        var isDark = isDarkBackground(bodyBg);
        var passedGroupBg = isDark ? 'rgba(40,167,69,0.15)' : '#d4edda';
        var passedGroupColor = isDark ? '#81c784' : '#155724';
        var failedGroupBg = isDark ? 'rgba(220,53,69,0.15)' : '#f8d7da';
        var failedGroupColor = isDark ? '#e57373' : '#721c24';
        var passedItemBg = isDark ? 'rgba(40,167,69,0.08)' : '#f0faf2';
        var passedItemBorder = isDark ? 'rgba(40,167,69,0.25)' : '#c3e6cb';
        var failedItemBg = isDark ? 'rgba(220,53,69,0.08)' : '#fef2f2';
        var failedItemBorder = isDark ? 'rgba(220,53,69,0.25)' : '#f5c6cb';
        var subtleBg = isDark ? 'rgba(255,255,255,0.05)' : '#f8f9fa';
        var subtleBorder = isDark ? 'rgba(255,255,255,0.1)' : '#e9ecef';
        var barTrackBg = isDark ? 'rgba(255,255,255,0.1)' : '#e9ecef';

        // Separate into passed / failed
        var passed = [];
        var failed = [];
        for (var i = 0; i < feedback.length; i++) {
            if (feedback[i].passed) {
                passed.push(feedback[i]);
            }
            else {
                failed.push(feedback[i]);
            }
        }

        // Backdrop
        var backdrop = document.createElement('div');
        backdrop.id = BACKDROP_ID;
        setStyles(backdrop, {
            position: 'fixed', top: '0', left: '0', width: '100%', height: '100%',
            background: 'rgba(0,0,0,0.5)', zIndex: '10000'
        });
        backdrop.addEventListener('click', closeModal);

        // Modal
        var modal = document.createElement('div');
        modal.id = MODAL_ID;
        modal.setAttribute('role', 'dialog');
        modal.setAttribute('aria-label', 'Feedback for task: ' + taskName);
        setStyles(modal, {
            position: 'fixed', top: '50%', left: '50%', transform: 'translate(-50%,-50%)',
            zIndex: '10001', background: bodyBg, color: bodyColor, borderRadius: '8px', width: '90%',
            maxWidth: '560px', maxHeight: '80vh', display: 'flex', flexDirection: 'column',
            boxShadow: '0 8px 32px rgba(0,0,0,.25)', overflow: 'hidden'
        });

        // Header
        var header = document.createElement('div');
        setStyles(header, {
            background: '#353d47', color: '#fff', padding: '16px 20px',
            display: 'flex', justifyContent: 'space-between', alignItems: 'center',
            flexShrink: '0'
        });
        var title = document.createElement('h3');
        title.textContent = 'Feedback for task: ' + taskName;
        setStyles(title, { margin: '0', fontSize: '16px', fontWeight: '600' });
        header.appendChild(title);
        var closeBtn = document.createElement('button');
        closeBtn.textContent = '\u2715';
        closeBtn.setAttribute('aria-label', 'Close');
        setStyles(closeBtn, {
            background: 'none', border: 'none', color: '#fff', fontSize: '20px',
            cursor: 'pointer', padding: '0 0 0 12px', lineHeight: '1'
        });
        closeBtn.addEventListener('click', closeModal);
        header.appendChild(closeBtn);
        modal.appendChild(header);

        // Body (scrollable)
        var body = document.createElement('div');
        setStyles(body, { padding: '20px', overflowY: 'auto', flex: '1' });

        // Score section
        if (result && result.score != null) {
            var scoreDiv = document.createElement('div');
            setStyles(scoreDiv, { marginBottom: '16px' });
            var scoreText = 'Score: ' + Math.round(result.score * 10) / 10 + '%';
            if (result.maxPoints) {
                var points = Math.round(result.score * result.maxPoints / 100 * 10) / 10;
                scoreText += ' \u00B7 ' + points + ' of ' + result.maxPoints + ' points';
            }
            var scoreH4 = document.createElement('h4');
            scoreH4.textContent = scoreText;
            setStyles(scoreH4, { margin: '0 0 4px', fontSize: '15px', fontWeight: '600' });
            scoreDiv.appendChild(scoreH4);

            // Submission info
            var metaParts = [];
            if (result.submissionDate) {
                metaParts.push('Submitted ' + formatDate(result.submissionDate));
            }
            if (result.commitHash) {
                metaParts.push('Commit ' + result.commitHash.substring(0, 8));
            }
            if (metaParts.length > 0) {
                var metaP = document.createElement('p');
                metaP.textContent = metaParts.join(' \u00B7 ');
                setStyles(metaP, { margin: '0', fontSize: '13px', color: secondaryColor });
                scoreDiv.appendChild(metaP);
            }
            body.appendChild(scoreDiv);

            var hr = document.createElement('hr');
            setStyles(hr, { border: 'none', borderTop: '1px solid ' + borderColor, margin: '0 0 16px' });
            body.appendChild(hr);
        }

        // Score bar
        if (result && result.score != null && result.maxPoints) {
            var barContainer = document.createElement('div');
            setStyles(barContainer, {
                height: '8px', background: barTrackBg, borderRadius: '4px',
                overflow: 'hidden', marginBottom: '16px'
            });
            var barFill = document.createElement('div');
            var pct = Math.min(100, Math.max(0, result.score));
            setStyles(barFill, {
                height: '100%', width: pct + '%', borderRadius: '4px',
                background: pct >= 50 ? successColor : dangerColor
            });
            barContainer.appendChild(barFill);
            body.appendChild(barContainer);
        }

        // Feedback groups
        if (failed.length > 0) {
            body.appendChild(buildGroup('Failed Tests', failed, false, failedGroupBg, failedGroupColor, failedItemBg, failedItemBorder, subtleBg, subtleBorder, bodyColor));
        }
        if (passed.length > 0) {
            body.appendChild(buildGroup('Passed Tests', passed, true, passedGroupBg, passedGroupColor, passedItemBg, passedItemBorder, subtleBg, subtleBorder, bodyColor));
        }

        modal.appendChild(body);

        // Footer
        var footer = document.createElement('div');
        setStyles(footer, {
            padding: '12px 20px', borderTop: '1px solid ' + borderColor,
            display: 'flex', justifyContent: 'flex-end', flexShrink: '0'
        });
        var footerBtn = document.createElement('button');
        footerBtn.textContent = 'Close';
        setStyles(footerBtn, {
            padding: '6px 20px', border: '1px solid ' + secondaryColor, borderRadius: '4px',
            background: bodyBg, cursor: 'pointer', fontSize: '14px', color: bodyColor
        });
        footerBtn.addEventListener('click', closeModal);
        footer.appendChild(footerBtn);
        modal.appendChild(footer);

        document.body.appendChild(backdrop);
        document.body.appendChild(modal);
        document.addEventListener('keydown', onEscape);

        // Focus the close button for keyboard accessibility
        closeBtn.focus();
    }

    function buildGroup(label, items, isPassed, groupBg, groupColor, itemBg, itemBorder, subtleBg, subtleBorder, textColor) {
        var wrapper = document.createElement('div');
        setStyles(wrapper, { marginBottom: '12px' });

        // Group header
        var groupHeader = document.createElement('div');
        setStyles(groupHeader, {
            padding: '10px 14px', borderRadius: '4px', cursor: 'pointer',
            display: 'flex', justifyContent: 'space-between', alignItems: 'center',
            userSelect: 'none',
            background: groupBg,
            color: groupColor
        });
        var headerLeft = document.createElement('div');
        var arrow = document.createElement('span');
        arrow.textContent = '\u25BC ';
        setStyles(arrow, { fontSize: '11px', marginRight: '6px' });
        headerLeft.appendChild(arrow);
        var headerText = document.createElement('strong');
        headerText.textContent = label + ' (' + items.length + ')';
        headerLeft.appendChild(headerText);
        groupHeader.appendChild(headerLeft);

        // Total credits for group
        var totalCredits = 0;
        var hasCredits = false;
        for (var i = 0; i < items.length; i++) {
            if (items[i].credits != null) {
                totalCredits += items[i].credits;
                hasCredits = true;
            }
        }
        if (hasCredits) {
            var creditsSpan = document.createElement('strong');
            creditsSpan.textContent = (totalCredits >= 0 ? '+' : '') + roundTo(totalCredits, 1) + 'P';
            groupHeader.appendChild(creditsSpan);
        }

        wrapper.appendChild(groupHeader);

        // Group body (items)
        var groupBody = document.createElement('div');
        setStyles(groupBody, { padding: '0 8px' });

        for (var j = 0; j < items.length; j++) {
            groupBody.appendChild(buildFeedbackItem(items[j], isPassed, itemBg, itemBorder, subtleBg, subtleBorder, textColor));
        }

        wrapper.appendChild(groupBody);

        // Toggle collapse
        var expanded = true;
        groupHeader.addEventListener('click', function () {
            expanded = !expanded;
            groupBody.style.display = expanded ? 'block' : 'none';
            arrow.textContent = expanded ? '\u25BC ' : '\u25B6 ';
        });

        return wrapper;
    }

    function buildFeedbackItem(item, isPassed, itemBg, itemBorder, subtleBg, subtleBorder, textColor) {
        var itemDiv = document.createElement('div');
        setStyles(itemDiv, {
            padding: '10px 12px', margin: '6px 0', borderRadius: '4px',
            border: '1px solid ' + itemBorder,
            background: itemBg
        });

        // Header row: icon + name + credits
        var headerRow = document.createElement('div');
        setStyles(headerRow, {
            display: 'flex', justifyContent: 'space-between', alignItems: 'center'
        });

        var nameSpan = document.createElement('div');
        var icon = document.createElement('span');
        icon.innerHTML = isPassed ? ICON_CHECK : ICON_FAIL;
        setStyles(icon, { marginRight: '6px', display: 'inline-flex', alignItems: 'center' });
        nameSpan.appendChild(icon);
        var nameText = document.createElement('strong');
        nameText.textContent = item.name;
        setStyles(nameText, { fontSize: '14px', color: textColor });
        nameSpan.appendChild(nameText);
        headerRow.appendChild(nameSpan);

        if (item.credits != null) {
            var creditsBadge = document.createElement('span');
            creditsBadge.textContent = (item.credits >= 0 ? '+' : '') + roundTo(item.credits, 1) + 'P';
            setStyles(creditsBadge, { fontWeight: '700', fontSize: '13px', flexShrink: '0' });
            headerRow.appendChild(creditsBadge);
        }

        itemDiv.appendChild(headerRow);

        // Detail message (always visible)
        if (item.message) {
            var msgContent = document.createElement('pre');
            msgContent.textContent = item.message;
            setStyles(msgContent, {
                fontSize: '12px', color: textColor,
                background: subtleBg, border: '1px solid ' + subtleBorder, borderRadius: '4px',
                padding: '8px', margin: '6px 0 0', whiteSpace: 'pre-wrap',
                overflowWrap: 'break-word', maxHeight: '200px', overflowY: 'auto'
            });
            itemDiv.appendChild(msgContent);
        }

        return itemDiv;
    }

    function closeModal() {
        var modal = document.getElementById(MODAL_ID);
        if (modal) {
            modal.parentNode.removeChild(modal);
        }
        var backdrop = document.getElementById(BACKDROP_ID);
        if (backdrop) {
            backdrop.parentNode.removeChild(backdrop);
        }
        document.removeEventListener('keydown', onEscape);
    }

    function onEscape(e) {
        if (e.key === 'Escape') {
            closeModal();
        }
    }

    function isDarkBackground(bgColor) {
        // Simple heuristic: parse hex color and check luminance
        var hex = bgColor.replace('#', '');
        if (hex.length === 3) {
            hex = hex[0] + hex[0] + hex[1] + hex[1] + hex[2] + hex[2];
        }
        if (hex.length !== 6) {
            return false;
        }
        var r = parseInt(hex.substring(0, 2), 16);
        var g = parseInt(hex.substring(2, 4), 16);
        var b = parseInt(hex.substring(4, 6), 16);
        var luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
        return luminance < 0.5;
    }

    function formatDate(isoString) {
        try {
            var d = new Date(isoString);
            return d.toLocaleString();
        }
        catch (e) {
            return isoString;
        }
    }

    function roundTo(val, decimals) {
        var factor = Math.pow(10, decimals);
        return Math.round(val * factor) / factor;
    }

    function setStyles(el, styles) {
        for (var prop in styles) {
            if (styles.hasOwnProperty(prop)) {
                el.style[prop] = styles[prop];
            }
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    }
    else {
        init();
    }
})();
