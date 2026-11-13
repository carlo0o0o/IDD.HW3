package idd;

/**
 * Nomi dei campi dell'indice. Usare SEMPRE queste costanti (nell'indicizzatore,
 * nella mappa degli analyzer e nelle query): un errore di battitura nel nome di
 * un campo non genera nessun errore, Lucene lo tratta come un campo diverso.
 */
public final class Campi {

    /** Nome del file (es. "divina-commedia-042.txt"). TextField, memorizzato. */
    public static final String NOME = "nome";

    /** Testo del file. TextField, NON memorizzato (e' gia' sul disco). */
    public static final String CONTENUTO = "contenuto";

    /** Libro di provenienza (es. "divina-commedia"). StringField, memorizzato. */
    public static final String LIBRO = "libro";

    private Campi() {
    }
}
