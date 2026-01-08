/**
 * API 호출 유틸리티
 */
const API = {
    baseUrl: '/api',

    /**
     * GET 요청
     */
    async get(endpoint) {
        const response = await fetch(`${this.baseUrl}${endpoint}`);
        return this.handleResponse(response);
    },

    /**
     * POST 요청
     */
    async post(endpoint, data = null) {
        const options = {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
        };
        if (data) {
            options.body = JSON.stringify(data);
        }
        const response = await fetch(`${this.baseUrl}${endpoint}`, options);
        return this.handleResponse(response);
    },

    /**
     * DELETE 요청
     */
    async delete(endpoint) {
        const response = await fetch(`${this.baseUrl}${endpoint}`, {
            method: 'DELETE',
        });
        return this.handleResponse(response);
    },

    /**
     * 응답 처리
     */
    async handleResponse(response) {
        const data = await response.json();
        if (!data.isSuccess) {
            throw new Error(data.message || '요청 실패');
        }
        return data.result;
    },

    // ===== Admin API =====

    /**
     * 약물 데이터 통계
     */
    getDrugStats() {
        return this.get('/admin/stats/drugs');
    },

    /**
     * 최근 등록 영양제 목록
     */
    getRecentSupplements(params = {}) {
        const query = new URLSearchParams({
            page: params.page || 0,
            size: params.size || 10,
            ...(params.days && { days: params.days }),
            ...(params.search && { search: params.search }),
        });
        return this.get(`/admin/supplements/recent?${query}`);
    },

    /**
     * 태그별 영양제 통계
     */
    getSupplementTagStats() {
        return this.get('/admin/supplements/stats/tags');
    },

    /**
     * 영양제 삭제
     */
    deleteSupplement(id) {
        return this.delete(`/admin/supplements/${id}`);
    },

    /**
     * 가입자 통계
     */
    getUserStats() {
        return this.get('/admin/users/stats');
    },

    /**
     * 일별 가입 추이
     */
    getDailySignups(days = 7) {
        return this.get(`/admin/users/daily?days=${days}`);
    },

    // ===== Batch API =====

    /**
     * 전체 동기화 시작 (JSON - CSV 파일 없이)
     */
    startFullSync(options = {}) {
        return this.post('/drugs/batch/full-sync', options);
    },

    /**
     * 전체 동기화 시작 (FormData - CSV 파일 포함)
     */
    async startFullSyncWithFile(options = {}) {
        const formData = new FormData();
        formData.append('testMode', options.testMode || false);
        if (options.maxRecords) {
            formData.append('maxRecords', options.maxRecords);
        }
        if (options.csvFile) {
            formData.append('csvFile', options.csvFile);
        }

        const response = await fetch(`${this.baseUrl}/drugs/batch/full-sync`, {
            method: 'POST',
            body: formData,
        });
        return this.handleResponse(response);
    },

    /**
     * 작업 상태 조회
     */
    getJobStatus(jobId) {
        return this.get(`/drugs/batch/full-sync/${jobId}/status`);
    },

    /**
     * 작업 취소
     */
    cancelJob(jobId) {
        return this.post(`/drugs/batch/full-sync/${jobId}/cancel`);
    },

    /**
     * 테스트 검증
     */
    verifyJob(jobId) {
        return this.post(`/drugs/batch/full-sync/${jobId}/verify`);
    },

    /**
     * 테스트 테이블 초기화
     */
    clearTestTable() {
        return this.delete('/drugs/batch/test-table');
    },

    /**
     * 테스트 데이터 운영 이관
     */
    promoteTestData() {
        return this.post('/drugs/batch/test-table/promote');
    },

    /**
     * CSV 파일 업로드
     */
    async uploadCsv(file) {
        const formData = new FormData();
        formData.append('file', file);

        const response = await fetch(`${this.baseUrl}/drugs/batch/csv-upload`, {
            method: 'POST',
            body: formData,
        });
        return this.handleResponse(response);
    },

    /**
     * 서버 헬스 상태 조회
     */
    getServerHealth() {
        return this.get('/admin/health');
    },
};

// 전역에서 사용 가능하도록
window.API = API;
