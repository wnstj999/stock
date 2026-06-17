import subprocess
import time
import urllib.request
import json
import psutil

# Start the server
process = subprocess.Popen(["./gradlew.bat", "bootRun"], stdout=subprocess.PIPE, stderr=subprocess.STDOUT)

# Wait for server to boot
started = False
for line in iter(process.stdout.readline, b''):
    line_str = line.decode('utf-8', errors='ignore')
    print("SERVER:", line_str.strip())
    if "Started StockApplication" in line_str:
        started = True
        break

if not started:
    print("Failed to start server")
    process.terminate()
    exit(1)

time.sleep(2)

print("--- Testing API ---")
try:
    data = json.dumps({"email": "test100@test.com", "password": "123", "nickname": "testuser"}).encode('utf-8')
    req = urllib.request.Request("http://localhost:8080/api/auth/signup", data=data, headers={"Content-Type": "application/json"})
    response = urllib.request.urlopen(req)
    print("Signup Status:", response.getcode())
    print("Signup Response:", response.read().decode('utf-8'))
except urllib.error.HTTPError as e:
    print("Signup HTTP Error:", e.code)
    print("Signup Error Response:", e.read().decode('utf-8'))
except Exception as e:
    print("Signup Error:", str(e))

# Kill server process and its children
def kill_process_tree(pid):
    try:
        parent = psutil.Process(pid)
        for child in parent.children(recursive=True):
            child.kill()
        parent.kill()
    except Exception as e:
        pass

kill_process_tree(process.pid)
