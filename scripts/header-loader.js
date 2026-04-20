(function () {
  var FALLBACK_HEADER_HTML = '<header>' +
    '<a href="index.html" class="logo">' +
      '<div class="logo-icon"><span></span><span></span><span></span><span></span></div>' +
      'Minecraft Society' +
    '</a>' +
    '<nav>' +
      '<a href="index.html" data-nav="docs">Docs</a>' +
      '<a href="events.html" data-nav="events">Events</a>' +
      '<a href="members.html" data-nav="members">Members</a>' +
      '<a href="https://api.mmuminecraftsociety.co.uk/login" style="color: var(--green);">Dashboard Login</a>' +
      '<a href="https://discord.gg/x2tMfSAnut" target="_blank" class="btn-invite">↗ Join Server</a>' +
    '</nav>' +
  '</header>';

  function applyHeaderBehavior(target) {
    var page = document.body.getAttribute('data-page');

    var activeLink = target.querySelector('[data-nav="' + page + '"]');
    if (activeLink) {
      activeLink.classList.add('active');
    }

    if (page === 'docs') {
      var header = target.querySelector('header');
      if (header && !header.querySelector('#hamburger')) {
        var hamburger = document.createElement('button');
        hamburger.className = 'hamburger';
        hamburger.id = 'hamburger';
        hamburger.setAttribute('aria-label', 'Open menu');
        hamburger.innerHTML = '<span></span><span></span><span></span>';
        header.appendChild(hamburger);
      }
    }

    document.dispatchEvent(new Event('site-header:ready'));
  }

  function mountHeader() {
    var target = document.getElementById('site-header');
    if (!target) {
      return;
    }

    if (window.location.protocol === 'file:') {
      target.innerHTML = FALLBACK_HEADER_HTML;
      applyHeaderBehavior(target);
      return;
    }

    fetch('header.html', { cache: 'no-store' })
      .then(function (response) {
        if (!response.ok) {
          throw new Error('Failed to load shared header');
        }
        return response.text();
      })
      .then(function (html) {
        target.innerHTML = html;
        applyHeaderBehavior(target);
      })
      .catch(function (error) {
        target.innerHTML = FALLBACK_HEADER_HTML;
        applyHeaderBehavior(target);
        console.error(error);
      });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', mountHeader);
  } else {
    mountHeader();
  }
})();
