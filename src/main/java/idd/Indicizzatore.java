package idd;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

/**
 * Programma 1: indicizza i file .txt di una cartella con due campi (nome, contenuto).
 *
 * Argomenti:
 *   0) cartella con i .txt da indicizzare   (es. corpus/n100)
 *   1) cartella dove scrivere l'indice      (es. indice/n100-italiano)
 *   2) configurazione analyzer              (standard | italiano)
 */
public class Indicizzatore {

    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("Uso: Indicizzatore <cartella-corpus> <cartella-indice> <standard|italiano>");
            System.exit(1);
        }
        Path cartellaCorpus = Paths.get(args[0]);
        Path cartellaIndice = Paths.get(args[1]);
        String configurazione = args[2];

        Analyzer analyzer = AnalyzerFactory.crea(configurazione);
        List<Path> files = elencaTxt(cartellaCorpus);
        System.out.println("File .txt trovati: " + files.size());

        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        // CREATE: se l'indice esiste gia' viene riscritto da zero (altrimenti i
        // documenti verrebbero aggiunti una seconda volta, perche' Lucene e' append-only).
        config.setOpenMode(OpenMode.CREATE);

        int indicizzati = 0;
        int errori = 0;
        long inizio = System.nanoTime();

        try (Directory dir = FSDirectory.open(cartellaIndice);
             IndexWriter writer = new IndexWriter(dir, config)) {
            for (Path file : files) {
                try {
                    String testo = Files.readString(file, StandardCharsets.UTF_8);
                    writer.addDocument(creaDocumento(file, testo));
                    indicizzati++;
                } catch (IOException e) {
                    errori++;
                    System.err.println("Saltato " + file.getFileName() + ": " + e);
                }
            }
            // Annotiamo nei metadati dell'indice quale analyzer abbiamo usato: Ricerca e
            // Valutazione lo controllano (vedi Indici.apri) per non mescolare analyzer diversi.
            writer.setLiveCommitData(Map.of(Indici.CHIAVE_ANALYZER, configurazione).entrySet());
            // Senza commit i documenti non sono visibili a chi legge l'indice.
            writer.commit();
        }

        double secondi = (System.nanoTime() - inizio) / 1e9;
        double mb = dimensioneCartella(cartellaIndice) / (1024.0 * 1024.0);

        System.out.println();
        System.out.println("=== Indicizzazione completata ===");
        System.out.println("Configurazione analyzer : " + configurazione);
        System.out.println("Documenti indicizzati   : " + indicizzati + " (errori: " + errori + ")");
        System.out.println(String.format(Locale.ROOT, "Tempo totale            : %.2f s", secondi));
        System.out.println(String.format(Locale.ROOT, "Dimensione indice       : %.2f MB", mb));
        // Riga pronta da copiare nella tabella dei tempi della relazione.
        System.out.println(String.format(Locale.ROOT, "RISULTATO;%s;%s;%d;%.3f;%.2f",
                cartellaCorpus.getFileName(), configurazione, indicizzati, secondi, mb));
    }

    /** Un Document per file, con i campi definiti in {@link Campi}. */
    static Document creaDocumento(Path file, String testo) {
        String nome = file.getFileName().toString();
        String libro = nome.replaceAll("-\\d+\\.txt$", "");

        Document doc = new Document();
        doc.add(new TextField(Campi.NOME, nome, Field.Store.YES));
        doc.add(new TextField(Campi.CONTENUTO, testo, Field.Store.NO));
        doc.add(new StringField(Campi.LIBRO, libro, Field.Store.YES));
        return doc;
    }

    static List<Path> elencaTxt(Path cartella) throws IOException {
        try (Stream<Path> s = Files.walk(cartella)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".txt"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    static long dimensioneCartella(Path cartella) throws IOException {
        try (Stream<Path> s = Files.walk(cartella)) {
            return s.filter(Files::isRegularFile).mapToLong(p -> p.toFile().length()).sum();
        }
    }
}
