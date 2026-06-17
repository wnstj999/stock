function switchTab(tab) {
    // 탭 버튼 스타일 변경
    document.querySelectorAll('.tab-btn').forEach(btn => btn.classList.remove('active'));
    // event.target 대신 좀 더 안전한 방법 사용
    if (event && event.target) {
        event.target.classList.add('active');
    } else {
        document.querySelector(`.tab-btn[onclick="switchTab('${tab}')"]`).classList.add('active');
    }

    // 폼 전환
    document.querySelectorAll('.form-section').forEach(form => form.classList.remove('active'));
    document.getElementById(tab + 'Form').classList.add('active');
    
    // 메세지 박스 초기화
    hideMessage();
}

function showMessage(msg, type) {
    const box = document.getElementById('messageBox');
    box.textContent = msg;
    box.className = 'message-box ' + type;
}

function hideMessage() {
    const box = document.getElementById('messageBox');
    box.style.display = 'none';
    box.className = 'message-box';
}

async function handleSignup(e) {
    e.preventDefault();
    const email = document.getElementById('signupEmail').value;
    const password = document.getElementById('signupPassword').value;
    const nickname = document.getElementById('signupNickname').value;

    try {
        const response = await fetch('/api/auth/signup', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password, nickname })
        });
        
        const data = await response.text();
        if (response.ok) {
            showMessage(data, 'success');
            setTimeout(() => switchTab('login'), 2000); // 2초 후 로그인 창으로 전환
        } else {
            try {
                const err = JSON.parse(data);
                showMessage(err.message || '가입 실패', 'error');
            } catch {
                showMessage(data, 'error');
            }
        }
    } catch (error) {
        showMessage('서버와 연결할 수 없습니다.', 'error');
    }
}

async function handleLogin(e) {
    e.preventDefault();
    const email = document.getElementById('loginEmail').value;
    const password = document.getElementById('loginPassword').value;

    try {
        const response = await fetch('/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password })
        });

        if (response.ok) {
            const data = await response.json();
            // 브라우저의 localStorage에 JWT 토큰을 안전하게 저장합니다!
            localStorage.setItem('accessToken', data.accessToken);
            showMessage('🎉 로그인 성공! (JWT 토큰 발급 완료)', 'success');
            setTimeout(() => {
                window.location.href = '/dashboard.html';
            }, 1000);
        } else {
            showMessage('이메일이나 비밀번호가 틀렸습니다.', 'error');
        }
    } catch (error) {
        showMessage('서버와 연결할 수 없습니다.', 'error');
    }
}
