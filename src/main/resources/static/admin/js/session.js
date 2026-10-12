'use strict';

// The cookie is HttpOnly. Only a short-lived CSRF value is kept in page memory.
window.AdminSession = (() => {
    let sessionPromise;
    const returnPath = () => /^\/admin\/[a-z-]+\.html$/.test(location.pathname)
        ? location.pathname : '/admin/index.html';
    const login = () => location.assign('/admin/login.html?next=' + encodeURIComponent(returnPath()));
    async function check(response) {
        if (response.status === 401) {
            sessionPromise = undefined;
            login();
            throw new Error('세션이 만료되었습니다. 다시 로그인해주세요.');
        }
        if (response.status === 403) throw new Error('이 작업의 권한 또는 요청 검증을 확인할 수 없습니다. 화면을 새로고침해주세요.');
        return response;
    }
    async function session() {
        if (!sessionPromise) sessionPromise = window.fetch('/api/admin/auth/session', {
            credentials: 'same-origin', cache: 'no-store', redirect: 'error',
        }).then(check).then(async response => {
            const body = await response.json();
            if (!response.ok || !body.isSuccess || !body.result?.csrfToken) throw new Error('관리자 세션을 확인할 수 없습니다.');
            return body.result;
        }).catch(error => { sessionPromise = undefined; throw error; });
        return sessionPromise;
    }
    async function request(input, options = {}) {
        const url = new URL(input, location.origin);
        if (url.origin !== location.origin || !url.pathname.startsWith('/api/')) throw new Error('잘못된 관리자 API 경로입니다.');
        const headers = new Headers(options.headers);
        const method = (options.method || 'GET').toUpperCase();
        if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) headers.set('X-Admin-CSRF', (await session()).csrfToken);
        return check(await window.fetch(url, { ...options, method, headers, credentials: 'same-origin', cache: 'no-store', redirect: 'error' }));
    }
    async function logout() {
        await request('/api/admin/auth/logout', { method: 'POST' });
        sessionPromise = undefined;
        login();
    }
    return { fetch: request, session, logout };
})();
