const API_BASE = 'http://localhost:8080/api';
let currentStocks = {}; 
let selectedTicker = 'KRW-BTC';
let selectedCompanyName = '비트코인';
let holdingsData = []; 
let currentOrderType = 'buy'; 

let chart = null;
let candleSeries = null;
let upbitSocket = null;

window.onload = async () => {
    const token = localStorage.getItem('accessToken');
    if (!token) {
        alert('로그인이 필요합니다.');
        window.location.href = '/index.html';
        return;
    }
    
    initChart();
    await fetchStocks(); 
    await fetchUserInfo();

    selectCoin('KRW-BTC', '비트코인'); // 초기 선택
    
    setInterval(() => updateLiveCandle(selectedTicker), 2000);
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
                currentStocks[s.ticker] = { ...s, currentPrice: 0, previousClose: 0 };
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
        const msg = [ {"ticket": "mockstock-client"}, {"type": "ticker", "codes": tickers} ];
        upbitSocket.send(JSON.stringify(msg));
    };
    
    upbitSocket.onmessage = async (evt) => {
        const enc = new TextDecoder("utf-8");
        const data = JSON.parse(enc.decode(evt.data));
        
        const ticker = data.code;
        const price = data.trade_price;
        const prevClose = data.prev_closing_price;
        const companyName = currentStocks[ticker]?.companyName || ticker;

        currentStocks[ticker].currentPrice = price;
        currentStocks[ticker].previousClose = prevClose;

        renderSingleStockItem(ticker, companyName, price, prevClose);
        if (holdingsData.some(h => h.ticker === ticker)) renderHoldings();
    };
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

    if (ticker === selectedTicker) {
        const headerPrice = document.getElementById('chartPrice');
        headerPrice.innerText = price.toLocaleString() + ' KRW';
        headerPrice.className = 'chart-price ' + colorClass;
    }
}

function renderHoldings() {
    const list = document.getElementById('holdingsList');
    if (!holdingsData || holdingsData.length === 0) {
        list.innerHTML = '<div style="text-align:center; color:#848e9c; margin-top:20px; font-size: 13px;">No assets</div>';
        return;
    }

    let html = '';
    holdingsData.forEach(h => {
        const livePrice = currentStocks[h.ticker]?.currentPrice || h.averagePrice;
        const diff = livePrice - h.averagePrice;
        const diffPercent = ((diff / h.averagePrice) * 100).toFixed(2);
        const colorClass = diff > 0 ? 'price-up' : (diff < 0 ? 'price-down' : '');
        
        html += `
        <div class="holding-item">
            <div class="holding-header">
                <span>${h.ticker.replace('KRW-', '')}</span>
                <span class="${colorClass}">${livePrice.toLocaleString()} (${diff > 0 ? '+' : ''}${diffPercent}%)</span>
            </div>
            <div class="holding-details">
                <span>Amt: ${h.quantity}</span>
                <span>Avg: ${h.averagePrice.toLocaleString()}</span>
            </div>
        </div>`;
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
    
    loadChartData(ticker);
}

function switchOrderTab(type) {
    currentOrderType = type;
    document.getElementById('tabBuy').classList.remove('active');
    document.getElementById('tabSell').classList.remove('active');
    
    const btn = document.getElementById('executeBtn');
    
    if(type === 'buy') {
        document.getElementById('tabBuy').classList.add('active');
        btn.className = 'execute-btn buy-btn';
        btn.innerHTML = `Buy <span id="orderTargetCoin">${selectedTicker.replace('KRW-', '')}</span>`;
    } else {
        document.getElementById('tabSell').classList.add('active');
        btn.className = 'execute-btn sell-btn';
        btn.innerHTML = `Sell <span id="orderTargetCoin">${selectedTicker.replace('KRW-', '')}</span>`;
    }
}

function showOrderMessage(msg, type) {
    const box = document.getElementById('orderMessage');
    box.innerText = msg;
    box.className = 'message-box ' + type;
    setTimeout(() => { box.style.display = 'none'; }, 3000);
}

async function executeTrade() {
    const quantity = document.getElementById('tradeQuantity').value;
    if (quantity < 1) {
        showOrderMessage('수량은 1 이상이어야 합니다.', 'error');
        return;
    }

    try {
        const res = await fetchWithAuth(`${API_BASE}/trades/${currentOrderType}`, {
            method: 'POST',
            body: JSON.stringify({ ticker: selectedTicker, quantity: Number(quantity) })
        });

        const data = await res.text();
        if (res.ok) {
            showOrderMessage(data, 'success');
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

// ----------------- 차트 로직 -----------------
function initChart() {
    const domElement = document.getElementById('tvchart');
    const chartProperties = {
        layout: { backgroundColor: '#161a1e', textColor: '#848e9c' },
        grid: { vertLines: { color: '#2b3139' }, horzLines: { color: '#2b3139' } },
        crosshair: { mode: LightweightCharts.CrosshairMode.Normal },
        rightPriceScale: { borderColor: '#2b3139' },
        timeScale: { borderColor: '#2b3139', timeVisible: true }
    };
    
    chart = LightweightCharts.createChart(domElement, chartProperties);
    candleSeries = chart.addCandlestickSeries({
        upColor: '#f6465d', downColor: '#2ebd85', 
        borderVisible: false, wickUpColor: '#f6465d', wickDownColor: '#2ebd85'
    });
    
    new ResizeObserver(entries => {
        if (entries.length === 0 || entries[0].target !== domElement) return;
        const newRect = entries[0].contentRect;
        chart.applyOptions({ width: newRect.width, height: newRect.height });
    }).observe(domElement);
}

async function loadChartData(ticker) {
    try {
        const res = await fetch(`https://api.upbit.com/v1/candles/minutes/1?market=${ticker}&count=200`);
        const data = await res.json();
        
        const cdata = data.map(d => ({
            time: (new Date(d.candle_date_time_kst)).getTime() / 1000 + (9 * 3600),
            open: d.opening_price,
            high: d.high_price,
            low: d.low_price,
            close: d.trade_price
        })).reverse();
        
        candleSeries.setData(cdata);
    } catch (e) { console.error("차트 로드 실패", e); }
}

async function updateLiveCandle(ticker) {
    try {
        const res = await fetch(`https://api.upbit.com/v1/candles/minutes/1?market=${ticker}&count=1`);
        const data = await res.json();
        const d = data[0];
        
        candleSeries.update({
            time: (new Date(d.candle_date_time_kst)).getTime() / 1000 + (9 * 3600),
            open: d.opening_price,
            high: d.high_price,
            low: d.low_price,
            close: d.trade_price
        });
    } catch (e) {}
}

function logout() {
    localStorage.removeItem('accessToken');
    window.location.href = '/index.html';
}
