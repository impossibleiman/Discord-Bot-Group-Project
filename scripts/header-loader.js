(function () {
  window.handleLoginClick = async function (event) {
    event.preventDefault();

    const sessionToken = localStorage.getItem('admin_session');

    if (sessionToken) {
      try {
        const response = await fetch(`https://api.mmuminecraftsociety.co.uk/check-session?session=${sessionToken}`);

        if (response.ok) {
          window.location.href = '/dashboard';
          return;
        }

        localStorage.removeItem('admin_session');
      } catch (error) {
        console.error('Failed to check session status.');
      }
    }

    window.location.href = 'https://api.mmuminecraftsociety.co.uk/login';
  };

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
        console.error(error);
      });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', mountHeader);
  } else {
    mountHeader();
  }
})();
