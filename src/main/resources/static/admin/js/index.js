/**
 * 관리자 대시보드 - 서버 상태 모니터링
 */
document.addEventListener('DOMContentLoaded', () => {
    initHealthCheck();
});

/**
 * 헬스체크 초기화
 */
function initHealthCheck() {
    const refreshBtn = document.getElementById('healthRefreshBtn');
    if (refreshBtn) {
        refreshBtn.addEventListener('click', loadServerHealth);
    }
}

/**
 * 서버 상태 조회
 */
async function loadServerHealth() {
    const refreshBtn = document.getElementById('healthRefreshBtn');

    // 로딩 상태 표시
    setLoadingState(true);
    refreshBtn.classList.add('loading');
    refreshBtn.disabled = true;

    try {
        const health = await API.getServerHealth();
        updateHealthUI(health);
    } catch (error) {
        console.error('헬스체크 실패:', error);
        showHealthError(error.message);
    } finally {
        refreshBtn.classList.remove('loading');
        refreshBtn.disabled = false;
    }
}

/**
 * 로딩 상태 표시
 */
function setLoadingState(loading) {
    const indicators = document.querySelectorAll('.status-indicator');
    indicators.forEach(el => {
        if (loading) {
            el.classList.remove('up', 'down');
            el.classList.add('loading');
        } else {
            el.classList.remove('loading');
        }
    });
}

/**
 * 헬스 상태 UI 업데이트
 */
function updateHealthUI(health) {
    // 연결 상태 표시
    updateStatusIndicator('serverStatus', health.serverUp, '정상', '오프라인');
    updateStatusIndicator('dbStatus', health.databaseUp, '연결됨', '연결 끊김');
    updateStatusIndicator('storageStatus', health.storageUp, health.storageProvider, '오류');

    // 응답 시간
    document.getElementById('responseTime').textContent = `${health.responseTimeMs}ms`;

    // 메모리 사용량
    const memoryPercent = Math.round((health.heapUsedMb / health.heapMaxMb) * 100);
    document.getElementById('memoryValue').textContent = `${health.heapUsedMb}MB / ${health.heapMaxMb}MB`;
    const memoryFill = document.getElementById('memoryFill');
    memoryFill.style.width = `${memoryPercent}%`;
    memoryFill.className = 'resource-fill ' + getResourceClass(memoryPercent);

    // CPU 사용량
    const cpuPercent = health.cpuUsage >= 0 ? Math.round(health.cpuUsage) : 0;
    document.getElementById('cpuValue').textContent = health.cpuUsage >= 0 ? `${cpuPercent}%` : 'N/A';
    const cpuFill = document.getElementById('cpuFill');
    cpuFill.style.width = `${cpuPercent}%`;
    cpuFill.className = 'resource-fill ' + getResourceClass(cpuPercent);

    // 배포 정보
    document.getElementById('appVersion').textContent = health.appVersion || 'unknown';
    document.getElementById('buildTime').textContent = formatBuildTime(health.buildTime);

    // 마지막 확인 시간
    document.getElementById('lastChecked').textContent = formatDateTime(health.checkedAt);
}

/**
 * 상태 인디케이터 업데이트
 */
function updateStatusIndicator(id, isUp, upText, downText) {
    const indicator = document.getElementById(id);
    const valueEl = indicator.parentElement.querySelector('.status-value');

    indicator.classList.remove('loading', 'up', 'down');
    indicator.classList.add(isUp ? 'up' : 'down');
    valueEl.textContent = isUp ? upText : downText;
}

/**
 * 리소스 사용량에 따른 색상 클래스
 */
function getResourceClass(percent) {
    if (percent >= 90) return 'danger';
    if (percent >= 70) return 'warning';
    return 'normal';
}

/**
 * 빌드 시간 포맷팅
 */
function formatBuildTime(buildTime) {
    if (!buildTime || buildTime === 'unknown') return 'unknown';
    try {
        const date = new Date(buildTime);
        return date.toLocaleString('ko-KR');
    } catch {
        return buildTime;
    }
}

/**
 * 날짜시간 포맷팅
 */
function formatDateTime(dateTime) {
    if (!dateTime) return '-';
    try {
        const date = new Date(dateTime);
        return date.toLocaleString('ko-KR');
    } catch {
        return dateTime;
    }
}

/**
 * 에러 표시
 */
function showHealthError(message) {
    setLoadingState(false);

    const indicators = document.querySelectorAll('.status-indicator');
    indicators.forEach(el => {
        el.classList.add('down');
    });

    document.getElementById('lastChecked').textContent = `오류: ${message}`;
}
