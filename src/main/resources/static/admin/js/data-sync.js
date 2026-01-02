/**
 * 데이터 수집 관리 페이지 로직
 */
const DataSync = {
    currentJobId: null,
    pollingInterval: null,

    /**
     * 초기화
     */
    async init() {
        await this.loadDrugStats();
        this.bindEvents();
    },

    /**
     * 약물 데이터 통계 로드
     */
    async loadDrugStats() {
        try {
            const stats = await API.getDrugStats();
            document.getElementById('totalDrugs').textContent = Utils.formatNumber(stats.totalCount);
            document.getElementById('withoutEfficacy').textContent = Utils.formatNumber(stats.withoutEfficacy);
            document.getElementById('withoutIngredient').textContent = Utils.formatNumber(stats.withoutIngredient);
        } catch (error) {
            console.error('약물 통계 로드 실패:', error);
        }
    },

    /**
     * 이벤트 바인딩
     */
    bindEvents() {
        // 동기화 시작 버튼
        document.getElementById('startSyncBtn').addEventListener('click', () => this.startSync());

        // 취소 버튼
        document.getElementById('cancelBtn').addEventListener('click', () => this.cancelSync());

        // 새로고침 버튼
        document.getElementById('refreshBtn').addEventListener('click', () => this.refreshStatus());

        // 검증 버튼
        document.getElementById('verifyBtn')?.addEventListener('click', () => this.verifyJob());

        // 테스트 테이블 삭제 버튼
        document.getElementById('clearTestBtn')?.addEventListener('click', () => this.clearTestTable());

        // 운영 이관 버튼
        document.getElementById('promoteBtn')?.addEventListener('click', () => this.promoteData());
    },

    /**
     * 동기화 시작
     */
    async startSync() {
        const testMode = document.getElementById('testMode').checked;
        const maxRecordsInput = document.getElementById('maxRecords').value;
        const maxRecords = maxRecordsInput ? parseInt(maxRecordsInput) : null;

        // CSV 파일 확인 (4단계 PDF 배치용)
        const csvFileInput = document.getElementById('syncCsvFile');
        const csvFile = csvFileInput?.files[0];

        const startBtn = document.getElementById('startSyncBtn');
        startBtn.disabled = true;
        startBtn.textContent = '시작 중...';

        try {
            const result = await API.startFullSyncWithFile({
                testMode,
                maxRecords,
                csvFile,
            });

            this.currentJobId = result.jobId;
            Utils.toast('동기화 작업이 시작되었습니다.', 'success');

            // 진행 상황 섹션 표시
            document.getElementById('progressSection').classList.remove('hidden');

            // 폴링 시작
            this.startPolling();

            // CSV 파일 입력 초기화
            if (csvFileInput) csvFileInput.value = '';

        } catch (error) {
            Utils.toast(`시작 실패: ${error.message}`, 'danger');
        } finally {
            startBtn.disabled = false;
            startBtn.textContent = '🚀 전체 동기화 시작';
        }
    },

    /**
     * 작업 취소
     */
    async cancelSync() {
        if (!this.currentJobId) return;

        const confirmed = await Utils.confirm(
            '작업 취소',
            '진행 중인 작업을 취소하시겠습니까?<br>현재 처리 중인 페이지 완료 후 중단됩니다.'
        );

        if (!confirmed) return;

        try {
            await API.cancelJob(this.currentJobId);
            Utils.toast('작업 취소가 요청되었습니다.', 'warning');
        } catch (error) {
            Utils.toast(`취소 실패: ${error.message}`, 'danger');
        }
    },

    /**
     * 상태 새로고침
     */
    async refreshStatus() {
        if (!this.currentJobId) return;

        try {
            const status = await API.getJobStatus(this.currentJobId);
            this.renderStatus(status);
        } catch (error) {
            console.error('상태 조회 실패:', error);
        }
    },

    /**
     * 폴링 시작
     */
    startPolling() {
        if (this.pollingInterval) {
            clearInterval(this.pollingInterval);
        }

        this.refreshStatus();

        this.pollingInterval = setInterval(async () => {
            if (!this.currentJobId) {
                this.stopPolling();
                return;
            }

            try {
                const status = await API.getJobStatus(this.currentJobId);
                this.renderStatus(status);

                // 완료 또는 실패시 폴링 중지
                if (['COMPLETED', 'FAILED', 'CANCELLED'].includes(status.status)) {
                    this.stopPolling();
                    await this.loadDrugStats(); // 통계 새로고침
                }
            } catch (error) {
                console.error('상태 조회 실패:', error);
            }
        }, 3000); // 3초마다
    },

    /**
     * 폴링 중지
     */
    stopPolling() {
        if (this.pollingInterval) {
            clearInterval(this.pollingInterval);
            this.pollingInterval = null;
        }
    },

    /**
     * 상태 렌더링
     */
    renderStatus(status) {
        // Job ID
        document.getElementById('jobId').textContent = status.jobId;

        // 상태 배지
        document.getElementById('jobStatus').innerHTML = Utils.getStatusBadge(status.status);

        // 전체 진행률
        document.getElementById('overallProgress').style.width = `${status.overallProgress}%`;
        document.getElementById('overallProgressText').textContent = `${status.overallProgress}%`;

        // 시간 정보
        document.getElementById('startedAt').textContent = Utils.formatDateTime(status.startedAt);
        document.getElementById('estimatedAt').textContent = Utils.formatDateTime(status.estimatedCompletionAt);

        // 단계별 진행 상황
        const stagesContainer = document.getElementById('stagesContainer');
        stagesContainer.innerHTML = status.stages.map(stage => `
            <div class="stage-item">
                <div class="stage-icon ${stage.status.toLowerCase()}">${Utils.getStageIcon(stage.status)}</div>
                <div class="stage-content">
                    <div class="stage-name">[${stage.stage}] ${stage.name}</div>
                    <div class="stage-status">
                        ${this.getStageStatusText(stage)}
                    </div>
                    ${stage.status === 'IN_PROGRESS' ? `
                        <div class="stage-progress">
                            <div class="progress">
                                <div class="progress-bar" style="width: ${stage.progress}%"></div>
                            </div>
                        </div>
                    ` : ''}
                </div>
            </div>
        `).join('');

        // 에러 메시지
        if (status.errorMessage) {
            document.getElementById('errorMessage').textContent = status.errorMessage;
            document.getElementById('errorMessage').classList.remove('hidden');
        } else {
            document.getElementById('errorMessage').classList.add('hidden');
        }

        // 버튼 상태
        const isRunning = status.status === 'IN_PROGRESS' || status.status === 'STARTED';
        document.getElementById('cancelBtn').disabled = !isRunning;
        document.getElementById('verifyBtn').disabled = isRunning || status.status !== 'COMPLETED';
    },

    /**
     * 단계 상태 텍스트
     */
    getStageStatusText(stage) {
        switch (stage.status) {
            case 'PENDING':
                return '대기 중';
            case 'IN_PROGRESS':
                return `진행 중 ${stage.progress}% (${Utils.formatNumber(stage.processedCount)}건)`;
            case 'COMPLETED':
                return `완료 (${Utils.formatNumber(stage.processedCount)}건)`;
            case 'FAILED':
                return '실패';
            default:
                return stage.status;
        }
    },

    /**
     * 테스트 검증
     */
    async verifyJob() {
        if (!this.currentJobId) return;

        try {
            const result = await API.verifyJob(this.currentJobId);
            this.showVerificationResult(result);
        } catch (error) {
            Utils.toast(`검증 실패: ${error.message}`, 'danger');
        }
    },

    /**
     * 검증 결과 표시
     */
    showVerificationResult(result) {
        const modal = document.createElement('div');
        modal.className = 'modal-overlay active';
        modal.innerHTML = `
            <div class="modal" style="max-width: 500px;">
                <div class="modal-header">검증 결과</div>
                <div class="modal-body">
                    <div class="stats-grid" style="grid-template-columns: repeat(2, 1fr);">
                        <div class="stat-card">
                            <div class="stat-label">운영 테이블</div>
                            <div class="stat-value">${Utils.formatNumber(result.summary?.originalTableCount)}</div>
                        </div>
                        <div class="stat-card">
                            <div class="stat-label">테스트 테이블</div>
                            <div class="stat-value">${Utils.formatNumber(result.summary?.testTableCount)}</div>
                        </div>
                        <div class="stat-card">
                            <div class="stat-label">일치</div>
                            <div class="stat-value success">${Utils.formatNumber(result.summary?.matchedCount)}</div>
                        </div>
                        <div class="stat-card">
                            <div class="stat-label">불일치</div>
                            <div class="stat-value warning">${Utils.formatNumber(result.summary?.mismatchedCount)}</div>
                        </div>
                    </div>
                </div>
                <div class="modal-footer">
                    <button class="btn btn-secondary" id="closeVerifyModal">닫기</button>
                    <button class="btn btn-primary" id="promoteFromModal">운영 이관</button>
                </div>
            </div>
        `;
        document.body.appendChild(modal);

        modal.querySelector('#closeVerifyModal').onclick = () => modal.remove();
        modal.querySelector('#promoteFromModal').onclick = async () => {
            modal.remove();
            await this.promoteData();
        };
        modal.onclick = (e) => {
            if (e.target === modal) modal.remove();
        };
    },

    /**
     * 테스트 테이블 초기화
     */
    async clearTestTable() {
        const confirmed = await Utils.confirm(
            '테스트 테이블 초기화',
            '테스트 테이블의 모든 데이터가 삭제됩니다.<br>계속하시겠습니까?'
        );

        if (!confirmed) return;

        try {
            await API.clearTestTable();
            Utils.toast('테스트 테이블이 초기화되었습니다.', 'success');
        } catch (error) {
            Utils.toast(`초기화 실패: ${error.message}`, 'danger');
        }
    },

    /**
     * 운영 이관
     */
    async promoteData() {
        const confirmed = await Utils.confirm(
            '운영 이관',
            '테스트 데이터를 운영 테이블로 이관합니다.<br>기존 운영 데이터가 덮어씌워질 수 있습니다.<br>계속하시겠습니까?'
        );

        if (!confirmed) return;

        try {
            const result = await API.promoteTestData();
            Utils.toast(result, 'success');
            await this.loadDrugStats();
        } catch (error) {
            Utils.toast(`이관 실패: ${error.message}`, 'danger');
        }
    },

};

// 페이지 로드 시 초기화
document.addEventListener('DOMContentLoaded', () => DataSync.init());
