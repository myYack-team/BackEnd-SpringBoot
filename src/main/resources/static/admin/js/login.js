'use strict';
(() => {
    let challenge;
    const send = document.getElementById('send-code');
    const form = document.getElementById('verify-form');
    const message = document.getElementById('login-message');
    async function post(path, body) {
        const response = await fetch('/api/admin/auth/' + path, {
            method: 'POST', credentials: 'same-origin', redirect: 'error', cache: 'no-store',
            headers: { 'Content-Type': 'application/json', 'X-Admin-Request': '1' },
            body: JSON.stringify(body || {}),
        });
        const data = await response.json();
        if (!response.ok || !data.isSuccess) throw new Error(data.message || '요청에 실패했습니다. 잠시 후 다시 시도해주세요.');
        return data.result;
    }
    send.addEventListener('click', async () => {
        send.disabled = true;
        message.textContent = '인증 메일을 보내는 중입니다…';
        try {
            const result = await post('request-code');
            challenge = result.challenge;
            form.hidden = false;
            document.getElementById('code').focus();
            send.textContent = '인증 코드 다시 보내기';
            message.textContent = '등록된 관리자 이메일을 확인해주세요.';
        } catch (error) { message.textContent = error.message; }
        finally { send.disabled = false; }
    });
    form.addEventListener('submit', async event => {
        event.preventDefault();
        const button = form.querySelector('button');
        button.disabled = true;
        try {
            await post('verify', { challenge, code: document.getElementById('code').value.trim() });
            const next = new URLSearchParams(location.search).get('next');
            location.replace(next && /^\/admin\/[a-z-]+\.html$/.test(next) && next !== '/admin/login.html' ? next : '/admin/index.html');
        } catch (error) { message.textContent = error.message; }
        finally { button.disabled = false; }
    });
})();
