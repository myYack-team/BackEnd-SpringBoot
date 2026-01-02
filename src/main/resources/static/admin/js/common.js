/**
 * 공통 유틸리티 함수들
 */
const Utils = {
    /**
     * 숫자 포맷 (천 단위 콤마)
     */
    formatNumber(num) {
        if (num === null || num === undefined) return '-';
        return num.toLocaleString('ko-KR');
    },

    /**
     * 날짜 포맷 (YYYY-MM-DD)
     */
    formatDate(dateStr) {
        if (!dateStr) return '-';
        const date = new Date(dateStr);
        return date.toLocaleDateString('ko-KR');
    },

    /**
     * 날짜/시간 포맷 (YYYY-MM-DD HH:mm)
     */
    formatDateTime(dateStr) {
        if (!dateStr) return '-';
        const date = new Date(dateStr);
        return date.toLocaleString('ko-KR', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
        });
    },

    /**
     * 짧은 날짜 포맷 (MM/DD)
     */
    formatShortDate(dateStr) {
        if (!dateStr) return '-';
        const date = new Date(dateStr);
        return `${(date.getMonth() + 1).toString().padStart(2, '0')}/${date.getDate().toString().padStart(2, '0')}`;
    },

    /**
     * 퍼센트 포맷
     */
    formatPercent(value, total) {
        if (!total || total === 0) return '0%';
        return `${Math.round((value / total) * 100)}%`;
    },

    /**
     * 로딩 표시
     */
    showLoading(container) {
        container.innerHTML = `
            <div class="loading">
                <div class="spinner"></div>
                <span>로딩 중...</span>
            </div>
        `;
    },

    /**
     * 에러 표시
     */
    showError(container, message) {
        container.innerHTML = `
            <div class="alert alert-danger">
                <span>오류: ${message}</span>
            </div>
        `;
    },

    /**
     * 토스트 알림
     */
    toast(message, type = 'success') {
        const toast = document.createElement('div');
        toast.className = `alert alert-${type}`;
        toast.style.cssText = `
            position: fixed;
            top: 1rem;
            right: 1rem;
            z-index: 2000;
            min-width: 250px;
            animation: slideIn 0.3s ease;
        `;
        toast.textContent = message;
        document.body.appendChild(toast);

        setTimeout(() => {
            toast.style.animation = 'slideOut 0.3s ease';
            setTimeout(() => toast.remove(), 300);
        }, 3000);
    },

    /**
     * 확인 모달
     */
    async confirm(title, message) {
        return new Promise((resolve) => {
            const overlay = document.createElement('div');
            overlay.className = 'modal-overlay active';
            overlay.innerHTML = `
                <div class="modal">
                    <div class="modal-header">${title}</div>
                    <div class="modal-body">${message}</div>
                    <div class="modal-footer">
                        <button class="btn btn-secondary" id="modal-cancel">취소</button>
                        <button class="btn btn-danger" id="modal-confirm">확인</button>
                    </div>
                </div>
            `;
            document.body.appendChild(overlay);

            overlay.querySelector('#modal-cancel').onclick = () => {
                overlay.remove();
                resolve(false);
            };

            overlay.querySelector('#modal-confirm').onclick = () => {
                overlay.remove();
                resolve(true);
            };

            overlay.onclick = (e) => {
                if (e.target === overlay) {
                    overlay.remove();
                    resolve(false);
                }
            };
        });
    },

    /**
     * 페이지네이션 렌더링
     */
    renderPagination(container, currentPage, totalPages, onPageChange) {
        if (totalPages <= 1) {
            container.innerHTML = '';
            return;
        }

        let html = '';

        // 이전 버튼
        html += `<button class="pagination-btn" ${currentPage === 0 ? 'disabled' : ''} data-page="${currentPage - 1}">&lt;</button>`;

        // 페이지 번호
        const start = Math.max(0, currentPage - 2);
        const end = Math.min(totalPages - 1, currentPage + 2);

        if (start > 0) {
            html += `<button class="pagination-btn" data-page="0">1</button>`;
            if (start > 1) html += `<span class="pagination-info">...</span>`;
        }

        for (let i = start; i <= end; i++) {
            html += `<button class="pagination-btn ${i === currentPage ? 'active' : ''}" data-page="${i}">${i + 1}</button>`;
        }

        if (end < totalPages - 1) {
            if (end < totalPages - 2) html += `<span class="pagination-info">...</span>`;
            html += `<button class="pagination-btn" data-page="${totalPages - 1}">${totalPages}</button>`;
        }

        // 다음 버튼
        html += `<button class="pagination-btn" ${currentPage === totalPages - 1 ? 'disabled' : ''} data-page="${currentPage + 1}">&gt;</button>`;

        container.innerHTML = html;

        // 이벤트 바인딩
        container.querySelectorAll('.pagination-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                if (!btn.disabled) {
                    onPageChange(parseInt(btn.dataset.page));
                }
            });
        });
    },

    /**
     * 상태 배지 HTML
     */
    getStatusBadge(status) {
        const statusMap = {
            'PENDING': { class: 'badge-gray', text: '대기' },
            'IN_PROGRESS': { class: 'badge-primary', text: '진행중' },
            'COMPLETED': { class: 'badge-success', text: '완료' },
            'FAILED': { class: 'badge-danger', text: '실패' },
            'CANCELLED': { class: 'badge-warning', text: '취소됨' },
        };
        const badge = statusMap[status] || { class: 'badge-gray', text: status };
        return `<span class="badge ${badge.class}">${badge.text}</span>`;
    },

    /**
     * 단계 아이콘 HTML
     */
    getStageIcon(status) {
        const iconMap = {
            'PENDING': '⏳',
            'IN_PROGRESS': '🔄',
            'COMPLETED': '✅',
            'FAILED': '❌',
        };
        return iconMap[status] || '⏳';
    },
};

// CSS 애니메이션 추가
const style = document.createElement('style');
style.textContent = `
    @keyframes slideIn {
        from { transform: translateX(100%); opacity: 0; }
        to { transform: translateX(0); opacity: 1; }
    }
    @keyframes slideOut {
        from { transform: translateX(0); opacity: 1; }
        to { transform: translateX(100%); opacity: 0; }
    }
`;
document.head.appendChild(style);

// 전역에서 사용 가능하도록
window.Utils = Utils;
