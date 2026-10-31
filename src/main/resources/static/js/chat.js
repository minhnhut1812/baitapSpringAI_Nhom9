(function () {
    const box = document.getElementById('chatBox');
    if (!box) return;
    const log = document.getElementById('chatLog');
    const input = document.getElementById('chatInput');
    const toggle = () => box.classList.toggle('d-none');
    document.getElementById('chatToggle').onclick = toggle;
    document.getElementById('chatClose').onclick = toggle;

    function add(text, cls) {
        const d = document.createElement('div');
        d.className = 'chat-msg ' + cls;
        // chỉ render link markdown [text](/products/id); còn lại là text thuần (chống XSS)
        const re = /\[([^\]]+)\]\((\/products\/\d+)\)/g;
        let last = 0, m;
        while ((m = re.exec(text)) !== null) {
            d.append(text.slice(last, m.index));
            const a = document.createElement('a');
            a.href = m[2];
            a.textContent = m[1];
            d.append(a);
            last = re.lastIndex;
        }
        d.append(text.slice(last));
        log.append(d);
        log.scrollTop = log.scrollHeight;
        return d;
    }

    document.getElementById('chatForm').onsubmit = async (e) => {
        e.preventDefault();
        const msg = input.value.trim();
        if (!msg) return;
        input.value = '';
        add(msg, 'user');
        const wait = add('...', 'bot');
        try {
            const r = await fetch('/api/chat', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({message: msg})
            });
            const data = await r.json();
            wait.remove();
            add(data.reply || 'Xin lỗi, có lỗi xảy ra.', 'bot');
        } catch (err) {
            wait.remove();
            add('Xin lỗi, không kết nối được máy chủ.', 'bot');
        }
    };
})();
