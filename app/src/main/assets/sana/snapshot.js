(() => {
  const visible = node => !!node.getClientRects().length && node.ownerDocument.defaultView.getComputedStyle(node).visibility !== 'hidden';
  const text = node => (node.innerText || node.textContent || '').replace(/\s+/g, ' ').trim();
  const normalize = value => value.replace(/[۰-۹]/g, digit => '۰۱۲۳۴۵۶۷۸۹'.indexOf(digit)).replace(/[٠-٩]/g, digit => '٠١٢٣٤٥٦٧٨٩'.indexOf(digit)).replace(/ي/g,'ی').replace(/ك/g,'ک');
  const result = {url: location.href, rows: [], login: false, loading: document.readyState !== 'complete', frames: 0, blockedFrames: 0, truncated: false, adapter: 'structured-v2', pagination: {state: 'unknown'}};
  const add = row => { if(result.rows.length < 1000) result.rows.push(row); else result.truncated = true; };
  const seen = new Set();
  const controls = [];
  function inspect(doc) {
    if([...doc.querySelectorAll('input[type=password]')].some(visible)) result.login = true;
    if([...doc.querySelectorAll('[aria-busy=true],.k-loading-mask')].some(visible)) result.loading = true;
    if([...doc.querySelectorAll('p')].some(node => visible(node) && /در حال بارگ[ذز]اری ابلاغیه/.test(text(node)))) result.loading = true;
    for(const card of doc.querySelectorAll('.card-table-row')) {
      if(!visible(card)) continue;
      const pairs = [...card.querySelectorAll('p')].map(text).filter(value => /^(شماره ابلاغیه|شماره پرونده|ردیف فرعی|تاریخ صدور|صادر کننده|موضوع ابلاغیه|موضوع پرونده)\s*[:：]/.test(value))
        .map(value => {const split = value.search(/[:：]/);return [value.slice(0,split).trim(),value.slice(split+1).trim()];});
      if(!pairs.some(pair => /شماره (ابلاغیه|پرونده)/.test(pair[0]))) continue;
      add({headers:pairs.map(pair=>pair[0]),cells:pairs.map(pair=>pair[1])});
      result.adapter = 'enotice-cards-v2';
    }
    for(const container of doc.querySelectorAll('table,[role=grid],[role=table],.ag-root')) {
      if(!visible(container)) continue;
      let headers = [...container.querySelectorAll('thead tr:last-child th,thead tr:last-child td,[role=columnheader],.ag-header-cell-text')].map(text);
      if(!headers.length) headers = [...(container.closest('.k-grid')?.querySelectorAll('.k-grid-header th') || [])].map(text);
      const pairs = [...container.querySelectorAll('tr')].filter(row => visible(row) && !row.closest('thead')).map(row => [...row.querySelectorAll(':scope > th,:scope > td')]).filter(nodes => nodes.length === 2 && nodes[1].tagName !== 'TH' && /^(شماره|ردیف فرعی|شعبه|موضوع|وضعیت|نوع اقدام|شرح اقدام|تاریخ)/.test(text(nodes[0])));
      if(pairs.some(nodes => /^شماره\s*(پرونده|ابلاغ)/.test(text(nodes[0])))) {
        add({context:true,headers:pairs.map(nodes=>text(nodes[0]).replace(/[:：]$/,'').trim()),cells:pairs.map(nodes=>text(nodes[1]))});
        continue;
      }
      for(const row of container.querySelectorAll('tr,[role=row],.ag-row')) {
        if(!visible(row) || seen.has(row) || row.closest('thead')) continue;
        seen.add(row);
        const nodes = [...row.querySelectorAll('td,[role=gridcell],[role=cell],.ag-cell')];
        if(!nodes.length) continue;
        const labels = nodes.map(node => node.getAttribute('data-label') || node.getAttribute('data-title') || '');
        add({headers:labels.some(Boolean) ? labels : headers, cells:nodes.map(text)});
      }
    }
    for(const list of doc.querySelectorAll('dl')) {
      if(!visible(list)) continue;
      const terms = [...list.querySelectorAll('dt')].filter(node=>node.nextElementSibling?.tagName === 'DD');
      if(terms.some(node=>/شماره\s*(پرونده|ابلاغ)/.test(text(node)))) add({context:true,headers:terms.map(text),cells:terms.map(node=>text(node.nextElementSibling))});
    }
    const found = new Set();
    for(const area of doc.querySelectorAll('.pagination,.k-pager-wrap,.k-pager,.p-paginator,.mat-paginator,nav[aria-label]')) {
      if(!visible(area)) continue;
      for(const node of area.querySelectorAll('button,a,[role=button],li.page-item[ng-click]')) {
        if(!visible(node) || found.has(node)) continue;
        const label = [node.getAttribute('aria-label'),node.getAttribute('title'),text(node)].filter(Boolean).join(' ');
        if(!/بعدی|صفحه بعد|next/i.test(label) || /آخر|last/i.test(label)) continue;
        found.add(node);
        let disabled = !!node.disabled || node.getAttribute('aria-disabled') === 'true' || !!node.closest('.disabled,.k-disabled,.p-disabled');
        const numbered = [...area.querySelectorAll('li[id^="page-item-"]')].map(item => Number(normalize(text(item))));
        const size = Number([...doc.querySelectorAll('select[aria-label="Default select example"]')].find(visible)?.value);
        const first = [...doc.querySelectorAll('.card-table-row')].find(visible);
        const ordinal = Number(normalize(first?.querySelector('p')?.textContent.trim() || ''));
        if(numbered.length && size > 0 && ordinal > 0 && numbered.every((value,index)=>value === index+1)) disabled ||= Math.ceil(ordinal/size) === numbered.length;
        controls.push({disabled});
      }
    }
    for(const frame of doc.querySelectorAll('iframe,frame')) {
      if(!visible(frame)) continue;
      result.frames++;
      try {if(frame.contentDocument) inspect(frame.contentDocument); else result.blockedFrames++;} catch(_) {result.blockedFrames++;}
    }
  }
  inspect(document);
  const details = result.rows.filter(row => row.context && row.headers.some(header=>/شماره\s*پرونده/.test(header)));
  if(details.length === 1) {
    const context = details[0];
    const index = context.headers.findIndex(header=>/شماره\s*پرونده/.test(header));
    for(const row of result.rows) {
      if(!row.headers.some(header=>/شماره\s*پرونده/.test(header)) && row.headers.some(header=>/شماره\s*ابلاغ/.test(header))) {
        row.headers = [...row.headers,'شماره پرونده']; row.cells = [...row.cells,context.cells[index]];
      }
    }
  }
  result.pagination.state = controls.length === 1 ? (controls[0].disabled ? 'end' : 'next') : 'unknown';
  const noticeList = [...document.querySelectorAll('h6')].some(node=>visible(node) && /ابلاغیه.*(مشاهده شده|جدید)/.test(text(node)));
  if(noticeList && result.adapter === 'enotice-cards-v2' && !controls.length && ![...document.querySelectorAll('nav[aria-label="Page navigation example"]')].some(visible)) result.pagination.state = 'end';
  return JSON.stringify(result);
})()
