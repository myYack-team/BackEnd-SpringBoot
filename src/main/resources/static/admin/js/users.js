/**
 * 가입자 통계 페이지 로직
 */
const Users = {
    currentPage: 0,
    pageSize: 20,
    searchKeyword: '',
    selectedUserIds: new Set(),

    /**
     * 초기화
     */
    async init() {
        this.bindEvents();
        await Promise.all([
            this.loadUserStats(),
            this.loadDailySignups(),
            this.loadUserList(),
        ]);
    },

    /**
     * 이벤트 바인딩
     */
    bindEvents() {
        // 검색 버튼
        document.getElementById('searchBtn')?.addEventListener('click', () => {
            this.searchKeyword = document.getElementById('userSearch').value.trim();
            this.currentPage = 0;
            this.loadUserList();
        });

        // 검색 입력 엔터
        document.getElementById('userSearch')?.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') {
                this.searchKeyword = document.getElementById('userSearch').value.trim();
                this.currentPage = 0;
                this.loadUserList();
            }
        });

        // 선택 삭제 버튼
        document.getElementById('deleteSelectedBtn')?.addEventListener('click', () => {
            this.deleteSelectedUsers();
        });
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

    /**
     * 사용자 목록 로드
     */
    async loadUserList() {
        const container = document.getElementById('userListContainer');
        container.innerHTML = '<div class="loading"><div class="spinner"></div><span>로딩 중...</span></div>';

        try {
            const result = await AdminAPI.getUserList(this.currentPage, this.pageSize, this.searchKeyword || null);
            this.renderUserList(result);
            this.renderPagination(result);
        } catch (error) {
            console.error('사용자 목록 로드 실패:', error);
            container.innerHTML = `<div class="text-danger text-center">로드 실패: ${error.message}</div>`;
        }
    },

    /**
     * 사용자 목록 렌더링
     */
    renderUserList(result) {
        const container = document.getElementById('userListContainer');

        if (!result.users || result.users.length === 0) {
            container.innerHTML = '<div class="text-muted text-center" style="padding: 2rem;">사용자가 없습니다.</div>';
            return;
        }

        container.innerHTML = `
            <table class="data-table" style="width: 100%; border-collapse: collapse;">
                <thead>
                    <tr style="background: var(--gray-100); text-align: left;">
                        <th style="padding: 0.75rem; width: 40px;">
                            <input type="checkbox" id="selectAllUsers" title="전체 선택">
                        </th>
                        <th style="padding: 0.75rem;">ID</th>
                        <th style="padding: 0.75rem;">이름</th>
                        <th style="padding: 0.75rem;">이메일</th>
                        <th style="padding: 0.75rem;">성별</th>
                        <th style="padding: 0.75rem;">연령대</th>
                        <th style="padding: 0.75rem;">약물</th>
                        <th style="padding: 0.75rem;">영양제</th>
                        <th style="padding: 0.75rem;">가입일</th>
                    </tr>
                </thead>
                <tbody>
                    ${result.users.map(user => this.renderUserRow(user)).join('')}
                </tbody>
            </table>
        `;

        // 전체 선택 체크박스 이벤트
        document.getElementById('selectAllUsers')?.addEventListener('change', (e) => {
            const checkboxes = container.querySelectorAll('.user-checkbox');
            checkboxes.forEach(cb => {
                cb.checked = e.target.checked;
                const userId = parseInt(cb.dataset.userId);
                if (e.target.checked) {
                    this.selectedUserIds.add(userId);
                } else {
                    this.selectedUserIds.delete(userId);
                }
            });
            this.updateSelectedCount();
        });

        // 개별 체크박스 이벤트
        container.querySelectorAll('.user-checkbox').forEach(cb => {
            cb.addEventListener('change', (e) => {
                const userId = parseInt(e.target.dataset.userId);
                if (e.target.checked) {
                    this.selectedUserIds.add(userId);
                } else {
                    this.selectedUserIds.delete(userId);
                }
                this.updateSelectedCount();
            });
        });
    },

    /**
     * 사용자 행 렌더링
     */
    renderUserRow(user) {
        const genderLabels = { 'MALE': '남성', 'FEMALE': '여성' };
        const createdAt = user.createdAt ? new Date(user.createdAt).toLocaleDateString('ko-KR') : '-';

        return `
            <tr style="border-bottom: 1px solid var(--gray-200);">
                <td style="padding: 0.75rem;">
                    <input type="checkbox" class="user-checkbox" data-user-id="${user.id}"
                           ${this.selectedUserIds.has(user.id) ? 'checked' : ''}>
                </td>
                <td style="padding: 0.75rem;">${user.id}</td>
                <td style="padding: 0.75rem;">${user.name || '-'}</td>
                <td style="padding: 0.75rem; font-size: 0.875rem;">${user.email || '-'}</td>
                <td style="padding: 0.75rem;">${genderLabels[user.gender] || '-'}</td>
                <td style="padding: 0.75rem;">${user.ageRange || '-'}</td>
                <td style="padding: 0.75rem;">${user.medicationCount}</td>
                <td style="padding: 0.75rem;">${user.supplementCount}</td>
                <td style="padding: 0.75rem; font-size: 0.875rem;">${createdAt}</td>
            </tr>
        `;
    },

    /**
     * 페이지네이션 렌더링
     */
    renderPagination(result) {
        const container = document.getElementById('userPagination');
        if (!container) return;

        if (result.totalPages <= 1) {
            container.innerHTML = '';
            return;
        }

        let html = '';

        // 이전 버튼
        html += `<button class="btn btn-secondary" ${this.currentPage === 0 ? 'disabled' : ''}
                         onclick="Users.goToPage(${this.currentPage - 1})">&lt;</button>`;

        // 페이지 번호들
        const startPage = Math.max(0, this.currentPage - 2);
        const endPage = Math.min(result.totalPages - 1, this.currentPage + 2);

        for (let i = startPage; i <= endPage; i++) {
            const isActive = i === this.currentPage;
            html += `<button class="btn ${isActive ? 'btn-primary' : 'btn-secondary'}"
                             onclick="Users.goToPage(${i})">${i + 1}</button>`;
        }

        // 다음 버튼
        html += `<button class="btn btn-secondary" ${this.currentPage >= result.totalPages - 1 ? 'disabled' : ''}
                         onclick="Users.goToPage(${this.currentPage + 1})">&gt;</button>`;

        container.innerHTML = html;
    },

    /**
     * 페이지 이동
     */
    goToPage(page) {
        this.currentPage = page;
        this.loadUserList();
    },

    /**
     * 선택 개수 업데이트
     */
    updateSelectedCount() {
        const countSpan = document.getElementById('selectedCount');
        const deleteBtn = document.getElementById('deleteSelectedBtn');
        if (countSpan) {
            countSpan.textContent = this.selectedUserIds.size;
        }
        if (deleteBtn) {
            deleteBtn.disabled = this.selectedUserIds.size === 0;
        }
    },

    /**
     * 선택된 사용자 삭제
     */
    async deleteSelectedUsers() {
        if (this.selectedUserIds.size === 0) {
            alert('선택된 사용자가 없습니다.');
            return;
        }

        const confirmed = confirm(
            `선택한 ${this.selectedUserIds.size}명의 사용자를 탈퇴 처리하시겠습니까?\n\n` +
            `⚠️ 주의: 사용자의 모든 데이터(약물, 영양제, 복용기록 등)가 삭제되며 복구할 수 없습니다.`
        );

        if (!confirmed) return;

        const deleteBtn = document.getElementById('deleteSelectedBtn');
        deleteBtn.disabled = true;
        deleteBtn.textContent = '삭제 중...';

        try {
            const userIds = Array.from(this.selectedUserIds);
            const result = await AdminAPI.batchDeleteUsers(userIds);

            alert(
                `탈퇴 처리 완료\n\n` +
                `요청: ${result.requestedCount}명\n` +
                `성공: ${result.deletedCount}명\n` +
                `실패: ${result.failedUserIds?.length || 0}명`
            );

            // 선택 초기화 및 목록 새로고침
            this.selectedUserIds.clear();
            this.updateSelectedCount();
            await Promise.all([
                this.loadUserList(),
                this.loadUserStats(),
            ]);

        } catch (error) {
            console.error('사용자 삭제 실패:', error);
            alert('삭제 실패: ' + error.message);
        } finally {
            deleteBtn.disabled = this.selectedUserIds.size === 0;
            deleteBtn.innerHTML = `선택 삭제 (<span id="selectedCount">${this.selectedUserIds.size}</span>명)`;
        }
    },
};

// 페이지 로드 시 초기화
document.addEventListener('DOMContentLoaded', () => Users.init());
