const API_BASE = 'http://localhost:8080/api';
let currentStocks = {}; // ticker -> stock data 저장
let selectedTicker = null;

// 시작 시 실행
window.onload = () => {
    const token = localStorage.getItem('accessToken');
    if (!token) {
        alert('로그인이 필요합니다.');
        window.location.href = '/index.html';
        return;
    }
    
    fetchUserInfo();
    fetchStocks();
    // 5초마다 주식 가격 및 내 자산 갱신
    setInterval(fetchStocks, 5000);
    setInterval(fetchUserInfo, 5000);
};

// 권한이 필요한 fetch 래퍼 함수
async function fetchWithAuth(url, options = {}) {
    const token = localStorage.getItem('accessToken');
    const headers = {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
        ...options.headers
    };
    const response = await fetch(url, { ...options, headers });
    if (response.status === 401 || response.status === 403) {
        alert('세션이 만료되었습니다. 다시 로그인해주세요.');
        logout();
    }
    return response;
}

// 유저 정보 가져오기
async function fetchUserInfo() {
    try {
        const res = await fetchWithAuth(`${API_BASE}/user/me`);
        if (res.ok) {
            const data = await res.json();
            document.getElementById('userNickname').innerText = data.nickname;
            document.getElementById('walletBalance').innerText = Number(data.walletBalance).toLocaleString() + ' 원';
            renderHoldings(data.holdings);
        }
    } catch (e) {
        console.error(e);
    }
}

// 주식 목록 가져오기
async function fetchStocks() {
    try {
        const res = await fetchWithAuth(`${API_BASE}/stocks`);
        if (res.ok) {
            const stocks = await res.json();
            renderStocks(stocks);
        }
    } catch (e) {
        console.error(e);
    }
}

// 주식 목록 렌더링 (깜빡임 효과 포함)
function renderStocks(stocks) {
    const list = document.getElementById('stockList');
    list.innerHTML = '';

    stocks.forEach(stock => {
        // 이전 가격과 비교
        const prevData = currentStocks[stock.ticker];
        let flashClass = '';
        if (prevData) {
            if (stock.currentPrice > prevData.currentPrice) flashClass = 'flash-up';
            else if (stock.currentPrice < prevData.currentPrice) flashClass = 'flash-down';
        }
        currentStocks[stock.ticker] = stock; // 업데이트

        const diff = stock.currentPrice - stock.previousClose;
        const diffPercent = ((diff / stock.previousClose) * 100).toFixed(2);
        const colorClass = diff > 0 ? 'price-up' : (diff < 0 ? 'price-down' : '');
        const sign = diff > 0 ? '▲' : (diff < 0 ? '▼' : '-');

        const div = document.createElement('div');
        div.className = `stock-item ${flashClass}`;
        div.onclick = () => openModal(stock);
        div.innerHTML = `
            <div>
                <div class="stock-name">${stock.companyName}</div>
                <div class="stock-ticker">${stock.ticker}</div>
            </div>
            <div class="stock-price ${colorClass}">
                ${Number(stock.currentPrice).toLocaleString()}원
                <div style="font-size:12px; text-align:right;">${sign} ${Math.abs(diffPercent)}%</div>
            </div>
        `;
        list.appendChild(div);
    });
}

// 보유 주식 렌더링
function renderHoldings(holdings) {
    const list = document.getElementById('holdingsList');
    if (!holdings || holdings.length === 0) {
        list.innerHTML = '<div style="text-align:center; color:#94a3b8; margin-top:20px;">보유한 주식이 없습니다.</div>';
        return;
    }

    list.innerHTML = '';
    holdings.forEach(h => {
        // 수익률 계산
        const currentPrice = currentStocks[h.ticker]?.currentPrice || h.currentPrice;
        const diff = currentPrice - h.averagePrice;
        const diffPercent = ((diff / h.averagePrice) * 100).toFixed(2);
        const colorClass = diff > 0 ? 'price-up' : (diff < 0 ? 'price-down' : '');

        const div = document.createElement('div');
        div.className = 'holding-item';
        div.innerHTML = `
            <div class="holding-header">
                <span>${h.companyName}</span>
                <span class="${colorClass}">${currentPrice.toLocaleString()}원 (${diff > 0 ? '+' : ''}${diffPercent}%)</span>
            </div>
            <div class="holding-details">
                <span>보유 수량: ${h.quantity}주</span>
                <span>평단가: ${h.averagePrice.toLocaleString()}원</span>
            </div>
        `;
        list.appendChild(div);
    });
}

// 모달 조작
function openModal(stock) {
    selectedTicker = stock.ticker;
    document.getElementById('modalTitle').innerText = `${stock.companyName} 거래`;
    document.getElementById('modalPrice').innerText = `현재가: ${Number(stock.currentPrice).toLocaleString()}원`;
    document.getElementById('tradeQuantity').value = 1;
    document.getElementById('modalMessage').style.display = 'none';
    document.getElementById('tradeModal').style.display = 'block';
}

function closeModal() {
    document.getElementById('tradeModal').style.display = 'none';
}

function showModalMessage(msg, type) {
    const box = document.getElementById('modalMessage');
    box.innerText = msg;
    box.className = 'message-box ' + type;
}

// 매수 / 매도 실행
async function executeTrade(type) {
    const quantity = document.getElementById('tradeQuantity').value;
    if (quantity < 1) {
        showModalMessage('수량은 1 이상이어야 합니다.', 'error');
        return;
    }

    try {
        const res = await fetchWithAuth(`${API_BASE}/trades/${type}`, {
            method: 'POST',
            body: JSON.stringify({ ticker: selectedTicker, quantity: Number(quantity) })
        });

        const data = await res.text();
        if (res.ok) {
            showModalMessage(data, 'success');
            fetchUserInfo(); // 지갑 및 보유 주식 갱신
            setTimeout(closeModal, 1500);
        } else {
            // 에러 메시지 추출
            try {
                const err = JSON.parse(data);
                showModalMessage(err.message || '거래 실패', 'error');
            } catch {
                showModalMessage(data, 'error');
            }
        }
    } catch (e) {
        showModalMessage('서버 오류가 발생했습니다.', 'error');
    }
}

function logout() {
    localStorage.removeItem('accessToken');
    window.location.href = '/index.html';
}
