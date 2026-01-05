/**
 * 가입자 통계 페이지 로직
 */
const Users = {
    /**
     * 초기화
     */
    async init() {
        await Promise.all([
            this.loadUserStats(),
            this.loadDailySignups(),
        ]);
    },

    /**
     * 가입자 통계 로드
     */
    async loadUserStats() {
        try {
            const stats = await API.getUserStats();
            this.renderUserStats(stats);
            this.renderGenderChart(stats.byGender, stats.total);
            this.renderAgeChart(stats.byAgeGroup, stats.total);
            this.renderPurposeChart(stats.bySignupPurpose, stats.total);
        } catch (error) {
            console.error('가입자 통계 로드 실패:', error);
        }
    },

    /**
     * 가입자 통계 렌더링
     */
    renderUserStats(stats) {
        document.getElementById('totalUsers').textContent = Utils.formatNumber(stats.total);
        document.getElementById('monthUsers').textContent = Utils.formatNumber(stats.month);
        document.getElementById('weekUsers').textContent = Utils.formatNumber(stats.week);
        document.getElementById('todayUsers').textContent = Utils.formatNumber(stats.today);
    },

    /**
     * 성별 분포 차트 렌더링
     */
    renderGenderChart(byGender, total) {
        const container = document.getElementById('genderChart');

        const genderLabels = {
            'MALE': '남성',
            'FEMALE': '여성',
            'UNKNOWN': '미입력',
        };

        const genderColors = {
            'MALE': 'primary',
            'FEMALE': 'success',
            'UNKNOWN': 'warning',
        };

        container.innerHTML = `
            <div class="bar-chart">
                ${Object.entries(byGender).map(([gender, count]) => {
                    const percent = total > 0 ? (count / total) * 100 : 0;
                    return `
                        <div class="bar-item">
                            <div class="bar-label">${genderLabels[gender] || gender}</div>
                            <div class="bar-track">
                                <div class="bar-fill ${genderColors[gender] || 'primary'}" style="width: ${percent}%"></div>
                            </div>
                            <div class="bar-value">${Utils.formatNumber(count)}명 (${Math.round(percent)}%)</div>
                        </div>
                    `;
                }).join('')}
            </div>
        `;
    },

    /**
     * 연령대 분포 차트 렌더링
     */
    renderAgeChart(byAgeGroup, total) {
        const container = document.getElementById('ageChart');

        const ageLabels = {
            '10s': '10대',
            '20s': '20대',
            '30s': '30대',
            '40s': '40대',
            '50s': '50대',
            '60+': '60대 이상',
            'UNKNOWN': '미입력',
        };

        // 순서 보장
        const orderedKeys = ['10s', '20s', '30s', '40s', '50s', '60+', 'UNKNOWN'];

        container.innerHTML = `
            <div class="bar-chart">
                ${orderedKeys.map(key => {
                    const count = byAgeGroup[key] || 0;
                    const percent = total > 0 ? (count / total) * 100 : 0;
                    return `
                        <div class="bar-item">
                            <div class="bar-label">${ageLabels[key]}</div>
                            <div class="bar-track">
                                <div class="bar-fill primary" style="width: ${percent}%"></div>
                            </div>
                            <div class="bar-value">${Utils.formatNumber(count)}명 (${Math.round(percent)}%)</div>
                        </div>
                    `;
                }).join('')}
            </div>
        `;
    },

    /**
     * 가입목적 분포 차트 렌더링
     */
    renderPurposeChart(bySignupPurpose, total) {
        const container = document.getElementById('purposeChart');
        if (!container) return;

        const purposeLabels = {
            'SELF': '나의 약 관리',
            'CHILD': '자녀 약 관리',
            'PARENT': '부모님 약 관리',
            'AI_REPORT': 'AI 복약 분석 레포트',
        };

        const purposeColors = {
            'SELF': 'primary',
            'CHILD': 'success',
            'PARENT': 'warning',
            'AI_REPORT': 'danger',
        };

        const orderedKeys = ['SELF', 'CHILD', 'PARENT', 'AI_REPORT'];

        container.innerHTML = `
            <div class="bar-chart">
                ${orderedKeys.map(key => {
                    const count = bySignupPurpose ? (bySignupPurpose[key] || 0) : 0;
                    const percent = total > 0 ? (count / total) * 100 : 0;
                    return `
                        <div class="bar-item">
                            <div class="bar-label">${purposeLabels[key]}</div>
                            <div class="bar-track">
                                <div class="bar-fill ${purposeColors[key]}" style="width: ${percent}%"></div>
                            </div>
                            <div class="bar-value">${Utils.formatNumber(count)}명 (${Math.round(percent)}%)</div>
                        </div>
                    `;
                }).join('')}
            </div>
        `;
    },

    /**
     * 일별 가입 추이 로드
     */
    async loadDailySignups() {
        try {
            const result = await API.getDailySignups(7);
            this.renderDailyChart(result.dailyCounts);
        } catch (error) {
            console.error('일별 가입 추이 로드 실패:', error);
        }
    },

    /**
     * 일별 가입 추이 차트 렌더링
     */
    renderDailyChart(dailyCounts) {
        const container = document.getElementById('dailyChart');

        if (!dailyCounts || dailyCounts.length === 0) {
            container.innerHTML = '<div class="text-muted text-center">데이터 없음</div>';
            return;
        }

        const maxCount = Math.max(...dailyCounts.map(d => d.count), 1);
        const chartHeight = 150;

        container.innerHTML = `
            <div style="display: flex; align-items: flex-end; justify-content: space-around; height: ${chartHeight}px; padding: 0 1rem;">
                ${dailyCounts.map(day => {
                    const height = (day.count / maxCount) * (chartHeight - 30);
                    const date = new Date(day.date);
                    const dayLabel = `${date.getMonth() + 1}/${date.getDate()}`;
                    return `
                        <div style="display: flex; flex-direction: column; align-items: center; gap: 0.25rem;">
                            <div style="font-size: 0.75rem; color: var(--gray-600);">${day.count}</div>
                            <div style="width: 30px; height: ${Math.max(height, 4)}px; background: var(--primary); border-radius: 4px 4px 0 0;"></div>
                            <div style="font-size: 0.75rem; color: var(--gray-500);">${dayLabel}</div>
                        </div>
                    `;
                }).join('')}
            </div>
        `;
    },
};

// 페이지 로드 시 초기화
document.addEventListener('DOMContentLoaded', () => Users.init());
