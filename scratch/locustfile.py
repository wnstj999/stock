import uuid
from locust import HttpUser, task, between

class StockTradingUser(HttpUser):
    # 각 가상 유저가 요청 사이에 대기할 시간 (0.1초 ~ 0.5초 사이로 매우 타이트하게 설정)
    wait_time = between(0.1, 0.5)

    def on_start(self):
        """가상 유저가 생성될 때 실행되는 함수로, 회원가입 후 토큰을 받아옵니다."""
        # 고유한 계정 생성을 위해 UUID 활용
        unique_id = str(uuid.uuid4())[:8]
        self.email = f"user_{unique_id}@stress.com"
        self.password = "password123!"
        self.nickname = f"유저_{unique_id}"
        self.token = ""

        # 1. 회원가입 요청
        signup_payload = {
            "email": self.email,
            "password": self.password,
            "nickname": self.nickname
        }
        signup_response = self.client.post("/api/auth/signup", json=signup_payload)
        
        # 2. 로그인 요청 및 토큰 획득
        if signup_response.status_code == 200:
            login_payload = {
                "email": self.email,
                "password": self.password
            }
            login_response = self.client.post("/api/auth/login", json=login_payload)
            if login_response.status_code == 200:
                self.token = login_response.json().get("accessToken", "")
        
        # 헤더 공통 설정
        self.headers = {
            "Authorization": f"Bearer {self.token}" if self.token else ""
        }

    @task(3)
    def view_stocks(self):
        """전체 주식/코인 시세 목록 조회 (가장 흔하게 발생하는 호출)"""
        if self.token:
            self.client.get("/api/stocks", headers=self.headers)

    @task(1)
    def view_ranking(self):
        """모의투자 수익률 랭킹 조회"""
        if self.token:
            self.client.get("/api/ranking", headers=self.headers)
