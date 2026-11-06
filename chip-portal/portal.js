'use strict';
const $ = s => document.querySelector(s);
const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const num = n => Number(n || 0).toLocaleString('zh-CN');
const money = n => '¥' + Number(n || 0).toLocaleString('zh-CN', {minimumFractionDigits:2, maximumFractionDigits:2});
const statusNames = ['','待审核','已审核','采购中','已入库','已取消'];
const labels = {overview:['采购总览','掌握每一颗芯片的供应节奏。'],catalog:['芯片目录','从料号到封装，匹配业务需求与供应资源。'],inventory:['库存监控','连接现有库存与补货需求，关注每一个供应缺口。'],orders:['采购订单','清晰记录从采购申请到芯片交付的全过程。'],suppliers:['供应网络','在价格、供货能力与交期之间，找到合适的平衡。'],replenishment:['智能补货','从库存水位、需求节奏与供应交期预测下一次缺货。'],'supplier-risk':['供应商风险雷达','把信用、履约、交期与供应集中度转化为可行动的风险信号。'],'order-alerts':['订单异常预警','主动识别审批停滞、发运缺失、交付逾期与物流异常。'],reconciliation:['三单匹配与采购对账','核验采购单、收货单与供应商发票，让每笔应付都有业务依据。'],'event-traces':['事件链路追踪','用统一 Trace ID 串联订单、仓储、财务和库存的业务事件。'],audit:['审计日志','追踪关键业务操作与安全事件，形成可核验的责任链。']};
const roleNames={admin:'管理员',purchase:'采购员',warehouse:'仓管员',sales:'销售员',auditor:'审计员',agent:'采购智能体'};
let state = {view:'overview', page:1, category:'', search:'', status:'', warningOnly:false, selectedRiskSupplier:'', suppliers:[], parts:[], user:null};
let revision = 0;
const tokenKey='chipsupply_access_token';
const rememberedAccountKey='chipsupply_remembered_account';
let loginAnimation=null;
const hasRole=(...roles)=>state.user&&roles.includes(state.user.role);
function setConnection(status) {
  document.body.dataset.connection=status;
  const indicator=$('#connection');
  indicator.dataset.state=status;
  indicator.querySelector('span').textContent={connecting:'正在连接',online:'服务在线',offline:'连接中断'}[status];
  indicator.title={connecting:'正在连接业务服务',online:'业务服务已连接，数据读取正常',offline:'业务服务连接中断，请尝试刷新数据'}[status];
}
function closeAccountMenu(restoreFocus=false) {
  $('#account-menu').hidden=true;
  $('#account-toggle').setAttribute('aria-expanded','false');
  if(restoreFocus)$('#account-toggle').focus();
}
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
const riskNames={HIGH:'高风险',MEDIUM:'需关注',LOW:'低风险'};
const riskTag=level=>`<span class="risk-tag ${String(level).toLowerCase()}"><i></i>${riskNames[level]||level}</span>`;
const matchNames={MATCHED:'核验通过',DISCREPANCY:'存在差异',WAITING:'待补单据'};
const matchTag=status=>`<span class="match-tag ${String(status).toLowerCase()}"><i></i>${matchNames[status]||status}</span>`;
function error(message) { $('#error').textContent=message; $('#error').hidden=false; }
function toast(message) { $('#toast').textContent=message; $('#toast').hidden=false; setTimeout(()=>$('#toast').hidden=true,3500); }
function table(heads, rows) { return `<div class="table-wrap"><table><thead><tr>${heads.map(h=>`<th>${h}</th>`).join('')}</tr></thead><tbody>${rows.join('')}</tbody></table></div>`; }
function pagination(data) { return `<div class="pagination"><span>共 ${num(data.total)} 条 · 第 ${data.current} / ${Math.max(1,data.pages)} 页</span><button class="secondary" data-page="${data.current-1}" ${data.current<=1?'disabled':''}>上一页</button><button class="secondary" data-page="${data.current+1}" ${data.current>=data.pages?'disabled':''}>下一页</button></div>`; }
function partLabel(p) {return `<strong>${escapeHtml(p?.name||'未关联物料')}</strong><span class="model">${escapeHtml(p?.manufacturerPartNumber||p?.model||'')} · ${escapeHtml(p?.packageType||'')}</span>`;}
function ordersTable(orders, compact=false) {return table(['订单编号','采购物料','订单金额','订单状态','预计交付',...(compact?[]:['操作'])],orders.map(o=>`<tr><td class="mono"><button class="text-button mono" data-order="${o.id}">${escapeHtml(o.orderNumber)}</button></td><td>${escapeHtml(o.orderDetail?.[0]?.partDetail?.name||'查看明细')}<span class="model">${o.orderDetail?.length||0} 项物料</span></td><td class="mono">${money(o.totalAmount)}</td><td>${pill(o.status)}</td><td class="mono">${escapeHtml(o.expectedDeliveryDate||'未设置')}</td>${compact?'':`<td><button class="text-button" data-order="${o.id}">查看详情 →</button></td>`}</tr>`));}
function overviewCircuit() {
  const routes = [
    'M20 62h112l40 40h52', 'M0 132h224', 'M44 222h96l48-60h36',
    'M280 0v75', 'M540 55H428l-40 47h-52', 'M560 132H336',
    'M520 218h-96l-48-56h-40', 'M280 260v-72',
  ];
  const pins = Array.from({length:6}, (_, i) => {
    const position=242+i*15, side=94+i*15;
    return `<path d="M${position} 65v11m0 112v11M214 ${side}h11m111 0h11"/>`;
  }).join('');
  return `<div class="circuit circuit-animated" aria-hidden="true"><div class="circuit-aura"></div><svg class="circuit-board" viewBox="0 0 560 260" fill="none"><g class="circuit-traces">${routes.map(d=>`<path d="${d}"/>`).join('')}</g><g class="circuit-signals">${routes.map((d,i)=>`<path d="${d}" pathLength="100" style="--signal-delay:-${i*.63}s;--signal-duration:${3.6+(i%3)*.7}s"/>`).join('')}</g><g class="circuit-nodes"><circle cx="20" cy="62" r="3"/><circle cx="44" cy="222" r="3"/><circle cx="540" cy="55" r="3"/><circle cx="520" cy="218" r="3"/></g><g class="chip-assembly"><rect class="chip-halo halo-outer" x="202" y="53" width="156" height="156" rx="29"/><rect class="chip-halo halo-inner" x="213" y="64" width="134" height="134" rx="23"/><g class="chip-pins">${pins}</g><rect class="chip-package" x="224" y="76" width="112" height="112" rx="16"/><rect class="chip-die" x="241" y="93" width="78" height="78" rx="10"/><path class="chip-die-circuit" d="M252 112h11v-8h14m30 48h-12v8h-15M253 151v-16m54-22v16"/><path class="chip-scan" d="M242 94h76"/><text x="280" y="138" class="chip-wordmark" text-anchor="middle">CHIP</text><circle class="chip-core-light" cx="280" cy="155" r="2"/></g></svg></div><button class="motion-toggle hero-motion-toggle" type="button" aria-label="暂停页面动效" aria-pressed="false" title="暂停页面动效"><svg class="motion-pause-icon" viewBox="0 0 16 16" aria-hidden="true"><path d="M5 4v8m6-8v8"/></svg><svg class="motion-play-icon" viewBox="0 0 16 16" aria-hidden="true"><path d="m5 3 7 5-7 5Z"/></svg><span>暂停动效</span></button>`;
}
async function overview() {
  const [dashboard, warnings, trend, orders] = await Promise.all([api('/statistics/dashboard'),api('/inventory/warning'),api('/statistics/monthly-trend'),api('/orders/page?size=5')]);
  const max = Math.max(...trend.map(t=>Number(t.totalAmount)),1);
  const metrics = [['在册芯片物料',dashboard.partCount,'六类芯片 · 按供应商区分物料','▦'],['合作供应商',dashboard.supplierCount,'多节点供应网络','⌘'],['本月采购金额',money(dashboard.monthPurchase.totalAmount),`${dashboard.monthPurchase.orderCount} 笔采购订单`,'↗'],['库存预警',warnings.length,'当前库存低于补货阈值','◷']];
  return `<section class="hero"><div class="hero-copy"><p class="eyebrow">FROM SILICON TO SUPPLY</p><h2>把每一次采购，连接成确定的交付。</h2><p>面向芯片供应链的采购协同平台。让物料、库存与采购决策，在同一处协同。</p><a class="hero-link" href="#catalog">探索芯片目录 <span>↗</span></a></div>${overviewCircuit()}</section>
  <div class="metrics">${metrics.map((m,i)=>`<div class="metric ${i===3?'warn':''}"><div class="metric-top"><span>${m[0]}</span><span class="metric-icon">${m[3]}</span></div><strong>${m[1]}</strong><small>${m[2]}</small></div>`).join('')}</div>
  <div class="grid-two"><section class="panel trend-panel"><div class="panel-head"><div><h2>采购金额趋势</h2><small>按下单月份汇总 · 含所有订单状态</small></div><span class="pill">人民币 CNY</span></div><div class="trend" aria-label="每月采购金额">${trend.length?trend.map((t,i)=>`<div class="bar-column" style="--enter-delay:${i*90}ms" title="${escapeHtml(t.month)}：${money(t.totalAmount)}"><small class="bar-value"><span>¥</span>${num(Math.round(t.totalAmount))}</small><div class="bar" style="height:${Math.max(4,Number(t.totalAmount)/max*78)}%"><i></i></div><label>${escapeHtml(t.month.slice(5))}<span>月</span></label><div class="bar-tooltip"><b>${escapeHtml(t.month)}</b><strong>${money(t.totalAmount)}</strong><span>${num(t.orderCount||0)} 笔采购订单</span></div></div>`).join(''):'<div class="empty">暂无采购记录</div>'}</div></section>
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
async function replenishment() {
  const data=await api('/intelligence/replenishment');const s=data.summary;
  return `<div class="decision-summary"><article class="decision-lead"><span class="decision-kicker">REPLENISHMENT ENGINE · 可解释规则模型</span><h2>让补货发生在缺货之前。</h2><p>系统以近 180 天采购节奏作为需求代理，叠加安全库存、MOQ 与供应商交期计算建议。所有建议只生成待审核采购申请，不会自动下单。</p><div class="decision-method"><span>需求窗口<b>180 天</b></span><span>补货策略<b>交期 + 30 天覆盖</b></span><span>决策边界<b>人工审批</b></span></div></article><div class="decision-metrics"><article><small>高风险物料</small><strong>${num(s.highRiskCount)}</strong><span>需优先处理</span></article><article><small>建议补货</small><strong>${num(s.suggestedOrderCount)}</strong><span>笔候选采购</span></article><article><small>预计资金</small><strong>${money(s.estimatedReplenishmentAmount)}</strong><span>按当前采购价</span></article></div></div>
  <section class="panel section-gap"><div class="panel-head"><div><h2>缺货风险队列</h2><small>${escapeHtml(s.model)} · 建议数量已按 MOQ 向上取整</small></div><span class="pill">每日动态评估</span></div>${data.items.length?table(['风险 / 芯片物料','当前水位','预计覆盖','补货触发点','建议补货','判断依据','操作'],data.items.map(x=>`<tr><td>${riskTag(x.riskLevel)}<div class="risk-part"><strong>${escapeHtml(x.partName)}</strong><span>${escapeHtml(x.partCode)} · ${escapeHtml(x.model||'')}</span></div></td><td class="mono">${num(x.currentQuantity)}<span class="model">安全库存 ${num(x.safetyStock)}</span></td><td><strong>${x.coverageDays} 天</strong><div class="coverage-track"><i style="width:${Math.min(100,Number(x.coverageDays)/Math.max(Number(x.leadTimeDays)+30,1)*100)}%"></i></div><span class="model">供应交期 ${x.leadTimeDays} 天</span></td><td class="mono">${num(x.reorderPoint)} 片</td><td><strong>${num(x.recommendedQuantity)} 片</strong><span class="model">${money(x.estimatedAmount)}</span></td><td class="reason-cell replenishment-reason">${escapeHtml(x.reason)}<div class="replenishment-supplier"><span>供应商</span><strong>${escapeHtml(x.supplierName||'未关联供应商')}</strong></div></td><td>${x.recommendedQuantity>0&&hasRole('admin','purchase','agent')?`<button class="text-button" data-replenish="${x.partId}" data-quantity="${x.recommendedQuantity}">生成采购申请 →</button>`:'<span class="muted">持续监控</span>'}</td></tr>`)):'<div class="empty">暂无需要补货的物料。</div>'}</section>`;
}
async function supplierRisk() {
  const data=await api('/intelligence/supplier-risks');const s=data.summary;
  if(!data.items.some(item=>String(item.supplierId)===String(state.selectedRiskSupplier)))state.selectedRiskSupplier=String(data.items[0]?.supplierId||'');
  const top=data.items.find(item=>String(item.supplierId)===String(state.selectedRiskSupplier))||data.items[0];
  const dimensionLabels={credit:'信用',delivery:'履约',leadTime:'交期',cooperation:'合作状态',concentration:'供应集中度'};
  const radar=top?Object.entries(top.dimensions).map(([k,v])=>`<div class="radar-row"><span>${dimensionLabels[k]}</span><div><i style="width:${v}%"></i></div><b>${v}</b></div>`).join(''):'';
  const options=data.items.map((item,index)=>`<option value="${item.supplierId}" ${String(item.supplierId)===String(top?.supplierId)?'selected':''}>${index+1}. ${escapeHtml(item.supplierName)} · ${item.riskScore} 分</option>`).join('');
  return `<div class="decision-summary supplier-intro"><article class="decision-lead"><span class="decision-kicker">SUPPLIER RISK RADAR · 五维动态评估</span><h2>把供应风险变成可比较的数字。</h2><p>风险分越高越需要关注。评分综合供应商信用、历史准时率、平均交期、合作状态和单一来源集中度，避免只用“价格最低”做采购决策。</p><div class="decision-method"><span>监控供应商<b>${num(s.supplierCount)} 家</b></span><span>平均风险分<b>${s.averageRiskScore}</b></span><span>高风险节点<b>${num(s.highRiskCount)} 家</b></span></div></article><article class="radar-card"><div class="radar-head"><label for="risk-supplier-select">供应商风险节点</label>${top?riskTag(top.riskLevel):''}</div><select id="risk-supplier-select" aria-label="切换风险雷达供应商">${options}</select><p class="radar-hint">按综合风险从高到低排列 · 当前 ${data.items.findIndex(item=>String(item.supplierId)===String(top?.supplierId))+1} / ${data.items.length}</p>${radar}</article></div>
  <section class="panel section-gap"><div class="panel-head"><div><h2>供应商风险清单</h2><small>${escapeHtml(s.model)} · 0 分最佳，100 分风险最高；点击供应商可切换上方雷达</small></div><span class="pill">评分规则 v1.0</span></div>${data.items.length?table(['供应商','综合风险','准时交付率','平均交期','业务暴露','策略建议'],data.items.map(x=>`<tr class="${String(x.supplierId)===String(top?.supplierId)?'selected-risk-row':''}"><td><button class="supplier-risk-link" data-risk-supplier="${x.supplierId}">${escapeHtml(x.supplierName)}</button><span class="model">${escapeHtml(x.supplierCode)} · 信用 ${escapeHtml(x.creditRating||'未评级')}</span></td><td>${riskTag(x.riskLevel)}<strong class="risk-score">${x.riskScore}</strong><span class="model">/ 100</span></td><td><strong>${x.onTimeRate}%</strong><span class="model">${num(x.orderCount)} 笔关联订单</span></td><td>${x.averageLeadTimeDays} 天</td><td>${num(x.partCount)} 个物料<span class="model">物流异常 ${num(x.abnormalLogisticsCount)} 次</span></td><td class="reason-cell">${escapeHtml(x.recommendation)}</td></tr>`)):'<div class="empty">暂无供应商风险数据。</div>'}</section>`;
}
async function orderAlerts() {
  const data=await api('/intelligence/order-anomalies');const s=data.summary;
  return `<div class="alert-header"><div><span class="decision-kicker">ORDER CONTROL TOWER · 实时规则巡检</span><h2>订单异常预警中心</h2><p>系统主动寻找需要人介入的订单，只展示结论、影响与下一步动作；完整信号保留在详情中。</p></div><div class="alert-stats"><article class="critical"><strong>${num(s.highCount)}</strong><span>高优先级</span></article><article><strong>${num(s.mediumCount)}</strong><span>待跟进</span></article><article><strong>${num(s.activeOrderCount)}</strong><span>执行中订单</span></article></div></div>
  <div class="alert-list section-gap">${data.items.map(x=>`<article class="alert-card ${x.severity.toLowerCase()}"><div class="alert-stripe"></div><div class="alert-icon">${x.severity==='HIGH'?'!':'·'}</div><div class="alert-main"><div class="alert-title">${riskTag(x.severity)}<strong>${escapeHtml(x.title)}</strong><span class="mono">${escapeHtml(x.orderNumber)}</span></div><p>${escapeHtml(x.description)}</p><div class="alert-context"><span>物料 <b>${escapeHtml(x.partName)}</b></span><span>金额 <b>${money(x.totalAmount)}</b></span><span>预计交付 <b>${escapeHtml(x.expectedDeliveryDate||'未设置')}</b></span>${x.signalCount>1?`<span>关联信号 <b>${x.signalCount} 条</b></span>`:''}</div><div class="alert-action"><span>建议动作</span><p>${escapeHtml(x.suggestedAction)}</p></div></div><button class="secondary" data-order="${x.orderId}">查看订单 →</button></article>`).join('')||'<section class="panel"><div class="healthy-state"><span>✓</span><h3>当前没有订单异常</h3><p>所有执行中订单均在预期流程与交付窗口内。</p></div></section>'}</div>`;
}
async function reconciliation() {
  const data=await api('/control-tower/reconciliation');const s=data.summary;
  return `<div class="control-hero reconcile-hero"><div><span class="decision-kicker">THREE-WAY MATCH · 采购财务协同</span><h2>采购、收货、发票，三份单据一个答案。</h2><p>系统以采购订单为基准，核验实收数量、收货金额、开票数量和发票金额。金额容差为 ${money(s.amountTolerance)}，存在差异时自动冻结付款建议。</p></div><div class="control-metrics"><article><strong>${s.matchRate}%</strong><span>自动匹配率</span></article><article><strong>${num(s.discrepancyCount)}</strong><span>差异单据</span></article><article><strong>${num(s.waitingCount)}</strong><span>待补单据</span></article><article><strong>${money(s.exceptionAmount)}</strong><span>差异金额</span></article></div></div>
  <div class="reconcile-flow"><span><i>01</i>采购订单<small>PO · 数量 / 金额</small></span><b>→</b><span><i>02</i>收货单<small>GR · 实收 / 验收</small></span><b>→</b><span><i>03</i>供应商发票<small>INV · 开票 / 税率</small></span><b>→</b><span class="result"><i>04</i>付款判断<small>匹配后进入审批</small></span></div>
  <section class="panel section-gap"><div class="panel-head"><div><h2>三单匹配队列</h2><small>优先展示差异与待补单据 · 支持收货、发票补录后实时重算</small></div><span class="pill">金额容差 ${money(s.amountTolerance)}</span></div>${table(['匹配状态 / 采购单','采购订单','收货单','供应商发票','差异说明','下一步'],data.items.map(x=>`<tr><td>${matchTag(x.matchStatus)}<strong class="reconcile-order">${escapeHtml(x.orderNumber)}</strong><span class="model">${escapeHtml(x.supplierName||'未关联供应商')}</span></td><td><strong>${num(x.orderQuantity)} 片</strong><span class="model">${money(x.orderAmount)}</span></td><td>${x.receiptId?`<strong>${num(x.receivedQuantity)} 片</strong><span class="model">${escapeHtml(x.receiptNumber)} · ${money(x.receivedAmount)}</span>`:'<span class="document-missing">尚未收货</span>'}</td><td>${x.invoiceId?`<strong>${num(x.invoiceQuantity)} 片</strong><span class="model">${escapeHtml(x.invoiceNumber)} · ${money(x.invoiceAmount)}</span>`:'<span class="document-missing">尚未开票</span>'}</td><td class="reason-cell">${x.differences.length?x.differences.map(d=>`<span class="difference-line">${escapeHtml(d)}</span>`).join(''):'<span class="matched-copy">数量与金额一致</span>'}${Number(x.amountDifference)!==0?`<span class="model">发票差额 ${money(x.amountDifference)}</span>`:''}</td><td><p class="next-action">${escapeHtml(x.nextAction)}</p>${x.matchStatus==='WAITING'&&hasRole('admin','purchase','warehouse')?`<button class="text-button" data-reconcile="${x.orderId}" data-reconcile-mode="${x.receiptId?'invoice':'receipt'}" data-order-quantity="${x.orderQuantity}" data-order-amount="${x.orderAmount}">${x.receiptId?'录入发票':'确认收货'} →</button>`:''}</td></tr>`))}</section>`;
}
async function eventTraces() {
  const data=await api('/control-tower/traces');const s=data.summary;const selected=data.traces[0];
  const sourceNames={'procurement-service':'采购服务','workflow-service':'流程引擎','warehouse-service':'仓储服务','inventory-service':'库存服务','finance-service':'财务服务'};
  const timeline=selected?data.events.filter(e=>e.traceId===selected.traceId).sort((a,b)=>String(a.occurredAt).localeCompare(String(b.occurredAt))):[];
  return `<div class="control-hero trace-hero"><div><span class="decision-kicker">EVENT-DRIVEN CONTROL PLANE · 事件驱动架构</span><h2>从一次操作，追踪到每个业务节点。</h2><p>订单、流程、仓储、库存与财务统一发布领域事件；同一 Trace ID 串联跨模块动作，既能排查问题，也能还原业务事实。</p></div><div class="control-metrics"><article><strong>${num(s.traceCount)}</strong><span>业务链路</span></article><article><strong>${num(s.eventCount)}</strong><span>领域事件</span></article><article><strong>${num(s.serviceCount)}</strong><span>事件来源</span></article><article><strong>${num(s.failureCount)}</strong><span>失败事件</span></article></div></div>
  <div class="trace-layout section-gap"><section class="panel trace-index"><div class="panel-head"><div><h2>最近链路</h2><small>按最近事件时间排序</small></div></div><div class="trace-index-list">${data.traces.map((x,i)=>`<button class="trace-index-item ${i===0?'active':''}" data-trace="${escapeHtml(x.traceId)}"><span>${escapeHtml(x.orderNumber||'通用业务事件')}</span><small>${escapeHtml(x.traceId)}</small><i>${x.eventCount} 个事件 · ${x.serviceCount} 个模块</i></button>`).join('')||'<div class="empty">暂无业务事件</div>'}</div></section><section class="panel trace-detail"><div class="panel-head"><div><h2>${escapeHtml(selected?.orderNumber||'端到端事件时间线')}</h2><small class="mono">${escapeHtml(selected?.traceId||'暂无 Trace ID')}</small></div><span class="status">${timeline.length} 个节点</span></div><div class="event-timeline">${timeline.map((e,i)=>`<article><div class="event-node"><i>${String(i+1).padStart(2,'0')}</i></div><div class="event-body"><div><strong>${escapeHtml(e.summary)}</strong><span>${escapeHtml(sourceNames[e.source]||e.source)}</span></div><p>${escapeHtml(e.eventType)} · ${escapeHtml(e.actor||'system')}</p><small>${escapeHtml(String(e.occurredAt||'').replace('T',' '))}</small></div></article>`).join('')||'<div class="empty">选择左侧链路查看事件</div>'}</div></section></div>`;
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
  setConnection('connecting');
  try {
    const markup=await ({overview,catalog,inventory,orders,suppliers,replenishment,'supplier-risk':supplierRisk,'order-alerts':orderAlerts,reconciliation,'event-traces':eventTraces,audit}[state.view])();
    if(version!==revision)return;
    $('#view').innerHTML=markup; setConnection('online'); enforceRoleVisibility(); bindView(); syncMotionControls();
  } catch(e) {if(version===revision){error('数据读取失败：'+e.message+'。请确认 Java 服务与 MySQL 已启动，然后点击刷新。'); setConnection('offline'); $('#view').innerHTML='<div class="empty">数据暂不可用</div>';}}
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
async function newOrder(id, suggestedQuantity) {
  state.parts=await allPages('/parts/page');
  if(!state.parts.length){toast('请先新建芯片物料');return;}
  const p=state.parts.find(p=>String(p.id)===String(id))||state.parts[0];
  modal('新建芯片采购单',`<p class="notice">选择物料与数量，提交后生成待审核采购单。</p><form id="order-form"><div class="form-grid section-gap"><label class="full">芯片物料 / 供应商<select name="partId" id="order-part">${state.parts.map(x=>`<option value="${x.id}" ${x.id===p.id?'selected':''}>${escapeHtml(x.name)} · ${escapeHtml(x.model)} · ${escapeHtml(supplier(x.supplierId))}</option>`).join('')}</select></label><label>采购数量（片）<input name="quantity" id="order-qty" type="number" min="${p.minOrderQuantity||1}" step="1" required value="${p.minOrderQuantity||100}"></label><label>单价（元 / 片）<input name="unitPrice" id="order-price" type="number" min="0.01" step="0.01" required value="${p.purchasePrice}"></label><label class="full">预计交货日期<input name="expectedDeliveryDate" id="order-date" type="date" required></label><label class="full">采购说明<textarea name="remark" maxlength="500" placeholder="例如：嵌入式系统课程 / 第三组实验"></textarea></label></div><p id="order-hint" class="muted section-gap"></p><p class="form-error" role="alert"></p><div class="form-actions"><strong id="order-total"></strong><button type="submit" class="primary">提交采购申请 →</button></div></form>`);
  function updatePart(){const item=state.parts.find(x=>String(x.id)===$('#order-part').value);$('#order-qty').min=item.minOrderQuantity||1;$('#order-qty').value=suggestedQuantity&&String(item.id)===String(id)?suggestedQuantity:(item.minOrderQuantity||1);$('#order-price').value=item.purchasePrice;const date=new Date();date.setDate(date.getDate()+(item.leadTimeDays||7));$('#order-date').value=[date.getFullYear(),String(date.getMonth()+1).padStart(2,'0'),String(date.getDate()).padStart(2,'0')].join('-');$('#order-hint').textContent=`MOQ ${item.minOrderQuantity||1} 片 · ${item.packageType||'未设置封装'} · 标准交期 ${item.leadTimeDays||7} 天${suggestedQuantity?' · 已带入智能补货建议':''}`;updateTotal();}
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
function reconciliationForm(button) {
  const id=button.dataset.reconcile;const mode=button.dataset.reconcileMode;const receipt=mode==='receipt';
  const quantity=button.dataset.orderQuantity;const amount=button.dataset.orderAmount;
  modal(receipt?'确认采购收货':'录入供应商发票',`<p class="notice">${receipt?'确认实收数量和收货金额后，系统会重新执行三单匹配。':'录入发票后，系统将采购单、收货单与发票自动核验。'}</p><form id="reconcile-form"><div class="form-grid section-gap">${receipt?`<label>实收数量（片）<input name="quantity" type="number" min="1" required value="${escapeHtml(quantity)}"></label><label>收货金额（元）<input name="amount" type="number" min="0.01" step="0.01" required value="${escapeHtml(amount)}"></label><label>收货人<input name="receiver" required value="仓储专员"></label><label>仓储位置<input name="location" required value="ESD-A / 验收区"></label>`:`<label class="full">发票号码<input name="invoiceNumber" required value="INV-${Date.now()}"></label><label>开票数量（片）<input name="quantity" type="number" min="1" required value="${escapeHtml(quantity)}"></label><label>含税金额（元）<input name="amount" type="number" min="0.01" step="0.01" required value="${escapeHtml(amount)}"></label><label>税率（%）<input name="taxRate" type="number" min="0" step="0.01" value="13.00"></label>`}</div><p class="form-error" role="alert"></p><div class="form-actions"><small class="muted">保存动作将写入业务事件流并生成 Trace ID。</small><button class="primary" type="submit">保存并重新核验</button></div></form>`);
  formTask($('#reconcile-form'),f=>api(`/control-tower/reconciliation/${id}/${mode}`,{method:'POST',body:JSON.stringify(Object.fromEntries(f))}));
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
  document.querySelectorAll('[data-replenish]').forEach(el=>el.onclick=()=>run(()=>newOrder(el.dataset.replenish,Number(el.dataset.quantity))));
  document.querySelectorAll('[data-stock]').forEach(el=>el.onclick=()=>run(()=>stockForm(el.dataset.stock,el.dataset.stockMode)));
  document.querySelectorAll('[data-reconcile]').forEach(el=>el.onclick=()=>reconciliationForm(el));
  document.querySelectorAll('[data-trace]').forEach(el=>el.onclick=async()=>{const events=await api('/control-tower/traces/'+encodeURIComponent(el.dataset.trace));document.querySelectorAll('[data-trace]').forEach(x=>x.classList.toggle('active',x===el));const detail=$('.trace-detail');if(!detail)return;const sourceNames={'procurement-service':'采购服务','workflow-service':'流程引擎','warehouse-service':'仓储服务','inventory-service':'库存服务','finance-service':'财务服务'};detail.innerHTML=`<div class="panel-head"><div><h2>${escapeHtml(events[0]?.orderNumber||'端到端事件时间线')}</h2><small class="mono">${escapeHtml(el.dataset.trace)}</small></div><span class="status">${events.length} 个节点</span></div><div class="event-timeline">${events.map((e,i)=>`<article><div class="event-node"><i>${String(i+1).padStart(2,'0')}</i></div><div class="event-body"><div><strong>${escapeHtml(e.summary)}</strong><span>${escapeHtml(sourceNames[e.source]||e.source)}</span></div><p>${escapeHtml(e.eventType)} · ${escapeHtml(e.actor||'system')}</p><small>${escapeHtml(String(e.occurredAt||'').replace('T',' '))}</small></div></article>`).join('')}</div>`;});
  if($('#status-filter'))$('#status-filter').onchange=e=>{state.status=e.target.value;state.page=1;render();};
  if($('#risk-supplier-select'))$('#risk-supplier-select').onchange=e=>{state.selectedRiskSupplier=e.target.value;render();};
  document.querySelectorAll('[data-risk-supplier]').forEach(el=>el.onclick=()=>{state.selectedRiskSupplier=el.dataset.riskSupplier;render();window.scrollTo({top:0,behavior:'smooth'});});
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
  state.user=null;$('#login-screen').hidden=false;$('#logout').hidden=true;closeAccountMenu();$('#account-toggle').disabled=true;$('#top-user').textContent='未登录';$('#top-role').textContent='工作账号';$('#top-avatar').textContent='—';$('#account-username').textContent='—';initLoginAnimation();
  if(loginAnimation&&!window.matchMedia('(prefers-reduced-motion: reduce)').matches)loginAnimation.play();
  const remembered=localStorage.getItem(rememberedAccountKey);
  if(remembered&&!$('#login-form').elements.username.value){$('#login-form').elements.username.value=remembered;$('#remember-account').checked=true;}
}
function applyIdentity(){
  const u=state.user;
  const name=u.realName||u.username;
  const role=roleNames[u.role]||u.role;
  $('#login-screen').hidden=true;if(loginAnimation)loginAnimation.pause();
  $('#logout').hidden=false;$('#account-toggle').disabled=false;
  $('#side-user').textContent=name;$('#side-role').textContent=role;
  $('#side-avatar').textContent=name.slice(0,1);$('#top-avatar').textContent=name.slice(0,1);
  $('#top-user').textContent=name;$('#top-role').textContent=role;
  $('#account-username').textContent=u.username;
  $('#account-toggle').setAttribute('aria-label',`${name}，${role}，账号菜单`);
  document.querySelectorAll('[data-roles]').forEach(el=>el.hidden=!el.dataset.roles.split(',').includes(u.role));
  $('#new-order').hidden=!hasRole('admin','purchase','agent');
}
async function initialize() {
  if(!localStorage.getItem(tokenKey)){showLogin();return;}
  setConnection('connecting');
  try {state.user=await api('/auth/me');applyIdentity();const [info,suppliers]=await Promise.all([api('/system/info'),allPages('/suppliers/page')]);state.suppliers=suppliers;state.categories=info.categories;navigate();}catch(e){if(localStorage.getItem(tokenKey)){error('无法连接芯片采购服务：'+e.message);setConnection('offline');$('#view').innerHTML='<div class="empty">请启动 Java 服务后刷新页面。</div>';}}
}
const today=new Date();
const calendarDate=[today.getFullYear(),String(today.getMonth()+1).padStart(2,'0'),String(today.getDate()).padStart(2,'0')];
$('#today').dateTime=calendarDate.join('-');
$('#today span').textContent=calendarDate.join('.');
$('#today').title=today.toLocaleDateString('zh-CN',{year:'numeric',month:'long',day:'numeric',weekday:'long'});
$('#refresh').onclick=initialize;$('#new-order').onclick=()=>run(()=>newOrder());$('#close-modal').onclick=()=>$('#modal').close();
$('#account-toggle').onclick=()=>{const opening=$('#account-menu').hidden;$('#account-menu').hidden=!opening;$('#account-toggle').setAttribute('aria-expanded',String(opening));};
document.addEventListener('click',event=>{if(!$('.account-control').contains(event.target))closeAccountMenu();});
document.addEventListener('keydown',event=>{if(event.key==='Escape'&&!$('#account-menu').hidden)closeAccountMenu(true);});
document.addEventListener('focusin',event=>{if(!$('.account-control').contains(event.target))closeAccountMenu();});
$('#logout').onclick=()=>{localStorage.removeItem(tokenKey);location.hash='overview';showLogin();};
$('#toggle-password').onclick=()=>{const input=$('#login-password');const reveal=input.type==='password';input.type=reveal?'text':'password';$('#toggle-password').setAttribute('aria-pressed',String(reveal));$('#toggle-password').setAttribute('aria-label',reveal?'隐藏密码':'显示密码');};
$('#login-form').onsubmit=async event=>{event.preventDefault();const form=event.currentTarget;const button=form.querySelector('[type=submit]');const buttonText=button.querySelector('span');button.disabled=true;buttonText.textContent='正在验证身份…';$('#login-error').textContent='';try{const data=Object.fromEntries(new FormData(form));const response=await fetch('/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)});const result=await response.json();if(!response.ok||result.code!==200)throw new Error(result.message||'登录失败');if($('#remember-account').checked)localStorage.setItem(rememberedAccountKey,data.username);else localStorage.removeItem(rememberedAccountKey);localStorage.setItem(tokenKey,result.data.token);form.elements.password.value='';await initialize();}catch(e){$('#login-error').textContent=e.message;}finally{button.disabled=false;buttonText.textContent='进入工作空间';}};
const motionPreference=window.matchMedia('(prefers-reduced-motion: reduce)');
let motionPaused=false;
try {motionPaused=localStorage.getItem('chipsupply_motion_paused')==='true';} catch { /* 动效无需依赖浏览器存储。 */ }
function syncMotionControls() {
  const paused=motionPaused||motionPreference.matches;
  document.body.classList.toggle('motion-paused',paused);
  document.body.classList.toggle('motion-suspended',document.hidden||!$('#login-screen').hidden);
  document.querySelectorAll('.motion-toggle').forEach(button=>{
    const label=motionPreference.matches?'已遵循系统设置，减少动态效果':paused?'播放页面动效':'暂停页面动效';
    button.setAttribute('aria-pressed',String(paused));
    button.setAttribute('aria-label',label);button.title=label;
    button.disabled=motionPreference.matches;
    const caption=button.querySelector('span');
    if(caption)caption.textContent=paused?'动效已暂停':'暂停动效';
  });
}
document.addEventListener('click',event=>{
  if(!event.target.closest('.motion-toggle')||motionPreference.matches)return;
  motionPaused=!motionPaused;
  try {localStorage.setItem('chipsupply_motion_paused',String(motionPaused));} catch { /* 存储不可用不影响暂停。 */ }
  syncMotionControls();
});
motionPreference.addEventListener('change',syncMotionControls);
document.addEventListener('visibilitychange',syncMotionControls);
new MutationObserver(syncMotionControls).observe($('#login-screen'),{attributes:true,attributeFilter:['hidden']});
syncMotionControls();
window.addEventListener('hashchange',()=>{if(state.categories)navigate();});
initialize();
