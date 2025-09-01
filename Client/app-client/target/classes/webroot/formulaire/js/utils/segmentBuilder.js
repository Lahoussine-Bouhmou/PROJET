/**
 * À partir d'une liste de définitions HL7,
 * renvoie un tableau de segments ordonnés, chacun avec :
 *  - line      : nom du segment (MSH, PID…)
 *  - fields    : définitions triées par r, selm, f, s
 *  - fieldsByR : mapping { [r]: [defs…] } regroupé par numéro de champ
 *  - rKeys     : liste triée des numéros de champ (r)
 */
export function prepareSegments(defs, segmentsOrder = ['MSH','EVN','PID','PV1','MRG','DRG']) {
  // 1) regrouper par segment
  const segMap = defs.reduce((map, def) => {
    (map[def.line] ??= []).push(def);
    return map;
  }, {});

  // 2) déterminer l'ordre des segments
  const allLines     = Object.keys(segMap);
  const orderedLines = [
    ...segmentsOrder.filter(l => allLines.includes(l)),
    ...allLines.filter(l => !segmentsOrder.includes(l))
  ];

  // 3) construire le tableau final
  return orderedLines.map(line => {
    // tri des définitions
    const fields = segMap[line]
      .slice()
      .sort((a, b) => (a.r - b.r) || (a.selm - b.selm) || (a.f - b.f) || (a.s - b.s));

    // regroupement par numéro de champ
    const fieldsByR = fields.reduce((map, def) => {
      (map[def.r] ??= []).push(def);
      return map;
    }, {});

    // liste triée des clés r
    const rKeys = Object.keys(fieldsByR).map(n => +n).sort((a, b) => a - b);

    return { line, fields, fieldsByR, rKeys };
  });
}
