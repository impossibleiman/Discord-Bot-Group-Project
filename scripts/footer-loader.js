(function () {
  function mountFooter() {
    var target = document.getElementById('site-footer');
    if (!target) {
      return;
    }

    fetch('footer.html', { cache: 'no-store' })
      .then(function (response) {
        if (!response.ok) {
          throw new Error('Failed to load shared footer');
        }
        return response.text();
      })
      .then(function (html) {
        target.innerHTML = html;
      })
      .catch(function (error) {
        console.error(error);
      });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', mountFooter);
  } else {
    mountFooter();
  }
})();
