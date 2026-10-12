'use strict';
document.addEventListener('DOMContentLoaded', () => {
    const refresh = document.getElementById('overview-refresh');
    async function load() {
        refresh.disabled = true;
        document.getElementById('overview-error').textContent = '';
        const results = await Promise.allSettled([API.getServerHealth(), API.get('/admin/incidents')]);
        if (results[0].status === 'fulfilled') {
            const health = results[0].value;
            document.getElementById('metric-server').textContent = health.serverUp ? '정상' : '확인 필요';
            document.getElementById('metric-db').textContent = health.databaseUp ? '연결됨' : '확인 필요';
            document.getElementById('metric-response').textContent = `응답 ${health.responseTimeMs}ms`;
        } else {
            for (const id of ['metric-server', 'metric-db']) document.getElementById(id).textContent = '조회 실패';
            document.getElementById('metric-response').textContent = '새로고침하여 다시 확인해 주세요.';
        }
        if (results[1].status === 'fulfilled') {
            const page = results[1].value;
            document.getElementById('metric-approval').textContent = page.enabled ? page.items.filter(item => item.state === 'AWAITING_APPROVAL').length : '미연결';
            document.getElementById('metric-manual').textContent = page.enabled ? page.items.filter(item => ['FAILED', 'MANUAL_REQUIRED', 'EXPIRED'].includes(item.state)).length : '미연결';
            IncidentUI.list(document.getElementById('recent-incidents'), page.items.slice(0, 5), page.enabled);
        } else {
            for (const id of ['metric-approval', 'metric-manual']) document.getElementById(id).textContent = '조회 실패';
            document.getElementById('recent-incidents').textContent = '장애 대응 이력을 가져오지 못했습니다.';
        }
        const failed = results.find(result => result.status === 'rejected');
        if (failed) document.getElementById('overview-error').textContent = failed.reason.message;
        document.getElementById('overview-updated').textContent = '마지막 조회: ' + new Date().toLocaleString('ko-KR');
        refresh.disabled = false;
    }
    refresh.addEventListener('click', load);
    load();
});
