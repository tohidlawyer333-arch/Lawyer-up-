const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { JSDOM } = require('jsdom');
const asset = name => fs.readFileSync(path.join(__dirname,'../../app/src/main/assets/sana',name),'utf8');
const snapshot = asset('snapshot.js');
const next = asset('next.js');
const card = (ordinal,number) => `<div class="card-table-row"><p>${ordinal}</p><p>شماره ابلاغیه: <span>${number}</span></p><p>تاریخ صدور: 1405/07/19</p><p>صادر کننده: شعبه نمونه</p><p>موضوع ابلاغیه: موضوع نمونه</p><button>مشاهده</button></div>`;
function document(html) {
  const dom = new JSDOM(html,{url:'https://eblagh1.adliran.ir/ENotice/Index',runScripts:'outside-only'});
  Object.defineProperty(dom.window.document,'readyState',{value:'complete'});
  dom.window.HTMLElement.prototype.getClientRects = function() { return this.closest('[hidden]') ? [] : [{width:100,height:20}]; };
  return dom;
}
function read(dom) {return JSON.parse(dom.window.eval(snapshot));}

test('real notice cards read only labeled fields, never ordinal or view buttons',()=>{
  const dom=document(`<h6>ابلاغیه‌های مشاهده شده</h6>${card(26,'200000000000000001')}<p>27 28 29</p>`);
  const result=read(dom);
  assert.equal(result.rows.length,1);assert.equal(result.rows[0].cells[0],'200000000000000001');
  assert.ok(!result.rows[0].cells.includes('26'));assert.equal(result.pagination.state,'end');dom.window.close();
});
test('table headings define identities independently of column order',()=>{
  const dom=document('<table><thead><tr><th>شماره ابلاغیه</th><th>شماره پرونده</th></tr></thead><tbody><tr><td>200000000000000001</td><td>1000000000000001</td></tr></tbody></table>');
  assert.deepEqual(read(dom).rows[0].headers,['شماره ابلاغیه','شماره پرونده']);dom.window.close();
});
test('aria grid and responsive data labels',()=>{
  const dom=document('<div role="grid"><div role="row"><div role="gridcell" data-label="شماره پرونده">۱۰۰۰۰۰۰۰۰۰۰۰۰۰۰۱</div></div></div>');
  assert.equal(read(dom).rows[0].headers[0],'شماره پرونده');dom.window.close();
});
test('labeled detail tables are one record, not unrelated fragments',()=>{
  const dom=document('<table><tr><td>شماره پرونده:</td><td>1000000000000001</td></tr><tr><td>شعبه:</td><td>نمونه</td></tr></table>');
  assert.deepEqual(read(dom).rows[0].headers,['شماره پرونده','شعبه']);dom.window.close();
});
test('hidden old cards and login fields do not pollute the current page',()=>{
  const dom=document(`<div hidden><input type=password>${card(1,'200000000000000004')}</div>${card(2,'200000000000000001')}`);
  const result=read(dom);assert.equal(result.rows.length,1);assert.equal(result.login,false);dom.window.close();
});
test('visible password means login, and values are never extracted',()=>{
  const dom=document('<input type=password value=secret>');const result=read(dom);assert.equal(result.login,true);assert.ok(!JSON.stringify(result).includes('secret'));dom.window.close();
});
test('angular end page uses visible page-size selector rather than hidden stale select',()=>{
  const dom=document(`<select aria-label="Default select example" hidden><option>?</option></select><select aria-label="Default select example"><option>3</option></select>${card(4,'200000000000000001')}<nav aria-label="Page navigation example"><ul class=pagination><li id=page-item-1>1</li><li id=page-item-2>2</li><li class=page-item ng-click="next()"><span>بعدی</span></li></ul></nav>`);
  assert.equal(read(dom).pagination.state,'end');dom.window.close();
});
test('next page click only hits the unique paginator, never view or submit',()=>{
  const dom=document(`${card(1,'200000000000000001')}<button id=submit>ثبت نهایی</button><nav aria-label="Page navigation example"><ul class=pagination><li class=page-item ng-click="next()"><span>بعدی</span></li></ul></nav>`);
  let clicked=0;let dangerous=0;
  dom.window.document.querySelector('li').addEventListener('click',()=>clicked++);
  dom.window.document.querySelectorAll('button').forEach(node=>node.addEventListener('click',()=>dangerous++));
  assert.equal(dom.window.eval(next),true);assert.equal(clicked,1);assert.equal(dangerous,0);dom.window.close();
});
test('ambiguous pagers stop instead of guessing',()=>{
  const dom=document('<div class=pagination><button>بعدی</button><button>بعدی</button></div>');assert.equal(read(dom).pagination.state,'unknown');assert.equal(dom.window.eval(next),false);dom.window.close();
});
test('disabled next is the end and cannot be clicked',()=>{
  const dom=document('<div class=pagination><button disabled>بعدی</button></div>');assert.equal(read(dom).pagination.state,'end');assert.equal(dom.window.eval(next),false);dom.window.close();
});
test('unknown layouts do not become empty-success',()=>{
  const dom=document('<div class=card>26 مشاهده شماره پرونده</div>');assert.equal(read(dom).rows.length,0);assert.equal(read(dom).pagination.state,'unknown');dom.window.close();
});
test('loading screen is not final data',()=>{
  const dom=document('<p>در حال بارگزاری ابلاغیه</p>');assert.equal(read(dom).loading,true);dom.window.close();
});
