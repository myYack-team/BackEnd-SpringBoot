'use strict';
window.IncidentUI = (() => {
    const states = { QUEUED: ['대기', 'neutral'], RUNNING: ['분석 중', 'info'], PUBLISHING: ['PR 작성 중', 'info'],
        CHECKING: ['검증 중', 'info'], AWAITING_APPROVAL: ['승인 대기', 'warning'], APPROVED: ['승인 완료', 'info'],
        MERGING: ['병합 중', 'info'], DEPLOYING: ['배포 중', 'info'], RESOLVED: ['처리 완료', 'success'],
        REJECTED: ['반려', 'neutral'], FAILED: ['실패', 'danger'], MANUAL_REQUIRED: ['수동 확인', 'danger'], EXPIRED: ['승인 만료', 'neutral'] };
    const el = (tag, text, className) => { const node = document.createElement(tag); if (text != null) node.textContent = text; if (className) node.className = className; return node; };
    const time = seconds => seconds ? new Date(seconds * 1000).toLocaleString('ko-KR') : '—';
    const badge = state => { const value = states[state] || ['상태 확인', 'neutral']; return el('span', value[0], 'state-badge ' + value[1]); };
    function list(container, items, enabled = true, select) {
        container.replaceChildren();
        if (!enabled || !items.length) { container.append(el('p', enabled ? '아직 수집된 장애 대응 이력이 없습니다.' : '장애 대응 시스템이 아직 연결되지 않았습니다.', 'empty-state')); return; }
        for (const item of items) {
            const row = el(select ? 'button' : 'a', null, 'incident-row');
            if (select) { row.type = 'button'; row.addEventListener('click', () => select(item.id)); row.dataset.id = item.id; }
            else row.href = 'incidents.html?id=' + encodeURIComponent(item.id);
            const heading = el('div', null, 'incident-row-heading'); heading.append(badge(item.state), el('span', time(item.createdAt), 'muted'));
            row.append(heading, el('strong', item.exception || '서버 오류'), el('span', (item.summary || item.reason || '수집한 코드 위치를 바탕으로 분석을 준비합니다.').slice(0, 130), 'incident-summary'));
            container.append(row);
        }
    }
    function detail(container, item) {
        container.replaceChildren();
        const heading = el('div', null, 'panel-heading'); const title = el('div'); title.append(badge(item.state), el('h2', item.exception || '서버 오류')); heading.append(title); container.append(heading);
        const body = el('div', null, 'panel-body');
        const meta = el('dl', null, 'incident-meta');
        for (const [label, value] of [['사건', item.id], ['발생', time(item.createdAt)], ['최근 변경', time(item.updatedAt)], ['수집 횟수', item.occurrences],
            ['오류 발생 배포', item.releaseSha?.slice(0, 12) || '—'], ['수정 커밋', item.headSha?.slice(0, 12) || '—']]) meta.append(el('dt', label), el('dd', value));
        body.append(meta);
        for (const [title, text] of [['분석 및 대응 방안', item.summary], ['검증', item.validation], ['처리 결과', item.reason]]) {
            if (text) body.append(el('h3', title), el('p', text, 'report-text'));
        }
        const links = el('div', null, 'incident-links');
        for (const [label, address] of [['PR 보기', item.prUrl], ['분석 실행', item.runUrl], ['배포 실행', item.deploymentUrl]]) {
            if (!address) continue;
            try { const url = new URL(address); if (url.origin !== 'https://github.com' || !url.pathname.startsWith('/myYack-team/')) continue;
                const link = el('a', label + ' ↗', 'text-link'); link.href = url.href; link.target = '_blank'; link.rel = 'noopener noreferrer'; links.append(link); } catch { /* Invalid stored links are not navigable. */ }
        }
        body.append(links);
        if (item.frames?.length) { const details = el('details'); details.append(el('summary', '오류가 발생한 코드 위치'), el('pre', item.frames.join('\n'), 'incident-stack')); body.append(details); }
        body.append(el('h3', '처리 이력'));
        const history = el('ol', null, 'incident-history');
        for (const event of item.history || []) { const row = el('li'); row.append(el('time', time(event.at)), badge(event.state)); history.append(row); }
        body.append(history); container.append(body);
    }
    return { list, detail };
})();
document.addEventListener('DOMContentLoaded', () => {
    const container = document.getElementById('incident-items'); if (!container) return;
    const detail = document.getElementById('incident-detail'), error = document.getElementById('incidents-error');
    const refresh = document.getElementById('incidents-refresh'), more = document.getElementById('incidents-more');
    let items = [], cursor = null, selected = new URLSearchParams(location.search).get('id'), generation = 0;
    async function select(id, focus = true) {
        if (!/^[a-f0-9-]{36}$/.test(id || '')) return;
        selected = id; const request = ++generation;
        detail.replaceChildren(Object.assign(document.createElement('p'), { textContent: '상세 내용을 불러오는 중입니다.', className: 'empty-state' }));
        try {
            const item = await API.get('/admin/incidents/' + encodeURIComponent(id));
            if (request !== generation) return;
            IncidentUI.detail(detail, item); history.replaceState(null, '', 'incidents.html?id=' + encodeURIComponent(id));
            container.querySelectorAll('button').forEach(button => { button.classList.toggle('selected', button.dataset.id === id); button.setAttribute('aria-pressed', String(button.dataset.id === id)); });
            if (focus) {
                detail.focus({ preventScroll: true });
                if (window.matchMedia('(max-width: 1100px)').matches) detail.scrollIntoView({ behavior: 'smooth', block: 'start' });
            }
        } catch (failure) { if (request === generation) detail.textContent = failure.message; }
    }
    async function load(append = false) {
        refresh.disabled = more.disabled = true; error.textContent = '';
        try {
            const page = await API.get('/admin/incidents' + (append && cursor ? '?cursor=' + encodeURIComponent(cursor) : ''));
            items = append ? [...new Map([...items, ...page.items].map(item => [item.id, item])).values()] : page.items;
            cursor = page.nextCursor; more.hidden = !cursor;
            IncidentUI.list(container, items, page.enabled, select);
            if (selected) await select(selected, false);
        } catch (failure) { error.textContent = failure.message; if (!items.length) container.textContent = '이력을 불러오지 못했습니다. 새로고침해 주세요.'; }
        finally { refresh.disabled = more.disabled = false; }
    }
    refresh.addEventListener('click', () => load()); more.addEventListener('click', () => load(true)); load();
});
