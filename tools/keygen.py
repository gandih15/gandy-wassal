# مولّد أكواد التفعيل لتطبيق غاندي وصّل
# الاستخدام:  python keygen.py ABCD-1234-EF56
# السر لازم يكون نفس قيمة WASSAL_SECRET اللي حاطتها في GitHub Secrets.
# حطه في متغير بيئة WASSAL_SECRET، أو هيسألك عليه.
import hashlib
import os
import sys

SECRET = os.environ.get("WASSAL_SECRET") or input("السر (WASSAL_SECRET): ").strip()

def key_for(device_code: str) -> str:
    raw = device_code.replace("-", "").strip().upper() + SECRET
    h = hashlib.sha256(raw.encode()).hexdigest()[:16].upper()
    return "-".join(h[i:i + 4] for i in range(0, 16, 4))

if __name__ == "__main__":
    code = sys.argv[1] if len(sys.argv) > 1 else input("كود الجهاز: ")
    print(key_for(code))
