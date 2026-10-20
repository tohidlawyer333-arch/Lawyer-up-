(() => {
  const visible = node => !!node.getClientRects().length && node.ownerDocument.defaultView.getComputedStyle(node).visibility !== 'hidden';
  const text = node => (node.innerText || node.textContent || '').replace(/\s+/g, ' ').trim();
  const normalize = value => value.replace(/[۰-۹]/g, digit => '۰۱۲۳۴۵۶۷۸۹'.indexOf(digit)).replace(/[٠-٩]/g, digit => '٠١٢٣٤٥٦٧٨٩'.indexOf(digit)).replace(/ي/g,'ی').replace(/ك/g,'ک');
  const result = {url: location.href, rows: [], login: false, loading: document.readyState !== 'complete', frames: 0, blockedFrames: 0, truncated: false, adapter: 'structured-v2', pagination: {state: 'unknown'}};
  const add = row => { if(result.rows.length < 1000) result.rows.push(row); else result.truncated = true; };
  const seen = new Set();
  const controls = [];
  let noticeHeading = "";
  let dateFilterPresent = false;
  function inspect(doc) {
    const visibleDateFilter = [...doc.querySelectorAll('input')].some(input => {
      if(!visible(input)) return false;
      const metadata = [
        input.getAttribute('aria-label'), input.getAttribute('placeholder'), input.getAttribute('title'),
        input.getAttribute('name'), input.id, ...[...(input.labels || [])].map(text), text(input.parentElement)
      ].filter(Boolean).join(' ');
      const value = normalize(input.value || input.getAttribute('value') || '').trim();
      const dateValue = /^(?:13|14|20)\d{2}[\/.-]\d{1,2}[\/.-]\d{1,2}$/.test(value);
      return /از\s*تاریخ|تا\s*تاریخ|تاریخ|date/i.test(metadata) || dateValue;
    });
    if(visibleDateFilter) dateFilterPresent = true;
    const candidateHeading = [...doc.querySelectorAll("h1,h2,h3,h4,h5,h6,[role=heading],.nav-link.active,.active")].filter(visible).map(text).find(value =>
      /ابلاغیه/.test(value) && /(مشاهده[\s\u200c]*شده|مشاهده[\s\u200c]*نشده|باز[\s\u200c]*نشده|جدید)/.test(value));
    if(candidateHeading) noticeHeading = candidateHeading;
    if([...doc.querySelectorAll('input[type=password]')].some(visible)) result.login = true;
    if([...doc.querySelectorAll('[aria-busy=true],.k-loading-mask')].some(visible)) result.loading = true;
    if([...doc.querySelectorAll('p')].some(node => visible(node) && /در حال بارگ[ذز]اری ابلاغیه/.test(text(node)))) result.loading = true;
    for(const card of doc.querySelectorAll('.card-table-row')) {
      if(!visible(card)) continue;
      const pairs = [...card.querySelectorAll('p')].map(text).filter(value => /^(شماره ابلاغیه|شماره پرونده|ردیف فرعی|شماره بایگانی|تاریخ صدور|تاریخ ابلاغ|تاریخ ثبت|صادر کننده|صادرکننده|موضوع ابلاغیه|موضوع پرونده|شعبه|مرجع قضایی|کلاسه پرونده)\s*[:：]/.test(value))
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
  // The active view title is source metadata only, not the lawyer's local review status.
  if(noticeHeading) {
    for(const row of result.rows) {
      if(row.headers && row.headers.some(header=>/شماره\s*ابلاغ/.test(header)) && !row.headers.includes("فهرست مبدأ ثنا")) {
        row.headers.push("فهرست مبدأ ثنا");
        row.cells.push(noticeHeading);
      }
    }
  }
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
  const noticeList = /ابلاغیه/.test(noticeHeading) && /(مشاهده[\s\u200c]*شده|مشاهده[\s\u200c]*نشده|باز[\s\u200c]*نشده|جدید)/.test(noticeHeading);
  if(noticeList && result.adapter === 'enotice-cards-v2') {
    const visiblePager = [...document.querySelectorAll('nav[aria-label="Page navigation example"]')].some(visible);
    if(dateFilterPresent) {
      result.pagination.dateFilterPresent = true;
      if(result.pagination.state === 'end') result.pagination.state = 'filtered-end';
      else if(result.pagination.state === 'unknown' || (!controls.length && !visiblePager)) result.pagination.state = 'filtered';
    } else if(!controls.length && !visiblePager) {
      result.pagination.state = 'end';
    }
  }
  return JSON.stringify(result);
})()
