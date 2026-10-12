'use strict';
(() => {
    const header = document.querySelector('.header');
    if (!header) return;
    document.body.classList.add('console-layout');
    header.innerHTML = `<div class="console-brand"><button type="button" id="nav-toggle" aria-label="메뉴 열기" aria-controls="console-nav" aria-expanded="false">☰</button><a href="index.html"><img src="images/logo.png" alt="마이약"><span>운영 콘솔</span></a></div><div class="console-account"><span>관리자</span><button id="console-logout" type="button">로그아웃</button></div>`;
    const sidebar = document.createElement('nav');
    sidebar.id = 'console-nav'; sidebar.className = 'console-nav'; sidebar.setAttribute('aria-label', '관리자 메뉴');
    const groups = [
        ['모니터링', [['index.html', '운영 개요', '◫'], ['incidents.html', '장애 대응', '◎'], ['server-status.html', '서버 상태', '▤'], ['client-errors.html', '앱 오류', '◇']]],
        ['서비스 관리', [['data-sync.html', '데이터 수집', '↻'], ['supplements.html', '영양제', '▧'], ['users.html', '가입자', '♧'], ['qna.html', '사용자 문의', '□']]],
        ['설정', [['settings.html', '운영 설정', '⚙']]],
    ];
    const current = location.pathname.split('/').pop() || 'index.html';
    for (const [label, links] of groups) {
        const title = document.createElement('p'); title.className = 'nav-group-title'; title.textContent = label; sidebar.append(title);
        for (const [href, text, symbol] of links) {
            const link = document.createElement('a'); link.href = href;
            const icon = document.createElement('span'); icon.className = 'nav-symbol'; icon.setAttribute('aria-hidden', 'true'); icon.textContent = symbol;
            link.append(icon, document.createTextNode(text));
            if (href === current) { link.className = 'active'; link.setAttribute('aria-current', 'page'); }
            if (href === 'qna.html') { const badge = document.createElement('span'); badge.id = 'nav-qna-badge'; badge.className = 'nav-badge hidden'; link.append(badge); }
            sidebar.append(link);
        }
    }
    header.after(sidebar);
    const toggle = document.getElementById('nav-toggle');
    const close = () => { document.body.classList.remove('nav-open'); toggle.setAttribute('aria-expanded', 'false'); };
    toggle.addEventListener('click', () => { const open = document.body.classList.toggle('nav-open'); toggle.setAttribute('aria-expanded', String(open)); });
    document.addEventListener('keydown', event => { if (event.key === 'Escape' && document.body.classList.contains('nav-open')) { close(); toggle.focus(); } });
    document.addEventListener('click', event => { if (!sidebar.contains(event.target) && !toggle.contains(event.target)) close(); });
    document.getElementById('console-logout').addEventListener('click', async event => {
        event.currentTarget.disabled = true;
        try { await AdminSession.logout(); } catch (error) { event.target.disabled = false; window.alert(error.message); }
    });
})();
