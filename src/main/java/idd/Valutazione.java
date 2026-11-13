package idd;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;

/**
 * Programma 3: esegue le query di test su uno o piu' indici e calcola le metriche
 * (precision, recall, F1, P@10, AP/MAP) rispetto al ground truth.
 *
 * Argomenti:
 *   0) file delle query       (docs/query.tsv:  id TAB query TAB descrizione)
 *   1) file del ground truth  (docs/qrels.csv:  query_id,file)
 *   2) cartella di uscita     (es. docs)
 *   3..) coppie  &lt;cartella-indice&gt; &lt;configurazione&gt;  da confrontare, es.
 *        indice/completo-standard standard indice/completo-italiano italiano
 *
 * Produce: valutazione.csv (una riga per query e configurazione) e
 *          dettaglio_valutazione.txt (primi 10 risultati di ogni query, R = rilevante).
 */
public class Valutazione {

    public static void main(String[] args) throws IOException {
        if (args.length < 5 || (args.length - 3) % 2 != 0) {
            System.err.println("Uso: Valutazione <query.tsv> <qrels.csv> <cartella-uscita> "
                    + "<indice1> <config1> [<indice2> <config2> ...]");
            System.exit(1);
        }
        List<String[]> query = leggiQuery(Paths.get(args[0]));
        Map<String, Set<String>> qrels = leggiQrels(Paths.get(args[1]));
        Path uscita = Paths.get(args[2]);
        Files.createDirectories(uscita);

        // config -> (query id -> metriche)
        Map<String, Map<String, Metriche>> risultati = new LinkedHashMap<>();

        try (PrintWriter csv = new PrintWriter(Files.newBufferedWriter(
                     uscita.resolve("valutazione.csv"), StandardCharsets.UTF_8));
             PrintWriter dettaglio = new PrintWriter(Files.newBufferedWriter(
                     uscita.resolve("dettaglio_valutazione.txt"), StandardCharsets.UTF_8))) {

            csv.println("configurazione;query_id;query;rilevanti;recuperati;rilevanti_recuperati;"
                    + "precision;recall;f1;p_at_10;ap;ms");

            for (int a = 3; a < args.length; a += 2) {
                Path cartellaIndice = Paths.get(args[a]);
                String configurazione = args[a + 1];
                Map<String, Metriche> perQuery = new LinkedHashMap<>();
                risultati.put(configurazione, perQuery);

                Analyzer analyzer = AnalyzerFactory.crea(configurazione);
                try (DirectoryReader reader = Indici.apri(cartellaIndice, configurazione)) {
                    IndexSearcher searcher = new IndexSearcher(reader);
                    StoredFields stored = searcher.storedFields();
                    int tutti = Math.max(1, reader.maxDoc());

                    System.out.println();
                    System.out.println("=== Configurazione '" + configurazione + "'  (" + cartellaIndice
                            + ", " + reader.numDocs() + " documenti) ===");
                    System.out.println(String.format(Locale.ROOT, "%-4s %5s %5s %5s  %-6s %-6s %-6s %-6s %-6s %7s  %s",
                            "id", "ril", "rec", "r&r", "P", "R", "F1", "P@10", "AP", "ms", "query"));
                    dettaglio.println("################ configurazione: " + configurazione + " ################");

                    for (String[] q : query) {
                        String id = q[0];
                        String testo = q[1];
                        Set<String> rilevanti = qrels.getOrDefault(id, Set.of());
                        List<String> classifica = new ArrayList<>();
                        double ms;
                        try {
                            Query lq = InterpreteQuery.interpreta(testo, analyzer);
                            long inizio = System.nanoTime();
                            // TUTTI i documenti che soddisfano la query, in ordine di score
                            TopDocs td = searcher.search(lq, tutti);
                            ms = (System.nanoTime() - inizio) / 1e6;
                            for (ScoreDoc sd : td.scoreDocs) {
                                classifica.add(stored.document(sd.doc).get(Campi.NOME));
                            }
                            dettaglio.println();
                            dettaglio.println(id + "  " + testo + "   ->   " + lq);
                        } catch (IllegalArgumentException | ParseException e) {
                            System.out.println(id + "  QUERY NON VALIDA: " + e.getMessage());
                            continue;
                        }
                        Metriche m = Metriche.calcola(classifica, rilevanti);
                        perQuery.put(id, m);

                        System.out.println(String.format(Locale.ROOT, "%-4s %5d %5d %5d  %-6s %-6s %-6s %-6s %-6s %7.1f  %s",
                                id, m.rilevanti, m.recuperati, m.rilevantiRecuperati,
                                Metriche.fmt(m.precision), Metriche.fmt(m.recall), Metriche.fmt(m.f1),
                                Metriche.fmt(m.pAt10), Metriche.fmt(m.averagePrecision), ms, testo));
                        csv.println(String.join(";", configurazione, id, testo.replace(";", ","),
                                String.valueOf(m.rilevanti), String.valueOf(m.recuperati),
                                String.valueOf(m.rilevantiRecuperati), Metriche.fmt(m.precision),
                                Metriche.fmt(m.recall), Metriche.fmt(m.f1), Metriche.fmt(m.pAt10),
                                Metriche.fmt(m.averagePrecision), String.format(Locale.ROOT, "%.1f", ms)));

                        for (int i = 0; i < Math.min(10, classifica.size()); i++) {
                            String nome = classifica.get(i);
                            dettaglio.println(String.format(Locale.ROOT, "   %2d. %s %s",
                                    i + 1, rilevanti.contains(nome) ? "R" : "-", nome));
                        }
                    }
                    stampaMedie(perQuery);
                }
            }
        }

        if (risultati.size() > 1) {
            stampaConfronto(query, risultati);
        }
        System.out.println();
        System.out.println("Scritti: " + uscita.resolve("valutazione.csv") + " e "
                + uscita.resolve("dettaglio_valutazione.txt"));
    }

    /** Medie "macro" (ogni query pesa uguale), escludendo i valori non definiti. */
    static void stampaMedie(Map<String, Metriche> perQuery) {
        System.out.println(String.format(Locale.ROOT,
                "MEDIA su %d query:  P=%s  R=%s  F1=%s  P@10=%s  MAP=%s",
                perQuery.size(),
                Metriche.fmt(media(perQuery, "p")), Metriche.fmt(media(perQuery, "r")),
                Metriche.fmt(media(perQuery, "f1")), Metriche.fmt(media(perQuery, "p10")),
                Metriche.fmt(media(perQuery, "ap"))));
    }

    static double media(Map<String, Metriche> perQuery, String quale) {
        double somma = 0;
        int n = 0;
        for (Metriche m : perQuery.values()) {
            double v;
            switch (quale) {
                case "p": v = m.precision; break;
                case "r": v = m.recall; break;
                case "f1": v = m.f1; break;
                case "p10": v = m.pAt10; break;
                default: v = m.averagePrecision; break;
            }
            if (!Double.isNaN(v)) {
                somma += v;
                n++;
            }
        }
        return n == 0 ? Double.NaN : somma / n;
    }

    /** Tabella affiancata: precision e recall di ogni configurazione, query per query. */
    static void stampaConfronto(List<String[]> query, Map<String, Map<String, Metriche>> risultati) {
        System.out.println();
        System.out.println("=== Confronto (P / R) ===");
        StringBuilder intest = new StringBuilder(String.format(Locale.ROOT, "%-4s", "id"));
        for (String conf : risultati.keySet()) {
            intest.append(String.format(Locale.ROOT, "  %-15s", conf));
        }
        System.out.println(intest + "  query");
        for (String[] q : query) {
            StringBuilder riga = new StringBuilder(String.format(Locale.ROOT, "%-4s", q[0]));
            for (Map<String, Metriche> perQuery : risultati.values()) {
                Metriche m = perQuery.get(q[0]);
                String cella = m == null ? "n.d." : Metriche.fmt(m.precision) + " / " + Metriche.fmt(m.recall);
                riga.append(String.format(Locale.ROOT, "  %-15s", cella));
            }
            System.out.println(riga + "  " + q[1]);
        }
    }

    /** Righe "id TAB query [TAB descrizione]"; righe vuote o che iniziano con # ignorate. */
    static List<String[]> leggiQuery(Path file) throws IOException {
        List<String[]> out = new ArrayList<>();
        for (String riga : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (riga.isBlank() || riga.startsWith("#")) {
                continue;
            }
            String[] campi = riga.split("\t");
            if (campi.length < 2) {
                throw new IllegalArgumentException("Riga non valida in " + file + ": " + riga);
            }
            out.add(new String[] {campi[0].trim(), campi[1].trim()});
        }
        return out;
    }

    /** CSV "query_id,file" con intestazione. */
    static Map<String, Set<String>> leggiQrels(Path file) throws IOException {
        Map<String, Set<String>> out = new HashMap<>();
        List<String> righe = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = 1; i < righe.size(); i++) {
            String riga = righe.get(i).trim();
            if (riga.isEmpty()) {
                continue;
            }
            String[] campi = riga.split(",", 2);
            out.computeIfAbsent(campi[0].trim(), x -> new HashSet<>()).add(campi[1].trim());
        }
        return out;
    }
}
