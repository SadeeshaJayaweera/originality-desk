/* Pure helpers are kept in this asset so the server needs only index.html, styles.css, and app.js. */
(function (root) {
  'use strict';
  const MAX_BYTES = 10 * 1024 * 1024;
  const status = value => String(value || 'UNKNOWN').toUpperCase();
  const isReady = value => ['READY', 'EXTRACTED', 'COMPLETED'].includes(status(value));
  const isComplete = value => ['COMPLETED', 'SUCCEEDED'].includes(status(value));
  const isFailed = value => ['FAILED', 'CANCELLED', 'CANCELED', 'ERROR'].includes(status(value));
  const isTerminal = value => isComplete(value) || isFailed(value);
  function validateFile(file) {
    if (!file) return 'Choose a PDF, DOCX, or TXT file first.';
    if (!/\.(pdf|docx|txt)$/i.test(file.name)) return 'Only PDF, DOCX, and TXT files are supported.';
    if (!file.size) return 'This file is empty. Choose a file containing text.';
    if (file.size > MAX_BYTES) return 'This file exceeds the 10 MiB (10,485,760 byte) limit.';
    return '';
  }
  function segments(text, matches) {
    // Segment every boundary so overlapping evidence never duplicates original text.
    const spans = matches.map((match, index) => ({ index, start: match.start, end: match.end }))
      .filter(span => Number.isInteger(span.start) && Number.isInteger(span.end) && span.start >= 0 && span.end > span.start && span.end <= text.length);
    const boundaries = [...new Set([0, text.length, ...spans.flatMap(span => [span.start, span.end])])].sort((a, b) => a - b);
    return boundaries.slice(0, -1).map((start, index) => {
      const end = boundaries[index + 1];
      return { text: text.slice(start, end), start, end, matches: spans.filter(span => span.start <= start && span.end >= end).map(span => span.index) };
    });
  }
  function formatBytes(value) {
    if (value == null || !Number.isFinite(Number(value))) return 'Not reported';
    const bytes = Number(value);
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KiB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MiB`;
  }
  function number(value) { return value != null && Number.isFinite(Number(value)) ? new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(Number(value)) : 'Not reported'; }
  function percent(value) { return value != null && Number.isFinite(Number(value)) ? new Intl.NumberFormat(undefined, { style: 'percent', maximumFractionDigits: 1 }).format(Number(value)) : 'Not reported'; }
  function collection(value) { return Array.isArray(value) ? value : (value && Array.isArray(value.items) ? value.items : []); }
  function safeFilename(value, fallback) { return String(value || fallback).replace(/[\x00-\x1f\x7f/\\]/g, '_').slice(0, 180) || fallback; }
  function reportResult(snapshot) { return snapshot.result || (Array.isArray(snapshot.matches) ? snapshot : null); }
  function isAdmin(role) { return ['ADMIN', 'INSTITUTION_ADMIN'].includes(status(role)); }
  function canReview(role) { return isAdmin(role) || status(role) === 'REVIEWER'; }
  root.OriginalityCore = Object.freeze({ MAX_BYTES, status, isReady, isComplete, isFailed, isTerminal, validateFile, segments, formatBytes, number, percent, collection, safeFilename, reportResult, isAdmin, canReview });
})(globalThis);

(function () {
  'use strict';
  const C = globalThis.OriginalityCore;
  const API = '/api/v1';
  const app = document.getElementById('app');
  const landing = app.cloneNode(true);
  const state = { me: null, sessionChecked: false, theme: 'system', route: null, scope: null, xhr: null, upload: null };
  const routes = [
    ['overview', 'Overview', '◫'], ['new', 'New analysis', '+'], ['documents', 'Documents', '▤'],
    ['history', 'History & reports', '◷'], ['compare', 'Compare documents', '⇄'], ['references', 'Reference library', '▥'],
    ['workspace', 'Workspace & profile', '▦'], ['settings', 'Settings', '⚙']
  ];

  // All untrusted content is assigned as text; HTML is never assembled from API data.
  function el(tag, attrs, ...children) {
    const node = document.createElement(tag);
    Object.entries(attrs || {}).forEach(([key, value]) => {
      if (value == null || value === false) return;
      if (key === 'class') node.className = value;
      else if (key === 'text') node.textContent = value;
      else if (key.startsWith('on') && typeof value === 'function') node.addEventListener(key.slice(2).toLowerCase(), value);
      else if (key === 'checked' || key === 'disabled' || key === 'hidden' || key === 'selected') node[key] = Boolean(value);
      else node.setAttribute(key, String(value));
    });
    children.flat(Infinity).forEach(child => { if (child != null && child !== false) node.append(child instanceof Node ? child : document.createTextNode(String(child))); });
    return node;
  }
  function replace(node, ...children) {
    // Native replaceChildren stringifies null/false; optional UI content must be omitted.
    node.replaceChildren(...children.flat(Infinity).filter(child => child != null && child !== false));
  }
  const link = (text, href, cls = 'text-link') => el('a', { href, class: cls }, text);
  const button = (text, handler, cls = 'outline', attrs = {}) => el('button', { type: 'button', class: `button ${cls}`, onclick: handler, ...attrs }, text);
  const text = (value, fallback = 'Not reported') => value == null || value === '' ? fallback : String(value);
  const date = value => { if (!value) return 'Not reported'; const d = new Date(value); return Number.isNaN(d.getTime()) ? text(value) : d.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }); };
  const statusLabel = value => text(value, 'UNKNOWN').replace(/_/g, ' ').toLowerCase();
  const badge = value => el('span', { class: `badge ${C.isFailed(value) ? 'error' : C.isReady(value) || C.isComplete(value) ? 'success' : 'pending'}` }, statusLabel(value));
  function notify(message, error = false) {
    const toast = el('div', { class: `toast${error ? ' error' : ''}` }, el('span', {}, message));
    const close = el('button', { type: 'button', 'aria-label': 'Dismiss notification', onclick: () => toast.remove() }, '×');
    toast.append(close);
    document.getElementById('notifications').append(toast);
    if (!error) setTimeout(() => toast.remove(), 8000);
  }
  function notice(message, type = '', attrs = {}) { return el('div', { class: `notice ${type}`, ...attrs }, message); }
  function loading(message = 'Loading workspace records…') { return el('div', { class: 'loading', role: 'status' }, el('span', { class: 'spinner', 'aria-hidden': 'true' }), message); }
  function empty(title, message, action) { return el('div', { class: 'empty-state' }, el('h3', {}, title), el('p', {}, message), action); }
  function field(label, input, help) {
    const id = input.id || `field-${++field.sequence}`; input.id = id;
    input.removeAttribute('aria-label'); // The visible label is also the accessible name.
    const helpId = `${id}-help`;
    if (help) input.setAttribute('aria-describedby', helpId);
    return el('div', { class: 'field' }, el('label', { for: id }, label), input, help ? el('p', { id: helpId, class: 'field-help' }, help) : null);
  }
  field.sequence = 0;
  function panel(title, ...children) { return el('section', { class: 'panel' }, title ? el('div', { class: 'panel-header' }, el('h2', {}, title)) : null, ...children); }
  function heading(title, description, action, eyebrow = 'Your workspace') {
    return el('div', { class: 'page-heading' }, el('div', {}, el('p', { class: 'eyebrow' }, eyebrow), el('h1', {}, title), description ? el('p', {}, description) : null), action);
  }
  function definitions(entries) { return el('dl', { class: 'definition-list' }, entries.map(([label, value]) => [el('dt', {}, label), el('dd', {}, value instanceof Node ? value : text(value))])); }
  function table(headers, rows) {
    return el('div', { class: 'table-wrap' }, el('table', {}, el('thead', {}, el('tr', {}, headers.map(h => el('th', { scope: 'col' }, h)))), el('tbody', {}, rows)));
  }
  function errorBox(error, retry) {
    return el('section', { class: 'panel error-state', role: 'alert' }, el('h2', {}, 'We couldn’t load this view.'), el('p', {}, error.message || String(error)), retry ? button('Try again', retry) : null);
  }
  class ApiError extends Error {
    constructor(message, status, code) { super(message); this.status = status; this.code = code; }
  }
  async function responseError(response) {
    let body;
    try { body = await response.json(); } catch (_) { /* A proxy may return a non-JSON error. */ }
    const detail = body?.error;
    return new ApiError(detail?.message || `The server returned HTTP ${response.status}. Please try again or contact your administrator.`, response.status, detail?.code);
  }
  async function api(path, options = {}) {
    const { json, ...rest } = options;
    const headers = new Headers(rest.headers || {}); headers.set('Accept', 'application/json');
    if (json !== undefined) headers.set('Content-Type', 'application/json');
    const response = await fetch(`${API}${path}`, { ...rest, credentials: 'same-origin', headers, ...(json !== undefined ? { body: JSON.stringify(json) } : {}) });
    if (!response.ok) {
      const error = await responseError(response);
      if (response.status === 401 && !['/auth/login', '/auth/register'].includes(path)) sessionExpired();
      throw error;
    }
    if (response.status === 204) return null;
    const body = await response.text();
    if (!body) return null;
    try { return JSON.parse(body); } catch (_) { throw new ApiError('The server returned an invalid JSON response.', response.status, 'INVALID_RESPONSE'); }
  }
  function sessionExpired() {
    if (!state.me) return;
    state.me = null; notify('Your session has ended. Sign in again to continue.', true);
    location.hash = '#/login';
  }
  function scope() {
    const controller = new AbortController(); const timers = new Map();
    return { signal: controller.signal, active: () => !controller.signal.aborted,
      later(fn, ms = 2200) { clearTimeout(timers.get(fn)); const id = setTimeout(() => { timers.delete(fn); if (!controller.signal.aborted) fn(); }, ms); timers.set(fn, id); },
      close() { controller.abort(); timers.forEach(clearTimeout); }
    };
  }
  function showAsyncError(container, error, retry, s = state.scope) { if (s.active() && error.name !== 'AbortError') container.replaceChildren(errorBox(error, retry)); }
  function setTheme(theme) {
    state.theme = ['light', 'dark', 'system'].includes(theme) ? theme : 'system';
    if (state.theme === 'system') document.documentElement.removeAttribute('data-theme');
    else document.documentElement.dataset.theme = state.theme;
    try { localStorage.setItem('originality-desk-theme', state.theme); } catch (_) { /* Storage may be blocked; the current page still changes theme. */ }
    document.querySelectorAll('input[name="theme"]').forEach(node => { node.checked = node.value === state.theme; });
    document.querySelectorAll('.theme-toggle').forEach(node => {
      node.setAttribute('aria-label', `Color theme: ${state.theme}. Switch to ${state.theme === 'system' ? 'light' : state.theme === 'light' ? 'dark' : 'system'}.`);
      node.title = `Theme: ${state.theme}`;
    });
  }
  function bindTheme() {
    document.querySelectorAll('.theme-toggle').forEach(node => node.addEventListener('click', () => {
      setTheme(state.theme === 'system' ? 'light' : state.theme === 'light' ? 'dark' : 'system');
    }));
    setTheme(state.theme);
  }
  const brand = () => el('a', { class: 'brand', href: '#/', 'aria-label': 'Originality Desk home' }, el('span', { class: 'brand-mark', 'aria-hidden': 'true' }, 'od.'), el('span', {}, 'Originality ', el('strong', {}, 'Desk')));
  function publicShell(content) {
    const source = landing.cloneNode(true);
    app.replaceChildren(...source.childNodes);
    if (content) document.getElementById('main').replaceWith(content);
    bindTheme();
    if (state.me) {
      const actions = app.querySelector('.header-actions');
      actions.querySelectorAll('a').forEach(a => a.remove());
      actions.append(link('Open workspace ↗', '#/overview', 'button primary'));
    }
    document.getElementById('main')?.focus({ preventScroll: true });
    return document.getElementById('main');
  }
  function shell(route) {
    const current = route === 'reports' || route === 'analysis' ? 'history' : route;
    const user = state.me.user;
    const nav = el('nav', { class: 'app-nav', 'aria-label': 'Workspace navigation' }, routes.map(([id, title, symbol], index) => [
      index === 6 ? el('div', { class: 'nav-divider' }) : null,
      el('a', { href: `#/${id}`, ...(current === id ? { 'aria-current': 'page' } : {}) }, el('span', { class: 'nav-symbol', 'aria-hidden': 'true' }, symbol), title)
    ]));
    if (C.isAdmin(user.role)) nav.append(el('a', { href: '#/admin', ...(route === 'admin' ? { 'aria-current': 'page' } : {}) }, el('span', { class: 'nav-symbol', 'aria-hidden': 'true' }, '◇'), 'Administration'));
    const sidebar = el('aside', { class: 'sidebar', id: 'workspace-sidebar' }, brand(),
      el('div', { class: 'workspace-label' }, el('p', { class: 'eyebrow' }, 'Workspace'), el('p', { class: 'workspace-name' }, text(state.me.workspace?.name))),
      el('p', { class: 'nav-group-label' }, 'Your desk'), nav,
      el('div', { class: 'sidebar-bottom' }, el('p', { class: 'sidebar-note' }, 'Textual evidence, not an automated verdict.'),
        el('div', { class: 'user-card' }, el('div', { class: 'avatar', 'aria-hidden': 'true' }, text(user.name, '?').split(/\s+/).map(p => p[0]).slice(0, 2).join('').toUpperCase()),
          el('div', { class: 'user-card-text' }, el('strong', {}, text(user.name)), el('span', {}, text(user.role))),
          el('button', { class: 'icon-button', type: 'button', 'aria-label': 'Sign out', title: 'Sign out', onclick: logout }, '↪'))));
    const overlay = el('button', { class: 'nav-overlay', type: 'button', hidden: true, 'aria-label': 'Close navigation', tabindex: '-1' });
    const menu = el('button', { class: 'icon-button mobile-menu', type: 'button', 'aria-controls': 'workspace-sidebar', 'aria-expanded': 'false', 'aria-label': 'Open navigation' }, '☰');
    const close = el('button', { class: 'icon-button mobile-close', type: 'button', 'aria-label': 'Close navigation' }, '×');
    function closeNav(focus = true) { sidebar.classList.remove('open'); sidebar.removeAttribute('role'); sidebar.removeAttribute('aria-modal'); overlay.hidden = true; menu.setAttribute('aria-expanded', 'false'); if (focus) menu.focus(); }
    menu.addEventListener('click', () => { sidebar.classList.add('open'); sidebar.setAttribute('role', 'dialog'); sidebar.setAttribute('aria-modal', 'true'); sidebar.setAttribute('aria-label', 'Workspace navigation'); overlay.hidden = false; menu.setAttribute('aria-expanded', 'true'); close.focus(); });
    close.addEventListener('click', () => closeNav()); overlay.addEventListener('click', () => closeNav());
    sidebar.addEventListener('keydown', event => {
      if (!sidebar.classList.contains('open')) return;
      if (event.key === 'Escape') closeNav();
      if (event.key === 'Tab') {
        const all = [...sidebar.querySelectorAll('a,button')].filter(n => n.getClientRects().length);
        const first = all[0], last = all[all.length - 1];
        if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
        if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
      }
    });
    sidebar.prepend(close);
    const media = matchMedia('(min-width: 701px)');
    media.addEventListener('change', () => closeNav(false), { signal: state.scope.signal });
    const main = el('main', { id: 'main', tabindex: '-1', class: 'app-main' });
    const title = routes.find(r => r[0] === current)?.[1] || (route === 'admin' ? 'Administration' : 'Evidence report');
    const topbar = el('header', { class: 'app-topbar' }, menu, el('p', { class: 'breadcrumb' }, 'Workspace', el('span', { 'aria-hidden': 'true' }, '/'), title),
      el('div', { class: 'topbar-right' }, el('span', { class: 'fine-print' }, 'Evidence-led review'), el('button', { type: 'button', class: 'icon-button theme-toggle', 'aria-label': 'Change color theme' }, '◐'), link('New analysis +', '#/new', 'button primary')));
    app.replaceChildren(el('div', { class: 'app-layout' }, overlay, sidebar, el('div', { class: 'app-body' }, topbar, main)));
    bindTheme(); main.focus({ preventScroll: true }); return main;
  }
  async function logout(event) {
    const target = event?.currentTarget; if (target) target.disabled = true;
    try { await api('/auth/logout', { method: 'POST', json: {} }); if (state.xhr) state.xhr.abort(); state.me = null; location.hash = '#/'; notify('You have signed out.'); }
    catch (error) { notify(error.message, true); if (target) target.disabled = false; }
  }
  async function confirmAction(title, message, actionText) {
    return new Promise(resolve => {
      const previous = document.activeElement;
      const dialog = el('div', { class: 'confirm-dialog', role: 'dialog', 'aria-modal': 'true', 'aria-labelledby': 'confirm-title', 'aria-describedby': 'confirm-message' }, el('h2', { id: 'confirm-title' }, title), el('p', { id: 'confirm-message' }, message));
      const backdrop = el('div', { class: 'dialog-backdrop' }, dialog);
      const finish = value => { backdrop.remove(); if (previous?.isConnected) previous.focus(); resolve(value); };
      const cancel = button('Keep document', () => finish(false));
      const confirm = button(actionText, () => finish(true), 'danger');
      dialog.append(el('div', { class: 'button-row' }, cancel, confirm));
      const background = document.getElementById('app'); background.inert = true;
      const cleanup = () => { background.inert = false; };
      const originalFinish = finish;
      // Inert removes the application from the tab order while the dialog is open.
      backdrop.addEventListener('keydown', e => {
        if (e.key === 'Escape') { e.preventDefault(); cleanup(); originalFinish(false); }
        if (e.key === 'Tab') { if (e.shiftKey && document.activeElement === cancel) { e.preventDefault(); confirm.focus(); } else if (!e.shiftKey && document.activeElement === confirm) { e.preventDefault(); cancel.focus(); } }
      });
      cancel.addEventListener('click', cleanup, { capture: true }); confirm.addEventListener('click', cleanup, { capture: true });
      backdrop.addEventListener('click', e => { if (e.target === backdrop) { cleanup(); originalFinish(false); } });
      document.body.append(backdrop); cancel.focus();
    });
  }

  function authView(mode) {
    const register = mode === 'register', s = state.scope;
    const main = el('main', { id: 'main', tabindex: '-1', class: 'auth-layout' });
    const aside = el('section', { class: 'auth-aside' }, el('p', { class: 'eyebrow' }, 'A workspace for close reading'), el('h1', {}, register ? 'Give your work\na closer look.' : 'Welcome back\nto your desk.'), el('p', {}, 'Compare documents, examine the matching passages, and keep a record of your academic judgment.'), el('div', { class: 'auth-footer' }, link('Explore a synthetic sample report →', '#/sample')));
    const card = el('section', { class: 'auth-card' }, el('h2', {}, register ? 'Create an account' : 'Sign in'), el('p', {}, register ? 'Create your account and workspace on this application server.' : 'Use the account associated with your workspace.'));
    const form = el('form', { class: 'form-stack' });
    const name = el('input', { type: 'text', name: 'name', autocomplete: 'name', required: true, maxlength: '120' });
    const workspace = el('input', { type: 'text', name: 'workspaceName', required: true, maxlength: '120', autocomplete: 'organization' });
    const email = el('input', { type: 'email', name: 'email', required: true, autocomplete: 'email', maxlength: '254', autocapitalize: 'none', spellcheck: 'false' });
    const password = el('input', { type: 'password', name: 'password', required: true, autocomplete: register ? 'new-password' : 'current-password', ...(register ? { minlength: '12' } : {}) });
    const passwordToggle = button('Show password', () => { const showing = password.type === 'password'; password.type = showing ? 'text' : 'password'; passwordToggle.textContent = showing ? 'Hide password' : 'Show password'; passwordToggle.setAttribute('aria-pressed', String(showing)); }, 'subtle small', { 'aria-pressed': 'false' });
    const errors = el('div', { role: 'alert', hidden: true });
    const submit = el('button', { type: 'submit', class: 'button primary' }, register ? 'Create account →' : 'Sign in →');
    if (register) form.append(field('Full name', name), field('Workspace name', workspace));
    form.append(field('Email address', email), field('Password', password, register ? 'Use at least 12 characters. The server may enforce additional requirements.' : null), passwordToggle, errors, submit);
    form.addEventListener('submit', async event => {
      event.preventDefault(); errors.hidden = true; submit.disabled = true; submit.textContent = register ? 'Creating account…' : 'Signing in…';
      try {
        await api(`/auth/${register ? 'register' : 'login'}`, { method: 'POST', json: register ? { name: name.value.trim(), email: email.value.trim(), password: password.value, workspaceName: workspace.value.trim() } : { email: email.value.trim(), password: password.value }, signal: s.signal });
        const me = await api('/auth/me', { signal: s.signal }); if (!s.active()) return; state.me = me; state.sessionChecked = true; password.value = ''; location.hash = '#/overview';
      } catch (error) { if (error.name !== 'AbortError') { errors.replaceChildren(notice(error.message, 'error')); errors.hidden = false; } }
      finally { submit.disabled = false; submit.textContent = register ? 'Create account →' : 'Sign in →'; }
    });
    card.append(form, el('div', { class: 'auth-footer' }, register ? 'Already have an account? ' : 'New to Originality Desk? ', link(register ? 'Sign in' : 'Create an account', register ? '#/login' : '#/register')),
      el('div', { class: 'auth-footer' }, el('p', {}, 'Email password reset and multi-factor authentication are unavailable. Contact your deployment administrator if you cannot access your account.')));
    main.append(aside, card); publicShell(main);
  }

  function metric(label, value, foot) { return el('div', { class: 'metric' }, el('p', { class: 'metric-label' }, label), el('strong', { class: 'metric-value' }, C.number(value)), el('p', { class: 'metric-foot' }, foot)); }
  function documentRow(doc, options = {}) {
    const actions = el('div', { class: 'table-actions' }, link('Details', `#/documents/${encodeURIComponent(doc.id)}`, 'button outline small'));
    if (C.isReady(doc.status) && doc.kind !== 'REFERENCE') actions.append(link('Analyze', `#/new?document=${encodeURIComponent(doc.id)}`, 'button outline small'));
    if (options.remove) actions.append(button('Delete', () => deleteDocument(doc, options.remove), 'subtle small'));
    return el('tr', {}, el('td', {}, link(text(doc.title || doc.filename), `#/documents/${encodeURIComponent(doc.id)}`, 'cell-title'), el('span', { class: 'cell-subtitle' }, text(doc.kind))), el('td', {}, badge(doc.status)), el('td', {}, C.number(doc.wordCount)), el('td', {}, date(doc.createdAt)), el('td', {}, actions));
  }
  function analysisRow(analysis) {
    const href = C.isComplete(analysis.status) ? `#/reports/${encodeURIComponent(analysis.id)}` : `#/analysis/${encodeURIComponent(analysis.id)}`;
    return el('tr', {}, el('td', {}, link(text(analysis.title, `Analysis ${analysis.id}`), href, 'cell-title'), el('span', { class: 'cell-subtitle' }, text(analysis.profile))), el('td', {}, badge(analysis.status)), el('td', {}, C.number(analysis.sourceCount)), el('td', {}, date(analysis.createdAt)), el('td', {}, link(C.isComplete(analysis.status) ? 'Open report →' : 'View status →', href, 'button outline small')));
  }
  async function dashboard(main, s) {
    main.append(heading('A clearer view of your work.', 'Your documents, review activity, and analyses — as recorded by the server.', link('Start an analysis +', '#/new', 'button primary'), 'Overview'));
    const content = el('div', {}, loading()); main.append(content);
    async function load() {
      try {
        const data = await api('/dashboard', { signal: s.signal }); if (!s.active()) return;
        const docs = C.collection(data.recentDocuments), analyses = C.collection(data.recentAnalyses), activity = C.collection(data.activity);
        const docsPanel = panel('Recent documents', docs.length ? table(['Document', 'Status', 'Words', 'Uploaded', 'Actions'], docs.map(doc => documentRow(doc))) : empty('Your desk is ready.', 'Upload your first submission or reference document.', link('Upload a document', '#/documents', 'button outline')));
        docsPanel.querySelector('.panel-header').append(link('All documents →', '#/documents'));
        const analysisPanel = panel('Recent analyses', analyses.length ? table(['Analysis', 'Status', 'Sources', 'Started', 'Actions'], analyses.map(analysisRow)) : empty('No analyses yet', 'Choose a submission and comparison sources to begin.', link('New analysis', '#/new', 'button outline')));
        const activityPanel = panel('Workspace activity', activity.length ? el('ul', { class: 'activity-list' }, activity.map(item => el('li', {}, typeof item === 'string' ? item : text(item.message || item.description || item.action || item.type, 'Activity entry'), item.createdAt || item.timestamp ? el('time', {}, date(item.createdAt || item.timestamp)) : null))) : el('p', { class: 'fine-print' }, 'No activity has been recorded.'));
        content.replaceChildren(el('div', { class: 'metrics-grid' }, metric('Documents', data.totalDocuments, 'Stored in this workspace'), metric('Completed analyses', data.completedAnalyses, 'Reports ready to review'), metric('Pending analyses', data.pendingAnalyses, 'Awaiting or in processing'), metric('Failed analyses', data.failedAnalyses, 'May need your attention')),
          el('div', { class: 'two-column' }, el('div', { class: 'stack' }, docsPanel, analysisPanel), el('div', { class: 'stack' }, panel('A considered review', el('p', { class: 'muted' }, 'Overlap is not an academic verdict. Read the source, check attribution, and record your judgment.'), el('hr'), link('Explore the synthetic example →', '#/sample'), el('p', { class: 'storage-note' }, `Server-reported storage: ${C.formatBytes(data.storageBytes)}`)), activityPanel)));
        if (Number(data.pendingAnalyses) > 0 || docs.some(doc => !C.isReady(doc.status) && !C.isFailed(doc.status))) s.later(load, 5000);
      } catch (error) { showAsyncError(content, error, load, s); }
    }
    await load();
  }

  async function deleteDocument(doc, reload) {
    if (!await confirmAction('Delete this document?', `“${text(doc.title || doc.filename)}” will be deleted from the server, along with dependent analyses, report snapshots, and reviews. This cannot be undone here.`, 'Delete document')) return;
    try { await api(`/documents/${encodeURIComponent(doc.id)}`, { method: 'DELETE' }); notify('Document deleted.'); await reload(); }
    catch (error) { notify(error.message, true); }
  }

  function uploadWidget(kind, onUploaded) {
    const zone = el('section', { class: 'upload-zone', 'aria-label': `Upload ${kind.toLowerCase()} document` });
    const input = el('input', { type: 'file', accept: '.pdf,.docx,.txt', 'aria-label': 'Choose a PDF, DOCX, or TXT file' });
    const summary = el('p', { class: 'file-summary' });
    const progress = el('progress', { max: '100', value: '0', 'aria-label': 'File upload progress' });
    const status = el('p', { class: 'upload-status', role: 'status', 'aria-live': 'polite' });
    const errorNode = el('div', { role: 'alert', hidden: true });
    const progressArea = el('div', { class: 'upload-progress', hidden: true }, status, progress);
    const start = button('Upload document ↑', send, 'primary');
    const abort = button('Abort upload', () => { if (xhr) xhr.abort(); }, 'outline', { hidden: true });
    const retry = button('Retry upload', send, 'outline', { hidden: true });
    let file = null, xhr = null, busy = false, uploadToast = null, globalStatus = null;
    function select(next) {
      if (busy) return;
      file = next; retry.hidden = true; errorNode.hidden = true; progressArea.hidden = true;
      const error = C.validateFile(file);
      summary.textContent = file ? `${file.name} · ${C.formatBytes(file.size)}` : '';
      start.disabled = Boolean(error);
      if (error && file) { errorNode.replaceChildren(notice(error, 'error')); errorNode.hidden = false; }
    }
    input.addEventListener('change', () => select(input.files[0]));
    zone.addEventListener('dragover', event => { event.preventDefault(); if (!busy) zone.classList.add('dragover'); });
    zone.addEventListener('dragleave', () => zone.classList.remove('dragover'));
    zone.addEventListener('drop', event => { event.preventDefault(); zone.classList.remove('dragover'); if (event.dataTransfer.files.length !== 1) { errorNode.replaceChildren(notice('Drop one file at a time.', 'error')); errorNode.hidden = false; return; } select(event.dataTransfer.files[0]); });
    function fail(message, canRetry = true) {
      errorNode.replaceChildren(notice(message, 'error')); errorNode.hidden = false; retry.hidden = !canRetry;
      status.textContent = 'Upload not completed.';
    }
    function send() {
      const validation = C.validateFile(file); if (validation) { fail(validation, false); return; }
      if (state.xhr && state.xhr !== xhr) { fail('Another upload is in progress. Wait for it to finish before starting this one.', false); return; }
      busy = true; input.disabled = true; start.disabled = true; retry.hidden = true; abort.hidden = false; errorNode.hidden = true; progressArea.hidden = false;
      progress.value = 0; status.textContent = 'Uploading file…';
      xhr = new XMLHttpRequest(); state.xhr = xhr;
      globalStatus = el('span', {}, `Uploading ${file.name}…`);
      uploadToast = el('div', { class: 'toast' }, globalStatus, button('Abort', () => xhr.abort(), 'outline small', { 'aria-label': `Abort upload of ${file.name}` }));
      document.getElementById('notifications').append(uploadToast);
      state.upload = { filename: file.name, kind, status: 'uploading' };
      xhr.open('POST', `${API}/documents?filename=${encodeURIComponent(file.name)}&kind=${encodeURIComponent(kind)}`);
      xhr.withCredentials = true; xhr.timeout = 180000;
      xhr.setRequestHeader('Content-Type', 'application/octet-stream'); xhr.setRequestHeader('Accept', 'application/json');
      xhr.upload.addEventListener('progress', event => {
        if (event.lengthComputable) {
          const value = Math.min(100, Math.round(event.loaded / event.total * 100)); progress.value = value;
          status.textContent = value === 100 ? 'File transferred. Waiting for the server to accept it…' : `Uploading file… ${value}%`;
          globalStatus.textContent = `${file.name}: ${value === 100 ? 'waiting for server acceptance' : `${value}% uploaded`}`;
        } else progress.removeAttribute('value');
      });
      xhr.addEventListener('load', async () => {
        let body; try { body = JSON.parse(xhr.responseText); } catch (_) { /* Report a useful invalid-response error below. */ }
        if (xhr.status < 200 || xhr.status >= 300) { if (xhr.status === 401) sessionExpired(); fail(body?.error?.message || `Upload failed (HTTP ${xhr.status}).`); return; }
        if (!body?.id) { fail('The server accepted the upload but did not return a document ID. Refresh Documents before retrying to avoid a duplicate.', false); return; }
        state.upload = { filename: file.name, kind, status: 'accepted', documentId: body.id };
        status.textContent = `File accepted. Extraction status: ${statusLabel(body.status)}.`; progress.value = 100;
        notify(`${file.name} uploaded. Extraction status: ${statusLabel(body.status)}.`);
        try { await onUploaded(body); } catch (error) { notify(error.message, true); }
      });
      xhr.addEventListener('error', () => fail('The upload could not reach the server. Check your connection. A retry sends the file again; check Documents first if the server may have accepted it.'));
      xhr.addEventListener('timeout', () => fail('The upload timed out. Check Documents before retrying; the server may have received the file.'));
      xhr.addEventListener('abort', () => { status.textContent = 'Upload aborted. If transfer had finished, check Documents before retrying.'; retry.hidden = false; });
      xhr.addEventListener('loadend', () => { busy = false; input.disabled = false; start.disabled = false; abort.hidden = true; uploadToast?.remove(); if (state.xhr === xhr) state.xhr = null; });
      xhr.send(file);
    }
    if (state.xhr) zone.append(notice('Another file is currently uploading. It will continue if you change views. Use the persistent upload notification to abort, or wait for completion.', 'warning'));
    start.disabled = true;
    zone.append(el('div', { class: 'upload-symbol', 'aria-hidden': 'true' }, '↥'), el('h3', {}, kind === 'REFERENCE' ? 'Add material to your reference library' : 'Bring your submission to the desk'), el('p', {}, 'Drop one PDF, DOCX, or TXT file here, or choose a file. Maximum 10 MiB. Scanned files need text; OCR is unavailable.'), input, summary, progressArea, errorNode, el('div', { class: 'button-row' }, start, abort, retry));
    return zone;
  }

  function pagination(data, page, change) {
    const total = Number(data.total) || 0, size = Number(data.size) || 20;
    return el('div', { class: 'pagination' }, el('span', {}, total ? `${C.number(total)} records · Page ${page} of ${Math.max(1, Math.ceil(total / size))}` : 'No records'), el('div', { class: 'button-row' }, button('← Previous', () => change(page - 1), 'outline small', { disabled: page <= 1 }), button('Next →', () => change(page + 1), 'outline small', { disabled: page * size >= total })));
  }
  function filterSelect(options, label) { return el('select', { 'aria-label': label }, options.map(([value, title]) => el('option', { value }, title))); }
  async function documentsView(main, s, references) {
    main.append(heading(references ? 'Your reference library.' : 'Documents, in order.', references ? 'Reference files are comparison material, not an automatically verified bibliography.' : 'Upload, inspect extraction status, and manage the files in your workspace.', null, references ? 'Source material' : 'Documents'));
    const upload = uploadWidget(references ? 'REFERENCE' : 'SUBMISSION', () => load());
    const list = panel(references ? 'Reference documents' : 'Workspace documents');
    const search = el('input', { type: 'search', placeholder: 'Find a title on this page', 'aria-label': 'Filter documents on this page' });
    const kind = filterSelect([['', 'All document kinds'], ['SUBMISSION', 'Submissions'], ['REFERENCE', 'References']], 'Document kind');
    const status = filterSelect([['', 'All statuses'], ['ready', 'Ready / extracted'], ['processing', 'Processing'], ['failed', 'Failed / cancelled']], 'Document status');
    const toolbar = el('div', { class: 'toolbar' }, field('Search this page', search), !references ? field('Kind', kind) : null, field('Status on this page', status));
    const results = el('div', {}, loading()); list.append(toolbar, results);
    main.append(el('div', { class: 'stack' }, upload, list));
    let page = 1, lastData = null, request = 0;
    function draw() {
      if (!lastData) return;
      const docs = C.collection(lastData).filter(doc => `${doc.title || ''} ${doc.filename || ''}`.toLowerCase().includes(search.value.toLowerCase()) && (!status.value || (status.value === 'ready' ? C.isReady(doc.status) : status.value === 'failed' ? C.isFailed(doc.status) : !C.isReady(doc.status) && !C.isFailed(doc.status))));
      results.replaceChildren(docs.length ? table(['Document', 'Status', 'Words', 'Uploaded', 'Actions'], docs.map(doc => documentRow(doc, { remove: load }))) : empty('No documents to show', search.value || status.value ? 'No records on this page match your filters. Change the filters or browse another page.' : 'Upload a document above to get started.'), pagination(lastData, page, next => { page = next; load(); }));
    }
    async function load() {
      if (!s.active()) return; const version = ++request;
      try {
        const params = new URLSearchParams({ page: String(page), size: '20' }); if (references || kind.value) params.set('kind', references ? 'REFERENCE' : kind.value);
        const data = await api(`/documents?${params}`, { signal: s.signal }); if (!s.active() || version !== request) return; lastData = data; draw();
        if (C.collection(lastData).some(doc => !C.isReady(doc.status) && !C.isFailed(doc.status))) s.later(load);
      } catch (error) { if (version === request) showAsyncError(results, error, load, s); }
    }
    search.addEventListener('input', draw); status.addEventListener('change', draw); kind.addEventListener('change', () => { page = 1; load(); });
    await load();
  }

  async function documentDetail(main, s, id) {
    main.append(heading('Document details.', 'Extraction status and metadata from the server.', link('All documents →', '#/documents', 'button outline')));
    const content = el('div', {}, loading()); main.append(content);
    async function load() {
      try {
        const doc = await api(`/documents/${encodeURIComponent(id)}`, { signal: s.signal }); if (!s.active()) return;
        const actions = el('div', { class: 'button-row' }, doc.kind !== 'REFERENCE' && C.isReady(doc.status) ? link('Analyze this submission →', `#/new?document=${encodeURIComponent(id)}`, 'button primary') : null,
          button('Refresh status', load), button('Delete document', () => deleteDocument(doc, () => { location.hash = '#/documents'; }), 'danger'));
        replace(content, panel(text(doc.title || doc.filename), definitions([['Status', badge(doc.status)], ['Kind', doc.kind], ['Filename', doc.filename], ['Words', C.number(doc.wordCount)], ['Pages', C.number(doc.pageCount)], ['Size', C.formatBytes(doc.bytes)], ['Uploaded', date(doc.createdAt)], ['Document ID', doc.id]]), el('hr'), actions),
          doc.error ? notice(text(doc.error), 'error') : null,
          !C.isReady(doc.status) && !C.isFailed(doc.status) ? notice(`Extraction is in progress. Actual server status: ${statusLabel(doc.status)}. This view checks again automatically.`) : null);
        if (!C.isReady(doc.status) && !C.isFailed(doc.status)) s.later(load);
      } catch (error) { showAsyncError(content, error, load, s); }
    }
    await load();
  }

  async function historyView(main, s) {
    main.append(heading('A record of every review.', 'Analysis history and completed reports share one searchable view.', link('New analysis +', '#/new', 'button primary'), 'History & reports'));
    const list = panel('Analyses'); main.append(list);
    const search = el('input', { type: 'search', placeholder: 'Find an analysis on this page' });
    const status = filterSelect([['', 'All statuses'], ['completed', 'Completed reports'], ['pending', 'In progress'], ['failed', 'Failed / cancelled']], 'Analysis status');
    const profile = filterSelect([['', 'All profiles'], ['QUICK', 'Quick'], ['STANDARD', 'Standard'], ['REFERENCE_AWARE', 'Reference-aware']], 'Analysis profile');
    const results = el('div', {}, loading());
    list.append(el('div', { class: 'toolbar' }, field('Search this page', search), field('Status on this page', status), field('Profile on this page', profile)), results);
    let page = 1, data, request = 0;
    function draw() {
      const items = C.collection(data).filter(a => `${a.title || ''} ${a.id}`.toLowerCase().includes(search.value.toLowerCase()) && (!profile.value || a.profile === profile.value) && (!status.value || (status.value === 'completed' ? C.isComplete(a.status) : status.value === 'failed' ? C.isFailed(a.status) : !C.isTerminal(a.status))));
      results.replaceChildren(items.length ? table(['Analysis', 'Status', 'Sources', 'Started', 'Actions'], items.map(analysisRow)) : empty('No analyses to show', 'Run an analysis or adjust this page’s filters.', link('New analysis', '#/new', 'button outline')), pagination(data, page, next => { page = next; load(); }));
    }
    async function load() {
      const version = ++request;
      try { const response = await api(`/analyses?page=${page}&size=20`, { signal: s.signal }); if (!s.active() || version !== request) return; data = response; draw(); if (C.collection(data).some(a => !C.isTerminal(a.status))) s.later(load, 4000); }
      catch (error) { if (version === request) showAsyncError(results, error, load, s); }
    }
    [search, status, profile].forEach(input => input.addEventListener(input === search ? 'input' : 'change', draw)); await load();
  }

  // Explicit pagination avoids silently limiting the set of available comparison documents.
  async function documentPicker(s, { kind = '', multiple = false, exclude = () => '', onChange = () => {}, initial = '' } = {}) {
    const root = el('div');
    const list = el('div', { class: 'source-list' }, loading('Loading documents…'));
    const search = el('input', { type: 'search', placeholder: 'Filter this page by title', 'aria-label': 'Filter available documents on this page' });
    const selected = new Map(); const controls = el('div'); const summary = el('p', {}, 'No documents selected');
    const selection = el('div', { class: 'selected-documents', 'aria-label': 'Selected documents', hidden: true });
    root.append(search, el('div', { class: 'source-tools' }, summary, button('Refresh', () => load(), 'subtle small')), selection, list, controls);
    let page = 1, data, request = 0;
    function update() {
      summary.textContent = multiple ? `${selected.size} source${selected.size === 1 ? '' : 's'} selected` : selected.size ? '1 submission selected' : 'No submission selected';
      selection.hidden = !selected.size;
      selection.replaceChildren(...[...selected.values()].map(doc => el('div', { class: 'selected-document' }, el('span', {}, text(doc.title || doc.filename)), button('Remove', () => { selected.delete(String(doc.id)); update(); if (data) draw(); search.focus(); }, 'subtle small', { 'aria-label': `Remove ${text(doc.title || doc.filename)} from selection` }))));
      onChange([...selected.values()]);
    }
    function draw() {
      const items = C.collection(data).filter(doc => String(doc.id) !== String(exclude()) && `${doc.title || ''} ${doc.filename || ''}`.toLowerCase().includes(search.value.toLowerCase()));
      list.replaceChildren(...(items.length ? items.map(doc => {
        const ready = C.isReady(doc.status);
        const input = el('input', { type: multiple ? 'checkbox' : 'radio', name: multiple ? 'sources' : 'submission', value: doc.id, checked: selected.has(String(doc.id)), disabled: !ready });
        input.addEventListener('change', () => { if (!multiple) selected.clear(); if (input.checked) selected.set(String(doc.id), doc); else selected.delete(String(doc.id)); update(); });
        return el('label', { class: 'source-option' }, input, el('span', {}, el('strong', {}, text(doc.title || doc.filename)), el('small', {}, `${text(doc.kind)} · ${statusLabel(doc.status)} · ${C.number(doc.wordCount)} words${ready ? '' : ' · not selectable until extraction is ready'}`)));
      }) : [el('p', { class: 'empty-state' }, 'No selectable documents on this page. Upload a file, change the search, or browse another page.')]));
      controls.replaceChildren(pagination(data, page, next => { page = next; load(); }));
    }
    async function load() {
      const version = ++request;
      try { const params = new URLSearchParams({ page: String(page), size: '20' }); if (kind) params.set('kind', kind); const response = await api(`/documents?${params}`, { signal: s.signal }); if (!s.active() || version !== request) return; data = response; draw(); }
      catch (error) { if (version === request) showAsyncError(list, error, load, s); }
    }
    search.addEventListener('input', () => { if (data) draw(); });
    const picker = { root, values: () => [...selected.keys()], refresh: load, excludeChanged() { if (selected.delete(String(exclude()))) update(); if (data) draw(); } };
    if (initial) {
      try { const doc = await api(`/documents/${encodeURIComponent(initial)}`, { signal: s.signal }); if (C.isReady(doc.status) && (!kind || doc.kind === kind)) { selected.set(String(doc.id), doc); update(); } }
      catch (error) { if (error.name !== 'AbortError') notify(error.message, true); }
    }
    await load(); return picker;
  }

  async function analysisForm(main, s, compare, params) {
    main.append(heading(compare ? 'Read two sides of the story.' : 'Begin with the evidence.', compare ? 'Compare one submission against one or more existing documents using the same text-matching engine.' : 'Choose a submission, select real reference documents, and configure your analysis.', null, compare ? 'Document comparison' : 'New analysis'));
    const form = el('form', { class: 'form-stack' });
    const left = panel(null, form);
    const sidebar = panel('Before you begin', el('ul', { class: 'checklist' }, el('li', {}, 'Only documents with completed extraction can be selected.'), el('li', {}, 'Selected source IDs are sent to the server explicitly. No web-wide search is implied.'), el('li', {}, 'Exact and near-text matching do not establish semantic equivalence or authorship.'), el('li', {}, 'Citations and shared language require human interpretation.')),
      el('hr'), el('p', { class: 'fine-print' }, 'Unavailable: semantic analysis, AI-authorship detection, OCR, and workspace invitations.'));
    main.append(el('div', { class: 'two-column' }, left, sidebar));
    const submissionBlock = el('section', {}, el('h2', { class: 'form-section-title' }, '01. Select your submission'), loading()); form.append(submissionBlock);
    let sources;
    const submission = await documentPicker(s, { kind: 'SUBMISSION', initial: params.get('document') || '', onChange: () => sources?.excludeChanged() }); if (!s.active()) return;
    submissionBlock.replaceChildren(el('h2', { class: 'form-section-title' }, '01. Select your submission'), submission.root);
    if (!compare) submissionBlock.append(el('hr'), uploadWidget('SUBMISSION', async doc => { await submission.refresh(); notify('Once extraction is ready, select the uploaded submission above.'); watchExtraction(doc.id); }));
    const sourcesBlock = el('section', {}, el('h2', { class: 'form-section-title' }, compare ? '02. Choose comparison documents' : '02. Choose reference documents'), el('p', { class: 'fine-print' }, 'Selections are retained when you browse source pages. Only the selected documents are compared.'), loading()); form.append(sourcesBlock);
    sources = await documentPicker(s, { kind: compare ? '' : 'REFERENCE', multiple: true, exclude: () => submission.values()[0] || '' }); if (!s.active()) return;
    sourcesBlock.replaceChildren(el('h2', { class: 'form-section-title' }, compare ? '02. Choose comparison documents' : '02. Choose reference documents'), el('p', { class: 'fine-print' }, 'Selections are retained across source pages. At least one source is required.'), sources.root,
      link(compare ? 'Manage existing documents →' : 'Upload reference material →', compare ? '#/documents' : '#/references'));
    const profiles = el('fieldset', {}, el('legend', { class: 'form-section-title' }, '03. Choose a processing profile'));
    const descriptions = [['QUICK', 'Quick', 'Exact normalized text only; 12-word shingles. No near-text matching.'], ['STANDARD', 'Standard', '8-word exact shingles plus lexical near-text matching.'], ['REFERENCE_AWARE', 'Reference-aware', '6-word exact shingles plus lexical near-text matching. Heuristically detected trailing reference lists are excluded.']];
    profiles.append(el('div', { class: 'profile-options' }, descriptions.map(([value, label, description]) => el('label', { class: 'profile-option' }, el('input', { type: 'radio', name: 'profile', value, checked: value === 'STANDARD' }), el('span', {}, el('strong', {}, label), el('small', {}, description))))));
    const errors = el('div', { role: 'alert', hidden: true });
    const submit = el('button', { type: 'submit', class: 'button primary' }, compare ? 'Run document comparison →' : 'Start analysis →');
    form.append(profiles, notice('The profile configures the actual engine. It is not an accuracy guarantee. Review the report’s methods and limitations.'), errors, el('div', { class: 'button-row' }, submit));
    let key = null, fingerprint = null;
    form.addEventListener('submit', async event => {
      event.preventDefault(); errors.hidden = true;
      const documentId = submission.values()[0]; const sourceIds = sources.values().filter(id => id !== documentId);
      if (!documentId || !sourceIds.length) { errors.replaceChildren(notice('Select a ready submission and at least one ready source document.', 'error')); errors.hidden = false; return; }
      if (sourceIds.length > 50) { errors.replaceChildren(notice('The engine supports at most 50 source documents per analysis. Deselect sources before continuing.', 'error')); errors.hidden = false; return; }
      const payload = { documentId, sourceIds, profile: form.querySelector('input[name="profile"]:checked').value };
      const current = JSON.stringify(payload); if (current !== fingerprint) { fingerprint = current; key = globalThis.crypto?.randomUUID ? crypto.randomUUID() : `od-${Date.now()}-${Math.random().toString(36).slice(2)}`; }
      submit.disabled = true; submit.textContent = 'Submitting analysis…';
      try {
        const analysis = await api('/analyses', { method: 'POST', json: payload, headers: { 'Idempotency-Key': key }, signal: s.signal });
        if (!analysis?.id) throw new Error('The server did not return an analysis ID. Check History & reports before retrying.');
        location.hash = `#/analysis/${encodeURIComponent(analysis.id)}`;
      } catch (error) { if (error.name !== 'AbortError') { errors.replaceChildren(notice(error.message, 'error')); errors.hidden = false; } }
      finally { submit.disabled = false; submit.textContent = compare ? 'Run document comparison →' : 'Start analysis →'; }
    });
    async function watchExtraction(id) {
      if (!s.active()) return;
      try { const doc = await api(`/documents/${encodeURIComponent(id)}`, { signal: s.signal }); if (!s.active()) return; if (C.isReady(doc.status)) { notify('Extraction completed. Your submission is ready to select.'); await submission.refresh(); } else if (C.isFailed(doc.status)) notify(text(doc.error, `Extraction ${statusLabel(doc.status)}.`), true); else s.later(() => watchExtraction(id)); }
      catch (error) { if (error.name !== 'AbortError') notify(error.message, true); }
    }
  }

  async function analysisStatus(main, s, id) {
    main.append(heading('Your analysis, in progress.', 'The status below is read directly from the server. No estimated completion time is implied.', link('History & reports →', '#/history', 'button outline'), 'Analysis status'));
    const content = el('div', {}, loading()); main.append(content);
    async function load() {
      try {
        const analysis = await api(`/analyses/${encodeURIComponent(id)}/status`, { signal: s.signal }); if (!s.active()) return;
        const completed = C.isComplete(analysis.status), terminal = C.isTerminal(analysis.status);
        const cancellation = button('Cancel analysis', async () => {
          cancellation.disabled = true;
          try { await api(`/analyses/${encodeURIComponent(id)}/cancel`, { method: 'POST', json: {}, signal: s.signal }); notify('Cancellation requested. The status below reflects the server response.'); await load(); }
          catch (error) { if (error.name !== 'AbortError') notify(error.message, true); } finally { cancellation.disabled = false; }
        }, 'danger');
        const card = panel(text(analysis.title, 'Analysis'), definitions([['Server status', badge(analysis.status)], ['Profile', analysis.profile], ['Started', date(analysis.createdAt)], ['Completed', date(analysis.completedAt)], ['Source documents', C.number(analysis.sourceCount)], ['Analysis ID', id]]),
          el('hr'), el('div', { class: 'button-row' }, completed ? link('Open evidence report →', `#/reports/${encodeURIComponent(id)}`, 'button primary') : null, !terminal ? cancellation : null, button('Refresh status', load)));
        replace(content, card, analysis.error ? notice(text(analysis.error), 'error') : null,
          !terminal ? notice(`Actual analysis stage: ${statusLabel(analysis.status)}. This page polls the server while processing continues. Leaving this view does not cancel the job.`) : null,
          C.isFailed(analysis.status) ? notice('This analysis has no completed report. To retry, start a new analysis with a ready document and sources. The original record remains in history.', 'warning') : null,
          C.isFailed(analysis.status) ? link('Configure a new analysis →', `#/new${analysis.documentId ? `?document=${encodeURIComponent(analysis.documentId)}` : ''}`, 'button outline') : null);
        if (!terminal) s.later(load);
      } catch (error) { showAsyncError(content, error, load, s); }
    }
    await load();
  }

  async function workspaceView(main, s) {
    main.append(heading('The people behind the work.', 'Your account and workspace details, as returned by the current session.', null, 'Workspace & profile'));
    const content = el('div', {}, loading()); main.append(content);
    try {
      const me = await api('/auth/me', { signal: s.signal }); if (!s.active()) return; state.me = me;
      content.replaceChildren(el('div', { class: 'equal-columns' }, panel('Your profile', definitions([['Name', me.user.name], ['Email', me.user.email], ['Role', me.user.role], ['User ID', me.user.id]])), panel('Your workspace', definitions([['Workspace', me.workspace?.name], ['Workspace ID', me.workspace?.id]]), el('hr'), notice('Workspace invitations and membership management are unavailable. Contact your deployment administrator for access changes.'))),
        el('hr'), notice('Profile editing, email reset, and MFA are unavailable through this API. Displayed account details are read-only.'));
    } catch (error) { showAsyncError(content, error, () => render(), s); }
  }
  function settingsView(main) {
    main.append(heading('A desk that feels like yours.', 'Appearance preferences are saved only in this browser.', null, 'Settings'));
    const appearance = panel('Appearance', el('p', { class: 'muted' }, 'Choose a color theme. System follows your device preference.'));
    const options = el('fieldset', {}, el('legend', { class: 'sr-only' }, 'Color theme'));
    const saveStatus = el('p', { class: 'fine-print', role: 'status' }, 'This is a local preference, not a workspace setting.');
    options.append(el('div', { class: 'settings-options' }, ['light', 'dark', 'system'].map(value => {
      const radio = el('input', { type: 'radio', name: 'theme', value, checked: state.theme === value });
      radio.addEventListener('change', () => { setTheme(value); let persisted = false; try { persisted = localStorage.getItem('originality-desk-theme') === value; } catch (_) { /* Browser storage unavailable. */ } saveStatus.textContent = persisted ? 'Theme saved in this browser.' : 'Theme applied for this page. Browser storage is unavailable, so it could not be saved.'; });
      return el('label', { class: 'radio-tile' }, radio, value[0].toUpperCase() + value.slice(1));
    })));
    appearance.append(options, saveStatus);
    const unavailable = panel('Not available in this deployment', el('p', { class: 'fine-print' }, 'These features have no supported control in this interface.'), el('ul', { class: 'unavailable-list' }, ['AI-authorship detection', 'Semantic similarity', 'OCR for scanned documents', 'Email password reset', 'Multi-factor authentication', 'Workspace invitations', 'Server preference editing'].map(label => el('li', {}, label, el('span', { class: 'badge' }, 'Unavailable')))));
    main.append(el('div', { class: 'equal-columns' }, appearance, unavailable));
  }

  async function adminView(main, s) {
    if (!C.isAdmin(state.me.user.role)) { main.append(heading('Administrator access required.', 'Your current account is not authorized to view administration.'), link('Return to overview →', '#/overview', 'button primary')); return; }
    main.append(heading('Operations, without guesswork.', 'Live server health, processing jobs, and audit records. Access is also enforced by the server.', null, 'Administration'));
    const tabs = el('nav', { class: 'admin-tabs', 'aria-label': 'Administration views' });
    const content = panel(null, loading()); main.append(tabs, content);
    let selected = 'health', request = 0, page = 1;
    async function load() {
      const version = ++request; content.replaceChildren(loading());
      tabs.querySelectorAll('button').forEach(node => { node.setAttribute('aria-pressed', String(node.dataset.endpoint === selected)); });
      try {
        const result = await api(`/admin/${selected}${selected === 'health' ? '' : `?page=${page}&size=20`}`, { signal: s.signal }); if (!s.active() || version !== request) return;
        const title = selected === 'health' ? 'Server health' : selected === 'jobs' ? 'Processing jobs' : 'Audit logs';
        const records = C.collection(result);
        const body = selected === 'health' ? el('div', {}, definitions([['Server status', text(result.status)], ['Worker alive', result.workerAlive == null ? 'Not reported' : String(result.workerAlive)]]), el('hr'), el('div', { class: 'metrics-grid' }, metric('Queued jobs', result.queuedJobs, 'Server-reported queue'), metric('Running jobs', result.runningJobs, 'Currently processing'), metric('Failed jobs', result.failedJobs, 'Require investigation')))
          : el('div', {}, records.length ? (selected === 'jobs' ? table(['Job', 'Status', 'Attempts', 'Created', 'Error'], records.map(job => el('tr', {}, el('td', {}, el('strong', {}, text(job.kind)), el('span', { class: 'cell-subtitle' }, text(job.resourceId))), el('td', {}, badge(job.status)), el('td', {}, C.number(job.attempts)), el('td', {}, date(job.createdAt)), el('td', { class: 'job-error' }, text(job.error, 'None reported')))))
            : table(['Action', 'User', 'Resource', 'Recorded'], records.map(entry => el('tr', {}, el('td', {}, text(entry.action)), el('td', {}, text(entry.userId)), el('td', {}, text(entry.resourceId)), el('td', {}, date(entry.createdAt)))))) : empty('No records returned', 'There are no entries on this page.'), pagination(result, page, next => { page = next; load(); }));
        content.replaceChildren(el('div', { class: 'panel-header' }, el('h2', {}, title), button('Refresh', load, 'outline small')), body, el('hr'), el('p', { class: 'fine-print' }, `Fetched ${date(new Date().toISOString())}.`), el('details', {}, el('summary', {}, 'Inspect original API response'), el('pre', { class: 'json-output', tabindex: '0', 'aria-label': `${selected} API response` }, JSON.stringify(result, null, 2))));
      } catch (error) { if (s.active() && version === request) showAsyncError(content, error, load, s); }
    }
    [['health', 'Health'], ['jobs', 'Jobs'], ['audit-logs', 'Audit logs']].forEach(([endpoint, title]) => tabs.append(button(title, () => { selected = endpoint; page = 1; load(); }, 'outline', { 'data-endpoint': endpoint, 'aria-pressed': String(selected === endpoint) })));
    await load();
  }

  // This fixture is deliberately static, synthetic, and separate from workspace records.
  function sampleReport() {
    const passage = 'Careful attribution makes the relationship between an argument and its sources visible.';
    const documentText = `Reading between the references\n\nThe archive is not simply a record of the past. ${passage} Review begins with that relationship, not a verdict.\n\nA useful review places a borrowed phrase beside its source and asks whether the reader can follow that connection. This demonstration is invented and is not a real academic source.`;
    const start = documentText.indexOf(passage);
    return { id: 'synthetic-example', title: 'Reading between the references', profile: 'STANDARD', status: 'COMPLETED', documentText,
      result: { engineVersion: 'Static illustration — not engine output', profile: 'STANDARD', exactOverlap: null, nearOverlap: null, totalOverlap: null, wordCount: null,
        matches: [{ sourceId: 'synthetic-source', sourceTitle: 'Invented reference passage', sourceType: 'SYNTHETIC', submittedPassage: passage, sourcePassage: `An invented handbook sentence: ${passage}`, method: 'EXACT (illustrative)', value: null, start, end: start + passage.length, sourceStart: 31, sourceEnd: 31 + passage.length, page: null, citationObservation: 'Illustrative observation: inspect the surrounding text for attribution. This is not a verified citation assessment.' }],
        citations: [], limitations: ['All text and source material in this example are synthetic.', 'No analysis was run. Scores, dates, word counts, and reviewer persistence are intentionally omitted.', 'Real reports are limited to the selected comparison documents and extractable text.'], semanticStatus: 'UNAVAILABLE', aiWritingStatus: 'UNAVAILABLE' } };
  }

  async function readReviews(id, signal) {
    const items = []; let page = 1;
    while (true) {
      const data = await api(`/analyses/${encodeURIComponent(id)}/reviews?page=${page}&size=100`, { signal });
      const batch = C.collection(data); items.push(...batch);
      if (Array.isArray(data) || !batch.length || items.length >= Number(data.total)) return items;
      page += 1;
    }
  }

  async function downloadReport(id, format, trigger) {
    trigger.disabled = true; const previous = trigger.textContent; trigger.textContent = 'Downloading…';
    try {
      const response = await fetch(`${API}/reports/${encodeURIComponent(id)}/${format}`, { credentials: 'same-origin', headers: { Accept: format === 'pdf' ? 'application/pdf' : format === 'json' ? 'application/json' : format === 'csv' ? 'text/csv' : 'text/html' } });
      if (!response.ok) { if (response.status === 401) sessionExpired(); throw await responseError(response); }
      const blob = await response.blob();
      const disposition = response.headers.get('Content-Disposition') || '';
      const matched = disposition.match(/filename\*?=(?:UTF-8''|\")?([^\";\n]+)/i);
      let filename = `originality-report-${id}.${format}`;
      if (matched) { try { filename = decodeURIComponent(matched[1]); } catch (_) { filename = matched[1]; } }
      const url = URL.createObjectURL(blob);
      const anchor = el('a', { href: url, download: C.safeFilename(filename, `report.${format}`), hidden: true });
      document.body.append(anchor); anchor.click(); anchor.remove(); setTimeout(() => URL.revokeObjectURL(url), 30000);
      notify(`${format.toUpperCase()} report downloaded.`);
    } catch (error) { notify(error.message, true); }
    finally { trigger.disabled = false; trigger.textContent = previous; }
  }

  async function reportView(main, s, id, sample = false) {
    const content = el('div', {}, loading('Loading the evidence report…')); main.append(content);
    let snapshot, reviews = [], reviewError = null;
    try {
      if (sample) snapshot = sampleReport();
      else {
        const [report, metadata] = await Promise.all([
          api(`/reports/${encodeURIComponent(id)}`, { signal: s.signal }),
          api(`/analyses/${encodeURIComponent(id)}/status`, { signal: s.signal })
        ]);
        snapshot = { ...metadata, ...report };
        if (C.canReview(state.me.user.role)) {
          try { reviews = await readReviews(id, s.signal); }
          catch (error) { if (error.name === 'AbortError') throw error; reviewError = error; }
        }
      }
      if (!s.active()) return;
      drawReport(content, snapshot, reviews, reviewError, sample, id, s);
    } catch (error) { showAsyncError(content, error, () => { content.replaceChildren(); reportView(content, s, id, sample); }, s); }
  }
  function drawReport(root, snapshot, reviews, reviewError, sample, id, s) {
    const result = C.reportResult(snapshot);
    if (!result) { replace(root, notice('This report has no result yet. Check the analysis status rather than treating missing data as zero.', 'warning'), !sample ? link('View analysis status →', `#/analysis/${encodeURIComponent(id)}`, 'button primary') : null); return; }
    const matches = C.collection(result.matches), citations = C.collection(result.citations);
    const downloads = el('div', { class: 'download-menu', 'aria-label': 'Download report' });
    if (!sample) ['pdf', 'json', 'html', 'csv'].forEach(format => { const control = button(format.toUpperCase() + ' ↓', () => downloadReport(id, format, control), 'outline'); downloads.append(control); });
    const summary = el('div', { class: 'report-summary' },
      el('div', { class: 'union' }, el('p', { class: 'metric-label' }, 'Union overlap'), el('strong', { class: 'metric-value' }, sample ? '—' : C.percent(result.totalOverlap)), el('p', { class: 'metric-foot' }, 'Exact + near, without double-counting')),
      el('div', {}, el('p', { class: 'metric-label' }, 'Exact overlap'), el('strong', { class: 'metric-value' }, sample ? '—' : C.percent(result.exactOverlap)), el('p', { class: 'metric-foot' }, 'Engine-reported exact text overlap')),
      el('div', {}, el('p', { class: 'metric-label' }, 'Near-text overlap'), el('strong', { class: 'metric-value' }, sample ? '—' : C.percent(result.nearOverlap)), el('p', { class: 'metric-foot' }, 'Not semantic similarity')),
      el('div', {}, el('p', { class: 'metric-label' }, 'Evidence matches'), el('strong', { class: 'metric-value' }, C.number(matches.length)), el('p', { class: 'metric-foot' }, sample ? 'Invented match for illustration' : `${C.number(result.wordCount)} submitted words`)));
    const docText = typeof snapshot.documentText === 'string' ? snapshot.documentText : '';
    const original = el('div', { class: 'document-text', tabindex: '0', 'aria-label': 'Submitted document with matching passages' });
    const matchList = el('div', { class: 'match-list' });
    const inspector = el('section', { class: 'panel match-inspector', 'aria-label': 'Selected match evidence' });
    let selected = matches.length ? 0 : -1;
    const markNodes = [], cards = [];
    function selectMatch(index, focus = false) {
      selected = index;
      cards.forEach((card, i) => card.setAttribute('aria-pressed', String(i === index)));
      markNodes.forEach(({ node, indices }) => node.classList.toggle('active', indices.includes(index)));
      drawInspector();
      if (focus) { inspector.scrollIntoView({ behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth', block: 'nearest' }); inspector.querySelector('h2')?.focus({ preventScroll: true }); }
    }
    if (docText) {
      C.segments(docText, matches).forEach(segment => {
        if (!segment.matches.length) { original.append(document.createTextNode(segment.text)); return; }
        const node = el('mark', { class: 'passage-mark', tabindex: '0', role: 'button', 'aria-label': `Match${segment.matches.length > 1 ? 'es' : ''} ${segment.matches.map(i => i + 1).join(', ')}: ${segment.text}` }, segment.text);
        const activate = () => selectMatch(segment.matches.includes(selected) ? segment.matches[(segment.matches.indexOf(selected) + 1) % segment.matches.length] : segment.matches[0], true);
        node.addEventListener('click', activate); node.addEventListener('keydown', event => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); activate(); } });
        markNodes.push({ node, indices: segment.matches }); original.append(node);
      });
    } else original.append(el('p', { class: 'muted' }, 'The server did not include the original document text. Evidence excerpts remain available below; highlights cannot be positioned.'));
    matches.forEach((match, index) => {
      const card = el('button', { type: 'button', class: 'match-card', 'aria-pressed': String(index === 0), onclick: () => selectMatch(index, true) },
        el('div', { class: 'match-card-top' }, el('span', { class: 'annotation-number' }, index + 1), el('span', { class: 'badge' }, text(match.method))),
        el('strong', {}, text(match.sourceTitle, `Source ${match.sourceId}`)), el('p', {}, text(match.submittedPassage)), el('small', {}, `Value: ${sample ? 'illustrative / not calculated' : C.number(match.value)}${match.page != null ? ` · page ${match.page}` : ''}`));
      cards.push(card); matchList.append(card);
    });
    if (!matches.length) matchList.append(empty('No matches returned', 'This is not a guarantee of originality. Read the limitations and check which sources were compared.'));
    const documentPanel = el('section', { class: 'panel document-panel' }, el('div', { class: 'document-toolbar' }, el('strong', {}, 'Submitted text'), el('span', { class: 'fine-print' }, 'Select a highlighted passage')), original);
    const sourcePanel = el('aside', { class: 'panel match-panel', 'aria-label': 'Evidence matches' }, el('div', { class: 'panel-header' }, el('h2', {}, 'Matching passages'), el('span', { class: 'badge' }, matches.length)), matchList);
    replace(root, sample ? notice('STATIC SYNTHETIC EXAMPLE — All passages and the source below are invented. No analysis was performed. Scores, exports, and reviewer saving are unavailable in this example.', 'sample-banner') : null,
      heading(text(snapshot.title, 'Evidence report'), 'Textual evidence for a considered review. Overlap alone is not a finding of plagiarism.', sample ? link(state.me ? 'Open workspace →' : 'Start your own review →', state.me ? '#/new' : '#/register', 'button primary') : downloads, sample ? 'Illustrative report / synthetic text' : 'Evidence report'),
      el('div', { class: 'report-meta' }, el('span', {}, `Profile: ${text(result.profile || snapshot.profile)}`), el('span', {}, `Engine: ${text(result.engineVersion)}`), !sample ? el('span', {}, `Completed: ${date(snapshot.completedAt)}`) : null, !sample ? el('span', {}, `Sources: ${C.number(snapshot.sourceCount)}`) : null), summary,
      el('p', { class: 'report-explanation' }, sample ? 'No numerical scores are shown for this synthetic illustration.' : 'Coverage fractions are displayed as percentages of normalized words. Exact coverage is counted first; near coverage adds only words outside the exact coverage. The total is their union, without double-counting. Match values below are raw method-specific engine values, not a misconduct probability.'),
      el('div', { class: 'report-layout' }, documentPanel, sourcePanel), matches.length ? inspector : null,
      el('div', { class: 'equal-columns report-footnotes' }, panel('Citation observations', citations.length ? el('ol', { class: 'citation-list' }, citations.map(citation => el('li', {}, text(citation.text), el('span', { class: 'cell-subtitle' }, `${text(citation.kind)} · ${citation.uncertain ? 'uncertain; verify manually' : 'engine observation; verify context'} · offsets ${text(citation.start)}–${text(citation.end)}`)))) : el('p', { class: 'fine-print' }, 'No citation observations were returned. This does not establish that citations are absent or correct.')),
        panel('Scope & limitations', el('ul', { class: 'limitations-list' }, C.collection(result.limitations).map(limit => el('li', {}, typeof limit === 'string' ? limit : JSON.stringify(limit))), el('li', {}, 'Comparison is limited to the selected sources and extracted text. Matching is not a verdict on attribution or intent.'), el('li', {}, `Semantic analysis: ${text(result.semanticStatus, 'unavailable')}. Semantic analysis is not offered by this interface.`), el('li', {}, `AI-authorship: ${text(result.aiWritingStatus, 'unavailable')}. AI-authorship detection is not offered by this interface.`), el('li', {}, 'OCR for scanned or image-only documents is unavailable.')))));
    if (matches.length) selectMatch(0);
    function drawInspector() {
      const match = matches[selected]; if (!match) return;
      const saved = reviews.filter(review => Number(review.matchIndex) === selected);
      const latest = saved[saved.length - 1];
      const title = el('h2', { tabindex: '-1' }, `Match ${selected + 1} · ${text(match.sourceTitle, 'Source evidence')}`);
      inspector.replaceChildren(title, el('div', { class: 'evidence-columns' },
        el('section', { class: 'evidence-excerpt' }, el('h3', {}, 'Submitted passage'), el('blockquote', {}, text(match.submittedPassage))),
        el('section', { class: 'evidence-excerpt' }, el('h3', {}, `Source · ${text(match.sourceType)}`), el('blockquote', {}, text(match.sourcePassage)))),
        el('div', { class: 'evidence-meta' }, el('span', {}, `Method: ${text(match.method)}`), el('span', {}, `Value: ${sample ? 'not calculated' : C.number(match.value)}`), el('span', {}, `Page (inferred): ${text(match.page)}`), el('span', {}, `Submitted offsets: ${text(match.start)}–${text(match.end)}`), el('span', {}, `Source offsets: ${text(match.sourceStart)}–${text(match.sourceEnd)}`), el('span', {}, `Source ID: ${text(match.sourceId)}`)),
        el('p', { class: 'notice' }, `Citation observation: ${text(match.citationObservation, 'No observation was returned. Inspect attribution manually.')}`));
      if (sample) { inspector.append(notice('Reviewer decisions in this static example cannot be saved. Open a real report to persist a decision and note.', 'warning')); return; }
      if (!C.canReview(state.me.user.role)) { inspector.append(notice('Reviewer decisions are read-only for your account. A reviewer or institution administrator role is required.', 'warning')); return; }
      if (reviewError) inspector.append(notice(`Saved reviews could not be loaded: ${reviewError.message}. Reload the report before editing to avoid overlooking an existing review.`, 'error'), button('Reload saved reviews', async () => { try { reviews = await readReviews(id, s.signal); reviewError = null; drawInspector(); } catch (error) { if (error.name !== 'AbortError') notify(error.message, true); } }, 'outline small'));
      const decision = filterSelect([['', 'Select a reviewer decision'], ['NEEDS_REVIEW', 'Needs review'], ['ACCEPTED', 'Acceptable overlap'], ['FLAGGED', 'Requires attention'], ['DISMISSED', 'Dismiss match']], 'Reviewer decision');
      if (latest?.decision && [...decision.options].some(option => option.value === latest.decision)) decision.value = latest.decision;
      else if (latest?.decision) { decision.append(el('option', { value: latest.decision }, latest.decision)); decision.value = latest.decision; }
      decision.required = true;
      const note = el('textarea', { name: 'note', maxlength: '4000', placeholder: 'Explain your interpretation, attribution checks, or follow-up.' }); note.value = latest?.note || '';
      const save = el('button', { type: 'submit', class: 'button primary', disabled: Boolean(reviewError) }, 'Save reviewer decision');
      const saveStatus = el('span', { class: 'fine-print', role: 'status' });
      const form = el('form', { class: 'review-form' }, field('Reviewer decision', decision), field('Reviewer note', note), el('div', { class: 'review-actions' }, save, saveStatus));
      const index = selected;
      form.addEventListener('submit', async event => {
        event.preventDefault(); save.disabled = true; saveStatus.textContent = 'Saving…';
        try {
          await api(`/analyses/${encodeURIComponent(id)}/reviews`, { method: 'POST', json: { matchIndex: index, decision: decision.value, note: note.value }, signal: s.signal });
          // Re-read persistence instead of displaying a locally invented saved record.
          reviews = await readReviews(id, s.signal);
          if (!s.active()) return;
          notify('Reviewer decision saved.'); if (selected === index) drawInspector();
        } catch (error) { if (error.name !== 'AbortError') { saveStatus.textContent = error.message; notify(error.message, true); } }
        finally { save.disabled = false; }
      });
      inspector.append(form);
      if (saved.length) inspector.append(el('div', { class: 'review-history' }, el('h3', {}, 'Saved reviewer records'), saved.map(review => el('div', {}, el('p', {}, el('strong', {}, text(review.decision)), review.createdAt ? ` · ${date(review.createdAt)}` : ''), el('p', {}, text(review.note, 'No note supplied.'))))));
    }
  }

  async function render() {
    if (state.scope) state.scope.close();
    const s = scope(); state.scope = s;
    const raw = location.hash.replace(/^#\/?/, '') || '';
    const [path, query = ''] = raw.split('?'); const parts = path.split('/'); const route = parts[0] || '';
    let id = ''; try { id = parts[1] ? decodeURIComponent(parts[1]) : ''; } catch (_) { notify('This address contains an invalid document or analysis ID.', true); location.hash = '#/'; return; }
    state.route = route;
    const params = new URLSearchParams(query);
    const publicRoutes = ['', 'capabilities', 'process', 'privacy', 'login', 'register', 'sample'];
    if (!state.sessionChecked) {
      try { state.me = await api('/auth/me', { signal: s.signal }); }
      catch (error) { if (error.name === 'AbortError') return; if (error.status !== 401) notify(`Session check unavailable: ${error.message}`, true); }
      state.sessionChecked = true;
    }
    if (!s.active()) return;
    if (!['capabilities', 'process', 'privacy'].includes(route)) window.scrollTo(0, 0);
    if (!publicRoutes.includes(route) && !state.me) { location.hash = '#/login'; return; }
    document.title = `${route === 'sample' ? 'Synthetic sample report' : routes.find(r => r[0] === route)?.[1] || (route === 'login' ? 'Sign in' : route === 'register' ? 'Create account' : route === 'reports' ? 'Evidence report' : 'Evidence, in context')} — Originality Desk`;
    try {
      if (['', 'capabilities', 'process', 'privacy'].includes(route)) {
        publicShell(); if (route) { const section = document.getElementById(route); section?.scrollIntoView(); section?.setAttribute('tabindex', '-1'); section?.focus({ preventScroll: true }); }
      } else if (route === 'login' || route === 'register') {
        if (state.me) { location.hash = '#/overview'; return; } authView(route);
      } else if (route === 'sample') {
        const main = el('main', { id: 'main', tabindex: '-1', class: 'report-public' }); publicShell(main); await reportView(main, s, '', true);
      } else {
        const main = shell(route);
        if (route === 'overview') await dashboard(main, s);
        else if (route === 'new' || route === 'compare') await analysisForm(main, s, route === 'compare', params);
        else if (route === 'documents' && id) await documentDetail(main, s, id);
        else if (route === 'documents' || route === 'references') await documentsView(main, s, route === 'references');
        else if (route === 'history' || (route === 'reports' && !id)) await historyView(main, s);
        else if (route === 'analysis' && id) await analysisStatus(main, s, id);
        else if (route === 'reports' && id) await reportView(main, s, id);
        else if (route === 'workspace') await workspaceView(main, s);
        else if (route === 'settings') settingsView(main);
        else if (route === 'admin') await adminView(main, s);
        else main.append(heading('This page is not on your desk.', 'The address may have changed. Use the navigation to find your documents and reports.'), link('Return to overview →', '#/overview', 'button primary'));
      }
    } catch (error) { if (s.active() && error.name !== 'AbortError') { const main = document.getElementById('main'); main?.append(errorBox(error, render)); } }

  }
  try { state.theme = localStorage.getItem('originality-desk-theme') || 'system'; } catch (_) { /* Private mode may disable storage. */ }
  setTheme(state.theme);
  window.addEventListener('hashchange', render);
  window.addEventListener('beforeunload', event => { if (state.xhr) { event.preventDefault(); event.returnValue = ''; } });
  // Keep the skip link a real in-page jump rather than treating it as a route.
  document.querySelector('.skip-link').addEventListener('click', event => { event.preventDefault(); document.getElementById('main')?.focus(); document.getElementById('main')?.scrollIntoView(); });
  render();
})();
