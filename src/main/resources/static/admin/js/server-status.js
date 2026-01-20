// 서버 상태 페이지 스크립트

document.addEventListener('DOMContentLoaded', function() {
    // 상태 관리
    let currentPage = 0;
    let totalPages = 0;
    let currentErrorLog = null;
    let chatMessages = [];
    let currentAiModelSetting = null;

    // DOM 요소
    const healthRefreshBtn = document.getElementById('healthRefreshBtn');
    const logsRefreshBtn = document.getElementById('logsRefreshBtn');
    const logLevelSelect = document.getElementById('logLevel');
    const logHoursSelect = document.getElementById('logHours');
    const logsTableBody = document.getElementById('logsTableBody');
    const logsTable = document.getElementById('logsTable');
    const logsLoading = document.getElementById('logsLoading');
    const logsEmpty = document.getElementById('logsEmpty');
    const logsPagination = document.getElementById('logsPagination');
    const logsPrevBtn = document.getElementById('logsPrevBtn');
    const logsNextBtn = document.getElementById('logsNextBtn');
    const logsPageInfo = document.getElementById('logsPageInfo');

    // 채팅 관련 DOM 요소
    const chatSidebar = document.getElementById('chatSidebar');
    const chatOverlay = document.getElementById('chatOverlay');
    const chatCloseBtn = document.getElementById('chatCloseBtn');
    const chatInput = document.getElementById('chatInput');
    const chatSendBtn = document.getElementById('chatSendBtn');
    const chatMessagesContainer = document.getElementById('chatMessages');
    const chatErrorTime = document.getElementById('chatErrorTime');
    const chatErrorLevel = document.getElementById('chatErrorLevel');
    const chatErrorMessage = document.getElementById('chatErrorMessage');

    // AI 모델 설정 DOM 요소
    const aiModelRefreshBtn = document.getElementById('aiModelRefreshBtn');
    const analysisModelSelect = document.getElementById('analysisModelSelect');
    const fallbackModelSelect = document.getElementById('fallbackModelSelect');
    const fallbackEnabledToggle = document.getElementById('fallbackEnabledToggle');
    const fallbackStatusText = document.getElementById('fallbackStatusText');
    const saveAiModelBtn = document.getElementById('saveAiModelBtn');
    const aiModelSaveStatus = document.getElementById('aiModelSaveStatus');
    const configDefaultModel = document.getElementById('configDefaultModel');
    const aiModelUpdatedAt = document.getElementById('aiModelUpdatedAt');

    // 초기화
    loadHealth();
    loadLogs();
    loadAiModelSetting();

    // 이벤트 리스너
    healthRefreshBtn.addEventListener('click', loadHealth);
    logsRefreshBtn.addEventListener('click', () => {
        currentPage = 0;
        loadLogs();
    });
    logLevelSelect.addEventListener('change', () => {
        currentPage = 0;
        loadLogs();
    });
    logHoursSelect.addEventListener('change', () => {
        currentPage = 0;
        loadLogs();
    });
    logsPrevBtn.addEventListener('click', () => {
        if (currentPage > 0) {
            currentPage--;
            loadLogs();
        }
    });
    logsNextBtn.addEventListener('click', () => {
        if (currentPage < totalPages - 1) {
            currentPage++;
            loadLogs();
        }
    });

    // 채팅 이벤트
    chatCloseBtn.addEventListener('click', closeChatSidebar);
    chatOverlay.addEventListener('click', closeChatSidebar);
    chatSendBtn.addEventListener('click', sendChatMessage);
    chatInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendChatMessage();
        }
    });

    // AI 모델 설정 이벤트
    aiModelRefreshBtn.addEventListener('click', loadAiModelSetting);
    saveAiModelBtn.addEventListener('click', saveAiModelSetting);
    fallbackEnabledToggle.addEventListener('change', () => {
        fallbackStatusText.textContent = fallbackEnabledToggle.checked ? '활성화' : '비활성화';
    });

    // 헬스 체크 로드
    async function loadHealth() {
        try {
            const data = await AdminAPI.getHealth();

            // 상태 표시
            updateStatusIndicator('serverStatus', data.serverUp);
            updateStatusIndicator('dbStatus', data.databaseUp);
            updateStatusIndicator('storageStatus', data.storageUp);

            // 응답 시간
            document.getElementById('responseTime').textContent = data.responseTimeMs + 'ms';

            // 메모리
            const memoryPercent = Math.round((data.heapUsedMb / data.heapMaxMb) * 100);
            document.getElementById('memoryValue').textContent =
                `${data.heapUsedMb}MB / ${data.heapMaxMb}MB (${memoryPercent}%)`;
            document.getElementById('memoryFill').style.width = memoryPercent + '%';
            updateResourceBarColor('memoryFill', memoryPercent);

            // CPU
            const cpuPercent = data.cpuUsage >= 0 ? data.cpuUsage : 0;
            document.getElementById('cpuValue').textContent =
                data.cpuUsage >= 0 ? cpuPercent + '%' : '측정 불가';
            document.getElementById('cpuFill').style.width = cpuPercent + '%';
            updateResourceBarColor('cpuFill', cpuPercent);

            // 배포 정보
            document.getElementById('appVersion').textContent = data.appVersion || '-';
            document.getElementById('buildTime').textContent = data.buildTime || '-';
            document.getElementById('lastChecked').textContent =
                data.checkedAt ? formatDateTime(data.checkedAt) : '-';

        } catch (error) {
            console.error('헬스 체크 실패:', error);
            showError('서버 상태를 불러오는데 실패했습니다.');
        }
    }

    // 로그 로드
    async function loadLogs() {
        const level = logLevelSelect.value;
        const hours = parseInt(logHoursSelect.value);

        logsLoading.style.display = 'flex';
        logsTable.style.display = 'none';
        logsEmpty.style.display = 'none';
        logsPagination.style.display = 'none';

        try {
            const data = await AdminAPI.getLogs(currentPage, 20, level, hours);

            totalPages = data.totalPages;

            if (data.logs.length === 0) {
                logsLoading.style.display = 'none';
                logsEmpty.style.display = 'flex';
                return;
            }

            renderLogs(data.logs);

            logsLoading.style.display = 'none';
            logsTable.style.display = 'table';
            logsPagination.style.display = 'flex';

            // 페이지네이션 업데이트
            logsPageInfo.textContent = `${currentPage + 1} / ${totalPages}`;
            logsPrevBtn.disabled = currentPage === 0;
            logsNextBtn.disabled = currentPage >= totalPages - 1;

        } catch (error) {
            console.error('로그 로드 실패:', error);
            logsLoading.style.display = 'none';
            logsEmpty.style.display = 'flex';
            logsEmpty.querySelector('span').textContent = '로그를 불러오는데 실패했습니다.';
        }
    }

    // 로그 렌더링
    function renderLogs(logs) {
        logsTableBody.innerHTML = '';

        logs.forEach(log => {
            const tr = document.createElement('tr');
            tr.className = `log-row log-${log.level.toLowerCase()}`;

            // 발생 횟수 배지 (동일 이벤트가 여러 번 발생한 경우)
            const occurrenceBadge = log.occurrenceCount > 1
                ? `<span class="log-occurrence-badge">${log.occurrenceCount}회 발생</span>`
                : '';

            // 관련 로그 배지 (이벤트 내 여러 로그가 있는 경우)
            const relatedBadge = log.relatedLogCount > 1
                ? `<span class="log-related-badge">+${log.relatedLogCount - 1}개 관련</span>`
                : '';

            tr.innerHTML = `
                <td class="log-time">${formatDateTime(log.timestamp)}</td>
                <td><span class="log-level-badge ${log.level.toLowerCase()}">${log.level}</span></td>
                <td class="log-logger" title="${log.logger}">${truncateLogger(log.logger)}</td>
                <td class="log-message">
                    <div class="log-message-text" title="${escapeHtml(log.message)}">${escapeHtml(log.message)}</div>
                    <div class="log-badges">
                        ${occurrenceBadge}
                        ${relatedBadge}
                        ${log.stackTrace ? '<span class="log-has-stack">+ 스택트레이스</span>' : ''}
                    </div>
                </td>
                <td>
                    <button class="btn-analyze" data-log-id="${log.id}">AI 분석</button>
                </td>
            `;

            // AI 분석 버튼 이벤트
            const analyzeBtn = tr.querySelector('.btn-analyze');
            analyzeBtn.addEventListener('click', () => openChatSidebar(log));

            // 스택트레이스 토글
            if (log.stackTrace) {
                const messageCell = tr.querySelector('.log-message');
                const stackIndicator = tr.querySelector('.log-has-stack');

                stackIndicator.addEventListener('click', (e) => {
                    e.stopPropagation();
                    let stackDiv = tr.querySelector('.log-stacktrace');

                    if (!stackDiv) {
                        stackDiv = document.createElement('div');
                        stackDiv.className = 'log-stacktrace';
                        stackDiv.innerHTML = `<pre>${escapeHtml(log.stackTrace)}</pre>`;
                        messageCell.appendChild(stackDiv);
                        stackIndicator.textContent = '- 스택트레이스 숨기기';
                    } else {
                        stackDiv.remove();
                        stackIndicator.textContent = '+ 스택트레이스';
                    }
                });
            }

            // 관련 로그 토글
            if (log.relatedLogs && log.relatedLogs.length > 0) {
                const messageCell = tr.querySelector('.log-message');
                const relatedBadge = tr.querySelector('.log-related-badge');

                relatedBadge.style.cursor = 'pointer';
                relatedBadge.addEventListener('click', (e) => {
                    e.stopPropagation();
                    let relatedDiv = tr.querySelector('.log-related-logs');

                    if (!relatedDiv) {
                        relatedDiv = document.createElement('div');
                        relatedDiv.className = 'log-related-logs';
                        relatedDiv.innerHTML = `
                            <div class="related-logs-header">관련 로그 (${log.relatedLogs.length}개)</div>
                            ${log.relatedLogs.map(r => `
                                <div class="related-log-item">
                                    <span class="related-log-time">${formatDateTime(r.timestamp)}</span>
                                    <span class="log-level-badge ${r.level.toLowerCase()}">${r.level}</span>
                                    <span class="related-log-message">${escapeHtml(r.message)}</span>
                                </div>
                            `).join('')}
                        `;
                        messageCell.appendChild(relatedDiv);
                        relatedBadge.textContent = `- ${log.relatedLogs.length}개 관련 숨기기`;
                    } else {
                        relatedDiv.remove();
                        relatedBadge.textContent = `+${log.relatedLogCount - 1}개 관련`;
                    }
                });
            }

            logsTableBody.appendChild(tr);
        });
    }

    // 채팅 사이드바 열기
    function openChatSidebar(log) {
        currentErrorLog = log;
        chatMessages = [];

        // 에러 정보 표시
        chatErrorTime.textContent = formatDateTime(log.timestamp);
        chatErrorLevel.textContent = log.level;
        chatErrorMessage.textContent = log.message;

        // 채팅 메시지 초기화
        chatMessagesContainer.innerHTML = '';

        // 초기 AI 분석 요청
        addChatMessage('assistant', '에러 로그를 분석하고 있습니다...');
        requestAIAnalysis('이 에러의 원인을 분석하고 해결 방법을 알려주세요.');

        // 사이드바 열기
        chatSidebar.classList.add('open');
        chatOverlay.classList.add('visible');
        document.body.style.overflow = 'hidden';
    }

    // 채팅 사이드바 닫기
    function closeChatSidebar() {
        chatSidebar.classList.remove('open');
        chatOverlay.classList.remove('visible');
        document.body.style.overflow = '';
        currentErrorLog = null;
        chatMessages = [];
    }

    // 채팅 메시지 추가
    function addChatMessage(role, content, isLoading = false) {
        const messageDiv = document.createElement('div');
        messageDiv.className = `chat-message ${role}`;

        if (isLoading) {
            messageDiv.innerHTML = `
                <div class="chat-message-content loading">
                    <div class="typing-indicator">
                        <span></span><span></span><span></span>
                    </div>
                </div>
            `;
            messageDiv.id = 'loadingMessage';
        } else {
            messageDiv.innerHTML = `
                <div class="chat-message-content">${formatChatContent(content)}</div>
            `;
        }

        chatMessagesContainer.appendChild(messageDiv);
        chatMessagesContainer.scrollTop = chatMessagesContainer.scrollHeight;

        return messageDiv;
    }

    // 로딩 메시지 제거
    function removeLoadingMessage() {
        const loadingMsg = document.getElementById('loadingMessage');
        if (loadingMsg) {
            loadingMsg.remove();
        }
    }

    // 채팅 메시지 전송
    async function sendChatMessage() {
        const message = chatInput.value.trim();
        if (!message || !currentErrorLog) return;

        chatInput.value = '';

        // 사용자 메시지 추가
        addChatMessage('user', message);
        chatMessages.push({ role: 'user', content: message });

        // AI 응답 요청
        await requestAIAnalysis(message);
    }

    // AI 분석 요청
    async function requestAIAnalysis(userMessage) {
        addChatMessage('assistant', '', true);

        try {
            const request = {
                errorLog: {
                    timestamp: currentErrorLog.timestamp,
                    level: currentErrorLog.level,
                    logger: currentErrorLog.logger,
                    message: currentErrorLog.message,
                    stackTrace: currentErrorLog.stackTrace
                },
                messages: chatMessages,
                userMessage: userMessage
            };

            const response = await AdminAPI.chat(request);

            removeLoadingMessage();
            addChatMessage('assistant', response.response);

            chatMessages.push({ role: 'assistant', content: response.response });

        } catch (error) {
            console.error('AI 채팅 오류:', error);
            removeLoadingMessage();
            addChatMessage('assistant', 'AI 응답을 받는데 실패했습니다. 다시 시도해주세요.');
        }
    }

    // 유틸리티 함수들
    function updateStatusIndicator(elementId, isUp) {
        const element = document.getElementById(elementId);
        element.className = 'status-indicator ' + (isUp ? 'up' : 'down');
        const valueEl = element.parentElement.querySelector('.status-value');
        if (valueEl) {
            valueEl.textContent = isUp ? '정상' : '오류';
        }
    }

    function updateResourceBarColor(elementId, percent) {
        const element = document.getElementById(elementId);
        if (percent > 80) {
            element.style.backgroundColor = 'var(--danger)';
        } else if (percent > 60) {
            element.style.backgroundColor = 'var(--warning)';
        } else {
            element.style.backgroundColor = 'var(--success)';
        }
    }

    function formatDateTime(isoString) {
        if (!isoString) return '-';
        const date = new Date(isoString);
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');
        const seconds = String(date.getSeconds()).padStart(2, '0');
        return `${month}-${day} ${hours}:${minutes}:${seconds}`;
    }

    function truncateLogger(logger) {
        if (!logger) return '-';
        const parts = logger.split('.');
        if (parts.length <= 2) return logger;
        return '...' + parts.slice(-2).join('.');
    }

    function escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    function formatChatContent(content) {
        if (!content) return '';
        // 코드 블록 처리
        content = content.replace(/```(\w*)\n?([\s\S]*?)```/g, '<pre><code>$2</code></pre>');
        // 인라인 코드 처리
        content = content.replace(/`([^`]+)`/g, '<code>$1</code>');
        // 줄바꿈 처리
        content = content.replace(/\n/g, '<br>');
        return content;
    }

    function showError(message) {
        alert(message);
    }

    // ===== AI 모델 설정 관련 함수 =====

    // AI 모델 설정 로드
    async function loadAiModelSetting() {
        try {
            const data = await AdminAPI.getAiModelSetting();
            currentAiModelSetting = data;

            // 모델 선택 옵션 렌더링
            renderModelOptions(analysisModelSelect, data.availableModels, data.analysisModel);
            renderModelOptions(fallbackModelSelect, data.availableModels, data.fallbackModel);

            // 폴백 토글 상태
            fallbackEnabledToggle.checked = data.fallbackEnabled;
            fallbackStatusText.textContent = data.fallbackEnabled ? '활성화' : '비활성화';

            // 설정 정보
            configDefaultModel.textContent = data.configAnalysisModel || '-';
            aiModelUpdatedAt.textContent = data.updatedAt ? formatDateTime(data.updatedAt) : '변경 기록 없음';

            // 저장 상태 초기화
            aiModelSaveStatus.textContent = '';

        } catch (error) {
            console.error('AI 모델 설정 로드 실패:', error);
            aiModelSaveStatus.textContent = '설정 로드 실패';
            aiModelSaveStatus.className = 'save-status error';
        }
    }

    // 모델 선택 옵션 렌더링
    function renderModelOptions(selectElement, models, selectedModel) {
        selectElement.innerHTML = '';
        models.forEach(model => {
            const option = document.createElement('option');
            option.value = model;
            option.textContent = model;
            if (model === selectedModel) {
                option.selected = true;
            }
            selectElement.appendChild(option);
        });
    }

    // AI 모델 설정 저장
    async function saveAiModelSetting() {
        const request = {
            analysisModel: analysisModelSelect.value,
            fallbackModel: fallbackModelSelect.value,
            fallbackEnabled: fallbackEnabledToggle.checked
        };

        saveAiModelBtn.disabled = true;
        aiModelSaveStatus.textContent = '저장 중...';
        aiModelSaveStatus.className = 'save-status';

        try {
            const data = await AdminAPI.updateAiModelSetting(request);
            currentAiModelSetting = data;

            aiModelSaveStatus.textContent = '저장 완료';
            aiModelSaveStatus.className = 'save-status success';

            // 마지막 변경 시간 업데이트
            aiModelUpdatedAt.textContent = data.updatedAt ? formatDateTime(data.updatedAt) : '-';

            // 3초 후 상태 메시지 제거
            setTimeout(() => {
                aiModelSaveStatus.textContent = '';
            }, 3000);

        } catch (error) {
            console.error('AI 모델 설정 저장 실패:', error);
            aiModelSaveStatus.textContent = '저장 실패: ' + (error.message || '알 수 없는 오류');
            aiModelSaveStatus.className = 'save-status error';
        } finally {
            saveAiModelBtn.disabled = false;
        }
    }
});
