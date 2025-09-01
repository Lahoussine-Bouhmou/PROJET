import { loadDefinitions, loadFieldOptions } from './utils/dataLoader.js';
import { prepareSegments } from './utils/segmentBuilder.js';

(async () => {
  const container = document.getElementById('formContainer');
  try {
    // Charger définitions + options
    const [fieldOptions, defs] = await Promise.all([
      loadFieldOptions(),
      loadDefinitions()
    ]);

    const segments = prepareSegments(defs);

    // génération du formulaire
    segments.forEach(({ line, fields }) => {
      const fieldset = document.createElement('fieldset');
      fieldset.innerHTML = `<legend>${line}</legend>`;

      fields.forEach(fld => {
        const { sqlname, type } = fld;
        const label = document.createElement('label');
        label.htmlFor = sqlname;
        label.textContent = sqlname;

        // création de l'input/select
        let input;
        if (fieldOptions[sqlname]) {
          input = document.createElement('select');
          fieldOptions[sqlname].forEach(opt => {
            const o = document.createElement('option');
            o.value = opt.value;
            o.textContent = opt.text;
            input.appendChild(o);
          });
        } else {
          switch (type) {
            case 'date':
              input = document.createElement('input');
              input.type = 'datetime-local';
              break;
            case 'bool':
               input = document.createElement('select');
               [
                 { value: '',  text: '— Sélectionner —' },
                 { value: 'N', text: 'No (N)'           },
                 { value: 'Y', text: 'Yes (Y)'           }
               ].forEach(optDef => {
                 const o = document.createElement('option');
                 o.value       = optDef.value;
                 o.textContent = optDef.text;
                 input.appendChild(o);
               });
              break;
            case 'sexe':
              input = document.createElement('select');
              ['', 'F', 'M', 'O'].forEach(v => {
                const o = document.createElement('option');
                o.value = v;
                o.textContent = v || '—';
                input.appendChild(o);
              });
              break;
            default:
              input = document.createElement('input');
              input.type = 'text';
          }
        }

        input.id = sqlname;
        if (sqlname === 'IDHL7') {
          input.readOnly = true;
          input.style.backgroundColor = '#f0f0f0';
        }
        if (input.tagName === 'INPUT') {
            input.placeholder = sqlname;
        }
        fieldset.append(label, input, document.createElement('br'));
      });

      container.appendChild(fieldset);
    });

    // Bouton d’auto-complétion (depuis autocomplete.json pour tester rapidement)
    document.getElementById('autocompleteBtn').addEventListener('click', () => {
        fetch('data/autocomplete.json')
          .then(resp => {
            if (!resp.ok) throw new Error('autocomplete.json introuvable');
            return resp.json();
          })
          .then(champs => {
            Object.entries(champs).forEach(([sqlname, val]) => {
              const el = document.getElementById(sqlname);
              if (el) el.value = val;
            });
          })
          .catch(err => {
            console.error(err);
            alert('Erreur lors du chargement de autocomplete.json');
          });
    });
  } catch (err) {
    console.error(err);
    alert(err.message);
  }
})();
