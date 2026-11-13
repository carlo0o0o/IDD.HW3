# Progetto Lucene – Ingegneria dei Dati

Indicizzazione e ricerca full-text con Apache Lucene (Java) su un corpus di testi italiani
ricavati da Project Gutenberg, con valutazione tramite precision e recall.

**Autore:** _nome cognome_ – _matricola_ – _indirizzo email_
**Repository:** _URL GitHub_

## Struttura

```
progetto-lucene/
  scripts/
    libri.csv            elenco dei 15 libri (ebook_id, slug, titolo, autore, genere, strategia)
    prepara_corpus.py    scarica, pulisce e spezza i libri in documenti .txt
  raw/                   testi grezzi scaricati (NON versionati)
  corpus/                documenti generati (NON versionati, si rigenerano)
    completo/            tutti i documenti
    n100/ n1000/ ...     sottoinsiemi annidati (n100 ⊂ n1000 ⊂ ...)
  docs/
    manifest_completo.csv  una riga per documento: libro, autore, genere, parole, metodo
    manifest_n*.csv        manifest dei sottoinsiemi
    report_corpus.txt      numero di pezzi e parole per libro, ripiego sui blocchi, avvisi
```

## Fase 2 – preparare il corpus

Serve Python 3.8+ (solo libreria standard). Dalla cartella `progetto-lucene`:

```
python scripts/prepara_corpus.py
```

Opzioni utili:

- `--block-words 400` blocchi più piccoli → più documenti (default 700)
- `--sizes 100,1000,5000` dimensioni dei sottoinsiemi annidati
- `--seed 42` seme del campionamento (scrivilo nella relazione)
- `--solo-scarica` scarica soltanto

Se un download fallisce, salva il testo UTF-8 a mano come `raw/<ebook_id>.txt` e rilancia.

**Cosa controllare nel report** (`docs/report_corpus.txt`):

1. i libri con la dicitura `RIPIEGO`: i titoli (capitoli/canti) non sono stati riconosciuti e
   il libro è stato spezzato in blocchi. Va bene, ma annotalo nella relazione;
2. avvisi su marker START/END non trovati (intestazione o licenza ancora presenti);
3. avvisi di codifica (`latin-1`);
4. libri con pochissimi pezzi: valuta di sostituirli o di abbassare `--block-words`;
5. a campione, apri 5-6 documenti e verifica che i tagli siano sensati.

## Decisioni da riportare nella relazione

- fonte e licenza dei testi (Project Gutenberg, pubblico dominio negli USA);
- pulizia: rimozione intestazione e licenza; nomi file `<slug-libro>-<NNN>.txt` (es. `divina-commedia-042.txt`), così le query sul campo `nome` hanno un significato e il loro ground truth si ricava dal manifest;
- spezzatura: capitoli o canti dove riconosciuti, altrimenti blocchi di ~N parole a fine paragrafo;
  pezzi sotto 150 parole uniti al successivo, capitoli sopra 3000 parole spezzati;
- campionamento dei sottoinsiemi: casuale con seme fisso, annidato;
- definizione di rilevanza per la valutazione (da fissare nella Fase 6).

## Fase 4 – indicizzazione

Progetto Maven (Eclipse: *File → Import → Existing Maven Projects*). Lucene 9.12.1, compilazione Java 21.

Classe `idd.Indicizzatore`, argomenti: `<cartella-corpus> <cartella-indice> <standard|italiano>`, es.

```
corpus/completo indice/completo-italiano italiano
corpus/completo indice/completo-standard standard
```

Campi: `nome` (TextField, memorizzato), `contenuto` (TextField, non memorizzato), `libro` (StringField, memorizzato).
Analyzer (`AnalyzerFactory`): `nome` sempre StandardAnalyzer; `contenuto` StandardAnalyzer o ItalianAnalyzer.
L'analyzer usato viene scritto nei metadati dell'indice e verificato da Ricerca e Valutazione.
Tempi misurati: `docs/tempi_indicizzazione.csv`.

## Fase 5 – ricerca da console

Classe `idd.Ricerca`, argomenti: `<cartella-indice> <standard|italiano> [k]`, es. `indice/completo-italiano italiano`.
Sintassi: `nome <termini>` oppure `contenuto <termini>`; `"frase esatta"`, `+obbligatorio`, `-escluso`; `esci` per uscire.
Per ogni query stampa la query dopo l'analisi, il numero di documenti trovati e i primi k con score e libro.

## Fase 6 – valutazione

1. `docs/bisogni.json`: 14 bisogni informativi, ognuno con la query, la descrizione e un **criterio esplicito di rilevanza**
   (regex sul nome, forme da trovare, frasi, esclusioni), scritto a mano e indipendente da Lucene.
2. `python scripts/ground_truth.py` → `docs/query.tsv`, `docs/qrels.csv` (documenti rilevanti), `docs/ground_truth_report.txt`.
3. Classe `idd.Valutazione`, argomenti:
   ```
   docs/query.tsv docs/qrels.csv docs indice/completo-standard standard indice/completo-italiano italiano
   ```
   → precision, recall, F1, P@10, AP/MAP per query e configurazione: `docs/valutazione.csv`,
   primi 10 risultati di ogni query (R = rilevante): `docs/dettaglio_valutazione.txt`.

## Prossime fasi

- [x] Fase 3: sottoinsiemi (prodotti dallo script)
- [x] Fase 4: indicizzatore Lucene
- [x] Fase 5: programma di ricerca da console
- [x] Fase 6: query di test e valutazione (da eseguire e commentare)
- [ ] Fase 7: relazione
