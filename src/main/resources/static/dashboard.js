const API_BASE = 'http://localhost:8080/api';
let currentStocks = {}; 
let selectedTicker = 'KRW-BTC';
let selectedCompanyName = '비트코인';
let holdingsData = []; 
let currentOrderType = 'buy'; 

let tvWidget = null;
let upbitSocket = null;

window.onload = async () => {
    const token = localStorage.getItem('accessToken');
    if (!token) {
        alert('로그인이 필요합니다.');
        window.location.href = '/index.html';
        return;
    }
    
    initTradingViewWidget('KRW-BTC');
    await fetchStocks(); 
    await fetchUserInfo();

    selectCoin('KRW-BTC', '비트코인'); // 초기 선택
    
    setInterval(fetchUserInfo, 5000);
};

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

async function fetchUserInfo() {
    try {
        const res = await fetchWithAuth(`${API_BASE}/user/me`);
        if (res.ok) {
            const data = await res.json();
            document.getElementById('userNickname').innerText = data.nickname;
            document.getElementById('walletBalance').innerText = Number(data.walletBalance).toLocaleString() + ' KRW';
            holdingsData = data.holdings;
            renderHoldings(); 
        }
    } catch (e) { console.error(e); }
}

async function fetchStocks() {
    try {
        const res = await fetchWithAuth(`${API_BASE}/stocks`);
        if (res.ok) {
            const stocks = await res.json();
            stocks.forEach(s => {
                currentStocks[s.ticker] = { 
                    ...s, 
                    currentPrice: 0, 
                    previousClose: 0,
                    highPrice: 0,
                    lowPrice: 0,
                    volume24h: 0
                };
            });
            connectUpbitWebSocket(stocks.map(s => s.ticker));
        }
    } catch (e) { console.error(e); }
}

function connectUpbitWebSocket(tickers) {
    if (upbitSocket) upbitSocket.close();
    upbitSocket = new WebSocket('wss://api.upbit.com/websocket/v1');
    upbitSocket.binaryType = 'arraybuffer';
    
    upbitSocket.onopen = () => {
        const msg = [ 
            {"ticket": "mockstock-client"}, 
            {"type": "ticker", "codes": tickers},
            {"type": "orderbook", "codes": tickers}
        ];
        upbitSocket.send(JSON.stringify(msg));
    };
    
    upbitSocket.onmessage = async (evt) => {
        const enc = new TextDecoder("utf-8");
        const data = JSON.parse(enc.decode(evt.data));
        
        if (data.type === 'ticker') {
            const ticker = data.code;
            const price = data.trade_price;
            const prevClose = data.prev_closing_price;
            const companyName = currentStocks[ticker]?.companyName || ticker;

            currentStocks[ticker].currentPrice = price;
            currentStocks[ticker].previousClose = prevClose;
            currentStocks[ticker].highPrice = data.high_price;
            currentStocks[ticker].lowPrice = data.low_price;
            currentStocks[ticker].volume24h = data.acc_trade_volume_24h;

            renderSingleStockItem(ticker, companyName, price, prevClose);
            
            // 실시간 선택 코인 시세 및 24h 스탯 갱신
            if (ticker === selectedTicker) {
                updateSelectedCoinStats(ticker, price, prevClose, data.high_price, data.low_price, data.acc_trade_volume_24h);
                updateExpectedTotal();
            }
            if (holdingsData.some(h => h.ticker === ticker)) renderHoldings();
        } else if (data.type === 'orderbook' && data.code === selectedTicker) {
            renderOrderbook(data.orderbook_units);
        }
    };
}

function updateSelectedCoinStats(ticker, price, prevClose, high, low, volume) {
    const headerPrice = document.getElementById('chartPrice');
    const diff = price - prevClose;
    const diffPercent = ((diff / prevClose) * 100).toFixed(2);
    const colorClass = diff > 0 ? 'price-up' : (diff < 0 ? 'price-down' : '');
    const sign = diff > 0 ? '+' : '';

    headerPrice.innerText = price.toLocaleString() + ' KRW';
    headerPrice.className = 'chart-price ' + colorClass;

    // 24시간 최고가, 최저가, 거래량 바인딩
    document.getElementById('tickerHigh').innerText = high.toLocaleString();
    document.getElementById('tickerLow').innerText = low.toLocaleString();
    document.getElementById('tickerVol').innerText = Math.round(volume).toLocaleString();

    // 호가창 중간 현재가 영역도 동시에 실시간 업데이트
    const midPriceText = document.getElementById('midPriceText');
    const midPriceChange = document.getElementById('midPriceChange');
    if (midPriceText && midPriceChange) {
        midPriceText.innerText = price.toLocaleString();
        midPriceText.className = colorClass;
        midPriceChange.innerText = `${sign}${diffPercent}%`;
        midPriceChange.className = 'mid-change ' + colorClass;
    }
}

function renderOrderbook(units) {
    const sellDiv = document.getElementById('orderbookSell');
    const buyDiv = document.getElementById('orderbookBuy');
    
    let sellHtml = '';
    let buyHtml = '';
    
    const maxAskSize = Math.max(...units.map(u => u.ask_size));
    const maxBidSize = Math.max(...units.map(u => u.bid_size));
    
    // 호가 클릭 시 해당 호가 물량이 입력되도록 수정
    units.forEach(u => {
        const askWidth = (u.ask_size / maxAskSize) * 100;
        sellHtml += `
            <div class="ob-row" onclick="document.getElementById('tradeQuantity').value='${u.ask_size.toFixed(4)}'">
                <div class="ob-bg ob-sell-bg" style="width: ${askWidth}%"></div>
                <div class="ob-price ob-sell-price">${u.ask_price.toLocaleString()}</div>
                <div class="ob-size">${u.ask_size.toFixed(4)}</div>
            </div>
        `;
        
        const bidWidth = (u.bid_size / maxBidSize) * 100;
        buyHtml += `
            <div class="ob-row" onclick="document.getElementById('tradeQuantity').value='${u.bid_size.toFixed(4)}'">
                <div class="ob-bg ob-buy-bg" style="width: ${bidWidth}%"></div>
                <div class="ob-price ob-buy-price">${u.bid_price.toLocaleString()}</div>
                <div class="ob-size">${u.bid_size.toFixed(4)}</div>
            </div>
        `;
    });
    
    sellDiv.innerHTML = sellHtml;
    buyDiv.innerHTML = buyHtml;
}

function renderSingleStockItem(ticker, companyName, price, prevClose) {
    const list = document.getElementById('stockList');
    let itemDiv = document.getElementById('stock-item-' + ticker);
    
    const diff = price - prevClose;
    const diffPercent = ((diff / prevClose) * 100).toFixed(2);
    const colorClass = diff > 0 ? 'price-up' : (diff < 0 ? 'price-down' : '');
    const sign = diff > 0 ? '+' : '';

    if (!itemDiv) {
        itemDiv = document.createElement('div');
        itemDiv.id = 'stock-item-' + ticker;
        itemDiv.className = 'stock-item' + (ticker === selectedTicker ? ' active' : '');
        itemDiv.onclick = () => selectCoin(ticker, companyName);
        list.appendChild(itemDiv);
    } else {
        const oldPriceText = itemDiv.getAttribute('data-price');
        if (oldPriceText && Number(oldPriceText) < price) {
            itemDiv.classList.remove('flash-down', 'flash-up');
            void itemDiv.offsetWidth;
            itemDiv.classList.add('flash-up');
        } else if (oldPriceText && Number(oldPriceText) > price) {
            itemDiv.classList.remove('flash-down', 'flash-up');
            void itemDiv.offsetWidth;
            itemDiv.classList.add('flash-down');
        }
        itemDiv.className = 'stock-item' + (ticker === selectedTicker ? ' active' : '');
    }
    
    itemDiv.setAttribute('data-price', price);
    itemDiv.innerHTML = `
        <div class="stock-name-group">
            <span class="stock-ticker">${ticker.replace('KRW-', '')}</span>
            <span class="stock-name">${companyName}</span>
        </div>
        <div class="stock-price ${colorClass}">${price.toLocaleString()}</div>
        <div class="stock-change ${colorClass}">${sign}${diffPercent}%</div>
    `;
}

function renderHoldings() {
    const list = document.getElementById('holdingsList');
    if (!holdingsData || holdingsData.length === 0) {
        list.innerHTML = '<tr><td colspan="5" style="text-align:center; color:#848e9c; padding:20px;">보유 자산이 없습니다.</td></tr>';
        return;
    }

    let html = '';
    holdingsData.forEach(h => {
        const livePrice = currentStocks[h.ticker]?.currentPrice || h.averagePrice;
        const diff = livePrice - h.averagePrice;
        const diffPercent = ((diff / h.averagePrice) * 100).toFixed(2);
        const colorClass = diff > 0 ? 'price-up' : (diff < 0 ? 'price-down' : '');
        
        html += `
        <tr>
            <td style="font-weight: 600;">${h.ticker.replace('KRW-', '')}</td>
            <td>${h.quantity.toFixed(4)}</td>
            <td>${h.averagePrice.toLocaleString()}</td>
            <td>${livePrice.toLocaleString()}</td>
            <td class="${colorClass}">${diff > 0 ? '+' : ''}${diffPercent}%</td>
        </tr>`;
    });
    list.innerHTML = html;
}

function selectCoin(ticker, companyName) {
    selectedTicker = ticker;
    selectedCompanyName = companyName;
    
    document.querySelectorAll('.stock-item').forEach(el => el.classList.remove('active'));
    const activeItem = document.getElementById('stock-item-' + ticker);
    if(activeItem) activeItem.classList.add('active');
    
    document.getElementById('chartTitle').innerText = `${ticker.replace('KRW-', '')}/KRW`;
    document.getElementById('orderTargetCoin').innerText = ticker.replace('KRW-', '');
    
    // 오더북 화면 로딩 스피너/플레이스홀더
    document.getElementById('orderbookSell').innerHTML = '<div style="text-align:center; color:#848e9c; padding:20px; font-size:11px;">로딩 중...</div>';
    document.getElementById('orderbookBuy').innerHTML = '';
    
    // 비율 버튼 선택 해제
    document.querySelectorAll('.ratio-btn').forEach(btn => btn.classList.remove('active'));
    document.getElementById('tradeQuantity').value = 1;

    const receiptCard = document.getElementById('receiptCard');
    if (receiptCard) receiptCard.style.display = 'none';

    initTradingViewWidget(ticker);
    updateExpectedTotal();
}

function switchOrderTab(type) {
    currentOrderType = type;
    document.getElementById('tabBuy').classList.remove('active');
    document.getElementById('tabSell').classList.remove('active');
    
    // 비율 버튼 선택 해제 및 금액 리셋
    document.querySelectorAll('.ratio-btn').forEach(btn => btn.classList.remove('active'));
    document.getElementById('tradeQuantity').value = 1;

    const btn = document.getElementById('executeBtn');
    const orderSection = document.querySelector('.order-section');
    
    if(type === 'buy') {
        document.getElementById('tabBuy').classList.add('active');
        btn.className = 'execute-btn buy-btn';
        btn.innerHTML = `매수 <span id="orderTargetCoin">${selectedTicker.replace('KRW-', '')}</span>`;
        if (orderSection) {
            orderSection.classList.add('buy-active');
            orderSection.classList.remove('sell-active');
        }
    } else {
        document.getElementById('tabSell').classList.add('active');
        btn.className = 'execute-btn sell-btn';
        btn.innerHTML = `매도 <span id="orderTargetCoin">${selectedTicker.replace('KRW-', '')}</span>`;
        if (orderSection) {
            orderSection.classList.add('sell-active');
            orderSection.classList.remove('buy-active');
        }
    }
    updateExpectedTotal();
}

// 비율 선택 버튼 핸들러
function selectRatio(ratio) {
    document.querySelectorAll('.ratio-btn').forEach(btn => btn.classList.remove('active'));
    event.currentTarget.classList.add('active');

    const amountInput = document.getElementById('tradeQuantity');
    const currentPrice = currentStocks[selectedTicker]?.currentPrice;
    
    if (!currentPrice || currentPrice <= 0) {
        alert('시세 정보를 불러오는 중입니다. 잠시 후 다시 시도해주세요.');
        return;
    }

    if (currentOrderType === 'buy') {
        // 내 가용 원화 잔고 찾기
        const balanceText = document.getElementById('walletBalance').innerText;
        const balance = Number(balanceText.replace(/[^0-9.]/g, ''));
        if (isNaN(balance) || balance <= 0) {
            amountInput.value = 0;
            updateExpectedTotal();
            return;
        }
        // 매수 가능 코인 수량 = (잔고 * 비율) / 현재 시세
        const targetQuantity = (balance * ratio) / currentPrice;
        amountInput.value = targetQuantity.toFixed(4);
    } else {
        // 보유 코인 잔액 찾기
        const holding = holdingsData.find(h => h.ticker === selectedTicker);
        if (!holding || holding.quantity <= 0) {
            amountInput.value = 0;
            updateExpectedTotal();
            return;
        }
        // 매도 가능 코인 수량 = 보유수량 * 비율
        const targetQuantity = holding.quantity * ratio;
        amountInput.value = targetQuantity.toFixed(4);
    }
    updateExpectedTotal();
}

// 마켓 검색 필터링
function filterMarkets() {
    const query = document.getElementById('marketSearch').value.toUpperCase().trim();
    document.querySelectorAll('.stock-item').forEach(item => {
        const ticker = item.querySelector('.stock-ticker').innerText;
        const name = item.querySelector('.stock-name').innerText;
        if (ticker.includes(query) || name.includes(query)) {
            item.style.display = 'grid';
        } else {
            item.style.display = 'none';
        }
    });
}

function showOrderMessage(msg, type) {
    const box = document.getElementById('orderMessage');
    box.innerText = msg;
    box.className = 'message-box ' + type;
    setTimeout(() => { box.style.display = 'none'; }, 3000);
}

async function executeTrade() {
    const quantity = document.getElementById('tradeQuantity').value;
    if (quantity <= 0) {
        showOrderMessage('수량은 0보다 커야 합니다.', 'error');
        return;
    }

    try {
        const res = await fetchWithAuth(`${API_BASE}/trades/${currentOrderType}`, {
            method: 'POST',
            body: JSON.stringify({ ticker: selectedTicker, quantity: Number(quantity) })
        });

        const data = await res.text();
        if (res.ok) {
            showOrderMessage('주문이 정상 체결되었습니다.', 'success');
            const currentPrice = currentStocks[selectedTicker]?.currentPrice || 0;
            renderReceiptCard(currentOrderType, currentPrice, Number(quantity));
            fetchUserInfo(); 
        } else {
            try {
                const err = JSON.parse(data);
                showOrderMessage(err.message || '거래 실패', 'error');
            } catch {
                showOrderMessage(data, 'error');
            }
        }
    } catch (e) {
        showOrderMessage('서버 오류가 발생했습니다.', 'error');
    }
}

// 실시간 예상 결제액 업데이트 데코레이터
function updateExpectedTotal() {
    const currentPrice = currentStocks[selectedTicker]?.currentPrice || 0;
    const qtyInput = document.getElementById('tradeQuantity');
    const qty = Number(qtyInput.value) || 0;
    const expectedTotalEl = document.getElementById('expectedTotal');
    
    const total = currentPrice * qty;
    expectedTotalEl.innerText = Math.round(total).toLocaleString() + ' KRW';
    
    if (currentOrderType === 'buy') {
        const balanceText = document.getElementById('walletBalance').innerText;
        const balance = Number(balanceText.replace(/[^0-9.]/g, '')) || 0;
        if (total > balance) {
            expectedTotalEl.classList.add('warning');
        } else {
            expectedTotalEl.classList.remove('warning');
        }
    } else {
        const holding = holdingsData.find(h => h.ticker === selectedTicker);
        const maxQty = holding ? holding.quantity : 0;
        if (qty > maxQty) {
            expectedTotalEl.classList.add('warning');
        } else {
            expectedTotalEl.classList.remove('warning');
        }
    }
}

// 주문 체결 영수증 카드 렌더링
function renderReceiptCard(type, price, qty) {
    const receiptCard = document.getElementById('receiptCard');
    const typeEl = document.getElementById('receiptType');
    const priceEl = document.getElementById('receiptPrice');
    const qtyEl = document.getElementById('receiptQty');
    const totalEl = document.getElementById('receiptTotal');
    
    const coinName = selectedTicker.replace('KRW-', '');
    
    typeEl.innerText = type === 'buy' ? '매수 체결 (BUY)' : '매도 체결 (SELL)';
    typeEl.style.color = type === 'buy' ? 'var(--binance-up)' : 'var(--binance-down)';
    
    priceEl.innerText = price.toLocaleString() + ' KRW';
    qtyEl.innerText = qty.toFixed(4) + ' ' + coinName;
    totalEl.innerText = Math.round(price * qty).toLocaleString() + ' KRW';
    
    receiptCard.style.display = 'block';
    
    if (window.receiptTimeout) clearTimeout(window.receiptTimeout);
    window.receiptTimeout = setTimeout(() => {
        receiptCard.style.display = 'none';
    }, 6000);
}

// ----------------- 차트 로직 -----------------
function initTradingViewWidget(ticker) {
    const symbol = "UPBIT:" + ticker.replace('KRW-', '') + "KRW";
    
    tvWidget = new TradingView.widget({
        "width": "100%",
        "height": "100%",
        "symbol": symbol,
        "interval": "1",
        "timezone": "Asia/Seoul",
        "theme": "dark",
        "style": "1",
        "locale": "ko",
        "toolbar_bg": "#161a1e",
        "enable_publishing": false,
        "hide_side_toolbar": false,
        "allow_symbol_change": true,
        "container_id": "tvchart",
        "studies": [
            "MASimple@tv-basicstudies"
        ]
    });
}

function logout() {
    localStorage.removeItem('accessToken');
    window.location.href = '/index.html';
}

// ----------------- AI 수동 분석 로직 -----------------
async function analyzeWithAI() {
    const btn = document.getElementById('aiPredictBtn');
    const resultBox = document.getElementById('aiResultBox');
    const directionEl = document.getElementById('aiDirection');
    const confidenceEl = document.getElementById('aiConfidence');
    const reasonEl = document.getElementById('aiReason');

    btn.disabled = true;
    btn.innerText = '⏳ AI 분석 중...';
    resultBox.style.display = 'none';

    try {
        const res = await fetchWithAuth(`${API_BASE}/auto-trade/predict?ticker=${selectedTicker}`);
        if (!res.ok) {
            throw new Error('AI 분석 요청 실패');
        }

        const data = await res.json();
        
        let predictionText = data.prediction;
        let colorStyle = 'var(--text-main)';

        if (data.prediction === 'BUY') {
            predictionText = '매수 (상승)';
            colorStyle = 'var(--binance-up)';
        } else if (data.prediction === 'SELL') {
            predictionText = '매도 (하락)';
            colorStyle = 'var(--binance-down)';
        } else if (data.prediction === 'HOLD') {
            predictionText = '관망 (보유)';
            colorStyle = 'var(--text-muted)';
        } else if (data.prediction === 'ERROR') {
            predictionText = '분석 오류';
            colorStyle = 'var(--binance-down)'; // 하락/에러 빨강
        }

        directionEl.innerText = predictionText;
        directionEl.style.color = colorStyle;
        confidenceEl.innerText = `(신뢰도: ${data.confidence.toFixed(1)}%)`;
        reasonEl.innerText = data.reason;

        resultBox.style.display = 'flex';
    } catch (e) {
        alert('AI 분석 중 오류가 발생했습니다: ' + e.message);
    } finally {
        btn.disabled = false;
        btn.innerText = '🔍 현재 시장 분석하기';
    }
}

// ----------------- 모달 (History / Ranking) 로직 -----------------
async function showHistoryModal() {
    document.getElementById('historyModal').style.display = 'flex';
    try {
        const res = await fetchWithAuth(`${API_BASE}/trades/history`);
        const list = await res.json();
        
        let html = '';
        list.forEach(h => {
            const date = new Date(h.tradeDate).toLocaleString();
            const color = h.tradeType === 'BUY' ? 'color: var(--binance-up)' : 'color: var(--binance-down)';
            html += `
                <tr style="border-bottom: 1px solid var(--border-color);">
                    <td style="padding: 8px;">${date}</td>
                    <td style="padding: 8px; font-weight: 600;">${h.ticker.replace('KRW-', '')}</td>
                    <td style="padding: 8px; ${color}">${h.tradeType}</td>
                    <td style="padding: 8px; text-align: right;">${h.price.toLocaleString()}</td>
                    <td style="padding: 8px; text-align: right;">${h.quantity}</td>
                </tr>
            `;
        });
        document.getElementById('historyList').innerHTML = html;
    } catch (e) {
        document.getElementById('historyList').innerHTML = '<tr><td colspan="5" style="text-align:center;">불러오기 실패</td></tr>';
    }
}
function closeHistoryModal() { document.getElementById('historyModal').style.display = 'none'; }

async function showRankingModal() {
    document.getElementById('rankingModal').style.display = 'flex';
    document.getElementById('rankingList').innerHTML = '<tr><td colspan="3" style="text-align:center; padding: 20px;">실시간 업비트 가격 조회 중...<br>(수 초가 소요될 수 있습니다)</td></tr>';
    try {
        const res = await fetchWithAuth(`${API_BASE}/ranking`);
        const list = await res.json();
        
        let html = '';
        list.forEach(r => {
            let rankIcon = r.rank;
            if (r.rank === 1) rankIcon = '🥇 1';
            else if (r.rank === 2) rankIcon = '🥈 2';
            else if (r.rank === 3) rankIcon = '🥉 3';
            
            html += `
                <tr style="border-bottom: 1px solid var(--border-color);">
                    <td style="padding: 8px; font-weight: 600; color: #fcd535;">${rankIcon}</td>
                    <td style="padding: 8px;">${r.nickname}</td>
                    <td style="padding: 8px; text-align: right; font-weight: 600;">${r.totalAsset.toLocaleString()}</td>
                </tr>
            `;
        });
        document.getElementById('rankingList').innerHTML = html;
    } catch (e) {
        document.getElementById('rankingList').innerHTML = '<tr><td colspan="3" style="text-align:center;">불러오기 실패</td></tr>';
    }
}
function closeRankingModal() { document.getElementById('rankingModal').style.display = 'none'; }
