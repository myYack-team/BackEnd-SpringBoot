/**
 * Q&A 관리 페이지 스크립트
 */
(function () {
    // 상태 관리
    let currentPage = 0;
    let currentStatus = null;
    let currentQuestionId = null;
    const pageSize = 10;

    // DOM 요소
    const tableBody = document.getElementById('qna-table-body');
    const paginationContainer = document.getElementById('pagination');
    const filterButtons = document.querySelectorAll('.filter-btn');
    const detailModal = document.getElementById('detail-modal');

    // 초기화
    async function init() {
        await Promise.all([
            loadStats(),
            loadQnAList(),
        ]);
        setupEventListeners();
    }

    // 이벤트 리스너 설정
    function setupEventListeners() {
        // 필터 버튼
        filterButtons.forEach(btn => {
            btn.addEventListener('click', () => {
                filterButtons.forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                currentStatus = btn.dataset.status || null;
                currentPage = 0;
                loadQnAList();
            });
        });

        // 모달 닫기
        document.getElementById('close-modal').addEventListener('click', closeModal);
        detailModal.addEventListener('click', (e) => {
            if (e.target === detailModal) closeModal();
        });

        // 답변 등록
        document.getElementById('submit-reply').addEventListener('click', submitReply);

        // 상태 변경
        document.getElementById('status-select').addEventListener('change', async (e) => {
            if (currentQuestionId) {
                await updateStatus(currentQuestionId, e.target.value);
            }
        });
    }

    // 통계 로드
    async function loadStats() {
        try {
            const stats = await AdminAPI.getQnAStats();
            document.getElementById('stat-total').textContent = Utils.formatNumber(stats.totalCount);
            document.getElementById('stat-pending').textContent = Utils.formatNumber(stats.pendingCount);
            document.getElementById('stat-answered').textContent = Utils.formatNumber(stats.answeredCount);
            document.getElementById('stat-closed').textContent = Utils.formatNumber(stats.closedCount);

            // 네비게이션 배지 업데이트
            updateNavBadge(stats.pendingCount);
        } catch (error) {
            console.error('통계 로드 실패:', error);
        }
    }

    // 네비게이션 배지 업데이트
    function updateNavBadge(count) {
        const badge = document.getElementById('nav-qna-badge');
        if (badge) {
            if (count > 0) {
                badge.textContent = count > 99 ? '99+' : count;
                badge.classList.remove('hidden');
            } else {
                badge.classList.add('hidden');
            }
        }
    }

    // Q&A 목록 로드
    async function loadQnAList() {
        tableBody.innerHTML = `
            <tr>
                <td colspan="6" class="text-center">
                    <div class="loading">
                        <div class="spinner"></div>
                        <span>로딩 중...</span>
                    </div>
                </td>
            </tr>
        `;

        try {
            const result = await AdminAPI.getQnAList(currentPage, pageSize, currentStatus);
            renderQnAList(result);
            Utils.renderPagination(paginationContainer, currentPage, result.totalPages, (page) => {
                currentPage = page;
                loadQnAList();
            });
        } catch (error) {
            console.error('Q&A 목록 로드 실패:', error);
            tableBody.innerHTML = `
                <tr>
                    <td colspan="6" class="text-center">
                        <div class="alert alert-danger">목록을 불러오는데 실패했습니다.</div>
                    </td>
                </tr>
            `;
        }
    }

    // Q&A 목록 렌더링
    function renderQnAList(result) {
        if (!result.questions || result.questions.length === 0) {
            tableBody.innerHTML = `
                <tr>
                    <td colspan="6">
                        <div class="empty-state">
                            <div class="empty-state-icon">💬</div>
                            <div>문의가 없습니다.</div>
                        </div>
                    </td>
                </tr>
            `;
            return;
        }

        tableBody.innerHTML = result.questions.map(q => `
            <tr class="clickable-row" data-id="${q.id}">
                <td>${q.id}</td>
                <td>${escapeHtml(q.title)}</td>
                <td>
                    <div>${escapeHtml(q.userName || '-')}</div>
                    <div class="text-small text-muted">${escapeHtml(q.userEmail || '')}</div>
                </td>
                <td><span class="status-badge ${q.status.toLowerCase()}">${q.statusLabel}</span></td>
                <td>
                    ${q.replyCount > 0 ? `<span class="text-muted">${q.replyCount}개</span>` : '-'}
                    ${q.hasAdminReply ? `<span class="admin-reply-badge">관리자 답변</span>` : ''}
                </td>
                <td>${Utils.formatDateTime(q.createdAt)}</td>
            </tr>
        `).join('');

        // 행 클릭 이벤트
        tableBody.querySelectorAll('.clickable-row').forEach(row => {
            row.addEventListener('click', () => {
                openDetail(parseInt(row.dataset.id));
            });
        });
    }

    // 상세 모달 열기
    async function openDetail(questionId) {
        currentQuestionId = questionId;
        detailModal.classList.add('active');

        try {
            const detail = await AdminAPI.getQnADetail(questionId);
            renderDetail(detail);
        } catch (error) {
            console.error('상세 조회 실패:', error);
            Utils.toast('상세 조회에 실패했습니다.', 'danger');
        }
    }

    // 상세 내용 렌더링
    function renderDetail(detail) {
        document.getElementById('modal-title').textContent = detail.title;
        document.getElementById('modal-user').textContent = `작성자: 사용자`;
        document.getElementById('modal-date').textContent = Utils.formatDateTime(detail.createdAt);
        document.getElementById('modal-content').textContent = detail.content;
        document.getElementById('status-select').value = detail.status;
        document.getElementById('reply-count').textContent = detail.replies?.length || 0;

        // 답변 목록 렌더링
        const repliesContainer = document.getElementById('replies-container');
        if (detail.replies && detail.replies.length > 0) {
            repliesContainer.innerHTML = detail.replies.map(r => `
                <div class="reply-item ${r.isAdmin ? 'admin' : ''}">
                    <div class="reply-header">
                        <span class="reply-author ${r.isAdmin ? 'admin' : ''}">
                            ${r.isAdmin ? (r.adminName || '관리자') : '사용자'}
                        </span>
                        <span class="reply-date">${Utils.formatDateTime(r.createdAt)}</span>
                    </div>
                    <div class="reply-content">${escapeHtml(r.content)}</div>
                </div>
            `).join('');
        } else {
            repliesContainer.innerHTML = '<div class="text-muted text-center">아직 답변이 없습니다.</div>';
        }

        // 입력 폼 초기화
        document.getElementById('reply-content').value = '';
    }

    // 모달 닫기
    function closeModal() {
        detailModal.classList.remove('active');
        currentQuestionId = null;
    }

    // 답변 등록
    async function submitReply() {
        const content = document.getElementById('reply-content').value.trim();
        const adminName = document.getElementById('admin-name').value.trim();

        if (!content) {
            Utils.toast('답변 내용을 입력해주세요.', 'warning');
            return;
        }

        try {
            await AdminAPI.addQnAReply(currentQuestionId, content, adminName);
            Utils.toast('답변이 등록되었습니다.', 'success');

            // 상세 새로고침
            const detail = await AdminAPI.getQnADetail(currentQuestionId);
            renderDetail(detail);

            // 목록 및 통계 새로고침
            await Promise.all([loadStats(), loadQnAList()]);
        } catch (error) {
            console.error('답변 등록 실패:', error);
            Utils.toast('답변 등록에 실패했습니다.', 'danger');
        }
    }

    // 상태 변경
    async function updateStatus(questionId, status) {
        try {
            await AdminAPI.updateQnAStatus(questionId, status);
            Utils.toast('상태가 변경되었습니다.', 'success');

            // 목록 및 통계 새로고침
            await Promise.all([loadStats(), loadQnAList()]);
        } catch (error) {
            console.error('상태 변경 실패:', error);
            Utils.toast('상태 변경에 실패했습니다.', 'danger');
        }
    }

    // HTML 이스케이프
    function escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // 페이지 로드 시 초기화
    document.addEventListener('DOMContentLoaded', init);
})();
