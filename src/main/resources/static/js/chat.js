(function () {
    const box = document.getElementById('chatBox');
    if (!box) return;

    const toggleButton = document.getElementById('chatToggle');
    const currentUser = box.getAttribute('data-user') || 'guest';
    const isLoggedIn = currentUser !== 'guest' && currentUser !== 'anonymousUser';

    const log = document.getElementById('chatLog');
    const input = document.getElementById('chatInput');
    const form = document.getElementById('chatForm');
    const newBtn = document.getElementById('chatNew');
    const historyBtn = document.getElementById('chatHistoryBtn');
    const historyPanel = document.getElementById('chatHistoryPanel');
    const historyList = document.getElementById('chatHistoryList');
    const clearAllBtn = document.getElementById('chatClearAll');

    const STORAGE_SESSIONS_KEY = 'eshop_chat_sessions_' + currentUser;
    const STORAGE_ACTIVE_KEY = 'eshop_active_session_' + currentUser;
    const OPEN_KEY = 'eshop_chat_open';
    const DEFAULT_GREETING = '👋 **Xin chào! Em là trợ lý mua sắm thông minh của eShop.**\n\nEm có thể hỗ trợ bạn:\n- 📋 **Đơn hàng & Thống kê:** *"Đơn hàng của tôi"*, *"Tôi đã mua bao nhiêu đơn, bao nhiêu tiền, đơn nào cao nhất?"*, *"Đơn #5 của mình"*\n- 💰 **Lọc theo giá:** *"Tìm sản phẩm dưới 10 triệu"*, *"Điện thoại từ 5 đến 15tr"*, *"Laptop trên 20tr"*\n- ⚖️ **So sánh:** *"So sánh Oppo Reno12 và iPhone 15"*\n- 🏆 **Hỏi cái nhất:** *"Sản phẩm rẻ nhất phân loại điện thoại"*, *"Máy nào màn hình to nhất?"*\n- 🎯 **Tư vấn theo nhu cầu:** *"Laptop cho sinh viên"*, *"Điện thoại chơi game pin trâu"*\n- 📦 **Tồn kho & Dịch vụ:** *"iPhone 15 còn hàng không?"*, *"Chính sách bảo hành và giao hàng"*';

    let currentSessionId = null;

    // Quản lý trạng thái đóng/mở khung chat
    const setOpen = (open) => {
        if (open) {
            box.classList.remove('d-none');
            localStorage.setItem(OPEN_KEY, 'true');
            log.scrollTop = log.scrollHeight;
        } else {
            box.classList.add('d-none');
            localStorage.setItem(OPEN_KEY, 'false');
        }
    };

    let isDraggingToggle = false;
    let suppressToggleClick = false;
    let dragOffsetX = 0;
    let dragOffsetY = 0;
    let pointerDownX = 0;
    let pointerDownY = 0;

    toggleButton.addEventListener('pointerdown', (event) => {
        if (event.button !== 0) return;
        const rect = toggleButton.getBoundingClientRect();
        dragOffsetX = event.clientX - rect.left;
        dragOffsetY = event.clientY - rect.top;
        pointerDownX = event.clientX;
        pointerDownY = event.clientY;
        isDraggingToggle = false;
        toggleButton.setPointerCapture(event.pointerId);
    });

    toggleButton.addEventListener('pointermove', (event) => {
        if (!toggleButton.hasPointerCapture(event.pointerId)) return;
        const maxLeft = Math.max(0, window.innerWidth - toggleButton.offsetWidth);
        const maxTop = Math.max(0, window.innerHeight - toggleButton.offsetHeight);
        const left = Math.min(Math.max(0, event.clientX - dragOffsetX), maxLeft);
        const top = Math.min(Math.max(0, event.clientY - dragOffsetY), maxTop);
        if (Math.hypot(event.clientX - pointerDownX, event.clientY - pointerDownY) > 4) {
            isDraggingToggle = true;
            toggleButton.style.left = `${left}px`;
            toggleButton.style.top = `${top}px`;
            toggleButton.style.right = 'auto';
            toggleButton.style.bottom = 'auto';
        }
    });

    toggleButton.addEventListener('pointerup', (event) => {
        if (toggleButton.hasPointerCapture(event.pointerId)) {
            toggleButton.releasePointerCapture(event.pointerId);
        }
        if (isDraggingToggle) {
            suppressToggleClick = true;
            window.setTimeout(() => { suppressToggleClick = false; }, 0);
        }
    });

    toggleButton.addEventListener('click', () => {
        if (suppressToggleClick) return;
        setOpen(box.classList.contains('d-none'));
    });

    document.addEventListener('pointerdown', (event) => {
        if (!box.contains(event.target) && !toggleButton.contains(event.target)) {
            setOpen(false);
        }
    });
    document.getElementById('chatClose').onclick = () => setOpen(false);

    if (localStorage.getItem(OPEN_KEY) === 'true') {
        box.classList.remove('d-none');
    }

    function formatNow() {
        const d = new Date();
        const hh = String(d.getHours()).padStart(2, '0');
        const mm = String(d.getMinutes()).padStart(2, '0');
        const dd = String(d.getDate()).padStart(2, '0');
        const mo = String(d.getMonth() + 1).padStart(2, '0');
        return `${hh}:${mm} ${dd}/${mo}`;
    }

    function showChatLog() {
        historyPanel.classList.add('d-none');
        log.classList.remove('d-none');
        form.classList.remove('d-none');
        log.scrollTop = log.scrollHeight;
    }

    function showHistoryPanel() {
        renderHistoryList();
        log.classList.add('d-none');
        form.classList.add('d-none');
        historyPanel.classList.remove('d-none');
    }

    function formatInline(str) {
        if (!str) return '';
        // Bold: **text**
        str = str.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
        // Inline code: `code`
        str = str.replace(/`([^`]+)`/g, '<code class="bg-light px-1 rounded">$1</code>');
        // Italic: *text*
        str = str.replace(/(^|[^\*])\*([^\*]+)\*([^\*]|$)/g, '$1<em>$2</em>$3');
        // Markdown Links: [text](url) - hỗ trợ tất cả các đường dẫn (/orders/1, /products/2, /cart, /login...) và URL ngoài
        str = str.replace(/\[([^\]]+)\]\(([^)]+)\)/g, function (match, text, url) {
            const cleanUrl = url.trim();
            const isExternal = cleanUrl.startsWith('http://') || cleanUrl.startsWith('https://');
            const targetAttr = isExternal ? ' target="_blank" rel="noopener noreferrer"' : '';
            return `<a href="${cleanUrl}" class="fw-semibold text-primary text-decoration-underline"${targetAttr}>${text}</a>`;
        });
        return str;
    }

    function parseMarkdown(text) {
        if (!text) return '';
        // Escape HTML
        let escaped = text
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;');

        // Xử lý Bảng Markdown (| Cột 1 | Cột 2 |)
        const lines = escaped.split('\n');
        let inTable = false;
        let tableHtml = '';
        let processedLines = [];

        for (let i = 0; i < lines.length; i++) {
            const line = lines[i].trim();
            if (line.startsWith('|') && line.endsWith('|')) {
                const cells = line.slice(1, -1).split('|').map(c => c.trim());
                if (!inTable) {
                    if (i + 1 < lines.length && lines[i + 1].trim().startsWith('|') && lines[i + 1].includes('-')) {
                        inTable = true;
                        tableHtml = '<table class="table table-sm table-bordered"><thead><tr>' +
                            cells.map(c => `<th>${formatInline(c)}</th>`).join('') +
                            '</tr></thead><tbody>';
                        i++; // bỏ qua dòng gạch ngang |---|---|
                        continue;
                    }
                } else {
                    tableHtml += '<tr>' + cells.map(c => `<td>${formatInline(c)}</td>`).join('') + '</tr>';
                    continue;
                }
            } else {
                if (inTable) {
                    tableHtml += '</tbody></table>';
                    processedLines.push(tableHtml);
                    tableHtml = '';
                    inTable = false;
                }
                processedLines.push(formatInline(line));
            }
        }
        if (inTable) {
            tableHtml += '</tbody></table>';
            processedLines.push(tableHtml);
        }

        return processedLines.join('<br>');
    }

    function renderMessage(text, cls) {
        const d = document.createElement('div');
        d.className = 'chat-msg ' + cls;
        d.innerHTML = parseMarkdown(text);
        log.append(d);
        log.scrollTop = log.scrollHeight;
        return d;
    }

    // ==========================================
    // LOGIC CHO NGƯỜI DÙNG ĐÃ ĐĂNG NHẬP (LƯU DB)
    // ==========================================
    async function loadSessionsFromDB() {
        try {
            const res = await fetch('/api/chat/sessions');
            if (res.ok) {
                return await res.json();
            }
        } catch (e) {
            console.warn('Lỗi tải phiên chat từ server', e);
        }
        return [];
    }

    async function loadSessionMessagesFromDB(sessionId) {
        try {
            const res = await fetch(`/api/chat/sessions/${sessionId}/messages`);
            if (res.ok) {
                return await res.json();
            }
        } catch (e) {
            console.warn('Lỗi tải tin nhắn từ server', e);
        }
        return [];
    }

    async function createSessionInDB() {
        try {
            const res = await fetch('/api/chat/sessions', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ title: 'Cuộc trò chuyện mới' })
            });
            if (res.ok) {
                return await res.json();
            }
        } catch (e) {
            console.warn('Lỗi tạo phiên chat mới', e);
        }
        return null;
    }

    async function deleteSessionInDB(sessionId) {
        try {
            await fetch(`/api/chat/sessions/${sessionId}`, { method: 'DELETE' });
        } catch (e) {
            console.warn('Lỗi xoá phiên chat', e);
        }
    }

    async function clearAllSessionsInDB() {
        try {
            await fetch('/api/chat/sessions', { method: 'DELETE' });
        } catch (e) {
            console.warn('Lỗi xoá tất cả phiên chat', e);
        }
    }

    // ==========================================
    // LOGIC CHO KHÁCH VÃNG LAI (LOCALSTORAGE)
    // ==========================================
    function getSessionsFromLocal() {
        try {
            const raw = localStorage.getItem(STORAGE_SESSIONS_KEY);
            return raw ? JSON.parse(raw) : [];
        } catch (e) {
            return [];
        }
    }

    function saveSessionsToLocal(sessions) {
        localStorage.setItem(STORAGE_SESSIONS_KEY, JSON.stringify(sessions));
    }

    function createSessionInLocal() {
        const sess = {
            id: 'sess_' + Date.now(),
            title: 'Cuộc trò chuyện mới',
            updatedAt: formatNow(),
            messages: [{ role: 'bot', content: DEFAULT_GREETING }]
        };
        const list = getSessionsFromLocal();
        list.unshift(sess);
        if (list.length > 20) list.splice(20);
        saveSessionsToLocal(list);
        currentSessionId = sess.id;
        localStorage.setItem(STORAGE_ACTIVE_KEY, sess.id);
        return sess;
    }

    // ==========================================
    // ĐIỀU HƯỚNG VÀ HIỂN THỊ CHUNG
    // ==========================================
    async function selectSession(sessionId) {
        if (!sessionId) return;
        currentSessionId = sessionId;
        localStorage.setItem(STORAGE_ACTIVE_KEY, sessionId);

        showChatLog();
        log.innerHTML = '<div class="text-center text-muted py-3 small">Đang tải tin nhắn...</div>';

        try {
            if (isLoggedIn) {
                const msgs = await loadSessionMessagesFromDB(sessionId);
                log.innerHTML = '';
                if (msgs && msgs.length > 0) {
                    msgs.forEach(m => renderMessage(m.content, m.role));
                } else {
                    renderMessage(DEFAULT_GREETING, 'bot');
                }
            } else {
                const list = getSessionsFromLocal();
                const sess = list.find(s => s.id === sessionId);
                log.innerHTML = '';
                if (sess && sess.messages && sess.messages.length > 0) {
                    sess.messages.forEach(m => renderMessage(m.content, m.role));
                } else {
                    renderMessage(DEFAULT_GREETING, 'bot');
                }
            }
        } catch (e) {
            console.error('Lỗi khi tải phiên chat', e);
            log.innerHTML = '';
            renderMessage('Không thể tải tin nhắn của phiên trò chuyện này.', 'bot');
        }
        log.scrollTop = log.scrollHeight;
    }

    async function renderHistoryList() {
        historyList.innerHTML = '<div class="text-center text-muted py-3">Đang tải...</div>';
        let sessions = [];

        if (isLoggedIn) {
            sessions = await loadSessionsFromDB();
        } else {
            sessions = getSessionsFromLocal();
        }

        historyList.innerHTML = '';
        if (!sessions || sessions.length === 0) {
            const empty = document.createElement('div');
            empty.className = 'text-center text-muted py-4';
            empty.textContent = 'Chưa có lịch sử trò chuyện.';
            historyList.appendChild(empty);
            return;
        }

        sessions.forEach(sess => {
            const item = document.createElement('div');
            item.className = 'list-group-item d-flex justify-content-between align-items-center history-item' +
                (sess.id === currentSessionId ? ' active' : '');
            item.style.cursor = 'pointer';

            const info = document.createElement('div');
            info.className = 'flex-grow-1 text-truncate me-2';

            const title = document.createElement('div');
            title.className = 'history-title text-truncate';
            title.textContent = sess.title || 'Cuộc trò chuyện';

            const meta = document.createElement('div');
            meta.className = 'history-time';
            const count = sess.messageCount !== undefined ? sess.messageCount : (sess.messages ? sess.messages.length : 0);
            meta.textContent = `${sess.updatedAt || ''} • ${count} tin`;

            info.appendChild(title);
            info.appendChild(meta);

            // Bấm vào bất kỳ đâu trên hàng để chọn và tải phiên trò chuyện
            item.onclick = () => selectSession(sess.id);

            const delBtn = document.createElement('span');
            delBtn.className = 'history-del';
            delBtn.innerHTML = '🗑️';
            delBtn.title = 'Xoá cuộc trò chuyện này';
            delBtn.onclick = async (e) => {
                e.stopPropagation(); // Ngăn chọn phiên khi bấm xoá
                if (isLoggedIn) {
                    await deleteSessionInDB(sess.id);
                } else {
                    const localList = getSessionsFromLocal().filter(s => s.id !== sess.id);
                    saveSessionsToLocal(localList);
                }
                if (currentSessionId === sess.id) {
                    await startNewSession();
                } else {
                    renderHistoryList();
                }
            };

            item.appendChild(info);
            item.appendChild(delBtn);
            historyList.appendChild(item);
        });
    }

    async function startNewSession() {
        if (isLoggedIn) {
            const newSess = await createSessionInDB();
            if (newSess) {
                currentSessionId = newSess.id;
            } else {
                currentSessionId = 'sess_' + Date.now();
            }
            localStorage.setItem(STORAGE_ACTIVE_KEY, currentSessionId);
            log.innerHTML = '';
            renderMessage(DEFAULT_GREETING, 'bot');
            showChatLog();
        } else {
            const sess = createSessionInLocal();
            selectSession(sess.id);
        }
    }

    // Nút "➕ Mới"
    if (newBtn) {
        newBtn.onclick = async () => {
            await startNewSession();
            input.focus();
        };
    }

    // Nút "📜 Lịch sử"
    if (historyBtn) {
        historyBtn.onclick = () => {
            if (historyPanel.classList.contains('d-none')) {
                showHistoryPanel();
            } else {
                showChatLog();
            }
        };
    }

    // Nút "Xóa hết" trong lịch sử
    if (clearAllBtn) {
        clearAllBtn.onclick = async () => {
            if (confirm('Bạn có chắc muốn xoá toàn bộ lịch sử trò chuyện của tài khoản này không?')) {
                if (isLoggedIn) {
                    await clearAllSessionsInDB();
                } else {
                    localStorage.removeItem(STORAGE_SESSIONS_KEY);
                    localStorage.removeItem(STORAGE_ACTIVE_KEY);
                }
                await startNewSession();
                showChatLog();
            }
        };
    }

    // Khởi tạo khi vào trang
    async function init() {
        if (isLoggedIn) {
            const sessions = await loadSessionsFromDB();
            const savedActive = localStorage.getItem(STORAGE_ACTIVE_KEY);
            if (savedActive && sessions.some(s => s.id === savedActive)) {
                await selectSession(savedActive);
            } else if (sessions && sessions.length > 0) {
                await selectSession(sessions[0].id);
            } else {
                await startNewSession();
            }
        } else {
            const sessions = getSessionsFromLocal();
            const savedActive = localStorage.getItem(STORAGE_ACTIVE_KEY);
            if (savedActive && sessions.some(s => s.id === savedActive)) {
                selectSession(savedActive);
            } else if (sessions && sessions.length > 0) {
                selectSession(sessions[0].id);
            } else {
                const s = createSessionInLocal();
                selectSession(s.id);
            }
        }
    }

    init();

    // Gửi tin nhắn
    form.onsubmit = async (e) => {
        e.preventDefault();
        const msg = input.value.trim();
        if (!msg) return;
        input.value = '';

        renderMessage(msg, 'user');

        if (!isLoggedIn) {
            const list = getSessionsFromLocal();
            let sess = list.find(s => s.id === currentSessionId);
            if (!sess) sess = createSessionInLocal();
            if (sess.title === 'Cuộc trò chuyện mới' || sess.messages.length <= 1) {
                sess.title = msg.length > 32 ? msg.slice(0, 32) + '...' : msg;
            }
            sess.updatedAt = formatNow();
            sess.messages.push({ role: 'user', content: msg });
            saveSessionsToLocal(list);
        }

        const wait = renderMessage('...', 'bot');
        try {
            let bodyData = { message: msg };
            if (isLoggedIn) {
                bodyData.sessionId = currentSessionId;
            } else {
                const list = getSessionsFromLocal();
                const sess = list.find(s => s.id === currentSessionId);
                bodyData.history = sess ? sess.messages.slice(-8) : [];
            }

            const r = await fetch('/api/chat', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(bodyData)
            });

            const data = await r.json();
            wait.remove();
            const reply = data.reply || 'Xin lỗi, có lỗi xảy ra.';
            renderMessage(reply, 'bot');

            if (isLoggedIn && data.sessionId) {
                currentSessionId = data.sessionId;
                localStorage.setItem(STORAGE_ACTIVE_KEY, currentSessionId);
            } else if (!isLoggedIn) {
                const list = getSessionsFromLocal();
                const sess = list.find(s => s.id === currentSessionId);
                if (sess) {
                    sess.messages.push({ role: 'bot', content: reply });
                    saveSessionsToLocal(list);
                }
            }
        } catch (err) {
            wait.remove();
            renderMessage('Xin lỗi, không kết nối được máy chủ.', 'bot');
        }
    };
})();
