
(function(){
  const src = document.querySelector('.brand .logo-chip svg');
  if(src){
    const clone = src.cloneNode(true);
    // У копии снимаем id: иначе на странице два элемента с одним
    // идентификатором, и document.getElementById находит не тот.
    clone.removeAttribute('id');
    clone.querySelectorAll('[id]').forEach(el => el.removeAttribute('id'));
    clone.querySelectorAll('[style]').forEach(el=>{
      el.setAttribute('style', el.getAttribute('style').replace(/fill:#FFFFFF/gi, 'fill:#1E2856'));
    });
    document.getElementById('loginLogo').appendChild(clone);
  }
})();
