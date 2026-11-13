#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ground_truth.py  --  Fase 6: costruisce il ground truth per la valutazione.

Legge i "bisogni informativi" da docs/bisogni.json e, per ognuno, decide quali
documenti del corpus sono RILEVANTI applicando un criterio esplicito, scritto a
mano e indipendente da Lucene:

  - nome_regex : il nome del file deve corrispondere all'espressione regolare;
  - tutti      : lista di gruppi; il documento deve contenere almeno una forma
                 di OGNI gruppo (es. [["guerra","guerre"],["pace","paci"]]);
  - almeno_uno : lista di gruppi; basta una forma di UN gruppo;
  - nessuno    : lista di gruppi; il documento NON deve contenere nessuna forma;
  - frase      : lista di gruppi da trovare in posizioni consecutive.

Il testo viene confrontato dopo una normalizzazione minima e dichiarata:
minuscolo e divisione in parole su tutto cio' che non e' una lettera o una cifra
(quindi "l'amore" -> "l", "amore"). Gli accenti NON vengono tolti: le varianti
accentate sono elencate esplicitamente nel file JSON.

Le varianti (plurali, coniugazioni, forme accentate...) sono scelte da noi e
rappresentano il bisogno informativo: il ground truth NON e' "quello che trova
Lucene", ma quello che un utente vorrebbe trovare. Lucene, con i suoi analyzer,
puo' avvicinarsi piu' o meno a questo insieme: e' proprio cio' che misuriamo.

Uscite:
  docs/query.tsv               id TAB query TAB descrizione (letto dalla Valutazione Java)
  docs/qrels.csv               query_id,file  (una riga per documento rilevante)
  docs/ground_truth_report.txt numero di rilevanti per query e libri di provenienza

Uso (dalla cartella principale del progetto):
    python scripts/ground_truth.py
    python scripts/ground_truth.py --corpus corpus/completo
"""

import argparse
import csv
import json
import re
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TOKEN_RE = re.compile(r"[^\W_]+")  # sequenze di lettere/cifre (Unicode)


def tokenizza(testo):
    return TOKEN_RE.findall(testo.lower())


def contiene_frase(tokens, gruppi):
    primi = set(gruppi[0])
    n = len(gruppi)
    for i, t in enumerate(tokens):
        if t in primi and i + n <= len(tokens):
            if all(tokens[i + j] in set(gruppi[j]) for j in range(1, n)):
                return True
    return False


def rilevante(nome, tokens, insieme, criterio):
    if "nome_regex" in criterio and not re.search(criterio["nome_regex"], nome):
        return False
    for gruppo in criterio.get("tutti", []):
        if not any(f in insieme for f in gruppo):
            return False
    if "almeno_uno" in criterio:
        if not any(any(f in insieme for f in g) for g in criterio["almeno_uno"]):
            return False
    for gruppo in criterio.get("nessuno", []):
        if any(f in insieme for f in gruppo):
            return False
    if "frase" in criterio and not contiene_frase(tokens, criterio["frase"]):
        return False
    return True


def libro_da_nome(nome):
    return re.sub(r"-\d+\.txt$", "", nome)


def main():
    ap = argparse.ArgumentParser(description="Costruisce query.tsv e qrels.csv dai bisogni informativi")
    ap.add_argument("--bisogni", default=str(ROOT / "docs" / "bisogni.json"))
    ap.add_argument("--corpus", default=str(ROOT / "corpus" / "completo"))
    ap.add_argument("--docs", default=str(ROOT / "docs"))
    opts = ap.parse_args()

    corpus = Path(opts.corpus)
    docs = Path(opts.docs)
    docs.mkdir(parents=True, exist_ok=True)
    with open(opts.bisogni, encoding="utf-8") as f:
        bisogni = json.load(f)

    files = sorted(p for p in corpus.rglob("*.txt") if p.is_file())
    if not files:
        print("Nessun file .txt in {}".format(corpus))
        return 1
    print("Documenti nel corpus: {}".format(len(files)))

    documenti = []
    for p in files:
        tokens = tokenizza(p.read_text(encoding="utf-8"))
        documenti.append((p.name, tokens, set(tokens)))

    with open(docs / "query.tsv", "w", encoding="utf-8", newline="\n") as f:
        f.write("# id\tquery\tdescrizione  (generato da scripts/ground_truth.py)\n")
        for b in bisogni:
            f.write("{}\t{}\t{}\n".format(b["id"], b["query"], b["descrizione"]))

    righe_report = [
        "GROUND TRUTH  (corpus: {}, {} documenti)".format(corpus, len(files)),
        "Normalizzazione: minuscolo, divisione su caratteri non alfanumerici, accenti mantenuti.",
        "",
    ]
    vuote = []
    with open(docs / "qrels.csv", "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["query_id", "file"])
        for b in bisogni:
            ril = [nome for nome, tokens, insieme in documenti
                   if rilevante(nome, tokens, insieme, b["criterio"])]
            for nome in ril:
                w.writerow([b["id"], nome])
            libri = Counter(libro_da_nome(n) for n in ril)
            top = ", ".join("{} ({})".format(l, c) for l, c in libri.most_common(5))
            righe_report.append("{}  {:<38} rilevanti: {:>5}".format(b["id"], b["query"], len(ril)))
            righe_report.append("     bisogno : {}".format(b["descrizione"]))
            righe_report.append("     verifica: {}".format(b.get("cosa_verifica", "")))
            righe_report.append("     libri   : {}".format(top if top else "-"))
            righe_report.append("")
            if not ril:
                vuote.append(b["id"])

    if vuote:
        righe_report.append("ATTENZIONE: nessun documento rilevante per {}. "
                            "Recall non calcolabile: valuta di cambiare queste query.".format(", ".join(vuote)))
    testo = "\n".join(righe_report)
    (docs / "ground_truth_report.txt").write_text(testo + "\n", encoding="utf-8")
    print(testo)
    print("Scritti: docs/query.tsv, docs/qrels.csv, docs/ground_truth_report.txt")
    return 0


if __name__ == "__main__":
    sys.exit(main())
