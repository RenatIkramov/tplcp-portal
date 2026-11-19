/*
  Личный кабинет (11.11.2026). Прежде — демо прототипа (js/demo/profile.js):
  ниже шапки реквизиты и менеджер с прочерками, адрес склада в коде,
  пустые списки и кнопка-заглушка «Создать пользователя».

  Шапка — из входа (auth.js, PORTAL.uiUser); остальное — GET /api/me/profile
  (ProfileService):
    * поклажедатель — «Организация» (реквизиты выбранного клиента),
      «Ваш менеджер» (менеджеры с доступом к клиенту), «Доступные склады»;
    * остальные роли — «Зона доступа»: поклажедатели и склады, списком
      или «все», как задано в карточке пользователя (V110).
  Пользователей своей организации кабинет не показывает (решение заказчика).

  Кабинет рисуют вход (auth.js) и смена языка (i18n.js) — по имени renderProfile.
*/
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  // общие помощники — core.js (аудит A4)
  const {esc, dash} = window.PORTAL;
  const ruDate = iso => {
    if (!iso) return '—';
    const d = String(iso).slice(0, 10).split('-');
    return d.length === 3 ? d[2] + '.' + d[1] + '.' + d[0] : iso;
  };

  /**
   * Ответ сервера для кабинета; null — ещё не пришёл или не дали
   * (например, пока не сменён выданный пароль).
   * @type {any}
   */
  let data = null;
  /** Чей это ответ: сменился вход или клиент — спрашиваем заново. */
  let dataFor = '';

  const kv = (labelKey, value) => '<div class="kv"><label>' + esc(T(labelKey)) + '</label><div>'
    + esc(dash(value)) + '</div></div>';
  const grid = inner => '<div class="detail-head"><div class="kv-grid"'
    + ' style="grid-template-columns:repeat(auto-fit,minmax(220px,1fr));width:100%;">' + inner + '</div></div>';
  const title = key => '<div class="section-title">' + esc(T(key)) + '</div>';
  const muted = text => '<div class="detail-head"><div style="color:var(--muted);font-size:13.5px;">'
    + esc(text) + '</div></div>';

  function head(user){
    const meta = [user.position, user.organization].filter(v => v && v !== '—').join(' · ');
    return '<div class="detail-head"><div>'
      + '<h2>' + esc(user.fullName || user.login) + '</h2>'
      + (meta ? '<div class="meta">' + esc(meta) + '</div>' : '')
      + '<div class="kv-grid">'
      + kv('common.column.email', user.login)
      + kv('partners.label.phone', user.phone)
      + kv('admin.text.portal_role', data && data.roleName)
      + kv('admin.text.last_sign_in', user.lastLogin)
      + '</div></div>'
      + '<div class="actions"><button class="btn"' + PORTAL.call('portalOpenPasswordChange') + '>'
      + esc(T('admin.text.change_password2')) + '</button></div></div>';
  }

  function organization(){
    const o = data.organization;
    if (!o) return '';
    return title('admin.text.organization')
      + grid(kv('portal.text.company_name', o.orgName)
        + kv('partners.label.bin', o.bin)
        + kv('common.text.legal_address', o.legalAddress)
        + kv('partners.label.phone', o.phone)
        + kv('common.column.email', o.email)
        + kv('portal.text.contract_number', o.contractNumber)
        + '<div class="kv"><label>' + esc(T('portal.label.contract_valid_until')) + '</label><div>'
        + esc(ruDate(o.contractValidUntil)) + '</div></div>');
  }

  function managers(){
    const list = data.managers || [];
    return title('profile.title.your_manager')
      + (list.length
        ? list.map(m => grid(kv('admin.text.full_name', m.fullName)
            + kv('admin.text.position', m.jobTitle)
            + kv('partners.label.phone', m.phone)
            + kv('common.column.email', m.email))).join('')
        : muted(T('profile.text.no_manager')));
  }

  function warehouseList(){
    const list = data.warehouses || [];
    return list.length
      ? grid(list.map(w => '<div class="kv"><label>' + esc(w.name) + '</label><div>'
          + esc(dash(w.address)) + '</div></div>').join(''))
      : muted(T('admin.text.no_warehouses_assigned'));
  }

  /** Менеджер, сотрудник склада, администратор: кому и где он может работать. */
  function zone(){
    const deps = data.allDepositors ? T('admin.text.all_depositors')
      : ((data.depositors || []).map(d => d.orgName).join(', ') || T('admin.text.no_depositors_assigned'));
    return title('profile.title.access_zone')
      + grid('<div class="kv" style="grid-column:1/-1;"><label>' + esc(T('admin.text.accessible_depositors'))
        + '</label><div>' + esc(deps) + '</div></div>'
        + (data.allWarehouses
          ? '<div class="kv"><label>' + esc(T('admin.title.available_warehouses')) + '</label><div>'
            + esc(T('admin.text.all_warehouses')) + '</div></div>'
          : ''))
      + (data.allWarehouses ? '' : title('admin.title.available_warehouses') + warehouseList());
  }

  function draw(user){
    const el = document.getElementById('profileContent');
    if (!el || !user) return;
    let html = head(user);
    if (data){
      html += user.role === 'depositor'
        ? organization() + managers() + title('admin.title.available_warehouses') + warehouseList()
        : zone();
    }
    el.innerHTML = html;
  }

  async function load(user){
    const key = user.login + '|' + ((PORTAL.session && PORTAL.session.depositorId) || '');
    if (key === dataFor && data) return;
    try {
      data = await PORTAL.fetchJson('/api/me/profile');
      dataFor = key;
    } catch (e){
      // Например, пока не сменён выданный пароль: шапку видно и без этого.
      data = null;
      dataFor = '';
    }
  }

  /** Открыть кабинет: читаем при каждом открытии — клиента могли сменить. */
  window.openProfile = function(){
    window.showView('profile');
    window.renderProfile(PORTAL.uiUser);
  };

  /**
   * Нарисовать кабинет: шапку сразу, остальное — когда ответит сервер.
   * @param {PortalUiUser | null | undefined} user
   */
  window.renderProfile = function(user){
    if (!user) return;
    draw(user);
    load(user).then(() => draw(user)).catch(() => {});
  };

  // Сменили клиента — реквизиты и менеджеры другие: при следующем
  // открытии кабинета спросим заново.
  PORTAL.onLogin(() => { dataFor = ''; });
})();
