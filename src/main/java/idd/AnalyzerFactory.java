package idd;

import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.it.ItalianAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;

/**
 * Costruisce l'analyzer da usare, per campo, sia in indicizzazione sia in ricerca.
 *
 * Configurazioni disponibili (per confrontarle nella valutazione):
 *   "standard" : nome -> StandardAnalyzer, contenuto -> StandardAnalyzer
 *   "italiano" : nome -> StandardAnalyzer, contenuto -> ItalianAnalyzer
 *
 * Il campo "nome" usa sempre StandardAnalyzer: spezza "divina-commedia-042.txt"
 * in divina / commedia / 042 / txt, mette tutto in minuscolo e NON fa stemming
 * ne' rimozione di stopword, perche' il nome del file non e' prosa italiana.
 *
 * Il campo "libro" e' uno StringField e non passa da nessun analyzer.
 */
public final class AnalyzerFactory {

    private AnalyzerFactory() {
    }

    public static Analyzer crea(String configurazione) {
        Analyzer perContenuto;
        switch (configurazione) {
            case "standard":
                perContenuto = new StandardAnalyzer();
                break;
            case "italiano":
                perContenuto = new ItalianAnalyzer();
                break;
            default:
                throw new IllegalArgumentException(
                        "Configurazione analyzer sconosciuta: '" + configurazione
                                + "' (valide: standard, italiano)");
        }

        // CHIAVE della mappa = nome del campo; VALORE = analyzer per quel campo.
        Map<String, Analyzer> perCampo = Map.of(
                Campi.NOME, new StandardAnalyzer(),
                Campi.CONTENUTO, perContenuto);

        // Il primo argomento e' l'analyzer di riserva per i campi non elencati.
        return new PerFieldAnalyzerWrapper(new StandardAnalyzer(), perCampo);
    }
}
