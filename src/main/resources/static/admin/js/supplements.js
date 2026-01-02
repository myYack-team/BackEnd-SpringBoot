/**
 * 영양제 모니터링 페이지 로직
 */
const Supplements = {
    currentPage: 0,
    pageSize: 10,
    totalPages: 0,
    currentDays: null,
    currentSearch: '',

    /**
     * 초기화
     */
    async init() {
        this.bindEvents();
        await Promise.all([
            this.loadSupplements(),
            this.loadTagStats(),
        ]);
    },

    /**
     * 이벤트 바인딩
     */
    bindEvents() {
        // 기간 선택
        document.getElementById('daysFilter').addEventListener('change', (e) => {
            this.currentDays = e.target.value ? parseInt(e.target.value) : null;
            this.currentPage = 0;
            this.loadSupplements();
        });

        // 검색
        document.getElementById('searchInput').addEventListener('keyup', (e) => {
            if (e.key === 'Enter') {
                this.currentSearch = e.target.value.trim();
                this.currentPage = 0;
                this.loadSupplements();
            }
        });

        document.getElementById('searchBtn').addEventListener('click', () => {
            this.currentSearch = document.getElementById('searchInput').value.trim();
            this.currentPage = 0;
            this.loadSupplements();
        });
    },

    /**
     * 영양제 목록 로드
     */
    async loadSupplements() {
        const container = document.getElementById('supplementsTable');
        Utils.showLoading(container);

        try {
            const result = await API.getRecentSupplements({
                page: this.currentPage,
                size: this.pageSize,
                days: this.currentDays,
                search: this.currentSearch,
            });

            this.totalPages = result.totalPages;
            this.renderTable(result.supplements);
            this.renderPagination();

        } catch (error) {
            Utils.showError(container, error.message);
        }
    },

    /**
     * 테이블 렌더링
     */
    renderTable(supplements) {
        const container = document.getElementById('supplementsTable');

        if (supplements.length === 0) {
            container.innerHTML = `
                <div class="text-center text-muted" style="padding: 2rem;">
                    등록된 영양제가 없습니다.
                </div>
            `;
            return;
        }

        container.innerHTML = `
            <div class="table-container">
                <table class="table">
                    <thead>
                        <tr>
                            <th>#</th>
                            <th>영양제명</th>
                            <th>태그</th>
                            <th>선택 수</th>
                            <th>등록일</th>
                            <th>등록자</th>
                            <th>삭제</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${supplements.map((item, index) => `
                            <tr>
                                <td>${this.currentPage * this.pageSize + index + 1}</td>
                                <td><strong>${item.name}</strong></td>
                                <td><span class="badge badge-primary">${item.tagDescription}</span></td>
                                <td>${item.selectionCount}명</td>
                                <td>${Utils.formatShortDate(item.createdAt)}</td>
                                <td>${item.createdByName}</td>
                                <td>
                                    <button class="btn-icon" onclick="Supplements.deleteSupplement(${item.id}, '${item.name}', ${item.selectionCount})" title="삭제">
                                        🗑️
                                    </button>
                                </td>
                            </tr>
                        `).join('')}
                    </tbody>
                </table>
            </div>
        `;
    },

    /**
     * 페이지네이션 렌더링
     */
    renderPagination() {
        const container = document.getElementById('pagination');
        Utils.renderPagination(container, this.currentPage, this.totalPages, (page) => {
            this.currentPage = page;
            this.loadSupplements();
        });
    },

    /**
     * 태그별 통계 로드
     */
    async loadTagStats() {
        try {
            const result = await API.getSupplementTagStats();
            this.renderTagStats(result);
        } catch (error) {
            console.error('태그 통계 로드 실패:', error);
        }
    },

    /**
     * 태그별 통계 렌더링
     */
    renderTagStats(stats) {
        const container = document.getElementById('tagStats');
        const tagCounts = stats.tagCounts;
        const total = stats.totalCount;

        if (!tagCounts || Object.keys(tagCounts).length === 0) {
            container.innerHTML = '<div class="text-muted text-center">데이터 없음</div>';
            return;
        }

        // 태그별 설명 매핑
        const tagDescriptions = {
            'VITAMIN_A': '비타민 A',
            'VITAMIN_B': '비타민 B',
            'VITAMIN_C': '비타민 C',
            'VITAMIN_D': '비타민 D',
            'VITAMIN_E': '비타민 E',
            'OMEGA_3': '오메가 3',
            'MAGNESIUM': '마그네슘',
            'CALCIUM': '칼슘',
            'IRON': '철분',
            'ZINC': '아연',
            'ARGININE': '아르기닌',
            'COLLAGEN': '콜라겐',
            'PROBIOTICS': '유산균',
            'LUTEIN': '루테인',
            'COENZYME_Q10': '코엔자임Q10',
            'OTHER': '기타',
        };

        // 정렬 (많은 순)
        const sorted = Object.entries(tagCounts).sort((a, b) => b[1] - a[1]);

        const colors = ['primary', 'success', 'warning', 'danger'];

        container.innerHTML = `
            <div class="bar-chart">
                ${sorted.map(([tag, count], index) => {
                    const percent = total > 0 ? (count / total) * 100 : 0;
                    const color = colors[index % colors.length];
                    return `
                        <div class="bar-item">
                            <div class="bar-label">${tagDescriptions[tag] || tag}</div>
                            <div class="bar-track">
                                <div class="bar-fill ${color}" style="width: ${percent}%"></div>
                            </div>
                            <div class="bar-value">${count}개 (${Math.round(percent)}%)</div>
                        </div>
                    `;
                }).join('')}
            </div>
        `;
    },

    /**
     * 영양제 삭제
     */
    async deleteSupplement(id, name, selectionCount) {
        const warningMessage = selectionCount > 0
            ? `<div class="alert alert-warning" style="margin-top: 1rem;">
                   ⚠️ 이 영양제를 복용 중인 <strong>${selectionCount}명</strong>의 사용자 데이터도 함께 삭제됩니다.
               </div>`
            : '';

        const confirmed = await Utils.confirm(
            '영양제 삭제',
            `<strong>"${name}"</strong>을(를) 삭제하시겠습니까?${warningMessage}`
        );

        if (!confirmed) return;

        try {
            const result = await API.deleteSupplement(id);
            Utils.toast(
                `영양제가 삭제되었습니다. (삭제된 사용자 연결: ${result.deletedUserSupplementCount}건)`,
                'success'
            );

            // 목록 새로고침
            await this.loadSupplements();
            await this.loadTagStats();

        } catch (error) {
            Utils.toast(`삭제 실패: ${error.message}`, 'danger');
        }
    },
};

// 페이지 로드 시 초기화
document.addEventListener('DOMContentLoaded', () => Supplements.init());
