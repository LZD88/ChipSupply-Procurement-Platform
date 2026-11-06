'use strict';
const $ = s => document.querySelector(s);
const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const num = n => Number(n || 0).toLocaleString('zh-CN');
const money = n => '¥' + Number(n || 0).toLocaleString('zh-CN', {minimumFractionDigits:2, maximumFractionDigits:2});
const statusNames = ['','待审核','已审核','采购中','已入库','已取消'];
const labels = {overview:['采购总览','掌握每一颗芯片的供应节奏。'],catalog:['芯片目录','从料号到封装，匹配业务需求与供应资源。'],inventory:['库存监控','连接现有库存与补货需求，关注每一个供应缺口。'],orders:['采购订单','清晰记录从采购申请到芯片交付的全过程。'],suppliers:['供应网络','在价格、供货能力与交期之间，找到合适的平衡。'],audit:['审计日志','追踪关键业务操作与安全事件，形成可核验的责任链。']};
const roleNames={admin:'管理员',purchase:'采购员',warehouse:'仓管员',sales:'销售员',auditor:'审计员',agent:'采购智能体'};
let state = {view:'overview', page:1, category:'', search:'', status:'', warningOnly:false, suppliers:[], parts:[], user:null};
let revision = 0;
const tokenKey='chipsupply_access_token';
const rememberedAccountKey='chipsupply_remembered_account';
let loginAnimation=null;
const hasRole=(...roles)=>state.user&&roles.includes(state.user.role);
async function api(path, options={}) {
  const token=localStorage.getItem(tokenKey);
  const headers={'Content-Type':'application/json',...(options.headers||{})};
  if(token)headers.Authorization='Bearer '+token;
  const response = await fetch('/api'+path, {...options,headers,signal:options.signal || AbortSignal.timeout(15000)});
  if(response.status===401){localStorage.removeItem(tokenKey);showLogin();throw new Error('登录已失效，请重新登录');}
  if (!response.ok) throw new Error('服务暂不可用（HTTP '+response.status+'）');
  const result = await response.json();
  if (result.code !== 200) throw new Error(result.message || result.msg || '业务操作未完成');
  return result.data;
}
async function allPages(path) {
  const list=[]; let page=1;
  for (;;) { const result=await api(path+(path.includes('?')?'&':'?')+'current='+page+'&size=100'); list.push(...result.records); if(page>=result.pages || !result.records.length) break; page++; }
  return list;
}
const params = object => new URLSearchParams(Object.entries(object).filter(([,v])=>v!==''&&v!==null&&v!==undefined));
const supplier = id => state.suppliers.find(s=>String(s.id)===String(id))?.name || '未指定供应商';
const pill = s => `<span class="status s${Number(s)}">${statusNames[s]||'未知'}</span>`;
function error(message) { $('#error').textContent=message; $('#error').hidden=false; }
function toast(message) { $('#toast').textContent=message; $('#toast').hidden=false; setTimeout(()=>$('#toast').hidden=true,3500); }
function table(heads, rows) { return `<div class="table-wrap"><table><thead><tr>${heads.map(h=>`<th>${h}</th>`).join('')}</tr></thead><tbody>${rows.join('')}</tbody></table></div>`; }
function pagination(data) { return `<div class="pagination"><span>共 ${num(data.total)} 条 · 第 ${data.current} / ${Math.max(1,data.pages)} 页</span><button class="secondary" data-page="${data.current-1}" ${data.current<=1?'disabled':''}>上一页</button><button class="secondary" data-page="${data.current+1}" ${data.current>=data.pages?'disabled':''}>下一页</button></div>`; }
function partLabel(p) {return `<strong>${escapeHtml(p?.name||'未关联物料')}</strong><span class="model">${escapeHtml(p?.manufacturerPartNumber||p?.model||'')} · ${escapeHtml(p?.packageType||'')}</span>`;}
function ordersTable(orders, compact=false) {return table(['订单编号','采购物料','订单金额','订单状态','预计交付',...(compact?[]:['操作'])],orders.map(o=>`<tr><td class="mono"><button class="text-button mono" data-order="${o.id}">${escapeHtml(o.orderNumber)}</button></td><td>${escapeHtml(o.orderDetail?.[0]?.partDetail?.name||'查看明细')}<span class="model">${o.orderDetail?.length||0} 项物料</span></td><td class="mono">${money(o.totalAmount)}</td><td>${pill(o.status)}</td><td class="mono">${escapeHtml(o.expectedDeliveryDate||'未设置')}</td>${compact?'':`<td><button class="text-button" data-order="${o.id}">查看详情 →</button></td>`}</tr>`));}
async function overview() {
  const [dashboard, warnings, trend, orders] = await Promise.all([api('/statistics/dashboard'),api('/inventory/warning'),api('/statistics/monthly-trend'),api('/orders/page?size=5')]);
  const max = Math.max(...trend.map(t=>Number(t.totalAmount)),1);
  const metrics = [['在册芯片物料',dashboard.partCount,'六类芯片 · 按供应商区分物料','▦'],['合作供应商',dashboard.supplierCount,'多节点供应网络','⌘'],['本月采购金额',money(dashboard.monthPurchase.totalAmount),`${dashboard.monthPurchase.orderCount} 笔采购订单`,'↗'],['库存预警',warnings.length,'当前库存低于补货阈值','◷']];
  return `<section class="hero"><div class="hero-copy"><p class="eyebrow">FROM SILICON TO SUPPLY</p><h2>把每一次采购，连接成确定的交付。</h2><p>面向芯片供应链的采购协同平台。让物料、库存与采购决策，在同一处协同。</p><a class="hero-link" href="#catalog">探索芯片目录 <span>↗</span></a></div><div class="circuit" aria-hidden="true"><div class="chip-diagram"><strong>CHIP</strong></div></div></section>
  <div class="metrics">${metrics.map((m,i)=>`<div class="metric ${i===3?'warn':''}"><div class="metric-top"><span>${m[0]}</span><span class="metric-icon">${m[3]}</span></div><strong>${m[1]}</strong><small>${m[2]}</small></div>`).join('')}</div>
  <div class="grid-two"><section class="panel"><div class="panel-head"><div><h2>采购金额趋势</h2><small>按下单月份汇总 · 含所有订单状态</small></div><span class="pill">人民币 CNY</span></div><div class="trend" aria-label="每月采购金额">${trend.length?trend.map(t=>`<div class="bar-column" title="${escapeHtml(t.month)}：${money(t.totalAmount)}"><small>${num(Math.round(t.totalAmount))}</small><div class="bar" style="height:${Number(t.totalAmount)/max*82}%"></div><label>${escapeHtml(t.month.slice(5))}月</label></div>`).join(''):'<div class="empty">暂无采购记录</div>'}</div></section>
  <section class="panel"><div class="panel-head"><div><h2>待关注物料 <span class="pill">${warnings.length}</span></h2><small>优先查看库存预警</small></div><a href="#inventory">查看全部 ↗</a></div><div class="warning-list">${warnings.slice(0,4).map(w=>`<div class="warning-row"><span class="part-icon">IC</span><div><h3>${escapeHtml(w.partDetail?.name)}</h3><p>${escapeHtml(w.partDetail?.model)}</p></div><div class="quantity">${num(w.currentQuantity)}<small>安全量 ${num(w.safetyStock)} 片</small></div></div>`).join('')||'<div class="empty">库存充足，暂无预警</div>'}</div></section></div>
  <section class="panel section-gap"><div class="panel-head"><div><h2>最近采购订单</h2><small>当前采购进展，一目了然</small></div><a href="#orders">全部订单 ↗</a></div>${ordersTable(orders.records,true)}</section>`;
}
async function catalog() {
  const result = await api('/parts/page?'+params({current:state.page,size:10,name:state.search,category:state.category}));
  return `<div class="toolbar"><input class="search-field" id="search" aria-label="搜索芯片" placeholder="搜索名称、MPN 或物料编码" value="${escapeHtml(state.search)}"><span class="count-label">${num(result.total)} 个芯片物料</span><button class="secondary" id="new-part">＋ 新建物料</button></div><div class="category-tabs">${['',...state.categories].map(c=>`<button data-category="${c}" class="${state.category===c?'active':''}">${c||'全部芯片'}</button>`).join('')}</div><section class="panel">${result.records.length?table(['芯片 / 制造商料号','物料编码','供应商','MOQ / 交期','采购单价','操作'],result.records.map(p=>`<tr><td>${partLabel(p)}</td><td class="mono">${escapeHtml(p.partCode)}</td><td>${escapeHtml(supplier(p.supplierId))}</td><td>${num(p.minOrderQuantity)} 片<span class="model">${p.leadTimeDays||'—'} 天</span></td><td class="mono">${money(p.purchasePrice)}</td><td><button class="text-button" data-part="${p.id}">参数</button> · <button class="text-button" data-buy="${p.id}">采购 ↗</button></td></tr>`)):'<div class="empty">没有找到匹配的芯片，请调整关键词或分类。</div>'}${pagination(result)}</section>`;
}
async function inventory() {
  const result=await api('/inventory/page?'+params({current:state.page,size:10,partName:state.search}));
  const warnings=state.warningOnly?await api('/inventory/warning'):null;
  const records=warnings?warnings.filter(w=>!state.search||w.partDetail?.name?.includes(state.search)):result.records;
  return `<div class="toolbar"><input class="search-field" id="search" aria-label="搜索库存" placeholder="搜索芯片名称" value="${escapeHtml(state.search)}"><label><input type="checkbox" id="warning-only" ${state.warningOnly?'checked':''}> 仅查看库存预警</label><span class="count-label">数量单位：片</span></div><section class="panel">${records.length?table(['芯片物料','仓储位置','现有 / 安全库存','库存状态','最近入库','操作'],records.map(w=>`<tr><td>${partLabel(w.partDetail)}</td><td>${escapeHtml(w.warehouseLocation)}</td><td class="mono">${num(w.currentQuantity)} / ${num(w.safetyStock)}<div class="stock-track ${w.currentQuantity<=w.safetyStock?'low':''}"><i style="width:${Math.min(100,100*w.currentQuantity/Math.max(w.safetyStock*3,1))}%"></i></div></td><td><span class="status ${w.currentQuantity<=w.safetyStock?'s1':''}">${w.currentQuantity<=w.safetyStock?'建议补货':'库存充足'}</span></td><td class="mono">${escapeHtml(w.lastInboundTime?.slice(0,10)||'—')}</td><td><button class="text-button" data-stock="${w.partId}" data-stock-mode="inbound">入库</button> · <button class="text-button" data-stock="${w.partId}" data-stock-mode="outbound">出库</button></td></tr>`)):'<div class="empty">暂无匹配的库存记录。</div>'}${warnings?'':pagination(result)}</section>`;
}
async function orders() {
  const result=await api('/orders/page?'+params({current:state.page,size:10,orderNumber:state.search,status:state.status}));
  return `<div class="toolbar"><input class="search-field" id="search" aria-label="搜索订单" placeholder="搜索采购单编号" value="${escapeHtml(state.search)}"><select id="status-filter" aria-label="订单状态"><option value="">全部状态</option>${statusNames.slice(1).map((s,i)=>`<option value="${i+1}" ${String(i+1)===state.status?'selected':''}>${s}</option>`).join('')}</select><span class="count-label">${num(result.total)} 笔采购订单</span></div><section class="panel">${result.records.length?ordersTable(result.records):'<div class="empty">暂无匹配的订单。</div>'}${pagination(result)}</section>`;
}
async function suppliers() {
  return `<div class="toolbar"><p class="muted">供应网络 · 统一维护准入、评级与履约状态</p><span class="count-label">${state.suppliers.length} 个供应节点</span></div><div class="supplier-grid">${state.suppliers.map((s,i)=>`<article class="panel supplier-card"><div class="symbol">⌘ <span class="eyebrow">NODE ${String(i+1).padStart(2,'0')}</span></div><h3>${escapeHtml(s.name)}</h3><p class="mono">${escapeHtml(s.supplierCode)}</p><p>${escapeHtml(s.address)}</p><p>供应商联系人：${escapeHtml(s.contactPerson)}</p><div class="card-bottom"><span class="status">${s.status===1?'合作中':'未合作'}</span><span class="pill">信用评级 ${escapeHtml(s.creditRating)}</span></div></article>`).join('')}</div>`;
}
async function audit() {
  const result=await api('/audit-logs/page?'+params({current:state.page,size:20,actor:state.search}));
  return `<div class="toolbar"><input class="search-field" id="search" aria-label="搜索操作账号" placeholder="搜索操作账号" value="${escapeHtml(state.search)}"><span class="count-label">${num(result.total)} 条不可变审计记录</span></div><section class="panel">${result.records.length?table(['时间','操作账号 / 角色','动作','业务资源','结果','请求'],result.records.map(x=>`<tr><td class="mono">${escapeHtml((x.createdAt||'').replace('T',' '))}</td><td>${escapeHtml(x.actorUsername||'匿名')}<span class="model">${escapeHtml(roleNames[x.actorRole]||x.actorRole||'—')}</span></td><td class="mono">${escapeHtml(x.action)}</td><td>${escapeHtml(x.resourceType)}<span class="model">${escapeHtml(x.resourceId||'—')}</span></td><td><span class="status ${x.result==='FAILURE'?'s5':''}">${x.result==='SUCCESS'?'成功':'失败'}</span></td><td class="mono">${escapeHtml(x.httpMethod)} ${escapeHtml(x.requestPath)}</td></tr>`)):'<div class="empty">暂无审计记录。</div>'}${pagination(result)}</section>`;
}
function enforceRoleVisibility(){
  if(!hasRole('admin','purchase'))$('#new-part')?.remove();
  if(!hasRole('admin','purchase','agent'))document.querySelectorAll('[data-buy]').forEach(el=>el.remove());
  if(!hasRole('admin','warehouse'))document.querySelectorAll('[data-stock]').forEach(el=>el.remove());
}
async function render() {
  const version=++revision;
  $('#error').hidden=true;
  try {
    const markup=await ({overview,catalog,inventory,orders,suppliers,audit}[state.view])();
    if(version!==revision)return;
    $('#view').innerHTML=markup; $('#connection').textContent='业务服务已连接'; enforceRoleVisibility(); bindView();
  } catch(e) {if(version===revision){error('数据读取失败：'+e.message+'。请确认 Java 服务与 MySQL 已启动，然后点击刷新。'); $('#connection').textContent='连接中断'; $('#view').innerHTML='<div class="empty">数据暂不可用</div>';}}
}
function navigate() {
  state.view=location.hash.slice(1) in labels?location.hash.slice(1):'overview';
  state.page=1;state.search='';state.category='';state.status='';
  document.querySelectorAll('[data-nav]').forEach(a=>a.classList.toggle('active',a.dataset.nav===state.view));
  $('#page-title').innerHTML=labels[state.view][0]+'<span class="title-dot">.</span>';
  $('#breadcrumb').textContent=labels[state.view][0];$('#page-description').textContent=labels[state.view][1];
  render();
}
function modal(title, markup) {$('#modal-title').textContent=title;$('#modal-body').innerHTML=markup;if(!$('#modal').open)$('#modal').showModal();}
function formTask(form, action) {
  form.addEventListener('submit',async event=>{event.preventDefault();const button=form.querySelector('[type=submit]');button.disabled=true;form.querySelector('.form-error').textContent='';try{await action(new FormData(form));$('#modal').close();toast('操作成功，数据已更新');await render();}catch(e){form.querySelector('.form-error').textContent=e.message;}finally{button.disabled=false;}});
}
async function newOrder(id) {
  state.parts=await allPages('/parts/page');
  if(!state.parts.length){toast('请先新建芯片物料');return;}
  const p=state.parts.find(p=>String(p.id)===String(id))||state.parts[0];
  modal('新建芯片采购单',`<p class="notice">选择物料与数量，提交后生成待审核采购单。</p><form id="order-form"><div class="form-grid section-gap"><label class="full">芯片物料 / 供应商<select name="partId" id="order-part">${state.parts.map(x=>`<option value="${x.id}" ${x.id===p.id?'selected':''}>${escapeHtml(x.name)} · ${escapeHtml(x.model)} · ${escapeHtml(supplier(x.supplierId))}</option>`).join('')}</select></label><label>采购数量（片）<input name="quantity" id="order-qty" type="number" min="${p.minOrderQuantity||1}" step="1" required value="${p.minOrderQuantity||100}"></label><label>单价（元 / 片）<input name="unitPrice" id="order-price" type="number" min="0.01" step="0.01" required value="${p.purchasePrice}"></label><label class="full">预计交货日期<input name="expectedDeliveryDate" id="order-date" type="date" required></label><label class="full">采购说明<textarea name="remark" maxlength="500" placeholder="例如：嵌入式系统课程 / 第三组实验"></textarea></label></div><p id="order-hint" class="muted section-gap"></p><p class="form-error" role="alert"></p><div class="form-actions"><strong id="order-total"></strong><button type="submit" class="primary">提交采购申请 →</button></div></form>`);
  function updatePart(){const item=state.parts.find(x=>String(x.id)===$('#order-part').value);$('#order-qty').min=item.minOrderQuantity||1;$('#order-qty').value=item.minOrderQuantity||1;$('#order-price').value=item.purchasePrice;const date=new Date();date.setDate(date.getDate()+(item.leadTimeDays||7));$('#order-date').value=[date.getFullYear(),String(date.getMonth()+1).padStart(2,'0'),String(date.getDate()).padStart(2,'0')].join('-');$('#order-hint').textContent=`MOQ ${item.minOrderQuantity||1} 片 · ${item.packageType||'未设置封装'} · 标准交期 ${item.leadTimeDays||7} 天`;updateTotal();}
  function updateTotal(){$('#order-total').textContent='合计 '+money(Number($('#order-qty').value)*Number($('#order-price').value));}
  $('#order-part').onchange=updatePart;$('#order-qty').oninput=updateTotal;$('#order-price').oninput=updateTotal;updatePart();
  formTask($('#order-form'),async f=>api('/orders/create',{method:'POST',body:JSON.stringify({orderNumber:'CL-'+Date.now()+'-'+crypto.randomUUID().slice(0,6),status:1,expectedDeliveryDate:f.get('expectedDeliveryDate'),remark:f.get('remark'),orderDetail:[{partId:Number(f.get('partId')),quantity:Number(f.get('quantity')),unitPrice:Number(f.get('unitPrice'))}]})}));
}
async function partDetails(id) {
  const p=await api('/parts/get/'+id);
  modal(p.name,`<p class="eyebrow">${escapeHtml(p.partCode)}</p><dl class="spec-list">${Object.entries({'制造商':p.manufacturer,'制造商料号':p.manufacturerPartNumber,'封装':p.packageType,'规格参数':p.specification,'温度等级':p.temperatureGrade,'供应商':supplier(p.supplierId),'采购单价':money(p.purchasePrice)+' / 片','最小起订量':num(p.minOrderQuantity)+' 片','标准交期':num(p.leadTimeDays)+' 天'}).map(([k,v])=>`<div><dt>${k}</dt><dd>${escapeHtml(v)}</dd></div>`).join('')}</dl><div class="form-actions"><button class="secondary" id="quote-link">查看供应商报价快照 ↗</button>${hasRole('admin','purchase','agent')?'<button class="primary" id="detail-buy">创建采购单</button>':''}</div>`);
  if($('#detail-buy'))$('#detail-buy').onclick=()=>run(()=>newOrder(id));
  $('#quote-link').onclick=async()=>{const url='/quotes/'+encodeURIComponent(p.partCode)+'.html';const response=await fetch(url,{method:'HEAD'});if(response.ok)window.open(url,'_blank','noopener');else toast('该物料没有报价快照，可在采购单中填写实际单价');};
}
async function orderDetails(id) {
  const o=await api('/orders/get/'+id);
  const transitions=hasRole('admin','purchase')?await api('/orders/'+id+'/allowed-transitions'):[];
  const statusForm=transitions.length?`<form id="status-form"><div class="form-grid"><label>流转至下一状态<select name="status">${transitions.map(s=>`<option value="${s.code}">${escapeHtml(s.description)}</option>`).join('')}</select></label></div><p class="form-error" role="alert"></p><div class="form-actions"><small class="muted">流转到“已入库”时，订单明细会在同一事务中自动增加库存。</small><button class="primary" type="submit">确认流转</button></div></form>`:'<p class="notice section-gap">当前状态没有可执行的下一步，或你的角色只有查看权限。</p>';
  modal('采购单 '+o.orderNumber,`<div class="form-actions">${pill(o.status)}<strong>${money(o.totalAmount)}</strong></div>${table(['物料','数量','单价','小计'],(o.orderDetail||[]).map(d=>`<tr><td>${partLabel(d.partDetail)}</td><td>${num(d.quantity)} 片</td><td>${money(d.unitPrice)}</td><td>${money(d.subtotal)}</td></tr>`))}<dl class="spec-list"><div><dt>预计交货</dt><dd>${escapeHtml(o.expectedDeliveryDate||'未设置')}</dd></div><div><dt>备注</dt><dd>${escapeHtml(o.remark||'无')}</dd></div></dl>${statusForm}`);
  if($('#status-form'))formTask($('#status-form'),f=>api('/orders/update-status/'+id+'?status='+f.get('status'),{method:'PATCH'}));
}
async function stockForm(id, mode) {
  const p=await api('/parts/get/'+id);const inbound=mode==='inbound';
  modal((inbound?'芯片入库':'芯片出库')+' · '+p.name,`<form id="stock-form"><div class="form-grid"><label>数量（片）<input type="number" name="quantity" min="1" step="1" required></label>${inbound?'<label>仓储位置<input name="warehouseLocation" placeholder="留空保留原库位"></label>':''}</div><p class="form-error" role="alert"></p><div class="form-actions"><span class="muted">${escapeHtml(p.model)}</span><button class="primary" type="submit">确认${inbound?'入库':'出库'}</button></div></form>`);
  formTask($('#stock-form'),f=>api('/inventory/'+mode+'?'+params({partId:id,quantity:f.get('quantity'),warehouseLocation:f.get('warehouseLocation')}),{method:'POST'}));
}
async function newPart() {
  modal('新建芯片物料',`<form id="part-form"><div class="form-grid">${[['partCode','物料编码'],['name','物料名称'],['manufacturerPartNumber','制造商料号 MPN'],['manufacturer','制造商'],['packageType','封装'],['temperatureGrade','温度等级']].map(([name,label])=>`<label>${label}<input name="${name}" required maxlength="${name==='partCode'?50:100}"></label>`).join('')}<label>芯片分类<select name="category">${state.categories.map(c=>`<option>${c}</option>`).join('')}</select></label><label>供应商<select name="supplierId" required>${state.suppliers.map(s=>`<option value="${s.id}">${escapeHtml(s.name)}</option>`).join('')}</select></label>${[['purchasePrice','采购单价（元 / 片）','0.01','0.01'],['minOrderQuantity','最小起订量（片）','1','1'],['leadTimeDays','交期（天）','1','1'],['stockWarningValue','预警库存（片）','0','1']].map(([name,label,min,step])=>`<label>${label}<input name="${name}" type="number" required min="${min}" step="${step}"></label>`).join('')}<label class="full">规格参数<input name="specification" maxlength="200"></label></div><p class="form-error" role="alert"></p><div class="form-actions"><small class="muted">新增后可通过入库操作建立库存。</small><button class="primary" type="submit">保存物料</button></div></form>`);
  formTask($('#part-form'),f=>{const body=Object.fromEntries(f);for(const k of ['supplierId','purchasePrice','minOrderQuantity','leadTimeDays','stockWarningValue'])body[k]=Number(body[k]);body.unit='片';body.model=body.manufacturerPartNumber;return api('/parts/create',{method:'POST',body:JSON.stringify(body)});});
}
async function run(action){try{await action();}catch(e){error(e.message);}}
function bindView() {
  if($('#search')){let timer;$('#search').oninput=e=>{const value=e.target.value;clearTimeout(timer);timer=setTimeout(async()=>{state.search=value;state.page=1;await render();const input=$('#search');if(input){input.focus();input.setSelectionRange(value.length,value.length);}},450);};}
  document.querySelectorAll('[data-category]').forEach(el=>el.onclick=()=>{state.category=el.dataset.category;state.page=1;render();});
  document.querySelectorAll('[data-page]').forEach(el=>el.onclick=()=>{state.page=Number(el.dataset.page);render();});
  document.querySelectorAll('[data-part]').forEach(el=>el.onclick=()=>run(()=>partDetails(el.dataset.part)));
  document.querySelectorAll('[data-buy]').forEach(el=>el.onclick=()=>run(()=>newOrder(el.dataset.buy)));
  document.querySelectorAll('[data-order]').forEach(el=>el.onclick=()=>run(()=>orderDetails(el.dataset.order)));
  document.querySelectorAll('[data-stock]').forEach(el=>el.onclick=()=>run(()=>stockForm(el.dataset.stock,el.dataset.stockMode)));
  if($('#status-filter'))$('#status-filter').onchange=e=>{state.status=e.target.value;state.page=1;render();};
  if($('#warning-only'))$('#warning-only').onchange=e=>{state.warningOnly=e.target.checked;state.page=1;render();};
  if($('#new-part'))$('#new-part').onclick=()=>run(newPart);
}
function initLoginAnimation(){
  if(loginAnimation||!window.lottie||!$('#login-lottie'))return;
  const reduceMotion=window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  loginAnimation=window.lottie.loadAnimation({container:$('#login-lottie'),renderer:'svg',loop:!reduceMotion,autoplay:false,path:'/animations/supply-network.json',rendererSettings:{preserveAspectRatio:'xMidYMid meet'}});
  loginAnimation.addEventListener('DOMLoaded',()=>{
    if(reduceMotion)loginAnimation.goToAndStop(80,true);
    else{loginAnimation.setSpeed(.9);loginAnimation.play();}
  });
}
function showLogin(){
  state.user=null;$('#login-screen').hidden=false;$('#logout').hidden=true;initLoginAnimation();
  if(loginAnimation&&!window.matchMedia('(prefers-reduced-motion: reduce)').matches)loginAnimation.play();
  const remembered=localStorage.getItem(rememberedAccountKey);
  if(remembered&&!$('#login-form').elements.username.value){$('#login-form').elements.username.value=remembered;$('#remember-account').checked=true;}
}
function applyIdentity(){const u=state.user;$('#login-screen').hidden=true;if(loginAnimation)loginAnimation.pause();$('#logout').hidden=false;$('#side-user').textContent=u.realName||u.username;$('#side-role').textContent=roleNames[u.role]||u.role;$('#side-avatar').textContent=(u.realName||u.username).slice(0,1);$('#top-avatar').textContent=(u.realName||u.username).slice(0,1);document.querySelectorAll('[data-roles]').forEach(el=>el.hidden=!el.dataset.roles.split(',').includes(u.role));$('#new-order').hidden=!hasRole('admin','purchase','agent');}
async function initialize() {
  if(!localStorage.getItem(tokenKey)){showLogin();return;}
  try {state.user=await api('/auth/me');applyIdentity();const [info,suppliers]=await Promise.all([api('/system/info'),allPages('/suppliers/page')]);state.suppliers=suppliers;state.categories=info.categories;navigate();}catch(e){if(localStorage.getItem(tokenKey)){error('无法连接芯片采购服务：'+e.message);$('#connection').textContent='连接中断';$('#view').innerHTML='<div class="empty">请启动 Java 服务后刷新页面。</div>';}}
}
$('#today').textContent=new Date().toLocaleDateString('zh-CN',{year:'numeric',month:'2-digit',day:'2-digit'});
$('#refresh').onclick=initialize;$('#new-order').onclick=()=>run(()=>newOrder());$('#close-modal').onclick=()=>$('#modal').close();
$('#logout').onclick=()=>{localStorage.removeItem(tokenKey);location.hash='overview';showLogin();};
$('#toggle-password').onclick=()=>{const input=$('#login-password');const reveal=input.type==='password';input.type=reveal?'text':'password';$('#toggle-password').setAttribute('aria-pressed',String(reveal));$('#toggle-password').setAttribute('aria-label',reveal?'隐藏密码':'显示密码');};
$('#login-form').onsubmit=async event=>{event.preventDefault();const form=event.currentTarget;const button=form.querySelector('[type=submit]');const buttonText=button.querySelector('span');button.disabled=true;buttonText.textContent='正在验证身份…';$('#login-error').textContent='';try{const data=Object.fromEntries(new FormData(form));const response=await fetch('/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)});const result=await response.json();if(!response.ok||result.code!==200)throw new Error(result.message||'登录失败');if($('#remember-account').checked)localStorage.setItem(rememberedAccountKey,data.username);else localStorage.removeItem(rememberedAccountKey);localStorage.setItem(tokenKey,result.data.token);form.elements.password.value='';await initialize();}catch(e){$('#login-error').textContent=e.message;}finally{button.disabled=false;buttonText.textContent='进入工作空间';}};
window.addEventListener('hashchange',()=>{if(state.categories)navigate();});
initialize();
