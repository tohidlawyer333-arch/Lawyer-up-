(() => {
  const visible = node => !!node.getClientRects().length;
  const found = new Set();
  const controls = [];
  function inspect(doc) {
    for(const area of doc.querySelectorAll('.pagination,.k-pager-wrap,.k-pager,.p-paginator,.mat-paginator,nav[aria-label]')) {
      if(!visible(area)) continue;
      for(const node of area.querySelectorAll('button,a,[role=button],li.page-item[ng-click]')) {
        if(!visible(node) || found.has(node)) continue;
        found.add(node);
        const label = [node.getAttribute('aria-label'),node.getAttribute('title'),node.textContent].filter(Boolean).join(' ').trim();
        if(/بعدی|صفحه بعد|next/i.test(label) && !/آخر|last/i.test(label) && !node.disabled && node.getAttribute('aria-disabled') !== 'true' && !node.closest('.disabled,.k-disabled,.p-disabled')) controls.push(node);
      }
    }
    for(const frame of doc.querySelectorAll('iframe,frame')) {try {if(visible(frame) && frame.contentDocument) inspect(frame.contentDocument);} catch(_) {}}
  }
  inspect(document);
  if(controls.length !== 1) return false;
  controls[0].click();
  return true;
})()
