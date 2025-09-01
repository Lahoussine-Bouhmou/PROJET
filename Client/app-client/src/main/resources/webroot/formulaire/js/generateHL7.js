import { loadDefinitions } from './utils/dataLoader.js';
import { prepareSegments } from './utils/segmentBuilder.js';

const FS = '|', CS = '^', SS = '&', RS = '~', CR = '\r';

// Remplit le tableau `arr` jusqu'à l'index `idx` et met `val` à la bonne position.
function setField(arr, idx, val) {
  while (arr.length < idx) arr.push('');
  arr[idx - 1] = val;
}

// Injecte `val` dans le composant `compIdx` et sous-composant `subIdx` de la chaîne HL7.
function setComponent(str, compIdx, subIdx, val) {
  const comps = str.split(CS);
  while (comps.length <= compIdx) comps.push('');
  if (subIdx > 0) {
    const subs = comps[compIdx].split(SS);
    while (subs.length <= subIdx) subs.push('');
    subs[subIdx] = val;
    comps[compIdx] = subs.join(SS);
  } else {
    comps[compIdx] = val;
  }
  return comps.join(CS);
}

// Construit le message HL7 à partir des définitions `defs`.
async function generateHL7(defs) {
  // Générer IDHL7 et DCRE automatiquement si vide
  const IDHL7 = document.getElementById('IDHL7');
  if (IDHL7) {
    IDHL7.value = 'MSG-' + Date.now();
  }

  const DCRE = document.getElementById('DCRE');
  if (DCRE && !DCRE.value) {
    const now = new Date();
    const pad2 = n => n.toString().padStart(2,'0');
    DCRE.value = [
      now.getFullYear(),
      pad2(now.getMonth()+1),
      pad2(now.getDate())
    ].join('-') + 'T' + pad2(now.getHours()) + ':' + pad2(now.getMinutes());
  }

  const segmentsInfo = prepareSegments(defs);

  const hl7Segments = [];
  for (const { line, fieldsByR, rKeys } of segmentsInfo) {
    const values = [];

    // MSH‑2 = ^~\&
    if (line === 'MSH') {
        setField(values, 1, `${CS}${RS}\\${SS}`);
    }

    // pour chaque numéro de champ
    rKeys.forEach(r => {
      const group = fieldsByR[r];

      // 1er cas: si def contient extraAttributes > ajouter les deux: userVal + val1
      if (group.some(def => def.extraAttributes && def.extraAttributes.length > 0)) {
        const reps = [];

        group.forEach(def => {
          const el = document.getElementById(def.sqlname);
          if (!el) return;
          let userVal = '';
          if (def.type === 'bool') {
            if (!el.value) return;
            userVal = el.value;
          } else if (def.type === 'date') {
            if (!el.value) return;
            const d = new Date(el.value);
            const pad = n => String(n).padStart(2,'0');

            const YYYY = d.getFullYear();
            const MM   = pad(d.getMonth() + 1);
            const DD   = pad(d.getDate());
            const hh   = pad(d.getHours());
            const mm   = pad(d.getMinutes());
            const ss   = pad(d.getSeconds());

            userVal = `${YYYY}${MM}${DD}${hh}${mm}${ss}`;
          } else {
            userVal = el.value.trim();
            if (!userVal) return;
          }
          // injecter la valeur utilisateur dans f,s
          let rep = setComponent('', def.f, def.s, userVal);
          // injecter la valeur val1
          def.extraAttributes.forEach(attrGroup => {
            rep = setComponent(rep, attrGroup.f, attrGroup.s, attrGroup.val);
          });
          reps.push(rep);
        });

        if (reps.length) {
          setField(values, +r, reps.join(RS));
        }
      }
      // 2ème cas: sinon logique selm
      else {
        const maxSelm = Math.max(...group.map(def => def.selm));
        // const reps = Array(maxSelm+1).fill('').map(_ => '');
        const reps = Array.from({ length: maxSelm + 1 }, () => '');

        group.forEach(def => {
          const el = document.getElementById(def.sqlname);
          if (!el) return;
          let val = '';
          if (def.type === 'bool') {
            if (!el.value) return;
            val = el.value;
          } else if (def.type === 'date') {
            if (!el.value) return;
            const d = new Date(el.value);
            const pad = n => String(n).padStart(2,'0');

            const YYYY = d.getFullYear();
            const MM   = pad(d.getMonth() + 1);
            const DD   = pad(d.getDate());
            const hh   = pad(d.getHours());
            const mm   = pad(d.getMinutes());
            const ss   = pad(d.getSeconds());

            val = `${YYYY}${MM}${DD}${hh}${mm}${ss}`;
          } else {
            val = el.value.trim();
            if (!val) return;
          }
          if (def.f > 0 || def.s > 0) {
            reps[def.selm] = setComponent(reps[def.selm], def.f, def.s, val);
          } else {
            reps[def.selm] = val;
          }
        });

        // ne garder que jusqu’au dernier(~) non vide
        const lastNonEmpty = reps.reduce((l, v, i) => v.trim() ? i : l, -1);
        if (lastNonEmpty >= 0) {
          const slice   = reps.slice(0, lastNonEmpty + 1);
          setField(values, +r, slice.join(RS));
        }
      }
    });

    // n’ajouter le segment que s’il y a un champ renseigné
    if (values.some(v => v)) {
      hl7Segments.push(line + FS + values.map(v => v||'').join(FS));
    }
  }

  // Concatène et affiche
  const hl7Message = hl7Segments.join(CR);
  document.getElementById('hl7Output').value = hl7Message;

   // vider le champ pour que le placeholder « IDHL7 » réapparaisse
    if (IDHL7) {
        IDHL7.value = '';
    }

  // Envoi du message HL7 via fetch POST
  try {
    const response = await fetch(
      '/app-client/servlet/socketSender?zs:fnc=send',
      {
        method: 'POST',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'
        },
        body: 'hl7msg=' + encodeURIComponent(hl7Message)
      }
    );

    const result = await response.text();
    if (response.ok) {
        alert(
          'Message HL7 envoyé avec succès\n' +
          `Status: ${response.status} ${response.statusText}\n` +
          `Réponse du serveur :\n${result}`
        );
    } else {
        alert(
          'Erreur lors de l’envoi HL7\n' +
          `Status: ${response.status} ${response.statusText}\n` +
          `Réponse du serveur :\n${result}`
        );
    }

  } catch (err) {
    console.error('Erreur lors de l’envoi HL7 :', err);
    alert(`Erreur technique lors de l’envoi HL7 : ${err.message}`);
  }
}

// Au chargement de la page, charger defs puis lier le bouton
document.addEventListener('DOMContentLoaded', async () => {
  try {
    const defs = await loadDefinitions();
    document.getElementById('generateBtn')
            .addEventListener('click', async () => { await generateHL7(defs); });
  } catch (err) {
    console.error(err);
    alert(err.message);
  }
});

