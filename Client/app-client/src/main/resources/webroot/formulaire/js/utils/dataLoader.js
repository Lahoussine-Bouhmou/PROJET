/**
 * Charge et parse le XML 'semantsel.xml' pour en extraire une liste de définitions de champs HL7.
 *
 * @param {string} xmlUrl - URL du fichier XML (défaut: 'semantsel.xml')
 * @returns {Promise<Array<Object>>} tableau d'objets { sqlname, type, line, r, selm, f, s, defaults }
 */
export async function loadDefinitions(xmlUrl = 'data/semantsel.xml') {
  // 1) Charger le XML
  const resp = await fetch(xmlUrl);
  if (!resp.ok) {
    throw new Error(`Impossible de charger ${xmlUrl}`);
  }
  const xmlText = await resp.text();
  const xml     = new DOMParser().parseFromString(xmlText, 'application/xml');
  const defs    = [];

  xml.querySelectorAll('sqlelement').forEach(sqel => {
    const sqlname = sqel.getAttribute('sqlname');
    const type    = sqel.getAttribute('type');

    // Cas simple : attribut prio="0" directement sur <sqlelement>
    if (sqel.hasAttribute('prio')) {
      defs.push({
        sqlname,
        type,
        line:     sqel.getAttribute('line'),
        r:        +sqel.getAttribute('r'),
        selm:     +sqel.getAttribute('selm'),
        f:        +sqel.getAttribute('f'),
        s:        +sqel.getAttribute('s'),
        extraAttributes: []
      });
      return;
    }

    // Cas complexe : on prend le premier <element> trié par prio
    const els = Array.from(sqel.querySelectorAll('element'));
    if (!els.length) return;

    els.sort((a, b) => +a.getAttribute('prio') - +b.getAttribute('prio'));
    const el = els[0];

    const line  = el.getAttribute('line');
    const r     = +el.getAttribute('r');
    const selm  = +el.getAttribute('selm') || 0;
    const f     = +el.getAttribute('f');
    const s     = +el.getAttribute('s');
    const extraAttributes = [];

    // Parcours de tous les attributs f1, f2, … et leurs val1, val2, …
    Array.from(el.attributes).forEach(attr => {
      const m = attr.name.match(/^f(\d+)$/);
      if (!m) return;
      const idx = m[1];
      const fX  = +attr.value;
      const sX  = +el.getAttribute('s' + idx);
      const val = el.getAttribute('val' + idx);
      if (!isNaN(fX) && !isNaN(sX) && val != null) {
        extraAttributes.push({ f: fX, s: sX, val });
      }
    });

    defs.push({ sqlname, type, line, r, selm, f, s, extraAttributes });
  });

  return defs;
}

/**
 * Charge les options de champs depuis fieldOptions.json pour remplir les <select> dans le formulaire.
 *
 * @param {string} jsonUrl - URL du fichier JSON (défaut: 'fieldOptions.json')
 * @returns {Promise<Object>} objet où chaque clé est un sqlname, valeur = tableau d’options
 */
export async function loadFieldOptions(jsonUrl = 'data/fieldOptions.json') {
  const resp = await fetch(jsonUrl);
  if (!resp.ok) {
    throw new Error(`Impossible de charger ${jsonUrl}`);
  }
  return await resp.json();
}
