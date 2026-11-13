package idd;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.Locale;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;

/**
 * Programma 2: legge una query da console, interroga l'indice e stampa i risultati.
 *
 * Argomenti:
 *   0) cartella dell'indice                (es. indice/completo-italiano)
 *   1) configurazione analyzer             (standard | italiano) - deve essere quella dell'indice
 *   2) [facoltativo] risultati da mostrare (default 10)
 *
 * Scrivi "esci" (o una riga vuota due volte, o Ctrl+Z / Ctrl+D) per terminare.
 */
public class Ricerca {

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Uso: Ricerca <cartella-indice> <standard|italiano> [k]");
            System.exit(1);
        }
        String configurazione = args[1];
        int k = args.length >= 3 ? Integer.parseInt(args[2]) : 10;

        Analyzer analyzer = AnalyzerFactory.crea(configurazione);

        try (DirectoryReader reader = Indici.apri(Paths.get(args[0]), configurazione)) {
            IndexSearcher searcher = new IndexSearcher(reader); // ranking di default: BM25
            StoredFields stored = searcher.storedFields();

            System.out.println("Indice: " + args[0] + "  |  documenti: " + reader.numDocs()
                    + "  |  analyzer: " + configurazione);
            System.out.println("Sintassi: nome <termini>  oppure  contenuto <termini>");
            System.out.println("          \"tra virgolette\" = frase esatta;  +termine = obbligatorio;  -termine = escluso");
            System.out.println("Scrivi 'esci' per terminare.");

            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
            while (true) {
                System.out.print("\n> ");
                System.out.flush();
                String riga = in.readLine();
                if (riga == null || riga.trim().equalsIgnoreCase("esci")) {
                    break;
                }
                if (riga.isBlank()) {
                    continue;
                }
                try {
                    Query query = InterpreteQuery.interpreta(riga, analyzer);
                    long inizio = System.nanoTime();
                    int totale = searcher.count(query);          // numero esatto di documenti trovati
                    TopDocs top = searcher.search(query, k);     // i primi k per punteggio
                    double ms = (System.nanoTime() - inizio) / 1e6;

                    // query.toString() mostra i termini DOPO l'analisi (es. contenuto:cavall)
                    System.out.println("Query Lucene      : " + query);
                    System.out.println(String.format(Locale.ROOT,
                            "Documenti trovati : %d   (%.1f ms)", totale, ms));
                    int pos = 1;
                    for (ScoreDoc sd : top.scoreDocs) {
                        Document doc = stored.document(sd.doc);
                        System.out.println(String.format(Locale.ROOT, "%3d. [score %7.3f]  %-34s (libro: %s)",
                                pos++, sd.score, doc.get(Campi.NOME), doc.get(Campi.LIBRO)));
                    }
                    if (totale > k) {
                        System.out.println("     ... e altri " + (totale - k));
                    }
                } catch (IllegalArgumentException | ParseException e) {
                    System.out.println("Query non valida: " + e.getMessage());
                }
            }
        }
        System.out.println("Fine.");
    }
}
