#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
prepara_corpus.py  --  Fase 2 del progetto Lucene (Ingegneria dei Dati)

Cosa fa:
  1. scarica da Project Gutenberg i libri elencati in scripts/libri.csv
     (salvando i file grezzi in raw/<id>.txt; se il file c'e' gia', non lo riscarica);
  2. toglie intestazione e licenza di Gutenberg;
  3. spezza ogni libro in "documenti" (capitoli, canti oppure blocchi di
     dimensione fissa), con ripiego automatico sui blocchi se i titoli non
     vengono riconosciuti;
  4. salva ogni documento come file .txt UTF-8 con nome <slug-libro>-<NNN>.txt (es. divina-commedia-042.txt)
     in corpus/completo/;
  5. crea sottoinsiemi ANNIDATI (n100 contenuto in n1000 contenuto in n5000...)
     con seed fisso, per misurare i tempi di indicizzazione su scale diverse;
  6. scrive il manifest (docs/manifest_completo.csv, e uno per ogni
     sottoinsieme) e un report (docs/report_corpus.txt).

Solo libreria standard, Python 3.8+.

Uso tipico (dalla cartella principale del progetto):
    python scripts/prepara_corpus.py
    python scripts/prepara_corpus.py --block-words 400 --sizes 100,1000,5000
    python scripts/prepara_corpus.py --solo-scarica
Se il download automatico fallisce per un libro, scaricalo a mano come testo
semplice (UTF-8) e salvalo come raw/<id>.txt: lo script lo usera' al posto del download.
"""

import argparse
import csv
import random
import re
import shutil
import sys
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

URL_TEMPLATES = [
    "https://www.gutenberg.org/cache/epub/{id}/pg{id}.txt",
    "https://www.gutenberg.org/files/{id}/{id}-0.txt",
    "https://www.gutenberg.org/ebooks/{id}.txt.utf-8",
]

START_RE = re.compile(
    r"\*\*\*\s*START OF (?:THE|THIS) PROJECT GUTENBERG EBOOK.*?\*\*\*", re.I | re.S
)
END_RE = re.compile(
    r"(?:\*\*\*\s*END OF (?:THE|THIS) PROJECT GUTENBERG EBOOK"
    r"|^\s*End of (?:the )?Project Gutenberg)",
    re.I | re.M,
)

# Titoli riconosciuti per le due strategie "strutturali".
HEADING_RES = {
    "capitoli": re.compile(r"^\s*(?:capitolo|cap\.?)\s+[IVXLCDM\d]+\b.*$", re.I | re.M),
    "canti": re.compile(
        r"^\s*(?:(?:Inferno|Purgatorio|Paradiso)\W+)?canto\s+[A-Za-zIVXLCDM\d]+\.?\s*$",
        re.I | re.M,
    ),
}


# --------------------------------------------------------------------------
# Download e lettura
# --------------------------------------------------------------------------
def scarica(book_id, raw_dir):
    dest = raw_dir / "{}.txt".format(book_id)
    if dest.exists() and dest.stat().st_size > 0:
        return dest
    for tpl in URL_TEMPLATES:
        url = tpl.format(id=book_id)
        try:
            req = urllib.request.Request(
                url,
                headers={"User-Agent": "Mozilla/5.0 (uso didattico, corso Ingegneria dei Dati)"},
            )
            with urllib.request.urlopen(req, timeout=60) as r:
                data = r.read()
            if data:
                dest.write_bytes(data)
                time.sleep(2)  # gentilezza verso il server
                return dest
        except Exception as e:  # noqa: BLE001
            print("   tentativo fallito ({}): {}".format(url, e))
    return None


def leggi(path):
    data = path.read_bytes()
    try:
        return data.decode("utf-8-sig"), "utf-8"
    except UnicodeDecodeError:
        return data.decode("latin-1"), "latin-1 (ATTENZIONE)"


def rimuovi_boilerplate(text):
    text = text.replace("\r\n", "\n").replace("\r", "\n")
    avvisi = []
    m = START_RE.search(text)
    if m:
        text = text[m.end():]
    else:
        avvisi.append("marker START non trovato (intestazione non rimossa)")
    m = END_RE.search(text)
    if m:
        text = text[: m.start()]
    else:
        avvisi.append("marker END non trovato (licenza finale non rimossa)")
    return text.strip(), avvisi


# --------------------------------------------------------------------------
# Spezzatura
# --------------------------------------------------------------------------
def conta(text):
    return len(text.split())


def spezza_per_titoli(text, regex):
    matches = list(regex.finditer(text))
    if not matches:
        return []
    starts = [m.start() for m in matches] + [len(text)]
    pezzi = []
    preambolo = text[: starts[0]].strip()
    if preambolo:
        pezzi.append(preambolo)
    for a, b in zip(starts, starts[1:]):
        pezzi.append(text[a:b].strip())
    return pezzi


def unisci_piccoli(pezzi, min_words):
    """Unisce in avanti i pezzi troppo corti (es. righe di indice)."""
    out, buf = [], ""
    for p in pezzi:
        buf = (buf + "\n\n" + p).strip() if buf else p
        if conta(buf) >= min_words:
            out.append(buf)
            buf = ""
    if buf:
        if out:
            out[-1] += "\n\n" + buf
        else:
            out.append(buf)
    return out


def _taglia_unita(unita, target):
    """Spezza un'unita' (paragrafo) troppo lunga per righe e, se serve, per parole."""
    if conta(unita) <= 2 * target:
        return [unita]
    righe = unita.split("\n")
    if len(righe) > 1:
        return spezza_in_blocchi("\n".join(righe), target, separatore="\n")
    parole = unita.split()
    return [" ".join(parole[i:i + target]) for i in range(0, len(parole), target)]


def spezza_in_blocchi(text, target, separatore=None):
    """Blocchi di ~target parole, tagliando a fine paragrafo (o riga)."""
    if separatore is None:
        unita = [u.strip() for u in re.split(r"\n\s*\n", text) if u.strip()]
        join = "\n\n"
    else:
        unita = [u.strip() for u in text.split(separatore) if u.strip()]
        join = separatore
    espanse = []
    for u in unita:
        if separatore is None:
            espanse.extend(_taglia_unita(u, target))
        else:
            espanse.append(u) if conta(u) <= 2 * target else espanse.extend(
                [" ".join(u.split()[i:i + target]) for i in range(0, conta(u), target)]
            )
    out, cur, n = [], [], 0
    for u in espanse:
        cur.append(u)
        n += conta(u)
        if n >= target:
            out.append(join.join(cur))
            cur, n = [], 0
    if cur:
        if out and n < target // 3:
            out[-1] += join + join.join(cur)
        else:
            out.append(join.join(cur))
    return out


def spezza_libro(text, strategia, opts):
    """Restituisce (lista_pezzi, descrizione_metodo)."""
    if strategia in HEADING_RES:
        grezzi = spezza_per_titoli(text, HEADING_RES[strategia])
        pezzi = unisci_piccoli(grezzi, opts.min_words)
        if len(pezzi) >= opts.min_titoli:
            finali, tagliati = [], False
            for p in pezzi:
                if conta(p) > opts.max_words:
                    finali.extend(spezza_in_blocchi(p, opts.block_words))
                    tagliati = True
                else:
                    finali.append(p)
            metodo = strategia + (" + blocchi (pezzi lunghi)" if tagliati else "")
            return finali, metodo
        return (
            spezza_in_blocchi(text, opts.block_words),
            "blocchi (RIPIEGO: solo {} {} riconosciuti)".format(len(pezzi), strategia),
        )
    return spezza_in_blocchi(text, opts.block_words), "blocchi"


# --------------------------------------------------------------------------
# Sottoinsiemi annidati
# --------------------------------------------------------------------------
def scrivi_manifest(path, righe):
    campi = ["file", "ebook_id", "titolo", "autore", "genere", "indice_pezzo", "parole", "metodo"]
    with open(path, "w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=campi)
        w.writeheader()
        for r in righe:
            w.writerow({k: r[k] for k in campi})


def crea_sottoinsiemi(righe, sizes, seed, corpus_dir, docs_dir):
    rng = random.Random(seed)
    ordine = list(righe)
    rng.shuffle(ordine)  # stesso ordine per tutti => sottoinsiemi annidati
    creati = []
    for n in sorted(sizes):
        if n >= len(righe):
            print("   [salto] n{} >= documenti disponibili ({}): usa 'completo'".format(n, len(righe)))
            continue
        d = corpus_dir / "n{}".format(n)
        if d.exists():
            shutil.rmtree(d)
        d.mkdir(parents=True)
        sel = sorted(ordine[:n], key=lambda r: r["file"])
        for r in sel:
            shutil.copy2(corpus_dir / "completo" / r["file"], d / r["file"])
        scrivi_manifest(docs_dir / "manifest_n{}.csv".format(n), sel)
        creati.append((n, len(sel)))
    return creati


# --------------------------------------------------------------------------
# Main
# --------------------------------------------------------------------------
def main():
    ap = argparse.ArgumentParser(description="Prepara il corpus (Gutenberg -> documenti .txt)")
    ap.add_argument("--libri", default=str(ROOT / "scripts" / "libri.csv"))
    ap.add_argument("--raw", default=str(ROOT / "raw"))
    ap.add_argument("--out", default=str(ROOT / "corpus"))
    ap.add_argument("--docs", default=str(ROOT / "docs"))
    ap.add_argument("--block-words", type=int, default=700, help="parole per blocco (default 700)")
    ap.add_argument("--min-words", type=int, default=150, help="sotto questa soglia un pezzo viene unito al successivo")
    ap.add_argument("--max-words", type=int, default=3000, help="sopra questa soglia un capitolo viene spezzato in blocchi")
    ap.add_argument("--min-titoli", type=int, default=5, help="titoli minimi per fidarsi di capitoli/canti")
    ap.add_argument("--sizes", default="100,1000,5000", help="dimensioni dei sottoinsiemi annidati")
    ap.add_argument("--seed", type=int, default=42)
    ap.add_argument("--solo-scarica", action="store_true")
    opts = ap.parse_args()

    raw_dir, out_dir, docs_dir = Path(opts.raw), Path(opts.out), Path(opts.docs)
    for d in (raw_dir, docs_dir):
        d.mkdir(parents=True, exist_ok=True)

    with open(opts.libri, encoding="utf-8", newline="") as f:
        libri = list(csv.DictReader(f))
    print("Libri in elenco: {}".format(len(libri)))

    # 1) download
    mancanti = []
    for b in libri:
        print("[{}] {} - {}".format(b["ebook_id"], b["autore"], b["titolo"]))
        if scarica(b["ebook_id"], raw_dir) is None:
            mancanti.append(b["ebook_id"])
    if mancanti:
        print("\nNON scaricati: {}. Scaricali a mano come testo UTF-8 e salvali in {} "
              "come <id>.txt, poi rilancia.".format(", ".join(mancanti), raw_dir))
    if opts.solo_scarica:
        return 0

    # 2-4) pulizia, spezzatura, scrittura
    completo = out_dir / "completo"
    if completo.exists():
        shutil.rmtree(completo)
    completo.mkdir(parents=True)

    righe, report = [], []
    contatore = 0
    for b in libri:
        path = raw_dir / "{}.txt".format(b["ebook_id"])
        if not path.exists():
            report.append((b, 0, 0, "-", "FILE GREZZO ASSENTE", ""))
            continue
        testo, codifica = leggi(path)
        testo, avvisi = rimuovi_boilerplate(testo)
        pezzi, metodo = spezza_libro(testo, b["strategia"], opts)
        parole_libro = 0
        for i, p in enumerate(pezzi, 1):
            contatore += 1
            nome = "{}-{:03d}.txt".format(b["slug"], i)
            (completo / nome).write_text(p + "\n", encoding="utf-8", newline="\n")
            w = conta(p)
            parole_libro += w
            righe.append({
                "file": nome, "ebook_id": b["ebook_id"], "titolo": b["titolo"],
                "autore": b["autore"], "genere": b["genere"],
                "indice_pezzo": i, "parole": w, "metodo": metodo,
            })
        note = "; ".join(avvisi + ([] if codifica == "utf-8" else ["codifica " + codifica]))
        report.append((b, len(pezzi), parole_libro, metodo, note, codifica))

    scrivi_manifest(docs_dir / "manifest_completo.csv", righe)

    # 5) sottoinsiemi annidati
    sizes = [int(x) for x in opts.sizes.split(",") if x.strip()]
    creati = crea_sottoinsiemi(righe, sizes, opts.seed, out_dir, docs_dir)

    # 6) report
    linee = []
    linee.append("REPORT CORPUS  (block-words={}, min-words={}, max-words={}, seed={})".format(
        opts.block_words, opts.min_words, opts.max_words, opts.seed))
    linee.append("")
    linee.append("{:<7}{:<28}{:>7}{:>9}  {}".format("ebook", "autore", "pezzi", "parole", "metodo / note"))
    for b, n, w, metodo, note, _ in report:
        linee.append("{:<7}{:<28}{:>7}{:>9}  {}{}".format(
            b["ebook_id"], b["autore"][:27], n, w, metodo, ("  | " + note) if note else ""))
    tot_w = sum(r[2] for r in report)
    linee.append("")
    linee.append("TOTALE documenti: {}   parole: {}".format(len(righe), tot_w))
    for n, k in creati:
        linee.append("sottoinsieme n{}: {} documenti".format(n, k))
    testo_report = "\n".join(linee)
    (docs_dir / "report_corpus.txt").write_text(testo_report + "\n", encoding="utf-8")
    print("\n" + testo_report)
    return 0


if __name__ == "__main__":
    sys.exit(main())
