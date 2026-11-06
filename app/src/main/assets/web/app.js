'use strict';

// The Admin web page. All text comes from the app's own translations, through /api/status.
// Text from the database only ever reaches the page as textContent, never as HTML.

const REFRESH_MS = 15000;

const state = {
    texts: {},
    language: 'en',
    data: null,
    tab: 'items',
    itemQuery: '',
    itemCategory: '',
    itemStock: 'all',
    logQuery: '',
    logType: 'all',
    refreshTimer: null,
};

const app = document.getElementById('app');
const banner = document.getElementById('banner');
const toast = document.getElementById('toast');
let toastTimer = null;

// ---------------------------------------------------------------- helpers

/** A translated text. Fills in the placeholders of Android string resources (%1$s, %2$d). */
function t(key, ...args) {
    const text = state.texts[key] ?? key;
    return text.replace(/%(\d+)\$[sd]/g, (_, n) => String(args[Number(n) - 1] ?? ''));
}

/** Creates an element. Strings among the children become text nodes. */
function el(tag, props, ...children) {
    const node = document.createElement(tag);
    for (const [name, value] of Object.entries(props ?? {})) {
        if (value === undefined || value === null) continue;
        if (name === 'class') node.className = value;
        else if (name === 'data-label') node.setAttribute(name, value);
        else node[name] = value;
    }
    node.append(...children.flat().filter((child) => child !== null && child !== undefined));
    return node;
}

function money(value) {
    return value.toLocaleString(state.language, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function number(value) {
    return value.toLocaleString(state.language);
}

function showToast(text) {
    toast.textContent = text;
    toast.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { toast.hidden = true; }, 4000);
}

class ApiProblem extends Error {
    constructor(code, message) {
        super(message);
        this.code = code;
    }
}

/** Calls the tablet. Being signed out or having the password turned off sends the page back to the start. */
async function api(path, body) {
    let response;
    try {
        response = await fetch(path, body === undefined ? {} : {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body),
        });
    } catch (error) {
        banner.textContent = t('web_connection_lost');
        banner.hidden = false;
        throw new ApiProblem('offline', t('web_connection_lost'));
    }
    banner.hidden = true;
    const result = await response.json();
    if (response.ok) return result;
    if (result.error === 'signed_out' || result.error === 'password_off') {
        const problem = new ApiProblem(result.error, result.message);
        closeDialogs();
        start(result.error === 'signed_out' ? result.message : null);
        throw problem;
    }
    throw new ApiProblem(result.error, result.message);
}

function closeDialogs() {
    document.querySelectorAll('dialog').forEach((dialog) => dialog.remove());
}

// ------------------------------------------------------------------ start

async function start(notice) {
    clearInterval(state.refreshTimer);
    let status;
    try {
        status = await api('/api/status');
    } catch (error) {
        app.replaceChildren(el('div', { class: 'center-page' }, el('div', { class: 'sign-in' },
            el('img', { src: '/logo.png', alt: '' }),
            el('p', { class: 'error' }, error.message),
            el('button', { type: 'button', onclick: () => start() }, 'OK'))));
        return;
    }
    state.texts = status.texts;
    state.language = status.language;
    document.documentElement.lang = status.language;
    document.title = t('web_page_title');
    if (!status.passwordOn) showPasswordOff();
    else if (!status.signedIn) showSignIn(notice);
    else await showMain();
}

function showPasswordOff() {
    app.replaceChildren(el('div', { class: 'center-page' }, el('div', { class: 'sign-in' },
        el('img', { src: '/logo.png', alt: 'Mi’ clinic' }),
        el('h1', null, t('web_password_off_title')),
        el('p', null, t('web_password_off')),
        el('button', { type: 'button', onclick: () => start() }, t('web_check_again')))));
}

function showSignIn(notice) {
    const password = el('input', { type: 'password', autocomplete: 'current-password', required: true, autofocus: true });
    const message = el('p', { class: 'error' }, notice ?? '');
    message.hidden = !notice;
    const submit = el('button', { type: 'submit' }, t('sign_in'));
    const form = el('form', {
        class: 'sign-in',
        onsubmit: async (event) => {
            event.preventDefault();
            submit.disabled = true;
            message.hidden = true;
            try {
                await api('/api/login', { password: password.value });
                await start();
            } catch (error) {
                if (error.code === 'password_off') return;
                message.textContent = error.message;
                message.hidden = false;
                password.value = '';
                password.focus();
            } finally {
                submit.disabled = false;
            }
        },
    },
    el('img', { src: '/logo.png', alt: 'Mi’ clinic' }),
    el('h1', null, t('admin_sign_in')),
    el('label', null, t('password'), password),
    message,
    submit);
    app.replaceChildren(el('div', { class: 'center-page' }, form));
    password.focus();
}

// ------------------------------------------------------------------- main

const TABS = [
    { id: 'items', label: 'tab_items', toolbar: itemsToolbar, body: itemsBody },
    { id: 'categories', label: 'categories', toolbar: categoriesToolbar, body: categoriesBody },
    { id: 'users', label: 'tab_users', toolbar: usersToolbar, body: usersBody },
    { id: 'activity', label: 'tab_activity', toolbar: activityToolbar, body: activityBody },
    { id: 'reports', label: 'tab_reports', toolbar: () => null, body: reportsBody },
    { id: 'settings', label: 'tab_settings', toolbar: () => null, body: settingsBody },
];

let statsNode;
let tabBodyNode;
let navNode;

async function showMain() {
    state.data = await api('/api/data');
    statsNode = el('section', { class: 'stats' });
    tabBodyNode = el('div', { class: 'tab-body' });
    navNode = el('nav');
    const toolbarSlot = el('div', { id: 'toolbar-slot' });
    app.replaceChildren(
        el('header', { class: 'topbar' },
            el('img', { src: '/logo.png', alt: 'Mi’ clinic' }),
            el('h1', null, t('web_page_title')),
            el('span', { class: 'spacer' }),
            el('button', { type: 'button', class: 'secondary', onclick: signOut }, t('web_sign_out'))),
        navNode,
        el('main', null, statsNode, el('section', { class: 'panel' }, toolbarSlot, tabBodyNode)));
    selectTab(state.tab);
    state.refreshTimer = setInterval(() => { if (!document.hidden) refresh().catch(() => {}); }, REFRESH_MS);
}

async function signOut() {
    await api('/api/logout', {});
    state.data = null;
    await start();
}

function selectTab(id) {
    state.tab = id;
    navNode.replaceChildren(...TABS.map((tab) => el('button', {
        type: 'button',
        class: tab.id === id ? 'active' : '',
        onclick: () => selectTab(tab.id),
    }, t(tab.label))));
    const tab = TABS.find((candidate) => candidate.id === id);
    const toolbar = tab.toolbar();
    document.getElementById('toolbar-slot').replaceChildren(...(toolbar ? [toolbar] : []));
    renderData();
}

/** Redraws what depends on the data. Toolbars stay, so typing in a search box is never interrupted. */
function renderData() {
    renderStats();
    // Settings has number boxes in its body: a refresh must not wipe what is being typed in one.
    if (state.tab === 'settings' && tabBodyNode.contains(document.activeElement) && document.activeElement.type === 'number') return;
    const tab = TABS.find((candidate) => candidate.id === state.tab);
    tabBodyNode.replaceChildren(tab.body());
}

async function refresh() {
    const data = await api('/api/data');
    // The language was changed in the app's Settings: start again with the new texts.
    if (data.language !== state.language) {
        location.reload();
        return;
    }
    state.data = data;
    renderData();
}

/** Makes a change on the tablet, shows its result and redraws. Problems are thrown to the caller. */
async function change(path, body) {
    const result = await api(path, body);
    if (result.message) showToast(result.message);
    await refresh();
}

/** The same as [change], for buttons that have no dialog to show a problem in. */
function changeOrToast(path, body) {
    change(path, body).catch((error) => showToast(error.message));
}

function renderStats() {
    const items = state.data.items;
    const low = items.filter((item) => item.low).length;
    const stat = (label, value, warn) => el('div', { class: warn ? 'stat warn' : 'stat' },
        el('strong', null, value), el('span', { class: 'muted' }, label));
    statsNode.replaceChildren(
        stat(t('tab_items'), number(items.length)),
        stat(t('units_in_stock'), number(items.reduce((sum, item) => sum + item.quantity, 0))),
        stat(t('low_stock_alerts'), number(low), low > 0),
        stat(t('stock_value'), money(items.reduce((sum, item) => sum + item.value, 0))));
}

// ------------------------------------------------------------------ items

function itemsToolbar() {
    const search = el('input', {
        class: 'grow', type: 'search', placeholder: t('search_items'), value: state.itemQuery,
        oninput: () => { state.itemQuery = search.value; renderData(); },
    });
    const category = el('select', {
        onchange: () => { state.itemCategory = category.value; renderData(); },
    }, el('option', { value: '' }, t('all_items')),
    state.data.categories.map((entry) => el('option', { value: entry.name }, entry.name)));
    category.value = state.itemCategory;

    const chips = el('div', { class: 'chip-group' });
    const drawChips = () => chips.replaceChildren(...[
        ['all', t('filter_all')], ['low', t('stock_filter_low')], ['out', t('pill_out')],
    ].map(([id, label]) => el('button', {
        type: 'button',
        class: state.itemStock === id ? 'active' : '',
        onclick: () => { state.itemStock = id; drawChips(); renderData(); },
    }, label)));
    drawChips();

    return el('div', { class: 'toolbar' }, search, category, chips,
        el('button', { type: 'button', onclick: () => itemDialog(null) }, '+ ' + t('new_item')));
}

function matchingItems() {
    const query = state.itemQuery.trim().toLocaleLowerCase(state.language);
    return state.data.items.filter((item) => {
        if (state.itemCategory && item.category.toLowerCase() !== state.itemCategory.toLowerCase()) return false;
        if (state.itemStock === 'low' && !item.low) return false;
        if (state.itemStock === 'out' && !item.out) return false;
        if (!query) return true;
        return [item.name, item.sku, item.category, item.location]
            .some((text) => text.toLocaleLowerCase(state.language).includes(query));
    });
}

function itemsBody() {
    if (state.data.items.length === 0) return el('p', { class: 'empty' }, t('no_items_short'));
    const items = matchingItems();
    if (items.length === 0) return el('p', { class: 'empty' }, t('no_items_match'));
    return el('div', { class: 'table-wrap' }, el('table', { class: 'cards' },
        el('thead', null, el('tr', null,
            el('th', null, t('col_item')),
            el('th', null, t('category')),
            el('th', null, t('location')),
            el('th', { class: 'num' }, t('quantity')),
            el('th', { class: 'num' }, t('col_value')),
            el('th'))),
        el('tbody', null, items.map(itemRow))));
}

function itemRow(item) {
    const pill = item.out ? el('span', { class: 'pill out' }, t('pill_out'))
        : item.low ? el('span', { class: 'pill low' }, t('pill_low')) : null;
    return el('tr', null,
        el('td', null,
            el('span', { class: 'item-name' }, item.name),
            item.sku ? el('div', { class: 'sub' }, item.sku) : null),
        el('td', { 'data-label': t('category') }, item.category || '—'),
        el('td', { 'data-label': t('location') }, item.location || '—'),
        el('td', { class: 'num', 'data-label': t('quantity') }, number(item.quantity) + ' ' + item.unit, pill),
        el('td', { class: 'num', 'data-label': t('col_value') }, money(item.value)),
        el('td', { class: 'actions' },
            el('button', { type: 'button', class: 'small check-in', onclick: () => moveDialog(item, 'in') }, t('check_in')),
            el('button', { type: 'button', class: 'small check-out', disabled: item.quantity <= 0, onclick: () => moveDialog(item, 'out') }, t('check_out')),
            el('button', { type: 'button', class: 'small secondary', onclick: () => itemDialog(item) }, t('edit')),
            el('button', { type: 'button', class: 'small danger', onclick: () => removeItemDialog(item) }, t('remove'))));
}

function itemDialog(item) {
    const value = (name, fallback = '') => (item ? String(item[name] ?? fallback) : fallback);
    const field = (label, props, wide) => {
        const input = el('input', props);
        return [input, el('label', { class: wide ? 'wide' : '' }, label, input)];
    };
    const [name, nameLabel] = field(t('item_name_required'), { value: value('name'), required: true, maxLength: 200 }, true);
    const [sku, skuLabel] = field(t('sku_barcode'), { value: value('sku'), maxLength: 200 });
    const [location, locationLabel] = field(t('location'), { value: value('location'), maxLength: 200 });
    const [unit, unitLabel] = field(t('unit'), { value: value('unit', t('unit_pcs')), maxLength: 200 });
    const [quantity, quantityLabel] = field(t('initial_qty'), { type: 'number', min: 0, max: 1000000, step: 1, value: 0 });
    const [minimum, minimumLabel] = field(t('alert_level'), { type: 'number', min: 0, max: 1000000, step: 1, value: value('minQuantity', '0') });
    const [cost, costLabel] = field(t('unit_cost'), { inputMode: 'decimal', maxLength: 12, value: item && item.unitCost ? item.unitCost : '' });
    const [note, noteLabel] = field(t('note'), { value: value('note'), maxLength: 1000 }, true);

    const categoryNames = state.data.categories.map((entry) => entry.name);
    if (item && item.category && !categoryNames.includes(item.category)) categoryNames.push(item.category);
    const category = el('select', null,
        el('option', { value: '' }, t('no_category_option')),
        categoryNames.map((entry) => el('option', { value: entry }, entry)));
    category.value = value('category');

    openDialog({
        title: item ? t('edit_item') : t('add_item'),
        submit: t('save'),
        content: [
            el('div', { class: 'form-grid' }, nameLabel, skuLabel,
                el('label', null, t('category'), category),
                locationLabel, unitLabel, item ? null : quantityLabel, minimumLabel, costLabel, noteLabel),
            el('p', { class: 'sub' }, t('alert_level_hint', state.data.threshold)),
        ],
        onSubmit: () => change('/api/items', {
            id: item ? item.id : 0,
            name: name.value,
            sku: sku.value,
            category: category.value,
            location: location.value,
            unit: unit.value,
            quantity: quantity.value,
            minQuantity: minimum.value,
            unitCost: cost.value,
            note: note.value,
        }),
    });
}

function moveDialog(item, type) {
    const isIn = type === 'in';
    const quantity = el('input', { type: 'number', min: 1, max: 1000000, step: 1, value: 1, required: true });
    const note = el('input', { maxLength: 1000 });
    const after = el('p', { class: 'muted' });
    const form = openDialog({
        title: (isIn ? t('check_in') : t('check_out')) + ': ' + item.name,
        submit: isIn ? t('check_in') : t('check_out'),
        submitClass: isIn ? 'check-in' : 'check-out',
        content: [
            el('p', null, t('current_stock', item.quantity, item.unit)),
            el('label', null, t('quantity'), quantity),
            after,
            el('label', null, isIn ? t('note_supplier') : t('note_customer'), note),
        ],
        onSubmit: () => change('/api/stock', { id: item.id, type, quantity: quantity.value, note: note.value }),
    });
    const update = () => {
        const amount = Number.parseInt(quantity.value, 10) || 0;
        const result = item.quantity + (isIn ? amount : -amount);
        const tooMany = result < 0;
        after.className = tooMany ? 'error' : 'muted';
        after.textContent = tooMany ? t('not_enough_stock', item.quantity, item.unit) : t('stock_after', result, item.unit);
        form.submitButton.disabled = tooMany || amount <= 0;
    };
    quantity.addEventListener('input', update);
    update();
    quantity.select();
}

function removeItemDialog(item) {
    openDialog({
        title: t('remove_item_title', item.name),
        submit: t('remove'),
        danger: true,
        content: [el('p', null, item.quantity > 0
            ? t('remove_item_text_qty', item.quantity, item.unit) : t('remove_item_text'))],
        onSubmit: () => change('/api/items/delete', { id: item.id }),
    });
}

// ------------------------------------------------------------- categories

function addForm(label, path) {
    const name = el('input', { placeholder: label, required: true, maxLength: 200 });
    return el('form', {
        class: 'add-form',
        onsubmit: async (event) => {
            event.preventDefault();
            try {
                await change(path, { name: name.value });
                name.value = '';
                name.focus();
            } catch (error) {
                showToast(error.message);
            }
        },
    }, name, el('button', { type: 'submit' }, t('add')));
}

function categoriesToolbar() {
    return el('div', { class: 'panel-head' }, el('div', { class: 'add-form-wrap' }, addForm(t('category'), '/api/categories')));
}

function categoriesBody() {
    const categories = state.data.categories;
    if (categories.length === 0) return el('p', { class: 'empty' }, t('no_categories_web'));
    return el('div', { class: 'rows' }, categories.map((entry) => el('div', { class: 'row' },
        el('div', null, el('div', { class: 'item-name' }, entry.name), el('div', { class: 'sub' }, entry.countText)),
        el('button', {
            type: 'button', class: 'small danger',
            onclick: () => openDialog({
                title: t('delete_category_title', entry.name),
                submit: t('delete'),
                danger: true,
                content: [el('p', null, entry.count > 0
                    ? t('delete_category_text_items', entry.count) : t('delete_category_text_none'))],
                onSubmit: async () => {
                    await change('/api/categories/delete', { name: entry.name });
                    if (state.itemCategory === entry.name) state.itemCategory = '';
                },
            }),
        }, t('delete')))));
}

// ------------------------------------------------------------------ users

function usersToolbar() {
    return el('div', { class: 'panel-head' },
        el('p', { class: 'muted' }, t('users_intro')),
        addForm(t('add_user'), '/api/users'));
}

function usersBody() {
    return el('div', { class: 'rows' }, state.data.users.map((user) => el('div', { class: 'row' },
        el('div', { class: 'item-name' }, user.name),
        el('button', {
            type: 'button', class: 'small danger',
            onclick: () => openDialog({
                title: t('remove_user_title', user.name),
                submit: t('remove'),
                danger: true,
                content: [el('p', null, t('remove_user_text'))],
                onSubmit: () => change('/api/users/delete', { id: user.id }),
            }),
        }, t('remove')))));
}

// --------------------------------------------------------------- activity

function activityToolbar() {
    const search = el('input', {
        class: 'grow', type: 'search', placeholder: t('search_log'), value: state.logQuery,
        oninput: () => { state.logQuery = search.value; renderData(); },
    });
    const chips = el('div', { class: 'chip-group' });
    const drawChips = () => chips.replaceChildren(...[
        ['all', t('filter_all')], ['CHECK_IN', t('filter_check_in')],
        ['CHECK_OUT', t('filter_check_out')], ['items', t('filter_item_changes')],
    ].map(([id, label]) => el('button', {
        type: 'button',
        class: state.logType === id ? 'active' : '',
        onclick: () => { state.logType = id; drawChips(); renderData(); },
    }, label)));
    drawChips();
    return el('div', { class: 'toolbar' }, search, chips);
}

function activityBody() {
    const log = state.data.log;
    if (log.length === 0) return el('p', { class: 'empty' }, t('no_activity'));
    const query = state.logQuery.trim().toLocaleLowerCase(state.language);
    const entries = log.filter((entry) => {
        if (state.logType === 'items' && entry.type !== 'ITEM_ADDED' && entry.type !== 'ITEM_EDITED' && entry.type !== 'ITEM_REMOVED') return false;
        if (state.logType !== 'all' && state.logType !== 'items' && entry.type !== state.logType) return false;
        return !query || [entry.item, entry.user, entry.note].some((text) => text.toLocaleLowerCase(state.language).includes(query));
    });
    if (entries.length === 0) return el('p', { class: 'empty' }, t('no_entries_match'));
    return el('div', { class: 'table-wrap' }, el('table', { class: 'cards' },
        el('thead', null, el('tr', null,
            el('th', null, t('col_date_time')),
            el('th', null, t('col_user')),
            el('th', null, t('col_action')),
            el('th', null, t('col_item')),
            el('th', { class: 'num' }, t('col_change')),
            el('th', { class: 'num' }, t('col_qty_after')),
            el('th', null, t('col_note')))),
        el('tbody', null, entries.map((entry) => el('tr', null,
            el('td', null, new Date(entry.time).toLocaleString(state.language)),
            el('td', { 'data-label': t('col_user') }, entry.user),
            el('td', { 'data-label': t('col_action') }, entry.action),
            el('td', { 'data-label': t('col_item') }, entry.item),
            el('td', {
                class: 'num ' + (entry.change > 0 ? 'change-in' : entry.change < 0 ? 'change-out' : ''),
                'data-label': t('col_change'),
            }, entry.change > 0 ? '+' + entry.change : String(entry.change)),
            el('td', { class: 'num', 'data-label': t('col_qty_after') }, String(entry.after)),
            el('td', { 'data-label': t('col_note') }, entry.note))))));
}

// ---------------------------------------------------------------- reports

function reportsBody() {
    const thisYear = new Date().getFullYear();
    const year = el('select', null, [0, 1, 2, 3, 4].map((back) =>
        el('option', { value: thisYear - back }, String(thisYear - back))));
    return el('div', { class: 'reports' },
        el('div', { class: 'report' },
            el('h2', null, t('storage_report')),
            el('p', { class: 'muted' }, t('storage_report_text')),
            el('div', { class: 'report-actions' },
                el('a', { class: 'button', href: '/report/storage' }, t('web_download')))),
        el('div', { class: 'report' },
            el('h2', null, t('monthly_report')),
            el('p', { class: 'muted' }, t('monthly_report_text')),
            el('div', { class: 'report-actions' },
                el('label', null, t('web_year'), year),
                el('button', {
                    type: 'button',
                    onclick: () => { location.href = '/report/monthly?year=' + encodeURIComponent(year.value); },
                }, t('web_download')))));
}

// --------------------------------------------------------------- settings

function saveSetting(key, value) {
    changeOrToast('/api/settings', { key, value });
}

function settingCard(title, text, ...children) {
    return el('div', { class: 'setting' },
        el('h3', null, title),
        text ? el('p', { class: 'muted' }, text) : null,
        children);
}

function settingSection(title) {
    return el('h2', { class: 'setting-section' }, title);
}

function chipRow(chips) {
    return el('div', { class: 'chip-group' }, chips.map(({ label, active, color, onclick }) => el('button', {
        type: 'button',
        class: active ? 'active' : '',
        onclick,
    }, color ? swatch(color) : null, label)));
}

function swatch(color) {
    const node = el('span', { class: 'swatch' });
    node.style.backgroundColor = color;
    return node;
}

function settingSwitch(label, checked, onchange, disabled) {
    const input = el('input', {
        type: 'checkbox', checked, disabled: Boolean(disabled),
        onchange: () => onchange(input.checked),
    });
    return el('label', { class: 'switch' }, input, el('span', { class: 'switch-track' }), el('span', null, label));
}

/** A number box with its own Save button. */
function numberSetting(label, value, max, key) {
    const input = el('input', { type: 'number', min: 0, max, step: 1, value });
    return el('form', {
        class: 'number-setting',
        onsubmit: (event) => {
            event.preventDefault();
            input.blur();
            saveSetting(key, input.value);
        },
    }, el('label', null, label, input), el('button', { type: 'submit' }, t('save')));
}

function settingsBody() {
    const s = state.data.settings;
    return el('div', { class: 'settings' },
        el('p', { class: 'muted' }, t('web_settings_note')),

        settingCard(t('language_title'), t('language_text'), chipRow([
            { label: 'English', active: s.language === 'en', onclick: () => saveSetting('language', 'en') },
            { label: 'Lietuvių', active: s.language === 'lt', onclick: () => saveSetting('language', 'lt') },
        ])),
        schemeCard(s),

        settingSection(t('section_admin_users')),
        settingCard(t('hide_admin_title'), t('hide_admin_text'), settingSwitch(
            s.hideAdmin ? t('admin_hidden') : t('admin_shown'), s.hideAdmin, (on) => saveSetting('hideAdmin', on))),
        settingCard(t('timeout_title'), t('timeout_text'),
            chipRow([0, 1, 2, 5, 10, 15, 30].map((minutes) => ({
                label: minutes === 0 ? t('off') : t('minutes_short', minutes),
                active: s.inactivityMinutes === minutes,
                onclick: () => saveSetting('inactivityMinutes', minutes),
            }))),
            numberSetting(t('minutes_label'), s.inactivityMinutes, 1440, 'inactivityMinutes')),

        settingSection(t('section_stock')),
        settingCard(t('low_threshold_title'), t('low_threshold_text'),
            numberSetting(t('units_label'), s.lowStockThreshold, 100000, 'lowStockThreshold')));
}

function schemeCard(s) {
    const active = s.schemes.find((scheme) => scheme.id === s.activeScheme);
    return el('div', { class: 'setting' },
        el('h3', null, t('gui_scheme_title')),
        el('p', { class: 'muted' }, t('web_app_scheme_text')),
        el('p', { class: 'scheme-now' },
            t('web_app_scheme_now') + ' ', swatch(active.color), el('strong', null, active.label),
            s.randomScheme ? ' (' + t('web_app_scheme_random') + ')' : ''),
        chipRow([
            ...s.schemes.map((scheme) => ({
                label: scheme.label,
                color: scheme.color,
                active: !s.randomScheme && s.guiScheme === scheme.id,
                onclick: () => saveSetting('guiScheme', scheme.id),
            })),
            {
                label: t('scheme_random'),
                active: s.randomScheme,
                onclick: () => saveSetting('randomScheme', !s.randomScheme),
            },
        ]));
}

// ---------------------------------------------------------------- dialogs

/** A modal window with Cancel and a confirm button. A problem from onSubmit is shown inside it. */
function openDialog({ title, submit, danger, submitClass, content, onSubmit }) {
    const problem = el('p', { class: 'error' });
    problem.hidden = true;
    const submitButton = el('button', { type: 'submit', class: danger ? 'danger-solid' : submitClass ?? '' }, submit);
    const dialog = el('dialog');
    const form = el('form', {
        onsubmit: async (event) => {
            event.preventDefault();
            submitButton.disabled = true;
            problem.hidden = true;
            try {
                await onSubmit();
                dialog.close();
                dialog.remove();
            } catch (error) {
                problem.textContent = error.message;
                problem.hidden = false;
                submitButton.disabled = false;
            }
        },
    },
    el('h2', null, title),
    content,
    problem,
    el('div', { class: 'dialog-actions' },
        el('button', { type: 'button', class: 'secondary', onclick: () => { dialog.close(); dialog.remove(); } }, t('cancel')),
        submitButton));
    dialog.append(form);
    dialog.addEventListener('cancel', () => dialog.remove());
    document.body.append(dialog);
    dialog.showModal();
    return { submitButton };
}

start();
