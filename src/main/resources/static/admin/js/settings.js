'use strict';
document.addEventListener('DOMContentLoaded', async () => {
    const button = document.getElementById('test-login-toggle');
    const status = document.getElementById('test-login-status');
    const error = document.getElementById('settings-error');
    let enabled;
    const render = value => { enabled = value; status.textContent = value ? '현재 활성화됨' : '현재 비활성화됨'; button.textContent = value ? '비활성화' : '활성화'; };
    try { render((await API.get('/admin/test-login/status')).enabled); button.disabled = false; }
    catch (failure) { status.textContent = '상태 조회 실패'; error.textContent = failure.message; }
    button.addEventListener('click', async () => {
        if (!window.confirm(`심사용 테스트 로그인을 ${enabled ? '비활성화' : '활성화'}할까요?`)) return;
        button.disabled = true; error.textContent = '';
        try { render((await API.post('/admin/test-login/toggle')).enabled); }
        catch (failure) { error.textContent = failure.message; }
        finally { button.disabled = false; }
    });
});
