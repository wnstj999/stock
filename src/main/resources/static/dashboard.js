const API_BASE = 'http://localhost:8080/api';
let currentStocks = {}; 
let selectedTicker = 'KRW-BTC';
let selectedCompanyName = '비트코인';
let holdingsData = []; 
let currentOrderType = 'buy'; 
let selectedLeverage = 1; 
let selectedOrderUnit = 'qty'; 
let currentBottomTab = 'pos'; 
 
 

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
    
    // 백그라운드 실시간 동기화 및 폴백 타이머 작동
    setInterval(fetchUserInfo, 5000);
    setInterval(fetchStocks, 3000);
    setInterval(fallbackFetchOrderbook, 3000);
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
            
            const walletBalance = Number(data.walletBalance);
            const lockedBalance = Number(data.lockedBalance || 0);
            
            document.getElementById('walletBalance').innerText = walletBalance.toLocaleString() + ' KRW';
            document.getElementById('lockedBalance').innerText = lockedBalance.toLocaleString() + ' KRW';
            
            holdingsData = data.holdings;
            
            // 총 자산 가치 = 가용 잔고 + 대기주문 락 증거금 + 총 포지션 증거금 + 총 미실현 손익
            let totalAssetValue = walletBalance + lockedBalance;
            holdingsData.forEach(h => {
                const livePrice = currentStocks[h.ticker]?.currentPrice || h.currentPrice || h.entryPrice;
                let unrealizedPnl = 0;
                if (h.positionType === 'LONG') {
                    unrealizedPnl = (livePrice - h.entryPrice) * h.quantity;
                } else {
                    unrealizedPnl = (h.entryPrice - livePrice) * h.quantity;
                }
                totalAssetValue += h.margin + unrealizedPnl;
            });
            document.getElementById('totalAssetValue').innerText = Math.round(totalAssetValue).toLocaleString() + ' KRW';
            
            // 대기 지정가 주문 목록 표시
            document.getElementById('pendingOrdersCount').innerText = `(${data.pendingOrders ? data.pendingOrders.length : 0})`;
            renderPendingOrders(data.pendingOrders || []);
            
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
                const livePrice = Number(s.currentPrice) || 0;
                const prevClose = Number(s.previousClose) || 0;

                if (!currentStocks[s.ticker]) {
                    currentStocks[s.ticker] = { 
                        ...s, 
                        currentPrice: livePrice, 
                        previousClose: prevClose,
                        highPrice: 0,
                        lowPrice: 0,
                        volume24h: 0
                    };
                } else {
                    currentStocks[s.ticker].companyName = s.companyName;
                    if (livePrice > 0) {
                        currentStocks[s.ticker].currentPrice = livePrice;
                        currentStocks[s.ticker].previousClose = prevClose;
                    }
                }

                // 시세가 유효한 경우에만 화면 요소를 렌더링
                renderSingleStockItem(s.ticker, s.companyName, livePrice, prevClose);
            });

            // 현재 선택된 코인의 헤더 정보 및 통계 동기화
            if (currentStocks[selectedTicker]) {
                const sel = currentStocks[selectedTicker];
                if (sel.currentPrice > 0) {
                    updateSelectedCoinStats(
                        selectedTicker, 
                        sel.currentPrice, 
                        sel.previousClose, 
                        sel.highPrice || sel.currentPrice, 
                        sel.lowPrice || sel.currentPrice, 
                        sel.volume24h
                    );
                }
            }

            // 웹소켓이 연결 안 되었을 경우에만 신규 연결 수립 시도
            if (!upbitSocket || upbitSocket.readyState === WebSocket.CLOSED) {
                connectUpbitWebSocket(stocks.map(s => s.ticker));
            }
        }
    } catch (e) { 
        console.error("fetchStocks 에러:", e); 
    }
}

function connectUpbitWebSocket(tickers) {
    if (upbitSocket) {
        try { upbitSocket.close(); } catch(e){}
    }
    console.log("업비트 웹소켓 연결 시도 중...", tickers);
    upbitSocket = new WebSocket('wss://api.upbit.com/websocket/v1');
    upbitSocket.binaryType = 'arraybuffer';
    
    upbitSocket.onopen = () => {
        console.log("업비트 웹소켓 연결 성공!");
        const msg = [ 
            {"ticket": "mockstock-client"}, 
            {"type": "ticker", "codes": tickers},
            {"type": "orderbook", "codes": tickers}
        ];
        upbitSocket.send(JSON.stringify(msg));
    };

    upbitSocket.onerror = (err) => {
        console.error("업비트 웹소켓 에러:", err);
    };

    upbitSocket.onclose = (evt) => {
        console.warn(`업비트 웹소켓 연결 해제 (코드: ${evt.code})`);
    };
    
    upbitSocket.onmessage = async (evt) => {
        try {
            const enc = new TextDecoder("utf-8");
            const data = JSON.parse(enc.decode(evt.data));
            
            if (data.type === 'ticker') {
                const ticker = data.code;
                const price = data.trade_price;
                const prevClose = data.prev_closing_price;
                const companyName = currentStocks[ticker]?.companyName || ticker;

                if (!currentStocks[ticker]) {
                    currentStocks[ticker] = { currentPrice: 0 };
                }
                currentStocks[ticker].currentPrice = price;
                currentStocks[ticker].previousClose = prevClose;
                currentStocks[ticker].highPrice = data.high_price;
                currentStocks[ticker].lowPrice = data.low_price;
                currentStocks[ticker].volume24h = data.acc_trade_volume_24h;

                renderSingleStockItem(ticker, companyName, price, prevClose);
                
                if (ticker === selectedTicker) {
                    updateSelectedCoinStats(ticker, price, prevClose, data.high_price, data.low_price, data.acc_trade_volume_24h);
                    updateExpectedTotal();
                }
                if (holdingsData.some(h => h.ticker === ticker)) renderHoldings();
            } else if (data.type === 'orderbook' && data.code === selectedTicker) {
                renderOrderbook(data.orderbook_units);
            }
        } catch (e) {
            console.error("웹소켓 메시지 수신 처리 실패:", e);
        }
    };
}

// 웹소켓 통신 장애 대비 직접 조회용 REST API 호가 폴백 함수
async function fallbackFetchOrderbook() {
    if (upbitSocket && upbitSocket.readyState === WebSocket.OPEN) {
        return; // 웹소켓이 잘 돌고 있다면 리퀘스트를 차단하여 부하 방지
    }
    
    try {
        const url = `https://api.upbit.com/v1/orderbook?markets=${selectedTicker}`;
        const res = await fetch(url);
        if (res.ok) {
            const data = await res.json();
            if (data && data.length > 0) {
                renderOrderbook(data[0].orderbook_units);
            }
        }
    } catch (e) {
        console.error("호가창 OpenAPI 폴백 조회 실패:", e);
    }
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
        list.innerHTML = '<tr><td colspan="10" style="text-align:center; color:#848e9c; padding:20px;">진입한 선물 포지션이 없습니다.</td></tr>';
        return;
    }

    let html = '';
    holdingsData.forEach(h => {
        const livePrice = currentStocks[h.ticker]?.currentPrice || h.currentPrice || h.entryPrice;
        
        // PNL 실시간 계산
        let pnl = 0;
        if (h.positionType === 'LONG') {
            pnl = (livePrice - h.entryPrice) * h.quantity;
        } else {
            pnl = (h.entryPrice - livePrice) * h.quantity;
        }
        const pnlRate = h.margin > 0 ? ((pnl / h.margin) * 100).toFixed(2) : '0.00';
        const colorClass = pnl > 0 ? 'up' : (pnl < 0 ? 'down' : '');
        const sign = pnl > 0 ? '+' : '';
        
        const badgeClass = h.positionType === 'LONG' ? 'long' : 'short';
        const typeText = h.positionType === 'LONG' ? '롱 (LONG)' : '숏 (SHORT)';

        const tpVal = h.takeProfitPrice ? `${Math.round(h.takeProfitPrice).toLocaleString()} KRW` : '-';
        const slVal = h.stopLossPrice ? `${Math.round(h.stopLossPrice).toLocaleString()} KRW` : '-';

        html += `
        <tr>
            <td style="font-weight: 600;">${h.ticker.replace('KRW-', '')}</td>
            <td><span class="position-badge ${badgeClass}">${typeText}</span></td>
            <td style="font-weight: 600; color: var(--binance-yellow);">${h.leverage}x</td>
            <td>${h.quantity.toFixed(4)}</td>
            <td>${Math.round(h.entryPrice).toLocaleString()}</td>
            <td>${livePrice.toLocaleString()}</td>
            <td>${Math.round(h.margin).toLocaleString()} KRW</td>
            <td class="pnl-text ${colorClass}">${sign}${pnlRate}% (${sign}${Math.round(pnl).toLocaleString()} KRW)</td>
            <td style="font-size: 11px; text-align: left; padding: 6px 8px;">
                <div style="color: var(--text-muted); display:flex; flex-direction:column; gap:2px;">
                    <span>익절(TP): <strong style="color:var(--binance-up);">${tpVal}</strong></span>
                    <span>손절(SL): <strong style="color:var(--binance-down);">${slVal}</strong></span>
                </div>
                <button type="button" onclick="setTpSl(${h.id}, ${h.takeProfitPrice || 0}, ${h.stopLossPrice || 0})" style="margin-top: 4px; padding: 2px 5px; background: rgba(252, 213, 53, 0.1); border: 1px solid var(--binance-yellow); color: var(--binance-yellow); font-size: 9px; font-weight: 600; border-radius: 3px; cursor: pointer; width: 100%; text-align: center;">⚙️ 익절/손절 설정</button>
            </td>
            <td>
                <button class="close-btn" onclick="closePosition(${h.id})">시장가 청산</button>
            </td>
        </tr>`;
    });
    list.innerHTML = html;
}

// 익절/손절 커스텀 모달 열기 핸들러
function setTpSl(positionId, currentTp, currentSl) {
    document.getElementById('tpslPositionId').value = positionId;
    document.getElementById('modalTpInput').value = currentTp > 0 ? currentTp : '';
    document.getElementById('modalSlInput').value = currentSl > 0 ? currentSl : '';
    document.getElementById('tpslModal').style.display = 'flex';
}

function closeTpSlModal() {
    document.getElementById('tpslModal').style.display = 'none';
}

// 익절/손절 API 전송 제출
async function submitTpSl() {
    const positionId = document.getElementById('tpslPositionId').value;
    const tpPrice = Number(document.getElementById('modalTpInput').value) || 0;
    const slPrice = Number(document.getElementById('modalSlInput').value) || 0;

    try {
        const res = await fetchWithAuth(`${API_BASE}/trades/position/${positionId}/tpsl`, {
            method: 'POST',
            body: JSON.stringify({ tpPrice, slPrice })
        });

        if (res.ok) {
            closeTpSlModal();
            alert("익절/손절(TP/SL) 예약 주문이 저장되었습니다.");
            fetchUserInfo(); 
        } else {
            const data = await res.text();
            alert("설정 실패: " + data);
        }
    } catch (e) {
        alert("서버 통신 실패");
    }
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
function selectRatio(ratio, element) {
    document.querySelectorAll('.ratio-btn').forEach(btn => btn.classList.remove('active'));
    if (element) {
        element.classList.add('active');
    }

    const amountInput = document.getElementById('tradeQuantity');
    const currentPrice = currentStocks[selectedTicker]?.currentPrice;
    
    if (!currentPrice || currentPrice <= 0) {
        console.warn('시세 정보를 불러오는 중입니다. 잠시 후 다시 시도해주세요.');
        amountInput.value = 0;
        updateExpectedTotal();
        return;
    }

    // 내 가용 원화 잔고 찾기
    const balanceText = document.getElementById('walletBalance').innerText;
    const balance = Number(balanceText.replace(/[^0-9.]/g, ''));
    if (isNaN(balance) || balance <= 0) {
        amountInput.value = 0;
        updateExpectedTotal();
        return;
    }
    
    if (selectedOrderUnit === 'qty') {
        // 수량 모드: 가용 잔고의 지정 비율만큼 증거금 할당 시 구매할 수 있는 코인 수량 환산
        // 수량 = (증거금 * 레버리지) / 현재가
        const targetQuantity = (balance * ratio * selectedLeverage) / currentPrice;
        amountInput.value = targetQuantity.toFixed(4);
    } else {
        // 금액 모드: 가용 잔고의 지정 비율에 해당하는 원화 증거금 액수 설정
        const targetVal = balance * ratio;
        amountInput.value = Math.round(targetVal);
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
    const qtyOrVal = Number(document.getElementById('tradeQuantity').value) || 0;
    if (qtyOrVal <= 0) {
        showOrderMessage('주문 값은 0보다 커야 합니다.', 'error');
        return;
    }

    const currentPrice = currentStocks[selectedTicker]?.currentPrice || 0;
    const orderType = document.getElementById('orderTypeSelect').value;
    
    let basePrice = currentPrice;
    let limitPrice = null;
    if (orderType === 'LIMIT') {
        limitPrice = Number(document.getElementById('limitPriceInput').value) || 0;
        if (limitPrice <= 0) {
            showOrderMessage('유효한 지정가를 입력해주세요.', 'error');
            return;
        }
        basePrice = limitPrice;
    }

    if (basePrice <= 0) {
        showOrderMessage('유효한 가격 정보를 가져오지 못했습니다. 잠시 후 다시 시도해 주세요.', 'error');
        return;
    }

    // 백엔드는 항상 '수량(quantity)' 기준으로 접수하므로 actualQty 계산
    let actualQty = 0;
    if (selectedOrderUnit === 'qty') {
        actualQty = qtyOrVal;
    } else {
        // 입력 금액을 기반으로 계약 수량 역산
        actualQty = (qtyOrVal * selectedLeverage) / basePrice;
    }

    try {
        const payload = { 
            ticker: selectedTicker, 
            quantity: Number(actualQty.toFixed(4)),
            leverage: selectedLeverage,
            orderType: orderType,
            price: limitPrice
        };

        const res = await fetchWithAuth(`${API_BASE}/trades/${currentOrderType}`, {
            method: 'POST',
            body: JSON.stringify(payload)
        });

        const data = await res.text();
        if (res.ok) {
            showOrderMessage('주문이 정상 접수되었습니다.', 'success');
            renderReceiptCard(currentOrderType, basePrice, actualQty);
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
    const qtyOrVal = Number(qtyInput.value) || 0;
    const expectedTotalEl = document.getElementById('expectedTotal');
    
    const orderType = document.getElementById('orderTypeSelect').value;
    let basePrice = currentPrice;
    if (orderType === 'LIMIT') {
        const limitInput = document.getElementById('limitPriceInput').value;
        basePrice = Number(limitInput) || currentPrice;
    }

    let actualQty = 0;
    let totalMargin = 0;

    if (selectedOrderUnit === 'qty') {
        actualQty = qtyOrVal;
        totalMargin = (basePrice * actualQty) / selectedLeverage;
    } else {
        totalMargin = qtyOrVal; // 금액 모드일 때는 입력한 KRW가 곧 필요 증거금
    }

    expectedTotalEl.innerText = Math.round(totalMargin).toLocaleString() + ' KRW';
    
    const balanceText = document.getElementById('walletBalance').innerText;
    const balance = Number(balanceText.replace(/[^0-9.]/g, '')) || 0;
    
    if (totalMargin > balance) {
        expectedTotalEl.classList.add('warning');
    } else {
        expectedTotalEl.classList.remove('warning');
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
    
    typeEl.innerText = type === 'buy' ? `롱 진입 (LONG, ${selectedLeverage}x)` : `숏 진입 (SHORT, ${selectedLeverage}x)`;
    typeEl.style.color = type === 'buy' ? 'var(--binance-up)' : 'var(--binance-down)';
    
    priceEl.innerText = price.toLocaleString() + ' KRW';
    qtyEl.innerText = qty.toFixed(4) + ' ' + coinName;
    
    const margin = (price * qty) / selectedLeverage;
    totalEl.innerText = Math.round(margin).toLocaleString() + ' KRW';
    
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
    const actionBadge = document.getElementById('aiActionBadge');
    const confidenceEl = document.getElementById('aiConfidence');
    const targetPriceEl = document.getElementById('aiTargetPrice');
    const stopLossPriceEl = document.getElementById('aiStopLossPrice');
    const recommendedLeverageEl = document.getElementById('aiRecommendedLeverage');
    const reasonEl = document.getElementById('aiReason');

    btn.disabled = true;
    btn.innerText = '⏳ 최신 AI 분석 중...';
    resultBox.style.display = 'none';

    try {
        const res = await fetchWithAuth(`${API_BASE}/auto-trade/predict?ticker=${selectedTicker}`);
        if (!res.ok) {
            throw new Error('AI 분석 요청 실패');
        }

        const data = await res.json();
        
        let actionText = data.action;
        let badgeClass = 'ai-badge-hold';

        if (data.action === 'STRONG_LONG') {
            actionText = '🚀 강력 롱 (BUY)';
            badgeClass = 'ai-badge-buy';
        } else if (data.action === 'LONG') {
            actionText = '🟢 롱 진입 (BUY)';
            badgeClass = 'ai-badge-buy';
        } else if (data.action === 'SHORT') {
            actionText = '🔴 숏 진입 (SELL)';
            badgeClass = 'ai-badge-sell';
        } else if (data.action === 'STRONG_SHORT') {
            actionText = '🚨 강력 숏 (SELL)';
            badgeClass = 'ai-badge-sell';
        } else if (data.action === 'HOLD') {
            actionText = '⏳ 관망 (HOLD)';
            badgeClass = 'ai-badge-hold';
        }

        actionBadge.innerText = actionText;
        actionBadge.className = `ai-badge ${badgeClass}`;
        
        confidenceEl.innerText = `(확률: ${data.confidence.toFixed(1)}%)`;
        
        if (data.targetPrice > 0) {
            targetPriceEl.innerText = data.targetPrice.toLocaleString() + ' KRW';
        } else {
            targetPriceEl.innerText = '-';
        }
        
        if (data.stopLossPrice > 0) {
            stopLossPriceEl.innerText = data.stopLossPrice.toLocaleString() + ' KRW';
        } else {
            stopLossPriceEl.innerText = '-';
        }

        if (recommendedLeverageEl) {
            recommendedLeverageEl.innerText = (data.recommendedLeverage || 1) + 'x';
        }

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
            let typeText = h.tradeType;
            let color = 'color: var(--text-muted)';
            if (h.tradeType === 'OPEN_LONG') {
                typeText = '📈 롱 오픈';
                color = 'color: var(--binance-up)';
            } else if (h.tradeType === 'OPEN_SHORT') {
                typeText = '📉 숏 오픈';
                color = 'color: var(--binance-down)';
            } else if (h.tradeType === 'CLOSE_LONG') {
                typeText = '🚪 롱 청산';
                color = 'color: #eaecef';
            } else if (h.tradeType === 'CLOSE_SHORT') {
                typeText = '🚪 숏 청산';
                color = 'color: #eaecef';
            }
            html += `
                <tr style="border-bottom: 1px solid var(--border-color);">
                    <td style="padding: 8px;">${date}</td>
                    <td style="padding: 8px; font-weight: 600;">${h.ticker.replace('KRW-', '')}</td>
                    <td style="padding: 8px; ${color}">${typeText}</td>
                    <td style="padding: 8px; text-align: right;">${h.price.toLocaleString()} KRW</td>
                    <td style="padding: 8px; text-align: right;">${h.quantity.toFixed(4)}</td>
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

// ----------------- 선물 거래 관련 전용 헬퍼 함수 -----------------
function selectLeverage(leverage, element) {
    selectedLeverage = leverage;
    document.querySelectorAll('.lev-btn').forEach(btn => btn.classList.remove('active'));
    
    // 버튼 스타일 업데이트
    if (element) {
        element.classList.add('active');
    }
    document.getElementById('selectedLeverage').value = leverage;
    document.getElementById('leverageVal').innerText = leverage + 'x';
    
    updateExpectedTotal();
}

async function closePosition(positionId) {
    if (!confirm('해당 포지션을 현재 시장가로 즉시 청산하시겠습니까?')) {
        return;
    }

    try {
        const res = await fetchWithAuth(`${API_BASE}/trades/close/${positionId}`, {
            method: 'POST'
        });

        if (res.ok) {
            alert('포지션이 성공적으로 청산되었습니다.');
            fetchUserInfo(); 
        } else {
            const data = await res.text();
            alert('청산 실패: ' + data);
        }
    } catch (e) {
        alert('서버 오류가 발생했습니다.');
    }
}

// ----------------- 지정가 & 금액 주문 단위 전환 인터렉션 -----------------
function toggleOrderTypeMode() {
    const orderType = document.getElementById('orderTypeSelect').value;
    const limitRow = document.getElementById('limitPriceRow');
    const currentPrice = currentStocks[selectedTicker]?.currentPrice || 0;
    
    if (orderType === 'LIMIT') {
        limitRow.style.display = 'flex';
        document.getElementById('limitPriceInput').value = currentPrice;
    } else {
        limitRow.style.display = 'none';
        document.getElementById('limitPriceInput').value = '';
    }
    updateExpectedTotal();
}

function switchOrderUnit(unit) {
    if (selectedOrderUnit === unit) return;
    
    selectedOrderUnit = unit;
    document.querySelectorAll('.unit-tab').forEach(btn => btn.classList.remove('active'));
    
    const qtyInput = document.getElementById('tradeQuantity');
    const inputVal = Number(qtyInput.value) || 0;
    const currentPrice = currentStocks[selectedTicker]?.currentPrice || 0;
    
    const orderType = document.getElementById('orderTypeSelect').value;
    let basePrice = currentPrice;
    if (orderType === 'LIMIT') {
        const limitInput = document.getElementById('limitPriceInput').value;
        basePrice = Number(limitInput) || currentPrice;
    }

    if (unit === 'qty') {
        document.getElementById('unitQty').classList.add('active');
        document.getElementById('quantityLabel').innerText = '주문수량 (BTC)';
        if (basePrice > 0) {
            // 금액 -> 수량 환산: 수량 = (금액 * 레버리지) / 가격
            const calculatedQty = (inputVal * selectedLeverage) / basePrice;
            qtyInput.value = calculatedQty.toFixed(4);
        } else {
            qtyInput.value = '0';
        }
    } else {
        document.getElementById('unitVal').classList.add('active');
        document.getElementById('quantityLabel').innerText = '주문금액 (KRW)';
        if (basePrice > 0) {
            // 수량 -> 금액 환산: 금액 = (수량 * 가격) / 레버리지
            const calculatedVal = (inputVal * basePrice) / selectedLeverage;
            qtyInput.value = Math.round(calculatedVal);
        } else {
            qtyInput.value = '0';
        }
    }
    
    updateExpectedTotal();
}

function switchBottomTab(tab) {
    currentBottomTab = tab;
    document.getElementById('tabPosBtn').classList.remove('active');
    document.getElementById('tabOrderBtn').classList.remove('active');
    
    document.getElementById('tabPosBtn').style.borderBottomColor = 'transparent';
    document.getElementById('tabPosBtn').style.color = 'var(--text-muted)';
    document.getElementById('tabOrderBtn').style.borderBottomColor = 'transparent';
    document.getElementById('tabOrderBtn').style.color = 'var(--text-muted)';
    
    if (tab === 'pos') {
        document.getElementById('tabPosBtn').classList.add('active');
        document.getElementById('tabPosBtn').style.borderBottomColor = 'var(--binance-yellow)';
        document.getElementById('tabPosBtn').style.color = 'var(--text-main)';
        document.getElementById('posTableContainer').style.display = 'block';
        document.getElementById('orderTableContainer').style.display = 'none';
    } else {
        document.getElementById('tabOrderBtn').classList.add('active');
        document.getElementById('tabOrderBtn').style.borderBottomColor = 'var(--binance-yellow)';
        document.getElementById('tabOrderBtn').style.color = 'var(--text-main)';
        document.getElementById('posTableContainer').style.display = 'none';
        document.getElementById('orderTableContainer').style.display = 'block';
    }
}

function renderPendingOrders(orders) {
    const list = document.getElementById('pendingOrdersList');
    if (!orders || orders.length === 0) {
        list.innerHTML = '<tr><td colspan="8" style="text-align:center; color:#848e9c; padding:20px;">대기 중인 지정가 주문이 없습니다.</td></tr>';
        return;
    }

    let html = '';
    orders.forEach(o => {
        const badgeClass = o.positionType === 'LONG' ? 'long' : 'short';
        const typeText = o.positionType === 'LONG' ? '롱 진입 (BUY)' : '숏 진입 (SELL)';
        const date = new Date(o.createdAt).toLocaleString();

        html += `
        <tr>
            <td style="font-weight: 600;">${o.ticker.replace('KRW-', '')}</td>
            <td><span class="position-badge ${badgeClass}">${typeText}</span></td>
            <td style="font-weight: 600; color: var(--binance-yellow);">${o.leverage}x</td>
            <td>${o.quantity.toFixed(4)}</td>
            <td>${Math.round(o.price).toLocaleString()} KRW</td>
            <td>${Math.round(o.margin).toLocaleString()} KRW</td>
            <td>${date}</td>
            <td>
                <button class="close-btn" onclick="cancelLimitOrder(${o.id})" style="border-color: #df294a; color: #df294a;">주문 취소</button>
            </td>
        </tr>`;
    });
    list.innerHTML = html;
}

async function cancelLimitOrder(orderId) {
    if (!confirm('해당 지정가 대기 주문을 취소하시겠습니까?')) {
        return;
    }

    try {
        const res = await fetchWithAuth(`${API_BASE}/trades/cancel/limit/${orderId}`, {
            method: 'POST'
        });

        if (res.ok) {
            alert('지정가 주문이 정상적으로 취소 및 반환되었습니다.');
            fetchUserInfo(); 
        } else {
            const data = await res.text();
            alert('주문 취소 실패: ' + data);
        }
    } catch (e) {
        alert('서버 오류가 발생했습니다.');
    }
}


