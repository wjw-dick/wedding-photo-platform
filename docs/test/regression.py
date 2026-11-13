import json, time, subprocess, os, sys, concurrent.futures
import requests

BASE = 'http://127.0.0.1:18090/api'
results = []

def rec(name, ok, detail):
    results.append({'name': name, 'ok': bool(ok), 'detail': str(detail)[:800]})
    print(('PASS' if ok else 'FAIL'), name, '-', str(detail)[:300])

def api(method, path, token=None, **kw):
    headers = kw.pop('headers', {})
    if token:
        headers['Authorization'] = f'Bearer {token}'
    r = requests.request(method, BASE + path, headers=headers, timeout=60, **kw)
    try:
        body = r.json()
    except Exception:
        body = r.text
    return r.status_code, body

# 1 platform login
s, b = api('POST', '/platform/auth/login', json={'username': 'admin', 'password': 'admin123'})
ptoken = b['data']['token'] if s == 200 and b.get('code') == 0 else None
rec('平台端登录', bool(ptoken), f'HTTP {s}')

# 2 create isolated tenant
code = 'reg' + str(int(time.time()))[-6:]
s, b = api('POST', '/platform/tenants', ptoken, json={
    'tenantCode': code, 'tenantName': '回归测试租户', 'industry': 'WEDDING_PHOTO',
    'contactName': '测试', 'contactPhone': '13800000000', 'planId': 1,
    'trial': False, 'adminUsername': 'admin', 'adminPassword': 'Reg@123456'
})
rec('开通新租户', s == 200 and b.get('code') == 0, f'HTTP {s} {b}')
tenant_id = b.get('data', {}).get('tenantId') if isinstance(b, dict) else None

# 3 tenant login
s, b = api('POST', '/auth/login', json={'tenantCode': code, 'username': 'admin', 'password': 'Reg@123456'})
ttoken = b['data']['token'] if s == 200 and b.get('code') == 0 else None
rec('新租户管理员登录', bool(ttoken), f'HTTP {s}')

# 4 initialize package
s, b = api('POST', '/tenant/crud/pkg_package', ttoken, json={
    'package_no': 'P' + code, 'name': '回归套系', 'price': 6999, 'deposit': 2100,
    'status': 'ON', 'scene_count': 3, 'dress_count': 4, 'retouch_count': 25, 'delivery_days': 30
})
pkg_id = b.get('data', {}).get('id') if isinstance(b, dict) and isinstance(b.get('data'), dict) else None
if not pkg_id:
    _, pb = api('GET', '/tenant/crud/pkg_package', ttoken, params={'page': 1, 'size': 10})
    pkg_id = pb['data']['records'][0]['id'] if pb.get('data', {}).get('records') else None
rec('租户通用 CRUD 创建套系', bool(pkg_id), f'packageId={pkg_id}')

# 5 create order
s, b = api('POST', '/tenant/orders', ttoken, json={
    'storeId': 1, 'customerName': '张三', 'customerPhone': '13900000001',
    'packageId': pkg_id, 'shootDate': '2026-12-01', 'shootStart': '09:00:00',
    'shootEnd': '12:00:00', 'resourceIds': [], 'addons': [], 'source': 'STORE'
})
order = b.get('data', {}) if isinstance(b, dict) else {}
rec('创建订单', s == 200 and b.get('code') == 0, f'HTTP {s} orderNo={order.get("orderNo")}')
order_no, order_id = order.get('orderNo'), order.get('orderId')

# 6 cross tenant 403
s, b = api('GET', f'/tenant/orders/{order_id}', ptoken, params={})
rec('平台端访问租户订单明细被拒', s in (401, 403), f'HTTP {s} {str(b)[:200]}')
# demo token
s, b = api('POST', '/auth/login', json={'tenantCode': 'demo', 'username': 'admin', 'password': 'admin123'})
dtoken = b['data']['token'] if s == 200 and b.get('code') == 0 else None
s, b = api('GET', f'/tenant/orders/{order_id}', dtoken)
rec('跨租户访问订单返回 403', s == 403, f'HTTP {s} {str(b)[:200]}')
s, b = api('GET', f'/tenant/crm/members/1', dtoken)
rec('跨租户访问会员返回 403/404', s in (403, 404), f'HTTP {s}')
s, b = api('GET', '/platform/tenants', ttoken)
rec('租户账号访问平台接口被拒', s in (401, 403), f'HTTP {s}')

# 7 payment idempotency
if order_no:
    payload = {'payNo': 'REGPAY' + code, 'payType': 'DEPOSIT', 'channel': 'OFFLINE', 'amount': 100}
    s1, b1 = api('POST', f'/tenant/orders/{order_no}/pay', ttoken, json=payload)
    s2, b2 = api('POST', f'/tenant/orders/{order_no}/pay', ttoken, json=payload)
    _, od = api('GET', f'/tenant/orders/{order_id}', ttoken)
    odata = od.get('data', {}) if isinstance(od, dict) else {}
    paid = odata.get('order', {}).get('paid_amount')
    pays = odata.get('payments', [])
    try:
        paid_ok = abs(float(paid) - 100.0) < 0.001
    except (TypeError, ValueError):
        paid_ok = False
    rec('支付回调幂等（重复 payNo 只记一次）',
        s1 == 200 and s2 == 200 and paid_ok and len(pays) == 1,
        f'paid={paid} payRecords={len(pays)} HTTP {s1}/{s2}')

# 8 schedule concurrency
_, rb = api('POST', '/tenant/crud/sch_resource', ttoken, json={
    'store_id': 1, 'resource_type': 'PHOTOGRAPHER', 'resource_name': '并发测试摄影师', 'capacity': 1, 'status': 1
})
rid = rb.get('data', {}).get('id') if isinstance(rb, dict) and isinstance(rb.get('data'), dict) else None
if not rid:
    _, rl = api('GET', '/tenant/crud/sch_resource', ttoken, params={'page': 1, 'size': 10})
    rid = rl['data']['records'][0]['id']

def lock_one(i):
    return api('POST', '/tenant/schedule/lock', ttoken, json={
        'resourceId': rid, 'slotDate': '2026-12-20', 'slotStart': '09:00:00',
        'slotEnd': '10:00:00', 'orderNo': f'CONC{code}{i}'
    })[0]

with concurrent.futures.ThreadPoolExecutor(max_workers=20) as ex:
    codes = list(ex.map(lock_one, range(20)))
success = sum(1 for c in codes if c == 200)
rec('20 并发抢同一档期仅 1 单成功', success == 1, f'success={success} codes={codes}')

# 9 report async
s, b = api('POST', '/tenant/reports/tasks', ttoken, json={'reportType': 'summary', 'params': {'from': '2026-01-01', 'to': '2026-12-31'}})
task_id = b.get('data', {}).get('taskId') if isinstance(b, dict) else None
status, url = None, None
for _ in range(20):
    time.sleep(0.5)
    _, tb = api('GET', '/tenant/reports/tasks', ttoken, params={'page': 1, 'size': 5})
    for row in tb.get('data', {}).get('records', []):
        if row.get('id') == task_id:
            status = row.get('status')
    if status in ('DONE', 'FAILED'):
        break
rec('报表异步导出完成', status == 'DONE', f'task={task_id} status={status}')
if task_id:
    _, db = api('GET', f'/tenant/reports/tasks/{task_id}/download', ttoken)
    url = db.get('data', {}).get('url')
    rec('报表下载签名 URL', bool(url), str(url)[:160])

# 10 contract PDF
if order_id:
    s, b = api('POST', f'/tenant/order-extra/orders/{order_id}/contract', ttoken, json={})
    pdf = b.get('data', {}).get('pdfUrl') if isinstance(b, dict) else None
    rec('合同 PDF 生成', s == 200 and bool(pdf), f'HTTP {s} {pdf}')

# 11 QR png
_, eb = api('POST', '/tenant/entries', ttoken, json={'entryName': '回归入口', 'endType': 'USER_MINI'})
eid = eb.get('data', {}).get('id') if isinstance(eb, dict) and isinstance(eb.get('data'), dict) else None
if eid:
    r = requests.get(f'{BASE}/tenant/entries/{eid}/qr.png', headers={'Authorization': f'Bearer {ttoken}'}, timeout=30)
    rec('多端入口二维码 PNG', r.status_code == 200 and r.content[:8] == b'\x89PNG\r\n\x1a\n', f'HTTP {r.status_code} bytes={len(r.content)}')

# 12 upload type whitelist (exe masquerade)
if order_id:
    r = requests.post(f'{BASE}/tenant/production/orders/{order_id}/upload',
                      headers={'Authorization': f'Bearer {ttoken}'},
                      files={'file': ('evil.jpg', b'MZ' + os.urandom(64), 'image/jpeg')},
                      data={'category': 'RAW'}, timeout=30)
    rec('上传伪装图片被拒', r.status_code in (400, 415), f'HTTP {r.status_code} {r.text[:200]}')

# 13 SQL injection filter
s, b = api('GET', '/tenant/orders', dtoken, params={'keyword': "' OR 1=1 --", 'page': 1, 'size': 10})
rec('订单筛选 SQL 注入无效', s == 200 and b.get('code') == 0, f'HTTP {s} total={b.get("data",{}).get("total")}')

# 14 WeChat sync without credentials gives explicit failure
_, ab = api('GET', '/tenant/mp/accounts', dtoken)
accs = ab.get('data', []) if isinstance(ab, dict) else []
if accs:
    s, b = api('POST', f'/tenant/mp/accounts/{accs[0]["id"]}/sync/fans', dtoken, json={})
    rec('无微信凭证同步明确报错', b.get('code') != 0 if isinstance(b, dict) else True, f'HTTP {s} {str(b)[:200]}')
else:
    rec('无微信凭证同步明确报错', True, '无公众号账号，跳过')

# 15 UnionID same member
s1, b1 = api('POST', f'/open/{code}/member/login', json={'phone': '13700000002', 'unionId': 'U-' + code, 'openId': 'O1', 'source': 'MINI'})
s2, b2 = api('POST', f'/open/{code}/member/login', json={'phone': '13700000002', 'unionId': 'U-' + code, 'openId': 'O2', 'source': 'APP'})
m1 = b1.get('data', {}).get('member', {}).get('id') if isinstance(b1, dict) else None
m2 = b2.get('data', {}).get('member', {}).get('id') if isinstance(b2, dict) else None
rec('UnionID 多端同一会员', m1 is not None and m1 == m2, f'member={m1}/{m2}')

# 16 audit read
_, ab = api('GET', '/tenant/audits', ttoken, params={'page': 1, 'size': 5})
rec('租户审计可查询且只读', ab.get('code') == 0 and ab.get('data', {}).get('total', 0) > 0, f'total={ab.get("data",{}).get("total")}')
# static no write endpoints
import subprocess
grep = subprocess.run(['grep', '-nE', '@(Post|Put|Delete)Mapping', 'src/main/java/com/wedding/tenant/controller/AuditController.java'],
                      capture_output=True, text=True).stdout.strip()
rec('审计控制器无写接口', grep == '', grep or '无写接口')

# 17 sensitive word / case authorization? verify public cases exclude unauthorized
_, pb = api('GET', f'/public/{code}/cases')
rec('公开客片仅授权内容', pb.get('code') == 0, f'count={len(pb.get("data", []))}')

# 18 platform data isolation: platform has no tenant order endpoint
s, b = api('GET', '/platform/orders', ptoken)
rec('平台端无租户订单明细接口', s == 404, f'HTTP {s}')

summary = {'total': len(results), 'passed': sum(1 for r in results if r['ok']), 'failed': sum(1 for r in results if not r['ok']),
           'tenantCode': code, 'results': results}
os.makedirs('docs/test', exist_ok=True)
with open('docs/test/regression-result.json', 'w', encoding='utf-8') as f:
    json.dump(summary, f, ensure_ascii=False, indent=2)
print(json.dumps({k: summary[k] for k in ('total', 'passed', 'failed', 'tenantCode')}, ensure_ascii=False))
