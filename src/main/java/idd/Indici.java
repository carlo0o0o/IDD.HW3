package idd;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.store.FSDirectory;

/**
 * Apertura di un indice in lettura, con un controllo di sicurezza.
 *
 * L'Indicizzatore scrive nei metadati dell'indice (commit user data) quale
 * configurazione di analyzer ha usato. Qui verifichiamo che chi cerca usi la
 * stessa: se l'indice e' stato costruito con ItalianAnalyzer e la query viene
 * analizzata con StandardAnalyzer, Lucene non da' errori ma non trova quasi
 * nulla. Meglio fermarsi subito con un messaggio chiaro.
 */
public final class Indici {

    /** Chiave usata nei metadati dell'indice. */
    public static final String CHIAVE_ANALYZER = "analyzer";

    private Indici() {
    }

    public static DirectoryReader apri(Path cartella, String configurazione) throws IOException {
        if (!Files.isDirectory(cartella)) {
            throw new IllegalArgumentException(
                    "Cartella dell'indice inesistente: " + cartella.toAbsolutePath()
                            + " (hai lanciato prima l'Indicizzatore?)");
        }
        DirectoryReader reader = DirectoryReader.open(FSDirectory.open(cartella));
        Map<String, String> metadati = reader.getIndexCommit().getUserData();
        String usato = metadati.get(CHIAVE_ANALYZER);
        if (usato == null) {
            System.err.println("ATTENZIONE: l'indice non dice con quale analyzer e' stato creato "
                    + "(indice di una versione precedente?). Verifica tu che sia '" + configurazione
                    + "', oppure rilancia l'Indicizzatore.");
        } else if (!usato.equals(configurazione)) {
            reader.close();
            throw new IllegalArgumentException("L'indice " + cartella + " e' stato creato con l'analyzer '"
                    + usato + "', ma hai indicato '" + configurazione + "'. Devono coincidere.");
        }
        return reader;
    }
}
