# 📖 Locust 压测脚本（Day 10）
# 为什么用 Locust：Python 协程并发，一台开发机就能压出 1000+ QPS；JMeter 配 GUI 对新手太重。
#
# 运行：
#   pip install locust
#   locust -f scripts/load-test/locustfile.py --headless -u 200 -r 50 -t 3m \
#          --host http://localhost:8080 --csv=report
#   # -u 200：200 并发虚拟用户；先看 report_stats.csv 的 99% 线
import time, hmac, hashlib, random
from locust import HttpUser, task, between

APP_KEY = "test_app"
SECRET = b"dev_secret_123456"   # 与 V2 种子数据一致（本地开发用）
DS = "ds_lead_scoring_001"


class TagQueryUser(HttpUser):
    # 每个虚拟用户拼命发，靠并发数控 QPS
    wait_time = between(0.001, 0.005)

    @task
    def query(self):
        ts = str(int(time.time() * 1000))
        # 每批 10 个 ID（签名规则必须和服务端完全一致）
        ids = [hashlib.md5(f"138{random.randint(10000000, 99999999)}".encode()).hexdigest()
               for _ in range(10)]
        content = APP_KEY + ts + "|".join(ids) + DS
        sign = hmac.new(SECRET, content.encode(), hashlib.sha256).hexdigest()
        self.client.post("/api/v1/tag/query", json={
            "appKey": APP_KEY, "signature": sign, "timestamp": int(ts),
            "dataSourceId": DS, "ids": ids
        })
