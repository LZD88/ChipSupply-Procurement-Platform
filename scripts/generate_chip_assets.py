"""从唯一教学目录生成 SQL 与可抓取报价页；无网络或数据库操作。"""
from pathlib import Path
from decimal import Decimal
import argparse
import html
import json

ROOT = Path(__file__).resolve().parents[1]

class SQL(str):
    pass

def quote(value):
    if isinstance(value, SQL): return str(value)
    if value is None: return 'NULL'
    if isinstance(value, (int, float, Decimal)): return str(value)
    return "'" + str(value).replace("'", "''") + "'"

def insert(table, row):
    columns = ', '.join(f'`{key}`' for key in row)
    values = ', '.join(quote(v) for v in row.values())
    return f'INSERT INTO `{table}` ({columns}) VALUES ({values}) ON DUPLICATE KEY UPDATE id=id;'

def generate(quote_base_url='http://host.docker.internal:8082'):
    data = json.loads((ROOT / 'docs/chip_catalog.json').read_text())
    seed = ['-- 自动生成：python3 scripts/generate_chip_assets.py；重复导入不覆盖已有业务记录。']
    for i, (name, role) in enumerate([('教学管理员', 'admin'), ('采购实验员', 'purchase'), ('仓储实验员', 'warehouse')], 1):
        seed.append(insert('user', dict(id=i, username=f'lab_user_{i}', password='DISABLED_TEACHING_ACCOUNT', real_name=name, role=role, department='芯片采购实验室', email=f'user{i}@chip-lab.example', status=1)))
    for s in data['suppliers']:
        seed.append(insert('supplier', dict(id=s['id'], supplier_code=s['code'], name=s['name'], contact_person=f"教学联系人{s['id']:02}", phone='000-00000000', email=f"supplier{s['id']}@chip-lab.example", address=f"教学园区 / 虚拟供应节点 {s['id']:02}", credit_rating=s['rating'], status=1)))
    fields = {'partCode':'part_code', 'name':'name', 'model':'model', 'specification':'specification', 'unit':'unit', 'supplierId':'supplier_id', 'category':'category', 'purchasePrice':'purchase_price', 'stockWarningValue':'stock_warning_value', 'manufacturer':'manufacturer', 'manufacturerPartNumber':'manufacturer_part_number', 'packageType':'package_type', 'temperatureGrade':'temperature_grade', 'minOrderQuantity':'min_order_quantity', 'leadTimeDays':'lead_time_days'}
    for p in data['parts']:
        row = {'id':p['id'], **{sql:p[key] for key,sql in fields.items()}, 'suggested_retail_price':p['purchasePrice'], 'description':data['dataNotice']}
        seed.append(insert('part', row))
        seed.append(insert('inventory', dict(id=p['id'], part_id=p['id'], current_quantity=p['currentQuantity'], safety_stock=p['safetyStock'], warehouse_location=p['warehouseLocation'], last_inbound_time=SQL('DATE_SUB(NOW(), INTERVAL 3 DAY)'))))
    for i, name in enumerate(['教学客户·嵌入式方案组', '教学客户·电子制造组', '教学客户·传感实验室'], 1):
        seed.append(insert('customer', dict(id=i, customer_code=f'LAB-CUS-{i:03}', name=name, customer_type=i, contact_person='教学联系人', email=f'customer{i}@chip-lab.example', address='虚拟教学园区')))
    for i in range(1, 73):
        month = (i-1)//12
        p = data['parts'][(i-1)%24]
        age = month * 30 + ((i-1)%12)
        status = [1,2,3,4,4,5][(i-1)%6] if month == 0 else 4
        qty = p['minOrderQuantity'] * (2 + i%4)
        price = (Decimal(str(p['purchasePrice'])) * (Decimal('1') + Decimal(month)/100)).quantize(Decimal('.01'))
        ordered = SQL(f'DATE_SUB(CURDATE(), INTERVAL {age} DAY)')
        expected = SQL(f'DATE_ADD({ordered}, INTERVAL {p["leadTimeDays"]} DAY)')
        actual = SQL(f'DATE_SUB(CURDATE(), INTERVAL {max(0, age-7)} DAY)') if status == 4 else None
        seed.append(insert('purchase_order', dict(id=i, order_number=f'CL-DEMO-{i:04}', total_amount=price*qty, status=status, order_time=ordered, expected_delivery_date=expected, actual_delivery_date=actual, created_by=2, remark=f'教学采购 / {p["model"]} / 第{month+1}组', create_time=ordered)))
        seed.append(insert('order_detail', dict(id=i, order_id=i, part_id=p['id'], quantity=qty, unit_price=price, remark='芯片采购教学样本', create_time=ordered)))
        if status in (3,4):
            seed.append(insert('logistics', dict(id=i, order_id=i, logistics_company='教学物流·防静电运输组', tracking_number=f'LAB-ESD-{i:05}', status=3 if status==4 else 2, ship_time=ordered, actual_arrival_time=actual, receiver='仓储实验员', remark='防静电袋 / 干燥剂 / 密封交接（教学场景）')))
    seed.append("INSERT IGNORE INTO chip_dataset(version) VALUES ('chip-lab-v2');")
    seed_sql = '\n'.join(seed)+'\n'
    (ROOT/'src/main/resources/db/seed.sql').write_text(seed_sql)
    schema=(ROOT/'src/main/resources/db/schema.sql').read_text()
    exported='-- 芯链 · 芯片采购实验室。仅操作新的 chip_supply_db。\nCREATE DATABASE IF NOT EXISTS chip_supply_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;\nUSE chip_supply_db;\n'+schema+'\nSTART TRANSACTION;\n'+seed_sql+'COMMIT;\n'
    for name in ['docs/database_schema.sql']:
        (ROOT/name).write_text(exported)
    quotes=ROOT/'chip-portal/quotes';quotes.mkdir(parents=True,exist_ok=True)
    mappings=[]
    for p in data['parts']:
        s=next(s for s in data['suppliers'] if s['id']==p['supplierId'])
        rows={'制造商料号':p['manufacturerPartNumber'],'制造商':p['manufacturer'],'封装':p['packageType'],'参数':p['specification'],'温度等级':p['temperatureGrade'],'最小起订量':str(p['minOrderQuantity'])+' 片','标准交期':str(p['leadTimeDays'])+' 天','供应商':s['name'],'供应商编码':s['code'],'ERP 物料编码':p['partCode'],'价格口径':'人民币 / 片 / 教学参考单价；不含运费，未单独计算税额'}
        table=''.join(f'<div><dt>{html.escape(k)}</dt><dd>{html.escape(v)}</dd></div>' for k,v in rows.items())
        page=f'''<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>{p['model']} · {s['name']} · 芯链报价</title><link rel="stylesheet" href="/portal.css"><link rel="icon" href="/chip.svg"></head><body class="quote-body"><main class="quote-sheet"><a class="brand" href="/"><img src="/chip.svg" alt="">芯链 <span>CHIP SUPPLY LAB</span></a><p class="eyebrow">TEACHING QUOTATION / {p['partCode']}</p><h1>{p['name']}</h1><p class="muted">{p['model']} · {p['packageType']}</p><div class="quote-price">¥{p['purchasePrice']:.2f}<small> / 片</small></div><p class="notice">课堂参考报价 · 版本 chip-lab-v2 · 虚构产品</p><dl class="spec-list">{table}</dl><p class="muted">{data['dataNotice']} 此页为固定版本的供应商报价快照；ERP 当前价格请在采购工作台查询。</p><a class="primary" href="/#catalog">返回物料目录 →</a></main></body></html>'''
        (quotes/(p['partCode']+'.html')).write_text(page)
        mappings.append({'part_code':p['partCode'],'part_name':p['name'],'manufacturer_part_number':p['manufacturerPartNumber'],'package_type':p['packageType'],'supplier':s['name'],'url':quote_base_url.rstrip('/')+'/quotes/'+p['partCode']+'.html'})
    print(f'Generated {len(data["parts"])} chip quotations, 72 orders and schema export.')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--quote-base-url', default='http://host.docker.internal:8082', help='OpenSandbox 内可访问的 Java 服务地址')
    generate(parser.parse_args().quote_base_url)
