'use strict';
document.addEventListener('DOMContentLoaded', () => {
    const refresh = document.getElementById('overview-refresh');
    const visitorBody = document.getElementById('visitor-body');
    const visitorChart = document.getElementById('visitor-chart');
    const visitorError = document.getElementById('visitor-error');
    const numberFormat = new Intl.NumberFormat('ko-KR');

    function resetVisitors(loading) {
        visitorBody.setAttribute('aria-busy', String(loading));
        visitorError.textContent = '';
        for (const id of ['visitor-today', 'visitor-yesterday']) {
            document.getElementById(id).textContent = loading ? '확인 중' : '—';
        }
        const status = document.createElement('p');
        status.className = 'visitor-status';
        status.setAttribute('role', 'status');
        status.textContent = loading ? '활성 계정 수를 불러오는 중입니다.' : '활성 계정 수를 가져오지 못했습니다.';
        visitorChart.replaceChildren(status);
    }

    function renderVisitors(visits) {
        const validCount = count => Number.isSafeInteger(count) && count >= 0;
        const validDate = date => typeof date === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(date);
        if (!visits || !validDate(visits.today) || visits.timeZone !== 'Asia/Seoul'
            || !validCount(visits.todayVisitors) || !validCount(visits.yesterdayVisitors)
            || !Array.isArray(visits.daily) || visits.daily.length !== 7
            || visits.daily.some(day => !day || !validDate(day.date) || !validCount(day.visitors))) {
            throw new Error('활성 계정 응답을 확인할 수 없습니다.');
        }
        const maximum = Math.max(1, ...visits.daily.map(day => day.visitors));
        const list = document.createElement('ul');
        list.className = 'visitor-bars';
        list.setAttribute('aria-label', '최근 7일 날짜별 활성 계정');
        for (const day of visits.daily) {
            const item = document.createElement('li');
            const today = day.date === visits.today;
            if (today) item.className = 'is-today';
            const count = document.createElement('span');
            count.className = 'visitor-bar-count';
            count.textContent = numberFormat.format(day.visitors) + '개';
            const track = document.createElement('div');
            track.className = 'visitor-bar-track';
            track.setAttribute('aria-hidden', 'true');
            const bar = document.createElement('span');
            bar.className = 'visitor-bar';
            bar.style.height = (day.visitors / maximum * 100) + '%';
            track.append(bar);
            const date = document.createElement('time');
            date.dateTime = day.date;
            date.textContent = day.date.slice(5).replace('-', '.');
            date.setAttribute('aria-label', day.date + (today ? ' 오늘' : ''));
            const label = document.createElement('span');
            label.className = 'visitor-day-label';
            label.textContent = today ? '오늘' : '';
            label.setAttribute('aria-hidden', 'true');
            item.append(count, track, date, label);
            list.append(item);
        }
        visitorChart.replaceChildren(list);
        document.getElementById('visitor-today').textContent = numberFormat.format(visits.todayVisitors) + '개';
        document.getElementById('visitor-yesterday').textContent = numberFormat.format(visits.yesterdayVisitors) + '개';
        visitorBody.setAttribute('aria-busy', 'false');
    }

    async function load() {
        refresh.disabled = true;
        document.getElementById('overview-error').textContent = '';
        resetVisitors(true);
        const results = await Promise.allSettled([API.getServerHealth(), API.get('/admin/incidents'), API.get('/admin/visits')]);
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
        try {
            if (results[2].status === 'rejected') throw results[2].reason;
            renderVisitors(results[2].value);
        } catch (error) {
            resetVisitors(false);
            visitorError.textContent = '활성 계정 통계 조회에 실패했습니다. 새로고침하여 다시 확인해 주세요.';
        }
        const failed = results.slice(0, 2).find(result => result.status === 'rejected');
        if (failed) document.getElementById('overview-error').textContent = failed.reason.message;
        document.getElementById('overview-updated').textContent = '마지막 조회: ' + new Date().toLocaleString('ko-KR');
        refresh.disabled = false;
    }
    refresh.addEventListener('click', load);
    load();
});
