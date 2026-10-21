(() => {
  const normalize = value => String(value || "")
    .replace(/[\u200c\u200e\u200f]/g, " ")
    .replace(/[يى]/g, "ی")
    .replace(/ك/g, "ک")
    .replace(/\s+/g, " ")
    .trim();

  function install(doc) {
    if (!doc || !doc.defaultView) return;
    const win = doc.defaultView;
    if (win.__payeshNoticeGuardInstalled) return;

    doc.addEventListener("click", event => {
      const target = event.target;
      if (!target || !target.closest) return;
      const control = target.closest('button,a,[role="button"],input[type="button"],input[type="submit"]');
      if (!control) return;
      const label = normalize(control.innerText || control.textContent || control.value || "");
      if (!/^مشاهده(?:\s+ابلاغیه)?$/.test(label)) return;

      let parent = control;
      let noticeCard = false;
      for (let depth = 0; depth < 8 && parent && parent !== doc.body; depth++, parent = parent.parentElement) {
        const classes = typeof parent.className === "string" ? parent.className : "";
        const markedCard = /card-table-row|notice[-_ ]?card|enotice/i.test(classes) ||
          parent.hasAttribute("data-notice-id") || parent.hasAttribute("data-notice-number");
        const content = normalize(parent.innerText || parent.textContent || "");
        if (markedCard && /شماره\s*ابلاغ/.test(content)) {
          noticeCard = true;
          break;
        }
        if (/شماره\s*ابلاغیه/.test(content) && /\b\d{16,20}\b/.test(content) &&
            parent.querySelector && parent.querySelector("button,a,[role=button]")) {
          noticeCard = true;
          break;
        }
      }
      if (!noticeCard && !/ابلاغیه/.test(label)) return;

      const allowed = win.confirm(
        "PAYESH_CONFIRM_NOTICE:بازکردن این ابلاغیه در سامانه رسمی ممکن است وضعیت آن را به «مشاهده‌شده» تغییر دهد. فقط در صورت آمادگی برای این تغییر ادامه بدهید."
      );
      if (!allowed) {
        event.preventDefault();
        event.stopPropagation();
        event.stopImmediatePropagation();
      }
    }, true);

    const installFrames = root => {
      const frames = [];
      if(root && root.matches && root.matches("iframe,frame")) frames.push(root);
      if(root && root.querySelectorAll) frames.push(...root.querySelectorAll("iframe,frame"));
      for (const frame of frames) {
        try { if (frame.contentDocument) install(frame.contentDocument); } catch (_) {}
      }
    };
    installFrames(doc);
    if(win.MutationObserver && doc.documentElement) {
      const observer = new win.MutationObserver(mutations => {
        for(const mutation of mutations) for(const node of mutation.addedNodes) installFrames(node);
      });
      observer.observe(doc.documentElement,{childList:true,subtree:true});
    }

    if (doc === win.top?.document) {
      let viewport = doc.querySelector('meta[name="viewport"]');
      if (!viewport) {
        viewport = doc.createElement("meta");
        viewport.name = "viewport";
        doc.head.appendChild(viewport);
      }
      viewport.content = "width=device-width, initial-scale=1, maximum-scale=3, user-scalable=yes";
    }
    win.__payeshNoticeGuardInstalled = true;
  }

  install(document);
})();
