from fastapi import FastAPI
import requests
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
import numpy as np

app = FastAPI()

def fetch_upbit_data(ticker="KRW-BTC", count=200):
    url = f"https://api.upbit.com/v1/candles/minutes/60?market={ticker}&count={count}"
    headers = {"accept": "application/json"}
    response = requests.get(url, headers=headers)
    data = response.json()
    
    df = pd.DataFrame(data)
    df = df[['candle_date_time_kst', 'opening_price', 'high_price', 'low_price', 'trade_price', 'candle_acc_trade_volume']]
    df.columns = ['date', 'open', 'high', 'low', 'close', 'volume']
    df = df.sort_values('date').reset_index(drop=True)
    return df

def calculate_rsi(data, window=14):
    delta = data.diff()
    gain = (delta.where(delta > 0, 0)).rolling(window=window).mean()
    loss = (-delta.where(delta < 0, 0)).rolling(window=window).mean()
    rs = gain / loss
    return 100 - (100 / (1 + rs))

def build_features(df):
    df['SMA_10'] = df['close'].rolling(window=10).mean()
    df['SMA_30'] = df['close'].rolling(window=30).mean()
    df['RSI'] = calculate_rsi(df['close'])
    
    # Target: 1 if next hour's close is higher than current close
    df['Target'] = (df['close'].shift(-1) > df['close']).astype(int)
    df = df.dropna()
    return df

@app.get("/predict")
def predict_trend(ticker: str = "KRW-BTC"):
    try:
        # 1. Fetch Data
        df = fetch_upbit_data(ticker, count=200)
        
        # 2. Build Features
        df = build_features(df)
        
        # 3. Train Model
        features = ['open', 'high', 'low', 'close', 'volume', 'SMA_10', 'SMA_30', 'RSI']
        X = df[features][:-1]
        y = df['Target'][:-1]
        
        model = RandomForestClassifier(n_estimators=100, random_state=42)
        model.fit(X, y)
        
        # 4. Predict
        current_features = df[features].iloc[-1].values.reshape(1, -1)
        prediction = model.predict(current_features)[0]
        probabilities = model.predict_proba(current_features)[0]
        
        prediction_text = "UP" if prediction == 1 else "DOWN"
        confidence = probabilities[prediction]
        
        return {
            "ticker": ticker,
            "prediction": prediction_text,
            "confidence": round(float(confidence) * 100, 2),
            "current_rsi": round(float(df['RSI'].iloc[-1]), 2),
            "current_price": float(df['close'].iloc[-1])
        }
        
    except Exception as e:
        return {"error": str(e)}
