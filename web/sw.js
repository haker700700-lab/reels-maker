// خدمة بسيطة: بتخلّي واجهة التطبيق تفتح حتى لو المحرّك مش شغّال (وتقول للمستخدم يشغّله).
const C = 'reels-shell-v1';
self.addEventListener('install', e => {
  e.waitUntil(caches.open(C).then(c => c.addAll(['/', '/manifest.json', '/icon-192.png'])).then(() => self.skipWaiting()));
});
self.addEventListener('activate', e => e.waitUntil(self.clients.claim()));
self.addEventListener('fetch', e => {
  const r = e.request, u = new URL(r.url);
  if (r.method !== 'GET' || u.pathname.startsWith('/api') || u.pathname.startsWith('/out') || r.headers.has('range')) return;
  e.respondWith(
    fetch(r).then(res => {
      if (res.ok) { const cp = res.clone(); caches.open(C).then(c => c.put(r, cp)).catch(() => {}); }
      return res;
    }).catch(() => caches.match(r, {ignoreSearch: true}).then(x => x || caches.match('/')))
  );
});
